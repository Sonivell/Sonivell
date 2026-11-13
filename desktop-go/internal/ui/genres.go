package ui

import (
	"fmt"
	"image"
	"image/color"
	"math"

	"gioui.org/font"
	"gioui.org/io/pointer"
	"gioui.org/layout"
	"gioui.org/op/clip"
	"gioui.org/op/paint"
	"gioui.org/unit"

	"github.com/Sonivell/sonivell/desktop-go/internal/core"
	"github.com/Sonivell/sonivell/desktop-go/internal/i18n"
)

// genreItems é a tela Gêneros no layout Mosaico (o escolhido para o Sonivell): o gênero mais
// ouvido em destaque, os outros em blocos do tamanho do acervo, as décadas numa linha do tempo e
// as etiquetas pequenas à parte.
func (u *UI) genreItems(gtx C, lib *core.Library) []item {
	items := []item{func(gtx C) D {
		return layout.Inset{Bottom: 22}.Layout(gtx, u.label(i18n.T("nav_genres"), 34, font.ExtraBold, u.pal.Text).Layout)
	}}
	if len(lib.Genres) == 0 {
		return append(items, u.para(i18n.T("no_genres"), 14, u.pal.Text2).Layout)
	}

	// destaque: o gênero mais tocado (pelo histórico), ou o com mais faixas
	plays := u.store.Get().Plays
	best, bestScore := 0, -1
	for i, g := range lib.Genres {
		score := 0
		for _, t := range g.Tracks {
			score += plays[t.ID]
		}
		if score > bestScore {
			best, bestScore = i, score
		}
	}
	featured := &lib.Genres[best]
	var blocks, tags []*core.Group
	for i := range lib.Genres {
		g := &lib.Genres[i]
		if i == best {
			continue
		}
		if len(g.Tracks) >= 3 {
			blocks = append(blocks, g)
		} else {
			tags = append(tags, g)
		}
	}

	items = append(items, func(gtx C) D {
		return layout.Inset{Bottom: 18}.Layout(gtx, func(gtx C) D { return u.featuredGenre(gtx, featured) })
	})

	// blocos em fileiras de até 4, cada um com largura proporcional ao número de faixas
	for start := 0; start < len(blocks); start += 4 {
		end := start + 4
		if end > len(blocks) {
			end = len(blocks)
		}
		row := blocks[start:end]
		offsetIdx := start
		items = append(items, func(gtx C) D {
			return layout.Inset{Bottom: 14}.Layout(gtx, func(gtx C) D {
				var ch []layout.FlexChild
				for k, g := range row {
					k, g := k, g
					if k > 0 {
						ch = append(ch, layout.Rigid(layout.Spacer{Width: 14}.Layout))
					}
					weight := float32(math.Sqrt(float64(len(g.Tracks))))
					ch = append(ch, layout.Flexed(weight, func(gtx C) D {
						return u.genreBlock(gtx, g, coverTones[(offsetIdx+k+1)%len(coverTones)])
					}))
				}
				return layout.Flex{}.Layout(gtx, ch...)
			})
		})
	}

	if decades := lib.Decades(); len(decades) > 0 {
		items = append(items, sectionTitle(u, i18n.T("decades")))
		items = append(items, func(gtx C) D {
			return layout.Inset{Bottom: 26}.Layout(gtx, func(gtx C) D { return u.decadeTimeline(gtx, decades) })
		})
	}
	if len(tags) > 0 {
		items = append(items, sectionTitle(u, i18n.T("other_tags")))
		items = append(items, func(gtx C) D { return u.tagChips(gtx, tags) })
	}
	return items
}

func (u *UI) featuredGenre(gtx C, g *core.Group) D {
	btn := u.rows.get("genre-featured:" + g.Name)
	if btn.Clicked(gtx) {
		u.openGroup(g, i18n.T("kicker_genre"))
	}
	return btn.Layout(gtx, func(gtx C) D {
		pointer.CursorPointer.Add(gtx.Ops)
		sz := image.Pt(gtx.Constraints.Max.X, gtx.Dp(190))
		gtx.Constraints = layout.Exact(sz)
		defer clip.UniformRRect(image.Rectangle{Max: sz}, gtx.Dp(22)).Push(gtx.Ops).Pop()
		bg := u.pal.Accent
		if btn.Hovered() {
			bg = mix(bg, rgb(0xFFFFFF), 0.08)
		}
		paint.FillShape(gtx.Ops, bg, clip.Rect{Max: sz}.Op())
		// capas do gênero em leque, à direita
		cs := unit.Dp(130)
		for k := 2; k >= 0; k-- {
			if k >= len(g.Tracks) {
				continue
			}
			t := g.Tracks[(k*3)%len(g.Tracks)]
			x := sz.X - gtx.Dp(cs) - gtx.Dp(30) - k*gtx.Dp(70)
			offset(gtx, x, gtx.Dp(30), func(gtx C) D { return u.coverArt(gtx, t, cs, 16) })
		}
		dark := rgb(0x0E0F11)
		return layout.UniformInset(26).Layout(gtx, func(gtx C) D {
			return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
				layout.Rigid(u.label(upper(i18n.T("genre_featured")), 12, font.ExtraBold, withAlpha(dark, 0xB0)).Layout),
				layout.Flexed(1, layout.Spacer{}.Layout),
				layout.Rigid(u.label(g.Name, 38, font.ExtraBold, dark).Layout),
				layout.Rigid(u.label(i18n.T("tracks_n", len(g.Tracks)), 14, font.Bold, withAlpha(dark, 0xB0)).Layout),
			)
		})
	})
}

func (u *UI) genreBlock(gtx C, g *core.Group, tone color.NRGBA) D {
	btn := u.rows.get("genre:" + g.Name)
	if btn.Clicked(gtx) {
		u.openGroup(g, i18n.T("kicker_genre"))
	}
	return btn.Layout(gtx, func(gtx C) D {
		pointer.CursorPointer.Add(gtx.Ops)
		sz := image.Pt(gtx.Constraints.Max.X, gtx.Dp(118))
		gtx.Constraints = layout.Exact(sz)
		bg := u.pal.Surface
		if btn.Hovered() {
			bg = u.pal.SurfaceHigh
		}
		fillRRect(gtx, bg, 20)
		return layout.UniformInset(18).Layout(gtx, func(gtx C) D {
			return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
				layout.Rigid(func(gtx C) D {
					return box(gtx, 28, 6, func(gtx C) D { return fillRRect(gtx, tone, 3) })
				}),
				layout.Flexed(1, layout.Spacer{}.Layout),
				layout.Rigid(u.label(g.Name, 20, font.ExtraBold, u.pal.Text).Layout),
				layout.Rigid(u.label(i18n.T("tracks_n", len(g.Tracks)), 13, font.Medium, u.pal.Text2).Layout),
			)
		})
	})
}

// decadeTimeline é a linha do tempo das décadas: um traço com um ponto por década.
func (u *UI) decadeTimeline(gtx C, decades []core.Group) D {
	var ch []layout.FlexChild
	for i := range decades {
		g := &decades[i]
		ch = append(ch, layout.Flexed(1, func(gtx C) D {
			btn := u.rows.get("decade:" + g.Name)
			if btn.Clicked(gtx) {
				u.openGroup(g, i18n.T("kicker_decade"))
			}
			return btn.Layout(gtx, func(gtx C) D {
				pointer.CursorPointer.Add(gtx.Ops)
				w := gtx.Constraints.Max.X
				lineY := gtx.Dp(10)
				paint.FillShape(gtx.Ops, u.pal.Outline, clip.Rect{Min: image.Pt(0, lineY-gtx.Dp(1)), Max: image.Pt(w, lineY+gtx.Dp(1))}.Op())
				r := gtx.Dp(6)
				dot := u.pal.Text2
				if btn.Hovered() {
					dot = u.pal.Accent
					r = gtx.Dp(8)
				}
				paint.FillShape(gtx.Ops, dot, clip.Ellipse(image.Rect(gtx.Dp(4), lineY-r, gtx.Dp(4)+2*r, lineY+r)).Op(gtx.Ops))
				return layout.Inset{Top: 26}.Layout(gtx, func(gtx C) D {
					gtx.Constraints.Min.X = w
					return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
						layout.Rigid(u.label(g.Name, 18, font.ExtraBold, u.pal.Text).Layout),
						layout.Rigid(u.label(i18n.T("tracks_n", len(g.Tracks)), 12, font.Medium, u.pal.Text2).Layout),
					)
				})
			})
		}))
	}
	return layout.Flex{}.Layout(gtx, ch...)
}

// tagChips são os gêneros pequenos (menos de 3 faixas), em pílulas que quebram linha.
func (u *UI) tagChips(gtx C, tags []*core.Group) D {
	maxW := gtx.Constraints.Max.X
	var rows [][]*core.Group
	var row []*core.Group
	x := 0
	for _, g := range tags {
		w := gtx.Dp(unit.Dp(36 + 8*len([]rune(g.Name))))
		if x+w > maxW && len(row) > 0 {
			rows = append(rows, row)
			row, x = nil, 0
		}
		row = append(row, g)
		x += w + gtx.Dp(8)
	}
	if len(row) > 0 {
		rows = append(rows, row)
	}
	var vch []layout.FlexChild
	for _, r := range rows {
		r := r
		vch = append(vch, layout.Rigid(func(gtx C) D {
			var ch []layout.FlexChild
			for _, g := range r {
				g := g
				ch = append(ch, layout.Rigid(func(gtx C) D {
					btn := u.rows.get("tag:" + g.Name)
					if btn.Clicked(gtx) {
						u.openGroup(g, i18n.T("kicker_genre"))
					}
					return layout.Inset{Right: 8, Bottom: 8}.Layout(gtx, func(gtx C) D {
						return btn.Layout(gtx, func(gtx C) D {
							pointer.CursorPointer.Add(gtx.Ops)
							bg := u.pal.Surface
							if btn.Hovered() {
								bg = u.pal.SurfaceHigh
							}
							return surface(gtx, bg, 16, func(gtx C) D {
								return layout.Inset{Top: 8, Bottom: 8, Left: 14, Right: 14}.Layout(gtx,
									u.label(fmt.Sprintf("%s · %d", g.Name, len(g.Tracks)), 13, font.Bold, u.pal.Text3).Layout)
							})
						})
					})
				}))
			}
			return layout.Flex{}.Layout(gtx, ch...)
		}))
	}
	return layout.Flex{Axis: layout.Vertical}.Layout(gtx, vch...)
}

// --- Playlists ---

func (u *UI) playlistItems(gtx C, lib *core.Library) []item {
	u.mu.Lock()
	pls := u.playlists
	u.mu.Unlock()
	items := []item{func(gtx C) D {
		return layout.Inset{Bottom: 22}.Layout(gtx, u.label(i18n.T("nav_playlists"), 34, font.ExtraBold, u.pal.Text).Layout)
	}}
	favs := &core.Group{Name: i18n.T("nav_favorites"), Tracks: u.favoriteTracks(lib)}
	items = append(items, func(gtx C) D { return u.playlistRow(gtx, favs, "fav", true) })
	for i := range pls {
		p := &pls[i]
		g := &core.Group{Name: p.Name, Tracks: p.Tracks}
		items = append(items, func(gtx C) D { return u.playlistRow(gtx, g, p.Path, false) })
	}
	if len(pls) == 0 {
		items = append(items, func(gtx C) D {
			return layout.Inset{Top: 14}.Layout(gtx, u.para(i18n.T("no_playlists"), 14, u.pal.Text2).Layout)
		})
	}
	return items
}

func (u *UI) playlistRow(gtx C, g *core.Group, key string, favorites bool) D {
	btn := u.rows.get("pl:" + key)
	if btn.Clicked(gtx) {
		u.openGroup(g, i18n.T("kicker_playlist"))
	}
	return layout.Inset{Bottom: 4}.Layout(gtx, func(gtx C) D {
		return btn.Layout(gtx, func(gtx C) D {
			pointer.CursorPointer.Add(gtx.Ops)
			gtx.Constraints.Min.X = gtx.Constraints.Max.X
			bg := u.pal.Bg
			if btn.Hovered() {
				bg = u.pal.SurfaceLow
			}
			return surface(gtx, bg, 16, func(gtx C) D {
				return layout.UniformInset(10).Layout(gtx, func(gtx C) D {
					return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
						layout.Rigid(func(gtx C) D {
							if favorites || len(g.Tracks) == 0 {
								return box(gtx, 56, 56, func(gtx C) D {
									fillRRect(gtx, u.pal.Surface, 14)
									ic := IcPlaylist
									if favorites {
										ic = IcHeart
									}
									return layout.Center.Layout(gtx, func(gtx C) D { return drawIcon(gtx, ic, 24, u.pal.Accent, favorites) })
								})
							}
							return u.coverArt(gtx, g.Tracks[0], 56, 14)
						}),
						layout.Rigid(layout.Spacer{Width: 14}.Layout),
						layout.Flexed(1, func(gtx C) D {
							return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
								layout.Rigid(u.label(g.Name, 16, font.Bold, u.pal.Text).Layout),
								layout.Rigid(u.label(i18n.T("tracks_n", len(g.Tracks)), 13, font.Medium, u.pal.Text2).Layout),
							)
						}),
						layout.Rigid(func(gtx C) D { return drawIcon(gtx, IcCaretRight, 18, u.pal.Text2, false) }),
					)
				})
			})
		})
	})
}
