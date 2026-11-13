package ui

import (
	"fmt"
	"image"
	"image/color"
	"strings"
	"time"

	"gioui.org/font"
	"gioui.org/io/pointer"
	"gioui.org/layout"
	"gioui.org/op/clip"
	"gioui.org/op/paint"
	"gioui.org/unit"
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
		if u.album != nil || u.artist != nil {
			u.album, u.artist = nil, nil
		}
	}
	if u.openSet.Clicked(gtx) {
		u.route = RouteSettings
	}

	var items []item
	// avisos no topo
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
	case u.artist != nil:
		items = append(items, u.artistItems(u.artist, st)...)
	case u.route == RouteHome:
		items = append(items, u.homeItems(gtx, lib, st)...)
	case u.route == RouteLibrary:
		items = append(items, u.libraryItems(gtx, lib, st)...)
	case u.route == RouteFavorites:
		items = append(items, u.favoritesItems(lib, st)...)
	}

	return layout.Inset{Left: 36, Right: 20}.Layout(gtx, func(gtx C) D {
		return u.list(gtx, &u.mainList, len(items)+1, func(gtx C, i int) D {
			if i == 0 {
				return layout.Spacer{Height: 28}.Layout(gtx)
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

func greeting() string {
	switch h := time.Now().Hour(); {
	case h >= 5 && h < 12:
		return i18n.T("greeting_morning")
	case h >= 12 && h < 18:
		return i18n.T("greeting_after")
	}
	return i18n.T("greeting_night")
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

func (u *UI) homeItems(gtx C, lib *core.Library, st player.State) []item {
	recent := lib.RecentlyAdded(60)
	favs := u.favoriteTracks(lib)
	mixes := []struct {
		title, sub string
		bgIdx      int
		tracks     []*core.Track
	}{
		{i18n.T("mix_all"), i18n.T("mix_all_sub"), -1, lib.Tracks},
		{i18n.T("mix_recent"), i18n.T("mix_recent_sub"), 1, recent},
		{i18n.T("mix_favs"), i18n.T("mix_favs_sub"), 3, favs},
	}
	for i := range mixes {
		if u.mixes[i].Clicked(gtx) && len(mixes[i].tracks) > 0 {
			u.ctl.PlayShuffled(mixes[i].tracks)
		}
	}

	items := []item{
		func(gtx C) D {
			return layout.Inset{Bottom: 26}.Layout(gtx, func(gtx C) D {
				return layout.Flex{Alignment: layout.End}.Layout(gtx,
					layout.Flexed(1, func(gtx C) D {
						return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
							layout.Rigid(u.label(strings.ToUpper(greeting()), 12, font.ExtraBold, u.pal.Text2).Layout),
							layout.Rigid(layout.Spacer{Height: 4}.Layout),
							layout.Rigid(u.label(i18n.T("nav_for_you"), 34, font.ExtraBold, u.pal.Text).Layout),
						)
					}),
					layout.Rigid(func(gtx C) D {
						txt := i18n.T("tracks_n", len(lib.Tracks)) + " · " + i18n.T("albums_n", len(lib.Albums))
						return surface(gtx, u.pal.Surface, 16, func(gtx C) D {
							return layout.Inset{Top: 8, Bottom: 8, Left: 14, Right: 14}.Layout(gtx, func(gtx C) D {
								return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
									layout.Rigid(func(gtx C) D {
										return box(gtx, 8, 8, func(gtx C) D { return fillRRect(gtx, u.pal.Accent, 4) })
									}),
									layout.Rigid(layout.Spacer{Width: 8}.Layout),
									layout.Rigid(u.label(txt, 12, font.Bold, u.pal.Text3).Layout),
								)
							})
						})
					}),
				)
			})
		},
		// os três mixes
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
						tone := u.pal.Accent
						if m.bgIdx >= 0 {
							tone = coverTones[m.bgIdx]
						}
						return u.mixCard(gtx, &u.mixes[i], m.title, m.sub, i18n.T("tracks_n", len(m.tracks)), tone, m.tracks)
					}))
				}
				return layout.Flex{}.Layout(gtx, children...)
			})
		},
	}

	albums := lib.RecentAlbums(12)
	if len(albums) > 0 {
		items = append(items, sectionTitle(u, i18n.T("recent_albums")))
		items = append(items, u.albumGridRows(gtx, albums, 6, "home")...)
		items = append(items, func(gtx C) D { return layout.Spacer{Height: 18}.Layout(gtx) })
	}
	if len(st.Queue) > 1 {
		items = append(items, sectionTitle(u, i18n.T("up_next")))
		for i, t := range st.Queue {
			if i > 8 {
				break
			}
			i, t := i, t
			items = append(items, func(gtx C) D {
				btn := u.rows.get(fmt.Sprintf("homeq:%d:%d", i, t.ID))
				if btn.Clicked(gtx) {
					u.ctl.JumpTo(i)
				}
				return u.trackRow(gtx, btn, t, i+1, i == 0, false)
			})
		}
	}
	items = append(items, func(gtx C) D { return layout.Spacer{Height: 24}.Layout(gtx) })
	return items
}

// mixCard é um cartão grande de mix: bloco colorido com a onda e o nome, e a legenda abaixo.
func (u *UI) mixCard(gtx C, btn *widget.Clickable, title, sub, meta string, tone color.NRGBA, tracks []*core.Track) D {
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
						gtx.Constraints = layout.Exact(image.Pt(gtx.Constraints.Max.X, h))
						sz := gtx.Constraints.Min
						defer clip.UniformRRect(image.Rectangle{Max: sz}, gtx.Dp(16)).Push(gtx.Ops).Pop()
						paint.FillShape(gtx.Ops, tone, clip.Rect{Max: sz}.Op())
						// leque de capas à direita (sólidas, com uma borda no tom do mix)
						if n := len(tracks); n > 0 {
							cs := unit.Dp(gtx.Metric.PxToDp(h)) * 0.56
							count := 3
							if gtx.Metric.PxToDp(sz.X) < 300 {
								count = 2
							}
							if count > n {
								count = n
							}
							step := gtx.Dp(cs * 0.38)
							for k := count - 1; k >= 0; k-- {
								t := tracks[(k*5)%n]
								x := sz.X - gtx.Dp(cs) - gtx.Dp(16) - k*step
								y := gtx.Dp(16) + k*gtx.Dp(8)
								b := gtx.Dp(3)
								offset(gtx, x-b, y-b, func(gtx C) D {
									return box(gtx, cs+6, cs+6, func(gtx C) D { return fillRRect(gtx, tone, 14) })
								})
								offset(gtx, x, y, func(gtx C) D { return u.coverArt(gtx, t, cs, 12) })
							}
						}
						// onda + nome no canto
						offset(gtx, gtx.Dp(14), sz.Y-gtx.Dp(70), func(gtx C) D {
							return drawWave(gtx, 34, withAlpha(u.pal.OnAccent, 0x90), 2.6)
						})
						offset(gtx, gtx.Dp(16), sz.Y-gtx.Dp(40), func(gtx C) D {
							gtx.Constraints.Min = image.Point{}
							return u.label(title, 22, font.ExtraBold, u.pal.OnAccent).Layout(gtx)
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
