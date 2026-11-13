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
	"gioui.org/unit"
	"gioui.org/widget"
	"gioui.org/widget/material"

	"github.com/Sonivell/sonivell/desktop-go/internal/core"
	"github.com/Sonivell/sonivell/desktop-go/internal/i18n"
	"github.com/Sonivell/sonivell/desktop-go/internal/player"
)

// Route é a tela aberta na área principal.
type Route int

const (
	RouteHome Route = iota
	RouteLibrary
	RouteFavorites
	RouteSettings
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
	Invalidate func()
	SetCompact func(bool)
	Rescan     func()
	PickFolder func() (string, bool) // seletor de pastas do sistema (zenity/kdialog), se houver
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
	scanning    bool
	scanDone    int
	scanTotal   int
	noFFmpeg    bool
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
	artist     *core.Group
	nowPlaying bool
	compact    bool
	showQueue  bool

	// widgets
	nav        [4]widget.Clickable
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
	backBtn    widget.Clickable
	playAll    widget.Clickable
	shufAll    widget.Clickable
	openSet    widget.Clickable
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

// SetLibrary troca a biblioteca (chamado ao fim da leitura).
func (u *UI) SetLibrary(lib *core.Library) {
	u.mu.Lock()
	u.lib = lib
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

// IsCompact diz se a janela está no modo compacto.
func (u *UI) IsCompact() bool { return u.compact }

// Library devolve a biblioteca atual (pode ser nil durante a primeira leitura).
func (u *UI) Library() *core.Library {
	u.mu.Lock()
	defer u.mu.Unlock()
	return u.lib
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

	switch {
	case u.compact:
		return u.compactView(gtx, st)
	case u.nowPlaying:
		return u.nowPlayingView(gtx, st)
	}
	return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
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
			return layout.Inset{Left: 16, Right: 16, Bottom: 16}.Layout(gtx, func(gtx C) D { return u.playerBar(gtx, st) })
		}),
	)
}

// handleKeys: espaço toca/pausa e as setas pulam (quando a busca não está com o foco).
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

// --- barra lateral ---

func (u *UI) sidebar(gtx C) D {
	w := gtx.Dp(248)
	gtx.Constraints = layout.Exact(image.Pt(w, gtx.Constraints.Max.Y))
	paint.FillShape(gtx.Ops, u.pal.SurfaceLow, clip.Rect{Max: gtx.Constraints.Max}.Op())

	for i := range u.nav {
		if u.nav[i].Clicked(gtx) {
			u.route = Route(i)
			u.album, u.artist, u.nowPlaying = nil, nil, false
			u.search.SetText("")
		}
	}
	item := func(i int, ic *Icon, txt string, wave bool) layout.FlexChild {
		return layout.Rigid(func(gtx C) D {
			btn := &u.nav[i]
			sel := u.route == Route(i) && u.search.Text() == ""
			return layout.Inset{Bottom: 4}.Layout(gtx, func(gtx C) D {
				return btn.Layout(gtx, func(gtx C) D {
					gtx.Constraints.Min.X = gtx.Constraints.Max.X
					bg := u.pal.SurfaceLow
					switch {
					case sel:
						bg = u.pal.SurfaceHigh
					case btn.Hovered():
						bg = u.pal.Surface
					}
					return surface(gtx, bg, 14, func(gtx C) D {
						return layout.Inset{Top: 12, Bottom: 12, Left: 14, Right: 14}.Layout(gtx, func(gtx C) D {
							return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
								layout.Rigid(func(gtx C) D {
									if wave {
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
									return drawIcon(gtx, ic, 20, c, sel)
								}),
								layout.Rigid(layout.Spacer{Width: 12}.Layout),
								layout.Flexed(1, func(gtx C) D {
									c, wgt := u.pal.Text3, font.SemiBold
									if sel {
										c, wgt = u.pal.Text, font.Bold
									}
									return u.label(txt, 14, wgt, c).Layout(gtx)
								}),
							)
						})
					})
				})
			})
		})
	}

	return layout.Inset{Top: 20, Bottom: 16, Left: 14, Right: 14}.Layout(gtx, func(gtx C) D {
		return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
			layout.Rigid(u.brand),
			layout.Rigid(layout.Spacer{Height: 20}.Layout),
			layout.Rigid(u.searchBox),
			layout.Rigid(layout.Spacer{Height: 14}.Layout),
			item(int(RouteHome), nil, i18n.T("nav_for_you"), true),
			item(int(RouteLibrary), IcVinylRecord, i18n.T("nav_library"), false),
			item(int(RouteFavorites), IcHeart, i18n.T("nav_favorites"), false),
			layout.Flexed(1, layout.Spacer{}.Layout),
			item(int(RouteSettings), IcGearSix, i18n.T("nav_settings"), false),
		)
	})
}

// brand é o nome do app com a descrição: "Sonivell GO · Reescrito em Go".
func (u *UI) brand(gtx C) D {
	return layout.Inset{Left: 6}.Layout(gtx, func(gtx C) D {
		return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
			layout.Rigid(func(gtx C) D {
				return box(gtx, 36, 36, func(gtx C) D {
					fillRRect(gtx, u.pal.SurfaceHigh, 10)
					return layout.Center.Layout(gtx, func(gtx C) D { return drawWave(gtx, 26, u.pal.Accent, 2.4) })
				})
			}),
			layout.Rigid(layout.Spacer{Width: 12}.Layout),
			layout.Flexed(1, func(gtx C) D {
				return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
					layout.Rigid(func(gtx C) D {
						return layout.Flex{Alignment: layout.Baseline}.Layout(gtx,
							layout.Rigid(u.label("Sonivell", 17, font.ExtraBold, u.pal.Text).Layout),
							layout.Rigid(layout.Spacer{Width: 5}.Layout),
							layout.Rigid(u.label("GO", 17, font.ExtraBold, u.pal.Accent).Layout),
						)
					}),
					layout.Rigid(func(gtx C) D {
						l := u.para(i18n.T("app_tagline"), 11, u.pal.Text2)
						l.MaxLines = 2
						return l.Layout(gtx)
					}),
				)
			}),
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
			u.album, u.artist = nil, nil
		}
	}
	if u.clearSrch.Clicked(gtx) {
		u.search.SetText("")
	}
	return surface(gtx, u.pal.Surface, 14, func(gtx C) D {
		return layout.Inset{Top: 11, Bottom: 11, Left: 14, Right: 8}.Layout(gtx, func(gtx C) D {
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
					return u.iconButton(gtx, &u.clearSrch, IcX, 24, 14, u.pal.Text2, false)
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
		return layout.Inset{Top: 8, Bottom: 12}.Layout(gtx, u.label(txt, 19, font.ExtraBold, u.pal.Text).Layout)
	}
}

var _ = unit.Dp(0)

// Demo abre uma tela pronta para as capturas de tela do CI (SONIVELL_GO_DEMO=<tela>).
// Telas: home, library, album, artist, nowplaying, compact, settings, queue.
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
		u.ctl.PlayList(lib.Tracks, 0)
		u.ctl.SetPlaying(true)
	}
	switch screen {
	case "library":
		u.route, u.libTab = RouteLibrary, TabAlbums
	case "album":
		u.route = RouteLibrary
		if len(lib.Albums) > 0 {
			u.album = lib.Albums[0]
			u.ctl.PlayList(u.album.Tracks, 1)
		}
	case "artist":
		u.route = RouteLibrary
		if len(lib.Artists) > 0 {
			u.artist = &lib.Artists[0]
		}
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
