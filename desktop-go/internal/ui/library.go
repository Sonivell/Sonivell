package ui

import (
	"fmt"
	"image"
	"strconv"
	"strings"

	"gioui.org/font"
	"gioui.org/io/pointer"
	"gioui.org/layout"
	"gioui.org/unit"
	"gioui.org/widget"

	"github.com/Sonivell/sonivell/desktop-go/internal/core"
	"github.com/Sonivell/sonivell/desktop-go/internal/i18n"
	"github.com/Sonivell/sonivell/desktop-go/internal/player"
)

// trackRow é uma linha de faixa: número, capa, título/artista, álbum, formato e duração.
func (u *UI) trackRow(gtx C, btn *widget.Clickable, t *core.Track, n int, current, showAlbum bool) D {
	return layout.Inset{Bottom: 2}.Layout(gtx, func(gtx C) D {
		return btn.Layout(gtx, func(gtx C) D {
			pointer.CursorPointer.Add(gtx.Ops)
			gtx.Constraints.Min.X = gtx.Constraints.Max.X
			bg := u.pal.Bg
			switch {
			case current:
				bg = u.pal.Surface
			case btn.Hovered():
				bg = u.pal.SurfaceLow
			}
			titleCol := u.pal.Text
			if current {
				titleCol = u.pal.Accent
			}
			return surface(gtx, bg, 14, func(gtx C) D {
				return layout.Inset{Top: 8, Bottom: 8, Left: 12, Right: 14}.Layout(gtx, func(gtx C) D {
					return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
						layout.Rigid(func(gtx C) D {
							gtx.Constraints.Min.X = gtx.Dp(26)
							gtx.Constraints.Max.X = gtx.Dp(26)
							if current {
								return layout.Center.Layout(gtx, func(gtx C) D { return drawWave(gtx, 18, u.pal.Accent, 2.4) })
							}
							l := u.label(strconv.Itoa(n), 13, font.Bold, u.pal.Text2)
							return layout.E.Layout(gtx, l.Layout)
						}),
						layout.Rigid(layout.Spacer{Width: 14}.Layout),
						layout.Rigid(func(gtx C) D { return u.coverArt(gtx, t, 40, 10) }),
						layout.Rigid(layout.Spacer{Width: 14}.Layout),
						layout.Flexed(1.4, func(gtx C) D {
							return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
								layout.Rigid(u.label(t.Title, 14, font.Bold, titleCol).Layout),
								layout.Rigid(u.label(firstNonEmpty(t.DisplayArtist(), i18n.T("unknown_artist")), 12, font.Medium, u.pal.Text2).Layout),
							)
						}),
						layout.Flexed(1, func(gtx C) D {
							if !showAlbum || gtx.Constraints.Max.X < gtx.Dp(120) {
								return D{}
							}
							return layout.Inset{Left: 12, Right: 12}.Layout(gtx, u.label(t.Album, 13, font.Medium, u.pal.Text2).Layout)
						}),
						layout.Rigid(func(gtx C) D {
							if t.Format == "" {
								return D{}
							}
							return u.pill(gtx, t.Format, u.pal.SurfaceHigh, u.pal.Text3)
						}),
						layout.Rigid(func(gtx C) D {
							gtx.Constraints.Min.X = gtx.Dp(56)
							return layout.E.Layout(gtx, u.label(core.FormatTime(t.DurationMs), 13, font.Medium, u.pal.Text2).Layout)
						}),
					)
				})
			})
		})
	})
}

// trackItems vira uma lista de faixas em linhas clicáveis (tocar a lista a partir da clicada).
func (u *UI) trackItems(list []*core.Track, ctx string, st player.State, showAlbum bool) []item {
	out := make([]item, 0, len(list))
	for i, t := range list {
		i, t := i, t
		out = append(out, func(gtx C) D {
			btn := u.rows.get(fmt.Sprintf("%s:%d", ctx, t.ID))
			if btn.Clicked(gtx) {
				u.ctl.PlayList(list, i)
			}
			cur := st.Current != nil && st.Current.ID == t.ID
			n := i + 1
			if t.TrackNo > 0 && ctx == "album" {
				n = t.TrackNo
			}
			return u.trackRow(gtx, btn, t, n, cur, showAlbum)
		})
	}
	return out
}

// albumGridRows divide os álbuns em linhas de cols colunas (no máximo; menos se a janela for estreita).
func (u *UI) albumGridRows(gtx C, albums []*core.Album, maxCols int, ctx string) []item {
	width := gtx.Metric.PxToDp(gtx.Constraints.Max.X) - 72 // a área de conteúdo menos as margens
	cols := int(width / 190)
	if cols < 2 {
		cols = 2
	}
	if maxCols > 0 && cols > maxCols {
		cols = maxCols
	}
	var rows []item
	for start := 0; start < len(albums); start += cols {
		end := start + cols
		if end > len(albums) {
			end = len(albums)
		}
		chunk := albums[start:end]
		rows = append(rows, func(gtx C) D {
			return layout.Inset{Bottom: 18}.Layout(gtx, func(gtx C) D {
				children := make([]layout.FlexChild, 0, cols*2)
				for k := 0; k < cols; k++ {
					k := k
					if k > 0 {
						children = append(children, layout.Rigid(layout.Spacer{Width: 16}.Layout))
					}
					children = append(children, layout.Flexed(1, func(gtx C) D {
						if k >= len(chunk) {
							return D{Size: image.Pt(gtx.Constraints.Max.X, 0)}
						}
						return u.albumCard(gtx, chunk[k], ctx)
					}))
				}
				return layout.Flex{}.Layout(gtx, children...)
			})
		})
	}
	return rows
}

func (u *UI) albumCard(gtx C, a *core.Album, ctx string) D {
	btn := u.rows.get(ctx + ":album:" + a.Key)
	if btn.Clicked(gtx) {
		u.album, u.artist = a, nil
		u.search.SetText("")
		if u.route != RouteLibrary {
			u.route = RouteLibrary
		}
		u.mainList.Position = layout.Position{}
	}
	return btn.Layout(gtx, func(gtx C) D {
		pointer.CursorPointer.Add(gtx.Ops)
		size := unit.Dp(gtx.Metric.PxToDp(gtx.Constraints.Max.X))
		return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
			layout.Rigid(func(gtx C) D {
				var first *core.Track
				if len(a.Tracks) > 0 {
					first = a.Tracks[0]
				}
				d := u.coverArt(gtx, first, size, 16)
				if btn.Hovered() {
					// botão de tocar aparece ao passar o mouse
					s := gtx.Dp(44)
					offset(gtx, d.Size.X-s-gtx.Dp(10), d.Size.Y-s-gtx.Dp(10), func(gtx C) D {
						return box(gtx, 44, 44, func(gtx C) D {
							fillRRect(gtx, u.pal.Accent, 16)
							return layout.Center.Layout(gtx, func(gtx C) D { return drawIcon(gtx, IcPlay, 20, u.pal.OnAccent, true) })
						})
					})
				}
				return d
			}),
			layout.Rigid(layout.Spacer{Height: 8}.Layout),
			layout.Rigid(u.label(a.Title, 14, font.Bold, u.pal.Text).Layout),
			layout.Rigid(u.label(firstNonEmpty(a.Artist, i18n.T("unknown_artist")), 12, font.Medium, u.pal.Text2).Layout),
		)
	})
}

// --- Biblioteca ---

func (u *UI) libraryItems(gtx C, lib *core.Library, st player.State) []item {
	names := []string{i18n.T("tab_tracks"), i18n.T("tab_albums"), i18n.T("tab_artists")}
	for i := range u.tabs {
		if u.tabs[i].Clicked(gtx) {
			u.libTab = LibTab(i)
			u.mainList.Position = layout.Position{}
		}
	}
	items := []item{
		func(gtx C) D {
			return layout.Inset{Bottom: 18}.Layout(gtx, u.label(i18n.T("nav_library"), 34, font.ExtraBold, u.pal.Text).Layout)
		},
		func(gtx C) D {
			return layout.Inset{Bottom: 20}.Layout(gtx, func(gtx C) D {
				var ch []layout.FlexChild
				for i, n := range names {
					i, n := i, n
					ch = append(ch, layout.Rigid(func(gtx C) D {
						return layout.Inset{Right: 8}.Layout(gtx, func(gtx C) D {
							btn := &u.tabs[i]
							return btn.Layout(gtx, func(gtx C) D {
								pointer.CursorPointer.Add(gtx.Ops)
								sel := u.libTab == LibTab(i)
								bg, fg := u.pal.Surface, u.pal.Text3
								if sel {
									bg, fg = u.pal.Text, u.pal.Bg
								} else if btn.Hovered() {
									bg = u.pal.SurfaceHigh
								}
								return surface(gtx, bg, 18, func(gtx C) D {
									return layout.Inset{Top: 9, Bottom: 9, Left: 18, Right: 18}.Layout(gtx, u.label(n, 13, font.ExtraBold, fg).Layout)
								})
							})
						})
					}))
				}
				return layout.Flex{}.Layout(gtx, ch...)
			})
		},
	}
	switch u.libTab {
	case TabTracks:
		items = append(items, u.trackItems(lib.Tracks, "lib", st, true)...)
	case TabAlbums:
		items = append(items, u.albumGridRows(gtx, lib.Albums, 0, "lib")...)
	case TabArtists:
		for i := range lib.Artists {
			g := &lib.Artists[i]
			items = append(items, func(gtx C) D { return u.artistRow(gtx, g) })
		}
	}
	return items
}

func (u *UI) artistRow(gtx C, g *core.Group) D {
	btn := u.rows.get("artist:" + g.Name)
	if btn.Clicked(gtx) {
		u.artist, u.album = g, nil
		u.mainList.Position = layout.Position{}
	}
	return layout.Inset{Bottom: 2}.Layout(gtx, func(gtx C) D {
		return btn.Layout(gtx, func(gtx C) D {
			pointer.CursorPointer.Add(gtx.Ops)
			gtx.Constraints.Min.X = gtx.Constraints.Max.X
			bg := u.pal.Bg
			if btn.Hovered() {
				bg = u.pal.SurfaceLow
			}
			return surface(gtx, bg, 14, func(gtx C) D {
				return layout.Inset{Top: 8, Bottom: 8, Left: 12, Right: 14}.Layout(gtx, func(gtx C) D {
					return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
						layout.Rigid(func(gtx C) D {
							return box(gtx, 48, 48, func(gtx C) D {
								tone := coverTones[int(core.TrackID(g.Name)%uint64(len(coverTones)))]
								fillRRect(gtx, tone, 24)
								return layout.Center.Layout(gtx, u.label(core.Initials(g.Name), 16, font.ExtraBold, u.pal.OnAccent).Layout)
							})
						}),
						layout.Rigid(layout.Spacer{Width: 14}.Layout),
						layout.Flexed(1, func(gtx C) D {
							return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
								layout.Rigid(u.label(g.Name, 15, font.Bold, u.pal.Text).Layout),
								layout.Rigid(u.label(i18n.T("tracks_n", len(g.Tracks)), 12, font.Medium, u.pal.Text2).Layout),
							)
						}),
						layout.Rigid(func(gtx C) D { return drawIcon(gtx, IcCaretRight, 18, u.pal.Text2, false) }),
					)
				})
			})
		})
	})
}

// detailHeader é o topo das telas de álbum e artista: capa grande, nomes e botões.
func (u *UI) detailHeader(cover func(gtx C) D, kicker, title, sub string, tracks []*core.Track) item {
	return func(gtx C) D {
		if u.playAll.Clicked(gtx) {
			u.ctl.PlayList(tracks, 0)
		}
		if u.shufAll.Clicked(gtx) {
			u.ctl.PlayShuffled(tracks)
		}
		return layout.Inset{Bottom: 26}.Layout(gtx, func(gtx C) D {
			return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
				layout.Rigid(func(gtx C) D {
					return layout.Inset{Bottom: 16}.Layout(gtx, func(gtx C) D {
						return u.iconButton(gtx, &u.backBtn, IcArrowLeft, 40, 20, u.pal.Text, false)
					})
				}),
				layout.Rigid(func(gtx C) D {
					return layout.Flex{Alignment: layout.End}.Layout(gtx,
						layout.Rigid(cover),
						layout.Rigid(layout.Spacer{Width: 26}.Layout),
						layout.Flexed(1, func(gtx C) D {
							return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
								layout.Rigid(u.label(kicker, 12, font.ExtraBold, u.pal.Accent).Layout),
								layout.Rigid(layout.Spacer{Height: 6}.Layout),
								layout.Rigid(func(gtx C) D {
									l := u.label(title, 36, font.ExtraBold, u.pal.Text)
									l.MaxLines = 2
									return l.Layout(gtx)
								}),
								layout.Rigid(layout.Spacer{Height: 6}.Layout),
								layout.Rigid(u.label(sub, 15, font.SemiBold, u.pal.Text2).Layout),
								layout.Rigid(layout.Spacer{Height: 18}.Layout),
								layout.Rigid(func(gtx C) D {
									return layout.Flex{}.Layout(gtx,
										layout.Rigid(func(gtx C) D { return u.textButton(gtx, &u.playAll, i18n.T("play_all"), true) }),
										layout.Rigid(layout.Spacer{Width: 10}.Layout),
										layout.Rigid(func(gtx C) D { return u.textButton(gtx, &u.shufAll, i18n.T("shuffle"), false) }),
									)
								}),
							)
						}),
					)
				}),
			)
		})
	}
}

func (u *UI) albumItems(a *core.Album, st player.State) []item {
	var first *core.Track
	if len(a.Tracks) > 0 {
		first = a.Tracks[0]
	}
	var total int64
	for _, t := range a.Tracks {
		total += t.DurationMs
	}
	sub := firstNonEmpty(a.Artist, i18n.T("unknown_artist"))
	if a.Year > 0 {
		sub += " · " + strconv.Itoa(a.Year)
	}
	sub += " · " + i18n.T("tracks_n", len(a.Tracks)) + " · " + core.FormatTime(total)
	items := []item{u.detailHeader(func(gtx C) D { return u.coverArt(gtx, first, 200, 22) },
		upper(i18n.T("kicker_album")), a.Title, sub, a.Tracks)}
	return append(items, u.trackItems(a.Tracks, "album", st, false)...)
}

func (u *UI) artistItems(g *core.Group, st player.State) []item {
	cover := func(gtx C) D {
		return box(gtx, 200, 200, func(gtx C) D {
			tone := coverTones[int(core.TrackID(g.Name)%uint64(len(coverTones)))]
			fillRRect(gtx, tone, 100)
			return layout.Center.Layout(gtx, u.label(core.Initials(g.Name), 64, font.ExtraBold, u.pal.OnAccent).Layout)
		})
	}
	items := []item{u.detailHeader(cover, upper(i18n.T("kicker_artist")), g.Name, i18n.T("tracks_n", len(g.Tracks)), g.Tracks)}
	return append(items, u.trackItems(g.Tracks, "artist", st, true)...)
}

func (u *UI) favoritesItems(lib *core.Library, st player.State) []item {
	favs := u.favoriteTracks(lib)
	items := []item{func(gtx C) D {
		return layout.Inset{Bottom: 20}.Layout(gtx, u.label(i18n.T("nav_favorites"), 34, font.ExtraBold, u.pal.Text).Layout)
	}}
	if len(favs) == 0 {
		items = append(items, u.label(i18n.T("no_results"), 14, font.Medium, u.pal.Text2).Layout)
	}
	return append(items, u.trackItems(favs, "fav", st, true)...)
}

func (u *UI) searchItems(lib *core.Library, q string) []item {
	if u.searchCache.q != q || u.searchCache.lib != lib {
		u.searchCache.q, u.searchCache.lib, u.searchCache.res = q, lib, lib.Search(q)
	}
	res := u.searchCache.res
	st := u.ctl.State()
	items := []item{func(gtx C) D {
		return layout.Inset{Bottom: 20}.Layout(gtx, u.label(i18n.T("search_results", q), 28, font.ExtraBold, u.pal.Text).Layout)
	}}
	if len(res) == 0 {
		items = append(items, u.label(i18n.T("no_results"), 14, font.Medium, u.pal.Text2).Layout)
	}
	if len(res) > 300 {
		res = res[:300]
	}
	return append(items, u.trackItems(res, "search", st, true)...)
}

func upper(s string) string { return strings.ToUpper(s) }
