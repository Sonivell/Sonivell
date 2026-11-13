package mpris

import (
	"os"
	"testing"

	"github.com/godbus/dbus/v5"

	"github.com/Sonivell/sonivell/desktop-go/internal/core"
	"github.com/Sonivell/sonivell/desktop-go/internal/player"
)

type quiet struct{ playing bool }

func (q *quiet) Load(string, int64, bool) {}
func (q *quiet) SetPlaying(p bool)        { q.playing = p }
func (q *quiet) PositionMs() int64        { return 1500 }
func (q *quiet) SetVolume(float64)        {}
func (q *quiet) Stop()                    {}

// Roda só com um D-Bus de sessão (no CI: dbus-run-session -- go test ./...).
func TestMPRISRoundTrip(t *testing.T) {
	if os.Getenv("DBUS_SESSION_BUS_ADDRESS") == "" {
		t.Skip("sem D-Bus de sessão")
	}
	t.Setenv("XDG_CONFIG_HOME", t.TempDir())
	out := &quiet{}
	ctl := player.New(out, core.LoadStore())
	srv, err := Start(ctl, nil)
	if err != nil {
		t.Fatal(err)
	}
	defer srv.Close()
	ctl.PlayList([]*core.Track{{ID: 1, Path: "/m/a.flac", Title: "Maré", Artist: "Lua", DurationMs: 90_000}}, 0)

	conn, err := dbus.ConnectSessionBus()
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	obj := conn.Object(busName, objectPath)

	var status dbus.Variant
	if err := obj.Call(ifaceProps+".Get", 0, ifacePlay, "PlaybackStatus").Store(&status); err != nil {
		t.Fatal(err)
	}
	if status.Value() != "Playing" {
		t.Fatalf("PlaybackStatus = %v", status.Value())
	}
	var meta dbus.Variant
	if err := obj.Call(ifaceProps+".Get", 0, ifacePlay, "Metadata").Store(&meta); err != nil {
		t.Fatal(err)
	}
	m := meta.Value().(map[string]dbus.Variant)
	if m["xesam:title"].Value() != "Maré" {
		t.Fatalf("título = %v", m["xesam:title"])
	}
	if err := obj.Call(ifacePlay+".PlayPause", 0).Err; err != nil {
		t.Fatal(err)
	}
	if ctl.State().Playing || out.playing {
		t.Fatal("PlayPause não pausou")
	}
	if err := obj.Call(ifacePlay+".Seek", 0, int64(2_000_000)).Err; err != nil {
		t.Fatalf("Seek (exportado de SeekBy): %v", err)
	}
	var id dbus.Variant
	if err := obj.Call(ifaceRoot+".Raise", 0).Err; err != nil {
		t.Fatal(err)
	}
	if err := obj.Call(ifaceProps+".Get", 0, ifaceRoot, "Identity").Store(&id); err != nil || id.Value() != "Sonivell GO" {
		t.Fatalf("Identity = %v (%v)", id.Value(), err)
	}
}
