package ui

import (
	"fmt"
	"image"
	"image/color"
	"sort"
	"strings"
	"time"

	"gioui.org/f32"
	"gioui.org/font"
	"gioui.org/io/pointer"
	"gioui.org/layout"
	"gioui.org/op/clip"
	"gioui.org/op/paint"
	"gioui.org/widget"

	"github.com/Sonivell/sonivell/desktop-go/internal/core"
	"github.com/Sonivell/sonivell/desktop-go/internal/i18n"
	"github.com/Sonivell/sonivell/desktop-go/internal/player"
)

// item é uma linha da lista principal (cada tela monta as suas).
type item = layout.Widget

func (u *UI) content(gtx C, st player.State) D {
	u.mu.Lock()
	lib, scanning, done, total, noFF := u.lib, u.scanning, u.scanDone, u.scanTotal, u.noFFmpeg
	u.mu.Unlock()

	if u.backBtn.Clicked(gtx) {
		u.closeDetail()
	}
	if u.openSet.Clicked(gtx) {
		u.route = RouteSettings
	}

	var items []item
	if noFF {
		items = append(items, u.banner(i18n.T("no_ffmpeg")))
	} else if scanning {
		msg := i18n.T("scanning_start")
		if total > 0 {
			msg = i18n.T("scanning", done, total)
		}
		items = append(items, u.banner(msg))
	}

	q := strings.TrimSpace(u.search.Text())
	switch {
	case q != "" && lib != nil:
		items = append(items, u.searchItems(lib, q)...)
	case u.route == RouteSettings:
		items = append(items, u.settingsItems(gtx)...)
	case lib == nil || len(lib.Tracks) == 0:
		if !scanning {
			items = append(items, u.emptyState)
		}
	case u.album != nil:
		items = append(items, u.albumItems(u.album, st)...)
	case u.group != nil:
		items = append(items, u.groupItems(u.group, u.groupKind, st)...)
	case u.showRecent:
		items = append(items, u.recentItems(lib, st)...)
	case u.route == RouteHome:
		items = append(items, u.homeItems(gtx, lib, st)...)
	case u.route == RouteLibrary:
		items = append(items, u.libraryItems(gtx, lib, st)...)
	case u.route == RouteGenres:
		items = append(items, u.genreItems(gtx, lib)...)
	case u.route == RoutePlaylists:
		items = append(items, u.playlistItems(gtx, lib)...)
	case u.route == RouteFavorites:
		items = append(items, u.favoritesItems(lib, st)...)
	}

	return layout.Inset{Left: 36, Right: 20}.Layout(gtx, func(gtx C) D {
		return u.list(gtx, &u.mainList, len(items)+2, func(gtx C, i int) D {
			if i == 0 {
				return layout.Spacer{Height: 28}.Layout(gtx)
			}
			if i == len(items)+1 {
				return layout.Spacer{Height: 24}.Layout(gtx)
			}
			return layout.Inset{Right: 16}.Layout(gtx, items[i-1])
		})
	})
}

func (u *UI) banner(msg string) item {
	return func(gtx C) D {
		return layout.Inset{Bottom: 18}.Layout(gtx, func(gtx C) D {
			gtx.Constraints.Min.X = gtx.Constraints.Max.X
			return surface(gtx, u.pal.Surface, 14, func(gtx C) D {
				return layout.Inset{Top: 12, Bottom: 12, Left: 16, Right: 16}.Layout(gtx, func(gtx C) D {
					return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
						layout.Rigid(func(gtx C) D {
							return box(gtx, 8, 8, func(gtx C) D { return fillRRect(gtx, u.pal.Accent, 4) })
						}),
						layout.Rigid(layout.Spacer{Width: 10}.Layout),
						layout.Flexed(1, u.label(msg, 13, font.Bold, u.pal.Text3).Layout),
					)
				})
			})
		})
	}
}

func (u *UI) emptyState(gtx C) D {
	return layout.Inset{Top: 80}.Layout(gtx, func(gtx C) D {
		gtx.Constraints.Min.X = gtx.Constraints.Max.X
		return layout.Flex{Axis: layout.Vertical, Alignment: layout.Middle}.Layout(gtx,
			layout.Rigid(func(gtx C) D { return drawWave(gtx, 72, u.pal.Accent, 2) }),
			layout.Rigid(layout.Spacer{Height: 16}.Layout),
			layout.Rigid(u.label(i18n.T("empty_title"), 22, font.ExtraBold, u.pal.Text).Layout),
			layout.Rigid(layout.Spacer{Height: 6}.Layout),
			layout.Rigid(u.label(i18n.T("empty_body"), 14, font.Medium, u.pal.Text2).Layout),
			layout.Rigid(layout.Spacer{Height: 20}.Layout),
			layout.Rigid(func(gtx C) D { return u.textButton(gtx, &u.openSet, i18n.T("open_settings"), true) }),
		)
	})
}

// textButton é um botão de texto (preenchido na cor de destaque ou em superfície).
func (u *UI) textButton(gtx C, btn *widget.Clickable, txt string, primary bool) D {
	return btn.Layout(gtx, func(gtx C) D {
		pointer.CursorPointer.Add(gtx.Ops)
		bg, fg := u.pal.SurfaceHigh, u.pal.Text
		if primary {
			bg, fg = u.pal.Accent, u.pal.OnAccent
		}
		if btn.Hovered() {
			bg = mix(bg, rgb(0xFFFFFF), 0.1)
		}
		return surface(gtx, bg, 22, func(gtx C) D {
			return layout.Inset{Top: 12, Bottom: 12, Left: 20, Right: 20}.Layout(gtx, u.label(txt, 14, font.ExtraBold, fg).Layout)
		})
	})
}

// --- Para você ---

// dayPart é o rótulo do topo, como no mockup: "Sexta à noite".
func dayPart(now time.Time) string {
	days := strings.Split(i18n.T("weekdays"), ",")
	day := days[int(now.Weekday())%len(days)]
	part := "part_night"
	switch h := now.Hour(); {
	case h >= 5 && h < 12:
		part = "part_morning"
	case h >= 12 && h < 18:
		part = "part_afternoon"
	}
	return i18n.T(part, day)
}

func (u *UI) favoriteTracks(lib *core.Library) []*core.Track {
	var out []*core.Track
	for _, t := range lib.Tracks {
		if u.ctl.IsFavorite(t.ID) {
			out = append(out, t)
		}
	}
	return out
}

// Gêneros que entram na Calmaria enquanto o motor SMART (que mede energia e BPM) não chega ao Go.
var calmGenres = []string{"ambient", "chill", "lo-fi", "lofi", "acoustic", "acustic", "jazz", "classical", "classic",
	"bossa", "mpb", "folk", "piano", "soul", "new age", "downtempo", "instrumental", "soundtrack", "trilha"}

func calmTracks(lib *core.Library) []*core.Track {
	var out []*core.Track
	for _, t := range lib.Tracks {
		g := core.Fold(t.Genre)
		if g == "" {
			continue
		}
		for _, c := range calmGenres {
			if strings.Contains(g, c) {
				out = append(out, t)
				break
			}
		}
	}
	return out
}

// discoveries são as faixas menos tocadas (as nunca tocadas primeiro).
func discoveries(lib *core.Library, plays map[uint64]int) []*core.Track {
	out := append([]*core.Track(nil), lib.Tracks...)
	sort.SliceStable(out, func(i, j int) bool { return plays[out[i].ID] < plays[out[j].ID] })
	if len(out) > 50 {
		out = out[:50]
	}
	return out
}

func (u *UI) recentlyPlayed(lib *core.Library, n int) []*core.Track {
	var out []*core.Track
	for _, id := range u.store.Get().Recent {
		if t := lib.ByID[id]; t != nil {
			out = append(out, t)
			if n > 0 && len(out) == n {
				break
			}
		}
	}
	return out
}

func thousands(n int) string {
	s := fmt.Sprint(n)
	sep := i18n.T("thousands_sep")
	for i := len(s) - 3; i > 0; i -= 3 {
		s = s[:i] + sep + s[i:]
	}
	return s
}

func (u *UI) homeItems(gtx C, lib *core.Library, st player.State) []item {
	settings := u.store.Get()
	mixes := []struct {
		title, sub, meta string
		tone             color.NRGBA
		tracks           []*core.Track
	}{
		{i18n.T("mix_all"), i18n.T("mix_all_sub"), i18n.T("mix_all_meta"), u.pal.Accent, lib.Tracks},
		{i18n.T("mix_disc"), i18n.T("mix_disc_sub"), i18n.T("mix_disc_meta"), rgb(0x4FE0C8), discoveries(lib, settings.Plays)},
		{i18n.T("mix_calm"), i18n.T("mix_calm_sub"), i18n.T("mix_calm_meta"), rgb(0xB79BFF), calmTracks(lib)},
	}
	for i := range mixes {
		if u.mixes[i].Clicked(gtx) && len(mixes[i].tracks) > 0 {
			u.ctl.PlayShuffledFrom(mixes[i].title, mixes[i].tracks)
		}
	}
	if u.seeAll.Clicked(gtx) {
		u.showRecent = true
		u.mainList.Position = layout.Position{}
	}

	items := []item{
		// cabeçalho: "SEXTA À NOITE" / "Para você" e o contador à direita
		func(gtx C) D {
			return layout.Inset{Bottom: 30}.Layout(gtx, func(gtx C) D {
				return layout.Flex{Alignment: layout.End}.Layout(gtx,
					layout.Flexed(1, func(gtx C) D {
						return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
							layout.Rigid(u.label(upper(dayPart(time.Now())), 13, font.Bold, u.pal.Text2).Layout),
							layout.Rigid(layout.Spacer{Height: 4}.Layout),
							layout.Rigid(u.label(i18n.T("nav_for_you"), 34, font.ExtraBold, u.pal.Text).Layout),
						)
					}),
					layout.Rigid(func(gtx C) D {
						txt := i18n.T("library_count", thousands(len(lib.Tracks)), thousands(len(lib.Albums)))
						return surface(gtx, u.pal.Surface, 16, func(gtx C) D {
							return minHeight(gtx, 32, func(gtx C) D {
								return layout.Inset{Left: 14, Right: 14}.Layout(gtx, func(gtx C) D {
									return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
										layout.Rigid(func(gtx C) D {
											return box(gtx, 8, 8, func(gtx C) D { return fillRRect(gtx, u.pal.Accent, 4) })
										}),
										layout.Rigid(layout.Spacer{Width: 8}.Layout),
										layout.Rigid(func(gtx C) D {
											return layout.W.Layout(gtx, u.label(txt, 12, font.Bold, u.pal.Text3).Layout)
										}),
									)
								})
							})
						})
					}),
				)
			})
		},
		// os três mixes, lado a lado
		func(gtx C) D {
			return layout.Inset{Bottom: 30}.Layout(gtx, func(gtx C) D {
				children := make([]layout.FlexChild, 0, 5)
				for i := range mixes {
					i := i
					if i > 0 {
						children = append(children, layout.Rigid(layout.Spacer{Width: 18}.Layout))
					}
					children = append(children, layout.Flexed(1, func(gtx C) D {
						m := mixes[i]
						meta := i18n.T("tracks_n", len(m.tracks)) + " · " + m.meta
						return u.mixCard(gtx, &u.mixes[i], m.title, m.sub, meta, m.tone)
					}))
				}
				return layout.Flex{}.Layout(gtx, children...)
			})
		},
	}

	// Tocadas recentemente; enquanto o histórico tem menos de 6, completa com as adicionadas por último.
	recent := u.recentlyPlayed(lib, 6)
	title := i18n.T("recently_played")
	if len(recent) == 0 {
		title = i18n.T("recently_added")
	}
	if len(recent) < 6 {
		seen := map[uint64]bool{}
		for _, t := range recent {
			seen[t.ID] = true
		}
		for _, t := range lib.RecentlyAdded(12) {
			if len(recent) == 6 {
				break
			}
			if !seen[t.ID] {
				recent = append(recent, t)
			}
		}
	}
	items = append(items, func(gtx C) D {
		return layout.Inset{Bottom: 14}.Layout(gtx, func(gtx C) D {
			return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
				layout.Flexed(1, u.label(title, 19, font.ExtraBold, u.pal.Text).Layout),
				layout.Rigid(func(gtx C) D {
					return u.seeAll.Layout(gtx, func(gtx C) D {
						pointer.CursorPointer.Add(gtx.Ops)
						c := u.pal.Accent
						if u.seeAll.Hovered() {
							c = mix(c, rgb(0xFFFFFF), 0.3)
						}
						return u.label(i18n.T("see_all"), 13, font.Bold, c).Layout(gtx)
					})
				}),
			)
		})
	})
	items = append(items, func(gtx C) D {
		return layout.Inset{Bottom: 30}.Layout(gtx, func(gtx C) D { return u.trackTiles(gtx, recent, 6, "recent") })
	})

	// A seguir no <origem>
	if len(st.Queue) > 0 {
		items = append(items, func(gtx C) D {
			txt := i18n.T("up_next")
			switch st.Source {
			case "":
			case i18n.T("mix_all"), i18n.T("mix_disc"), i18n.T("mix_calm"):
				txt = i18n.T("up_next_in_mix", st.Source)
			default:
				txt = i18n.T("up_next_in", st.Source)
			}
			return layout.Inset{Bottom: 14}.Layout(gtx, u.label(txt, 19, font.ExtraBold, u.pal.Text).Layout)
		})
		for i, t := range st.Queue {
			if i > 7 {
				break
			}
			i, t := i, t
			items = append(items, func(gtx C) D {
				btn := u.rows.get(fmt.Sprintf("homeq:%d:%d", i, t.ID))
				if btn.Clicked(gtx) {
					u.ctl.JumpTo(i)
				}
				return u.trackRowStyled(gtx, btn, t, i+1, i == 0, false, false)
			})
		}
	}
	return items
}

// trackTiles é a fileira de capas de "Tocadas recentemente": capa quadrada, título e artista.
func (u *UI) trackTiles(gtx C, tracks []*core.Track, cols int, ctx string) D {
	children := make([]layout.FlexChild, 0, cols*2)
	for k := 0; k < cols; k++ {
		k := k
		if k > 0 {
			children = append(children, layout.Rigid(layout.Spacer{Width: 16}.Layout))
		}
		children = append(children, layout.Flexed(1, func(gtx C) D {
			if k >= len(tracks) {
				return D{Size: image.Pt(gtx.Constraints.Max.X, 0)}
			}
			t := tracks[k]
			btn := u.rows.get(fmt.Sprintf("%s:%d", ctx, t.ID))
			if btn.Clicked(gtx) {
				u.ctl.PlayFrom(i18n.T("recently_played"), tracks, k)
			}
			return btn.Layout(gtx, func(gtx C) D {
				pointer.CursorPointer.Add(gtx.Ops)
				size := gtx.Metric.PxToDp(gtx.Constraints.Max.X)
				return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
					layout.Rigid(func(gtx C) D {
						d := u.coverArt(gtx, t, size, 16)
						if btn.Hovered() {
							paint.FillShape(gtx.Ops, color.NRGBA{A: 0x40}, clip.UniformRRect(image.Rectangle{Max: d.Size}, gtx.Dp(16)).Op(gtx.Ops))
							s := gtx.Dp(40)
							offset(gtx, (d.Size.X-s)/2, (d.Size.Y-s)/2, func(gtx C) D {
								return box(gtx, 40, 40, func(gtx C) D {
									fillRRect(gtx, u.pal.Accent, 14)
									return layout.Center.Layout(gtx, func(gtx C) D { return drawIcon(gtx, IcPlay, 18, u.pal.OnAccent, true) })
								})
							})
						}
						return d
					}),
					layout.Rigid(layout.Spacer{Height: 8}.Layout),
					layout.Rigid(u.label(t.Title, 14, font.Bold, u.pal.Text).Layout),
					layout.Rigid(layout.Spacer{Height: 2}.Layout),
					layout.Rigid(u.label(firstNonEmpty(t.DisplayArtist(), i18n.T("unknown_artist")), 12, font.Medium, u.pal.Text2).Layout),
				)
			})
		}))
	}
	return layout.Flex{}.Layout(gtx, children...)
}

// recentItems é a lista completa de "Tocadas recentemente" (o "Ver tudo").
func (u *UI) recentItems(lib *core.Library, st player.State) []item {
	recent := u.recentlyPlayed(lib, 0)
	title := i18n.T("recently_played")
	if len(recent) == 0 {
		recent = lib.RecentlyAdded(60)
		title = i18n.T("recently_added")
	}
	items := []item{u.backHeader(title)}
	return append(items, u.trackItems(recent, "recentall", st, true)...)
}

// backHeader é o título de uma tela secundária, com o botão de voltar.
func (u *UI) backHeader(title string) item {
	return func(gtx C) D {
		return layout.Inset{Bottom: 20}.Layout(gtx, func(gtx C) D {
			return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
				layout.Rigid(func(gtx C) D { return u.iconButton(gtx, &u.backBtn, IcArrowLeft, 40, 20, u.pal.Text, false) }),
				layout.Rigid(layout.Spacer{Width: 10}.Layout),
				layout.Rigid(u.label(title, 30, font.ExtraBold, u.pal.Text).Layout),
			)
		})
	}
}

// mixCard é o cartão de mix do mockup: um bloco colorido com a onda escura atravessando e o
// nome embaixo, e a legenda com a descrição e os números.
func (u *UI) mixCard(gtx C, btn *widget.Clickable, title, sub, meta string, tone color.NRGBA) D {
	return btn.Layout(gtx, func(gtx C) D {
		pointer.CursorPointer.Add(gtx.Ops)
		bg := u.pal.Surface
		if btn.Hovered() {
			bg = u.pal.SurfaceHigh
		}
		gtx.Constraints.Min.X = gtx.Constraints.Max.X
		return surface(gtx, bg, 22, func(gtx C) D {
			return layout.UniformInset(18).Layout(gtx, func(gtx C) D {
				return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
					layout.Rigid(func(gtx C) D {
						h := gtx.Dp(150)
						sz := image.Pt(gtx.Constraints.Max.X, h)
						gtx.Constraints = layout.Exact(sz)
						defer clip.UniformRRect(image.Rectangle{Max: sz}, gtx.Dp(16)).Push(gtx.Ops).Pop()
						paint.FillShape(gtx.Ops, tone, clip.Rect{Max: sz}.Op())
						// a onda do mockup: "M0 30 C25 5 50 5 75 30 S125 55 150 30 S190 10 200 20" (200×60),
						// esticada na largura e começando a 40 dp do topo
						w := float32(sz.X)
						sx, sy := w/200, float32(gtx.Dp(60))/60
						oy := float32(gtx.Dp(40))
						pt := func(x, y float32) f32.Point { return f32.Pt(x*sx, oy+y*sy) }
						var p clip.Path
						p.Begin(gtx.Ops)
						p.MoveTo(pt(0, 30))
						p.CubeTo(pt(25, 5), pt(50, 5), pt(75, 30))
						p.CubeTo(pt(100, 55), pt(125, 55), pt(150, 30))
						p.CubeTo(pt(175, 5), pt(190, 10), pt(200, 20))
						paint.FillShape(gtx.Ops, withAlpha(rgb(0x0E0F11), 0x5A), clip.Stroke{Path: p.End(), Width: float32(gtx.Dp(5))}.Op())
						offset(gtx, gtx.Dp(14), sz.Y-gtx.Dp(14)-gtx.Sp(30), func(gtx C) D {
							gtx.Constraints.Min = image.Point{}
							gtx.Constraints.Max.X = sz.X - gtx.Dp(28)
							return u.label(title, 22, font.ExtraBold, rgb(0x0E0F11)).Layout(gtx)
						})
						return D{Size: sz}
					}),
					layout.Rigid(layout.Spacer{Height: 14}.Layout),
					layout.Rigid(u.label(sub, 15, font.Bold, u.pal.Text).Layout),
					layout.Rigid(layout.Spacer{Height: 2}.Layout),
					layout.Rigid(u.label(meta, 13, font.Medium, u.pal.Text2).Layout),
				)
			})
		})
	})
}
