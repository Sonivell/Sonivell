// Package ui é a interface do Sonivell GO, desenhada com o Gio no layout Ondas.
package ui

import (
	"image"
	"sync"
	"time"

	"gioui.org/font"
	"gioui.org/io/key"
	"gioui.org/layout"
	"gioui.org/op"
	"gioui.org/op/clip"
	"gioui.org/op/paint"
	"gioui.org/widget"
	"gioui.org/widget/material"

	"github.com/Sonivell/sonivell/desktop-go/internal/core"
	"github.com/Sonivell/sonivell/desktop-go/internal/i18n"
	"github.com/Sonivell/sonivell/desktop-go/internal/player"
)

// Route é a tela aberta na área principal (na ordem da barra lateral).
type Route int

const (
	RouteHome Route = iota
	RouteLibrary
	RouteGenres
	RoutePlaylists
	RouteFavorites
	RouteSettings
	routeCount
)

// LibTab é a aba da Biblioteca.
type LibTab int

const (
	TabTracks LibTab = iota
	TabAlbums
	TabArtists
)

// Hooks são as ações que dependem da janela (feitas pelo main).
type Hooks struct {
	Invalidate     func()
	SetCompact     func(bool)
	Rescan         func()
	PickFolder     func() (string, bool) // seletor de pastas do sistema (zenity/kdialog), se houver
	Minimize       func()
	ToggleMaximize func()
	Close          func()
	// StartMove e StartResize pedem ao sistema para mover/redimensionar (X11). Nulos no Wayland.
	StartMove   func()
	StartResize func(dir int)
}

// UI guarda o estado da interface entre os quadros.
type UI struct {
	th     *material.Theme
	pal    Palette
	ctl    *player.Controller
	store  *core.Store
	covers *core.Covers
	hooks  Hooks
	imgOps map[any]paint.ImageOp

	mu          sync.Mutex
	lib         *core.Library
	playlists   []core.Playlist
	audioServer string
	scanning    bool
	scanDone    int
	scanTotal   int
	noFFmpeg    bool
	maximized   bool
	x11         bool   // janela X11: a barra de título e as bordas pedem mover/redimensionar ao sistema
	picked      string // pasta escolhida no seletor (vem de outra goroutine)
	pendingDemo string
	searchCache struct {
		q   string
		lib *core.Library
		res []*core.Track
	}

	route      Route
	libTab     LibTab
	album      *core.Album
	group      *core.Group // artista, gênero, década ou playlist aberta
	groupKind  string      // rótulo acima do nome ("ARTISTA", "GÊNERO"…)
	showRecent bool
	nowPlaying bool
	compact    bool
	showQueue  bool
	moreOpen   bool

	// widgets
	nav        [routeCount]widget.Clickable
	search     widget.Editor
	clearSrch  widget.Clickable
	mainList   widget.List
	queueList  widget.List
	playBtn    widget.Clickable
	prevBtn    widget.Clickable
	nextBtn    widget.Clickable
	shufBtn    widget.Clickable
	repBtn     widget.Clickable
	likeBtn    widget.Clickable
	queueBtn   widget.Clickable
	compactBtn widget.Clickable
	openNow    widget.Clickable
	closeNow   widget.Clickable
	moreBtn    widget.Clickable
	menuCmp    widget.Clickable
	menuAlbum  widget.Clickable
	backBtn    widget.Clickable
	playAll    widget.Clickable
	shufAll    widget.Clickable
	openSet    widget.Clickable
	seeAll     widget.Clickable
	seek       widget.Float
	volume     widget.Float
	tabs       [3]widget.Clickable
	mixes      [3]widget.Clickable
	accentBtns []widget.Clickable
	addFolder  widget.Clickable
	pickFolder widget.Clickable
	rescanBtn  widget.Clickable
	folderEd   widget.Editor
	rmFolder   []widget.Clickable
	rows       clickPool
	chrome     chromeState
}

// clickPool dá um Clickable estável para cada chave (faixa, álbum…).
type clickPool struct{ m map[string]*widget.Clickable }

func (p *clickPool) get(key string) *widget.Clickable {
	if p.m == nil {
		p.m = map[string]*widget.Clickable{}
	}
	c := p.m[key]
	if c == nil {
		c = &widget.Clickable{}
		p.m[key] = c
	}
	return c
}

// New monta a interface.
func New(ctl *player.Controller, store *core.Store, covers *core.Covers, hooks Hooks) *UI {
	u := &UI{th: NewTheme(), ctl: ctl, store: store, covers: covers, hooks: hooks, imgOps: map[any]paint.ImageOp{}}
	u.pal = PaletteFor(store.Get().Accent)
	u.applyPalette()
	u.search.SingleLine = true
	u.search.Submit = true
	u.folderEd.SingleLine = true
	u.folderEd.Submit = true
	u.mainList.Axis = layout.Vertical
	u.queueList.Axis = layout.Vertical
	u.accentBtns = make([]widget.Clickable, len(Accents))
	return u
}

func (u *UI) applyPalette() {
	u.th.Palette = material.Palette{Bg: u.pal.Bg, Fg: u.pal.Text, ContrastBg: u.pal.Accent, ContrastFg: u.pal.OnAccent}
}

// SetLibrary troca a biblioteca e as playlists (chamado ao fim da leitura).
func (u *UI) SetLibrary(lib *core.Library, playlists []core.Playlist) {
	u.mu.Lock()
	u.lib = lib
	u.playlists = playlists
	u.scanning = false
	u.mu.Unlock()
}

// SetScanProgress mostra o andamento da leitura.
func (u *UI) SetScanProgress(scanning bool, done, total int) {
	u.mu.Lock()
	u.scanning, u.scanDone, u.scanTotal = scanning, done, total
	u.mu.Unlock()
}

// SetNoFFmpeg avisa que falta o ffmpeg.
func (u *UI) SetNoFFmpeg() {
	u.mu.Lock()
	u.noFFmpeg = true
	u.mu.Unlock()
}

// SetAudioServer informa o servidor de som (PipeWire, PulseAudio, ALSA), mostrado no player Vidro.
func (u *UI) SetAudioServer(name string) {
	u.mu.Lock()
	u.audioServer = name
	u.mu.Unlock()
}

// SetMaximized acompanha o estado da janela (para o botão maximizar/restaurar).
func (u *UI) SetMaximized(m bool) { u.maximized = m }

// SetX11 diz se a janela é X11 (mover/redimensionar pelo _NET_WM_MOVERESIZE) ou não (Wayland).
func (u *UI) SetX11(on bool) { u.x11 = on }

// IsCompact diz se a janela está no modo compacto.
func (u *UI) IsCompact() bool { return u.compact }

// Library devolve a biblioteca atual (pode ser nil durante a primeira leitura).
func (u *UI) Library() *core.Library {
	u.mu.Lock()
	defer u.mu.Unlock()
	return u.lib
}

func (u *UI) openGroup(g *core.Group, kind string) {
	u.group, u.groupKind, u.album, u.showRecent = g, kind, nil, false
	u.search.SetText("")
	u.mainList.Position = layout.Position{}
}

func (u *UI) closeDetail() {
	u.album, u.group, u.showRecent = nil, nil, false
}

// Layout desenha um quadro inteiro.
func (u *UI) Layout(gtx C) D {
	u.mu.Lock()
	demo := u.pendingDemo
	u.pendingDemo = ""
	u.mu.Unlock()
	if demo != "" {
		u.applyDemo(demo)
	}
	u.handleKeys(gtx)
	st := u.ctl.State()
	if st.Playing {
		gtx.Execute(op.InvalidateCmd{At: gtx.Now.Add(250 * time.Millisecond)})
	}
	paint.Fill(gtx.Ops, u.pal.Bg)

	var d D
	switch {
	case u.compact:
		d = u.compactView(gtx, st)
	case u.nowPlaying:
		d = u.nowPlayingView(gtx, st)
	default:
		d = layout.Flex{Axis: layout.Vertical}.Layout(gtx,
			layout.Rigid(u.titleBar),
			layout.Flexed(1, func(gtx C) D {
				return layout.Flex{}.Layout(gtx,
					layout.Rigid(u.sidebar),
					layout.Flexed(1, func(gtx C) D { return u.content(gtx, st) }),
					layout.Rigid(func(gtx C) D {
						if !u.showQueue {
							return D{}
						}
						return u.queuePanel(gtx, st)
					}),
				)
			}),
			layout.Rigid(func(gtx C) D {
				return layout.Inset{Left: 16, Right: 16, Bottom: 16, Top: 4}.Layout(gtx, func(gtx C) D { return u.playerBar(gtx, st) })
			}),
		)
	}
	u.windowEdges(gtx)
	return d
}

// handleKeys: espaço toca/pausa e Ctrl+setas pulam (quando a busca não está com o foco).
func (u *UI) handleKeys(gtx C) {
	if gtx.Focused(&u.search) || gtx.Focused(&u.folderEd) {
		return // digitando na busca: o espaço é do texto
	}
	for {
		ev, ok := gtx.Event(
			key.Filter{Name: key.NameSpace},
			key.Filter{Name: key.NameRightArrow, Required: key.ModCtrl},
			key.Filter{Name: key.NameLeftArrow, Required: key.ModCtrl},
		)
		if !ok {
			break
		}
		e, ok := ev.(key.Event)
		if !ok || e.State != key.Press {
			continue
		}
		switch e.Name {
		case key.NameSpace:
			u.ctl.TogglePlay()
		case key.NameRightArrow:
			u.ctl.Next()
		case key.NameLeftArrow:
			u.ctl.Previous()
		}
	}
}

// --- barra lateral (como no mockup: busca, cinco atalhos e Ajustes embaixo) ---

func (u *UI) sidebar(gtx C) D {
	w := gtx.Dp(248)
	gtx.Constraints = layout.Exact(image.Pt(w, gtx.Constraints.Max.Y))
	paint.FillShape(gtx.Ops, u.pal.SurfaceLow, clip.Rect{Max: gtx.Constraints.Max}.Op())

	for i := range u.nav {
		if u.nav[i].Clicked(gtx) {
			u.route = Route(i)
			u.closeDetail()
			u.nowPlaying = false
			u.search.SetText("")
			u.mainList.Position = layout.Position{}
		}
	}
	item := func(r Route, ic *Icon, txt string) layout.FlexChild {
		return layout.Rigid(func(gtx C) D {
			btn := &u.nav[r]
			sel := u.route == r && u.search.Text() == ""
			return layout.Inset{Bottom: 4}.Layout(gtx, func(gtx C) D {
				return btn.Layout(gtx, func(gtx C) D {
					gtx.Constraints.Min.X = gtx.Constraints.Max.X
					gtx.Constraints.Min.Y = gtx.Dp(44)
					bg := u.pal.SurfaceLow
					switch {
					case sel:
						bg = u.pal.SurfaceHigh
					case btn.Hovered():
						bg = u.pal.Surface
					}
					return surface(gtx, bg, 14, func(gtx C) D {
						gtx.Constraints.Min.Y = gtx.Dp(44)
						return layout.Inset{Left: 14, Right: 14}.Layout(gtx, func(gtx C) D {
							return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
								layout.Rigid(func(gtx C) D {
									if ic == nil {
										c := u.pal.Text3
										if sel {
											c = u.pal.Accent
										}
										return drawWave(gtx, 20, c, 2.2)
									}
									c := u.pal.Text3
									if sel {
										c = u.pal.Text
									}
									return drawIcon(gtx, ic, 20, c, false)
								}),
								layout.Rigid(layout.Spacer{Width: 12}.Layout),
								layout.Flexed(1, func(gtx C) D {
									c, wgt := u.pal.Text3, font.SemiBold
									if sel {
										c, wgt = u.pal.Text, font.Bold
									}
									return layout.W.Layout(gtx, u.label(txt, 14, wgt, c).Layout)
								}),
							)
						})
					})
				})
			})
		})
	}

	return layout.Inset{Top: 20, Bottom: 20, Left: 14, Right: 14}.Layout(gtx, func(gtx C) D {
		return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
			layout.Rigid(u.searchBox),
			layout.Rigid(layout.Spacer{Height: 14}.Layout),
			item(RouteHome, nil, i18n.T("nav_for_you")),
			item(RouteLibrary, IcBooks, i18n.T("nav_library")),
			item(RouteGenres, IcSquaresFour, i18n.T("nav_genres")),
			item(RoutePlaylists, IcPlaylist, i18n.T("nav_playlists")),
			item(RouteFavorites, IcHeart, i18n.T("nav_favorites")),
			layout.Flexed(1, layout.Spacer{}.Layout),
			item(RouteSettings, IcGearSix, i18n.T("nav_settings")),
		)
	})
}

func (u *UI) searchBox(gtx C) D {
	for {
		ev, ok := u.search.Update(gtx)
		if !ok {
			break
		}
		if _, ok := ev.(widget.ChangeEvent); ok {
			u.closeDetail()
		}
	}
	if u.clearSrch.Clicked(gtx) {
		u.search.SetText("")
	}
	gtx.Constraints.Min.X = gtx.Constraints.Max.X
	return surface(gtx, u.pal.Surface, 14, func(gtx C) D {
		gtx.Constraints.Min.Y = gtx.Dp(44)
		return layout.Inset{Left: 14, Right: 8}.Layout(gtx, func(gtx C) D {
			return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
				layout.Rigid(func(gtx C) D { return drawIcon(gtx, IcMagnifyingGlass, 18, u.pal.Text2, false) }),
				layout.Rigid(layout.Spacer{Width: 10}.Layout),
				layout.Flexed(1, func(gtx C) D {
					e := material.Editor(u.th, &u.search, i18n.T("search_hint"))
					e.Font = font.Font{Typeface: typeface, Weight: font.Medium}
					e.TextSize = 13
					e.Color = u.pal.Text
					e.HintColor = u.pal.Text2
					e.SelectionColor = withAlpha(u.pal.Accent, 0x60)
					return e.Layout(gtx)
				}),
				layout.Rigid(func(gtx C) D {
					if u.search.Text() == "" {
						return D{}
					}
					return u.iconButton(gtx, &u.clearSrch, IcX, 28, 14, u.pal.Text2, false)
				}),
			)
		})
	})
}

// list desenha uma lista rolável com a barra de rolagem discreta do tema.
func (u *UI) list(gtx C, l *widget.List, n int, item layout.ListElement) D {
	ls := material.List(u.th, l)
	ls.Indicator.Color = withAlpha(u.pal.Text, 0x30)
	ls.Indicator.HoverColor = withAlpha(u.pal.Text, 0x50)
	ls.Indicator.MinorWidth = 6
	ls.Indicator.CornerRadius = 3
	return ls.Layout(gtx, n, item)
}

func sectionTitle(u *UI, txt string) layout.Widget {
	return func(gtx C) D {
		return layout.Inset{Top: 8, Bottom: 14}.Layout(gtx, u.label(txt, 19, font.ExtraBold, u.pal.Text).Layout)
	}
}

// Demo abre uma tela pronta para as capturas de tela do CI (SONIVELL_GO_DEMO=<tela>).
// Telas: home, library, album, artist, genres, playlists, nowplaying, compact, settings, queue.
func (u *UI) Demo(screen string) {
	u.mu.Lock()
	u.pendingDemo = screen
	u.mu.Unlock()
	if u.hooks.Invalidate != nil {
		u.hooks.Invalidate()
	}
}

func (u *UI) applyDemo(screen string) {
	lib := u.Library()
	if lib == nil || len(lib.Tracks) == 0 {
		return
	}
	switch screen {
	case "home", "queue", "nowplaying", "compact":
		u.ctl.PlayFrom(i18n.T("mix_all"), lib.Tracks, 0)
	}
	switch screen {
	case "library":
		u.route, u.libTab = RouteLibrary, TabAlbums
	case "album":
		u.route = RouteLibrary
		if len(lib.Albums) > 0 {
			u.album = lib.Albums[0]
			u.ctl.PlayFrom(u.album.Title, u.album.Tracks, 1)
		}
	case "artist":
		u.route = RouteLibrary
		if len(lib.Artists) > 0 {
			u.openGroup(&lib.Artists[0], i18n.T("kicker_artist"))
		}
	case "genres":
		u.route = RouteGenres
	case "playlists":
		u.route = RoutePlaylists
	case "nowplaying":
		u.nowPlaying = true
	case "compact":
		u.compact = true
		if u.hooks.SetCompact != nil {
			u.hooks.SetCompact(true)
		}
	case "settings":
		u.route = RouteSettings
	case "queue":
		u.showQueue = true
	}
}
