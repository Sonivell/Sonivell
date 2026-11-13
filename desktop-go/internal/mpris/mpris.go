// Package mpris publica o Sonivell GO no D-Bus da sessão (org.mpris.MediaPlayer2), para as
// teclas de mídia e o controle de música do GNOME/KDE funcionarem.
package mpris

import (
	"fmt"
	"net/url"
	"sync"

	"github.com/godbus/dbus/v5"
	"github.com/godbus/dbus/v5/introspect"

	"github.com/Sonivell/sonivell/desktop-go/internal/player"
)

const (
	busName    = "org.mpris.MediaPlayer2.sonivellgo"
	objectPath = dbus.ObjectPath("/org/mpris/MediaPlayer2")
	ifaceRoot  = "org.mpris.MediaPlayer2"
	ifacePlay  = "org.mpris.MediaPlayer2.Player"
	ifaceProps = "org.freedesktop.DBus.Properties"
)

// Server responde ao D-Bus em nome do Controller.
type Server struct {
	conn  *dbus.Conn
	ctl   *player.Controller
	raise func()

	mu   sync.Mutex
	last map[string]dbus.Variant
}

// Start conecta e registra o nome. raise traz a janela para a frente.
func Start(ctl *player.Controller, raise func()) (*Server, error) {
	conn, err := dbus.ConnectSessionBus()
	if err != nil {
		return nil, err
	}
	s := &Server{conn: conn, ctl: ctl, raise: raise}
	if err := conn.Export(root{s}, objectPath, ifaceRoot); err != nil {
		return nil, err
	}
	if err := conn.ExportWithMap(controls{s}, map[string]string{"SeekBy": "Seek"}, objectPath, ifacePlay); err != nil {
		return nil, err
	}
	if err := conn.Export(props{s}, objectPath, ifaceProps); err != nil {
		return nil, err
	}
	playMethods := introspect.Methods(controls{s})
	for i := range playMethods {
		if playMethods[i].Name == "SeekBy" {
			playMethods[i].Name = "Seek" // exportado como Seek (ExportWithMap acima)
		}
	}
	node := &introspect.Node{Name: string(objectPath), Interfaces: []introspect.Interface{
		introspect.IntrospectData,
		{Name: ifaceProps, Methods: introspect.Methods(props{s})},
		{Name: ifaceRoot, Methods: introspect.Methods(root{s})},
		{Name: ifacePlay, Methods: playMethods},
	}}
	if err := conn.Export(introspect.NewIntrospectable(node), objectPath, "org.freedesktop.DBus.Introspectable"); err != nil {
		return nil, err
	}
	reply, err := conn.RequestName(busName, dbus.NameFlagDoNotQueue)
	if err != nil {
		return nil, err
	}
	if reply != dbus.RequestNameReplyPrimaryOwner {
		return nil, fmt.Errorf("mpris: nome %s já em uso", busName)
	}
	s.last = s.playerProps()
	ctl.OnChange(s.changed)
	return s, nil
}

// Close solta o nome.
func (s *Server) Close() { _ = s.conn.Close() }

func (s *Server) rootProps() map[string]dbus.Variant {
	return map[string]dbus.Variant{
		"CanQuit":             dbus.MakeVariant(false),
		"CanRaise":            dbus.MakeVariant(true),
		"HasTrackList":        dbus.MakeVariant(false),
		"Identity":            dbus.MakeVariant("Sonivell GO"),
		"DesktopEntry":        dbus.MakeVariant("sonivell-go"),
		"SupportedUriSchemes": dbus.MakeVariant([]string{"file"}),
		"SupportedMimeTypes":  dbus.MakeVariant([]string{"audio/mpeg", "audio/flac", "audio/ogg", "audio/opus", "audio/mp4"}),
	}
}

func (s *Server) playerProps() map[string]dbus.Variant {
	st := s.ctl.State()
	status := "Stopped"
	if st.Current != nil {
		status = "Paused"
		if st.Playing {
			status = "Playing"
		}
	}
	loop := "None"
	switch st.Repeat {
	case player.RepeatAll:
		loop = "Playlist"
	case player.RepeatOne:
		loop = "Track"
	}
	meta := map[string]dbus.Variant{"mpris:trackid": dbus.MakeVariant(dbus.ObjectPath("/org/mpris/MediaPlayer2/TrackList/NoTrack"))}
	if t := st.Current; t != nil {
		meta = map[string]dbus.Variant{
			"mpris:trackid": dbus.MakeVariant(dbus.ObjectPath(fmt.Sprintf("/com/sonivell/track/%d", t.ID))),
			"mpris:length":  dbus.MakeVariant(t.DurationMs * 1000),
			"xesam:title":   dbus.MakeVariant(t.Title),
			"xesam:artist":  dbus.MakeVariant([]string{t.DisplayArtist()}),
			"xesam:album":   dbus.MakeVariant(t.Album),
			"xesam:url":     dbus.MakeVariant((&url.URL{Scheme: "file", Path: t.Path}).String()),
		}
	}
	has := st.Current != nil
	return map[string]dbus.Variant{
		"PlaybackStatus": dbus.MakeVariant(status),
		"LoopStatus":     dbus.MakeVariant(loop),
		"Shuffle":        dbus.MakeVariant(st.Shuffle),
		"Volume":         dbus.MakeVariant(st.Volume),
		"Metadata":       dbus.MakeVariant(meta),
		"Position":       dbus.MakeVariant(s.ctl.PositionMs() * 1000),
		"Rate":           dbus.MakeVariant(1.0),
		"MinimumRate":    dbus.MakeVariant(1.0),
		"MaximumRate":    dbus.MakeVariant(1.0),
		"CanGoNext":      dbus.MakeVariant(has),
		"CanGoPrevious":  dbus.MakeVariant(has),
		"CanPlay":        dbus.MakeVariant(has),
		"CanPause":       dbus.MakeVariant(has),
		"CanSeek":        dbus.MakeVariant(has),
		"CanControl":     dbus.MakeVariant(true),
	}
}

// changed avisa o D-Bus só do que mudou (Position nunca: os clientes perguntam).
func (s *Server) changed() {
	now := s.playerProps()
	s.mu.Lock()
	diff := map[string]dbus.Variant{}
	for k, v := range now {
		if k == "Position" {
			continue
		}
		if old, ok := s.last[k]; !ok || old.String() != v.String() {
			diff[k] = v
		}
	}
	s.last = now
	s.mu.Unlock()
	if len(diff) > 0 {
		_ = s.conn.Emit(objectPath, ifaceProps+".PropertiesChanged", ifacePlay, diff, []string{})
	}
}

// --- org.mpris.MediaPlayer2 ---

type root struct{ s *Server }

func (r root) Raise() *dbus.Error {
	if r.s.raise != nil {
		r.s.raise()
	}
	return nil
}
func (r root) Quit() *dbus.Error { return nil }

// --- org.mpris.MediaPlayer2.Player ---

type controls struct{ s *Server }

func (c controls) Next() *dbus.Error      { c.s.ctl.Next(); return nil }
func (c controls) Previous() *dbus.Error  { c.s.ctl.Previous(); return nil }
func (c controls) Pause() *dbus.Error     { c.s.ctl.SetPlaying(false); return nil }
func (c controls) Play() *dbus.Error      { c.s.ctl.SetPlaying(true); return nil }
func (c controls) PlayPause() *dbus.Error { c.s.ctl.TogglePlay(); return nil }
func (c controls) Stop() *dbus.Error      { c.s.ctl.Stop(); return nil }
func (c controls) SeekBy(offsetUs int64) *dbus.Error {
	c.s.ctl.SeekTo(c.s.ctl.PositionMs() + offsetUs/1000)
	return nil
}
func (c controls) SetPosition(_ dbus.ObjectPath, posUs int64) *dbus.Error {
	c.s.ctl.SeekTo(posUs / 1000)
	return nil
}
func (c controls) OpenUri(string) *dbus.Error { return nil }

// --- org.freedesktop.DBus.Properties (valores sempre atuais) ---

type props struct{ s *Server }

func (p props) all(iface string) (map[string]dbus.Variant, *dbus.Error) {
	switch iface {
	case ifaceRoot:
		return p.s.rootProps(), nil
	case ifacePlay:
		return p.s.playerProps(), nil
	}
	return nil, dbus.MakeFailedError(fmt.Errorf("interface desconhecida: %s", iface))
}

func (p props) Get(iface, name string) (dbus.Variant, *dbus.Error) {
	m, err := p.all(iface)
	if err != nil {
		return dbus.Variant{}, err
	}
	v, ok := m[name]
	if !ok {
		return dbus.Variant{}, dbus.MakeFailedError(fmt.Errorf("propriedade desconhecida: %s", name))
	}
	return v, nil
}

func (p props) GetAll(iface string) (map[string]dbus.Variant, *dbus.Error) { return p.all(iface) }

func (p props) Set(iface, name string, v dbus.Variant) *dbus.Error {
	if iface != ifacePlay {
		return nil
	}
	switch name {
	case "Volume":
		if f, ok := v.Value().(float64); ok {
			p.s.ctl.SetVolume(f)
		}
	case "Shuffle":
		if b, ok := v.Value().(bool); ok && b != p.s.ctl.State().Shuffle {
			p.s.ctl.ToggleShuffle()
		}
	case "LoopStatus":
		if want, ok := v.Value().(string); ok {
			target := map[string]player.Repeat{"None": player.RepeatOff, "Playlist": player.RepeatAll, "Track": player.RepeatOne}[want]
			for i := 0; i < 3 && p.s.ctl.State().Repeat != target; i++ {
				p.s.ctl.CycleRepeat()
			}
		}
	}
	return nil
}
