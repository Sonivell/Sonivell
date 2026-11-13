package ui

import (
	"fmt"
	"image"
	"image/color"

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

// handleControls trata os botões comuns do player (barra, Vidro e compacto usam os mesmos).
func (u *UI) handleControls(gtx C, st player.State) {
	if u.playBtn.Clicked(gtx) {
		u.ctl.TogglePlay()
	}
	if u.prevBtn.Clicked(gtx) {
		u.ctl.Previous()
	}
	if u.nextBtn.Clicked(gtx) {
		u.ctl.Next()
	}
	if u.shufBtn.Clicked(gtx) {
		u.ctl.ToggleShuffle()
	}
	if u.repBtn.Clicked(gtx) {
		u.ctl.CycleRepeat()
	}
	if u.likeBtn.Clicked(gtx) && st.Current != nil {
		u.ctl.ToggleFavorite(st.Current.ID)
	}
	if u.queueBtn.Clicked(gtx) {
		u.showQueue = !u.showQueue
	}
	if u.compactBtn.Clicked(gtx) {
		u.compact = !u.compact
		if u.hooks.SetCompact != nil {
			u.hooks.SetCompact(u.compact)
		}
	}
	if u.openNow.Clicked(gtx) && st.Current != nil {
		u.nowPlaying = true
	}
	if u.closeNow.Clicked(gtx) {
		u.nowPlaying = false
	}
}

// seekBar desenha a barra de posição e aplica o arraste.
func (u *UI) seekBar(gtx C, st player.State, track, fill color.NRGBA) D {
	var value float32
	if t := st.Current; t != nil && t.DurationMs > 0 {
		value = float32(u.ctl.PositionMs()) / float32(t.DurationMs)
		if value > 1 {
			value = 1
		}
	}
	d, changed := u.progressBar(gtx, &u.seek, value, 5, track, fill)
	if changed && !u.seek.Dragging() && st.Current != nil {
		u.ctl.SeekTo(int64(u.seek.Value * float32(st.Current.DurationMs)))
	}
	return d
}

func (u *UI) timeLabel(ms int64, col color.NRGBA) layout.Widget {
	return u.label(core.FormatTime(ms), 12, font.Bold, col).Layout
}

func (u *UI) repeatIcon(r player.Repeat) *Icon {
	if r == player.RepeatOne {
		return IcRepeatOnce
	}
	return IcRepeat
}

// playerBar é a barra de baixo: capa e nome, controles com a posição, e volume/fila/compacto.
func (u *UI) playerBar(gtx C, st player.State) D {
	u.handleControls(gtx, st)
	t := st.Current
	gtx.Constraints.Min.X = gtx.Constraints.Max.X
	return layout.Background{}.Layout(gtx, func(gtx C) D {
		sz := gtx.Constraints.Min
		rr := clip.UniformRRect(image.Rectangle{Max: sz}, gtx.Dp(22))
		paint.FillShape(gtx.Ops, mix(u.pal.Bg, u.pal.SurfaceHigh, 0.72), rr.Op(gtx.Ops))
		paint.FillShape(gtx.Ops, color.NRGBA{R: 255, G: 255, B: 255, A: 0x14}, clip.Stroke{Path: rr.Path(gtx.Ops), Width: float32(gtx.Dp(1))}.Op())
		return D{Size: sz}
	}, func(gtx C) D {
		gtx.Constraints.Min.Y = gtx.Dp(88)
		return layout.Inset{Top: 12, Bottom: 12, Left: 20, Right: 20}.Layout(gtx, func(gtx C) D {
			return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
				// esquerda: capa + título
				layout.Flexed(1, func(gtx C) D {
					return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
						layout.Rigid(func(gtx C) D {
							return u.openNow.Layout(gtx, func(gtx C) D {
								pointer.CursorPointer.Add(gtx.Ops)
								return u.coverArt(gtx, t, 58, 16)
							})
						}),
						layout.Rigid(layout.Spacer{Width: 14}.Layout),
						layout.Flexed(1, func(gtx C) D {
							if t == nil {
								return u.label(i18n.T("nothing_playing"), 14, font.Bold, u.pal.Text2).Layout(gtx)
							}
							return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
								layout.Rigid(u.label(t.Title, 15, font.ExtraBold, u.pal.Text).Layout),
								layout.Rigid(u.label(t.DisplayArtist(), 13, font.Medium, u.pal.Text2).Layout),
							)
						}),
						layout.Rigid(func(gtx C) D {
							if t == nil {
								return D{}
							}
							return u.iconButton(gtx, &u.likeBtn, IcHeart, 44, 20, u.pal.Text3, u.ctl.IsFavorite(t.ID))
						}),
					)
				}),
				// centro: controles + posição
				layout.Flexed(1.3, func(gtx C) D {
					return layout.Flex{Axis: layout.Vertical, Alignment: layout.Middle}.Layout(gtx,
						layout.Rigid(func(gtx C) D {
							return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
								layout.Rigid(func(gtx C) D {
									return u.iconButton(gtx, &u.shufBtn, IcShuffle, 40, 20, u.pal.Text3, st.Shuffle)
								}),
								layout.Rigid(layout.Spacer{Width: 8}.Layout),
								layout.Rigid(func(gtx C) D { return u.iconButton(gtx, &u.prevBtn, IcSkipBack, 44, 22, u.pal.Text, false) }),
								layout.Rigid(layout.Spacer{Width: 8}.Layout),
								layout.Rigid(func(gtx C) D { return u.playButton(gtx, &u.playBtn, st.Playing, 50, u.pal.Accent, u.pal.OnAccent) }),
								layout.Rigid(layout.Spacer{Width: 8}.Layout),
								layout.Rigid(func(gtx C) D { return u.iconButton(gtx, &u.nextBtn, IcSkipForward, 44, 22, u.pal.Text, false) }),
								layout.Rigid(layout.Spacer{Width: 8}.Layout),
								layout.Rigid(func(gtx C) D {
									return u.iconButton(gtx, &u.repBtn, u.repeatIcon(st.Repeat), 40, 20, u.pal.Text3, st.Repeat != player.RepeatOff)
								}),
							)
						}),
						layout.Rigid(layout.Spacer{Height: 2}.Layout),
						layout.Rigid(func(gtx C) D {
							if gtx.Constraints.Max.X > gtx.Dp(620) {
								gtx.Constraints.Max.X = gtx.Dp(620)
							}
							var dur int64
							if t != nil {
								dur = t.DurationMs
							}
							return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
								layout.Rigid(u.timeLabel(u.ctl.PositionMs(), u.pal.Text2)),
								layout.Rigid(layout.Spacer{Width: 10}.Layout),
								layout.Flexed(1, func(gtx C) D { return u.seekBar(gtx, st, u.pal.Outline, u.pal.Accent) }),
								layout.Rigid(layout.Spacer{Width: 10}.Layout),
								layout.Rigid(u.timeLabel(dur, u.pal.Text2)),
							)
						}),
					)
				}),
				// direita: formato, volume, fila, compacto
				layout.Flexed(1, func(gtx C) D {
					return layout.E.Layout(gtx, func(gtx C) D {
						return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
							layout.Rigid(func(gtx C) D {
								if t == nil || t.Format == "" {
									return D{}
								}
								return layout.Inset{Right: 12}.Layout(gtx, func(gtx C) D { return u.pill(gtx, t.Format, u.pal.Outline, u.pal.Text3) })
							}),
							layout.Rigid(func(gtx C) D {
								ic := IcSpeakerHigh
								if st.Volume == 0 {
									ic = IcSpeakerX
								} else if st.Volume < 0.4 {
									ic = IcSpeakerLow
								}
								return drawIcon(gtx, ic, 20, u.pal.Text3, false)
							}),
							layout.Rigid(layout.Spacer{Width: 8}.Layout),
							layout.Rigid(func(gtx C) D {
								gtx.Constraints.Max.X = gtx.Dp(96)
								gtx.Constraints.Min.X = gtx.Constraints.Max.X
								d, changed := u.progressBar(gtx, &u.volume, float32(st.Volume), 5, u.pal.Outline, u.pal.Text)
								if changed {
									u.ctl.SetVolume(float64(u.volume.Value))
								}
								return d
							}),
							layout.Rigid(layout.Spacer{Width: 8}.Layout),
							layout.Rigid(func(gtx C) D { return u.iconButton(gtx, &u.queueBtn, IcQueue, 44, 20, u.pal.Text3, u.showQueue) }),
							layout.Rigid(func(gtx C) D {
								return u.iconButton(gtx, &u.compactBtn, IcPictureInPicture, 44, 20, u.pal.Text3, false)
							}),
						)
					})
				}),
			)
		})
	})
}

// queuePanel é a fila à direita.
func (u *UI) queuePanel(gtx C, st player.State) D {
	w := gtx.Dp(320)
	gtx.Constraints = layout.Exact(image.Pt(w, gtx.Constraints.Max.Y))
	return layout.Inset{Top: 16, Right: 16, Bottom: 12}.Layout(gtx, func(gtx C) D {
		gtx.Constraints.Min = gtx.Constraints.Max
		return surface(gtx, u.pal.SurfaceLow, 22, func(gtx C) D {
			return layout.UniformInset(14).Layout(gtx, func(gtx C) D {
				return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
					layout.Rigid(func(gtx C) D {
						return layout.Inset{Left: 6, Bottom: 12, Top: 4}.Layout(gtx, u.label(i18n.T("queue"), 17, font.ExtraBold, u.pal.Text).Layout)
					}),
					layout.Flexed(1, func(gtx C) D {
						return u.list(gtx, &u.queueList, len(st.Queue), func(gtx C, i int) D {
							t := st.Queue[i]
							btn := u.rows.get(fmt.Sprintf("q:%d:%d", i, t.ID))
							if btn.Clicked(gtx) {
								u.ctl.JumpTo(i)
							}
							return btn.Layout(gtx, func(gtx C) D {
								pointer.CursorPointer.Add(gtx.Ops)
								gtx.Constraints.Min.X = gtx.Constraints.Max.X
								bg := u.pal.SurfaceLow
								if i == 0 {
									bg = u.pal.Surface
								} else if btn.Hovered() {
									bg = u.pal.Surface
								}
								return surface(gtx, bg, 12, func(gtx C) D {
									return layout.UniformInset(8).Layout(gtx, func(gtx C) D {
										col := u.pal.Text
										if i == 0 {
											col = u.pal.Accent
										}
										return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
											layout.Rigid(func(gtx C) D { return u.coverArt(gtx, t, 38, 9) }),
											layout.Rigid(layout.Spacer{Width: 10}.Layout),
											layout.Flexed(1, func(gtx C) D {
												return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
													layout.Rigid(u.label(t.Title, 13, font.Bold, col).Layout),
													layout.Rigid(u.label(t.DisplayArtist(), 11, font.Medium, u.pal.Text2).Layout),
												)
											}),
										)
									})
								})
							})
						})
					}),
				)
			})
		})
	})
}

// --- player Vidro ---

// glass é um painel translúcido com borda clara (o "vidro" do Sonivell).
func glass(gtx C, radius unit.Dp, alpha uint8, w layout.Widget) D {
	return layout.Background{}.Layout(gtx, func(gtx C) D {
		sz := gtx.Constraints.Min
		r := gtx.Dp(radius)
		rr := clip.UniformRRect(image.Rectangle{Max: sz}, r)
		paint.FillShape(gtx.Ops, color.NRGBA{R: 255, G: 255, B: 255, A: alpha}, rr.Op(gtx.Ops))
		paint.FillShape(gtx.Ops, color.NRGBA{R: 255, G: 255, B: 255, A: 0x1E}, clip.Stroke{Path: rr.Path(gtx.Ops), Width: float32(gtx.Dp(1))}.Op())
		return D{Size: sz}
	}, w)
}

// glow imita um brilho difuso (círculos concêntricos bem transparentes; o Gio não tem desfoque).
func glow(gtx C, cx, cy, radius int, col color.NRGBA, strength float32) {
	const steps = 48
	for i := steps; i >= 1; i-- {
		r := radius * i / steps
		a := uint8(strength * 255 / steps)
		c := col
		c.A = a
		paint.FillShape(gtx.Ops, c, clip.Ellipse(image.Rect(cx-r, cy-r, cx+r, cy+r)).Op(gtx.Ops))
	}
}

// vidroColors deriva do tom da capa o fundo e o texto secundário do player Vidro.
func vidroColors(tone color.NRGBA) (bg, soft color.NRGBA) {
	bg = mix(tone, color.NRGBA{A: 255}, 0.78)
	soft = mix(tone, color.NRGBA{R: 255, G: 255, B: 255, A: 255}, 0.62)
	return
}

func (u *UI) nowPlayingView(gtx C, st player.State) D {
	u.handleControls(gtx, st)
	t := st.Current
	if t == nil {
		u.nowPlaying = false
		return D{}
	}
	tone := u.coverTone(t)
	bg, soft := vidroColors(tone)
	sz := gtx.Constraints.Max
	paint.FillShape(gtx.Ops, bg, clip.Rect{Max: sz}.Op())
	glow(gtx, sz.X/5, sz.Y/6, sz.Y*2/3, tone, 0.5)
	glow(gtx, sz.X*9/10, sz.Y, sz.Y*2/3, mix(tone, rgb(0x1C6E8C), 0.5), 0.4)

	white := rgb(0xF2F3F5)
	if u.moreBtn.Clicked(gtx) {
		u.moreOpen = !u.moreOpen
	}
	if u.menuCmp.Clicked(gtx) {
		u.moreOpen = false
		u.compact = true
		if u.hooks.SetCompact != nil {
			u.hooks.SetCompact(true)
		}
	}
	if u.menuAlbum.Clicked(gtx) {
		u.moreOpen = false
		if lib := u.Library(); lib != nil {
			for _, a := range lib.Albums {
				if a.Key == t.AlbumKey() {
					u.album, u.group, u.route, u.nowPlaying = a, nil, RouteLibrary, false
					u.search.SetText("")
				}
			}
		}
	}
	source := st.Source
	if source == "" {
		source = firstNonEmpty(t.Album, t.DisplayArtist())
	}
	d := layout.Flex{Axis: layout.Vertical}.Layout(gtx,
		// topo: voltar, "TOCANDO DE <origem>", mais opções (a faixa toda arrasta a janela)
		layout.Rigid(func(gtx C) D {
			return layout.Inset{Top: 14, Left: 22, Right: 22}.Layout(gtx, func(gtx C) D {
				gtx.Constraints.Min.X = gtx.Constraints.Max.X
				return layout.Stack{}.Layout(gtx,
					layout.Expanded(func(gtx C) D {
						u.dragArea(gtx, gtx.Constraints.Min)
						return D{Size: gtx.Constraints.Min}
					}),
					layout.Stacked(func(gtx C) D {
						gtx.Constraints.Min.X = gtx.Constraints.Max.X
						return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
							layout.Rigid(func(gtx C) D {
								return glass(gtx, 22, 0x14, func(gtx C) D {
									return u.iconButton(gtx, &u.closeNow, IcCaretDown, 44, 20, white, false)
								})
							}),
							layout.Flexed(1, func(gtx C) D {
								return layout.Center.Layout(gtx, func(gtx C) D {
									return layout.Flex{Axis: layout.Vertical, Alignment: layout.Middle}.Layout(gtx,
										layout.Rigid(u.label(upper(i18n.T("playing_from")), 11, font.Bold, soft).Layout),
										layout.Rigid(u.label(source, 14, font.ExtraBold, white).Layout),
									)
								})
							}),
							layout.Rigid(func(gtx C) D {
								return glass(gtx, 22, 0x14, func(gtx C) D {
									return u.iconButton(gtx, &u.moreBtn, IcDotsThree, 44, 20, white, false)
								})
							}),
						)
					}),
				)
			})
		}),
		// capa + painel
		layout.Flexed(1, func(gtx C) D {
			return layout.Inset{Top: 24, Bottom: 48, Left: 64, Right: 64}.Layout(gtx, func(gtx C) D {
				return layout.Center.Layout(gtx, func(gtx C) D {
					coverSize := gtx.Metric.PxToDp(gtx.Constraints.Max.Y)
					if half := gtx.Metric.PxToDp(gtx.Constraints.Max.X) * 0.45; coverSize > half {
						coverSize = half
					}
					if coverSize > 480 {
						coverSize = 480
					}
					return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
						layout.Rigid(func(gtx C) D {
							// sombra suave sob a capa
							s := gtx.Dp(coverSize)
							for i := 6; i >= 1; i-- {
								g := gtx.Dp(unit.Dp(i * 5))
								paint.FillShape(gtx.Ops, color.NRGBA{A: 0x10},
									clip.UniformRRect(image.Rect(-g/2, g/2, s+g/2, s+g), gtx.Dp(28)+g/2).Op(gtx.Ops))
							}
							return u.coverArt(gtx, t, coverSize, 28)
						}),
						layout.Rigid(layout.Spacer{Width: 48}.Layout),
						layout.Rigid(func(gtx C) D {
							gtx.Constraints.Max.X = gtx.Dp(560)
							gtx.Constraints.Min.X = gtx.Constraints.Max.X
							return u.vidroPanel(gtx, st, white, soft)
						}),
					)
				})
			})
		}),
	)
	if u.moreOpen {
		u.moreMenu(gtx, white)
	}
	return d
}

// moreMenu é o menu do botão "…" do player Vidro.
func (u *UI) moreMenu(gtx C, white color.NRGBA) {
	scrim := u.rows.get("more-scrim")
	if scrim.Clicked(gtx) {
		u.moreOpen = false
	}
	scrim.Layout(gtx, func(gtx C) D { return D{Size: gtx.Constraints.Max} })
	w := gtx.Dp(240)
	offset(gtx, gtx.Constraints.Max.X-gtx.Dp(22)-w, gtx.Dp(66), func(gtx C) D {
		gtx.Constraints = layout.Exact(image.Pt(w, gtx.Dp(116)))
		fillRRect(gtx, rgb(0x1E2125), 18)
		return layout.UniformInset(8).Layout(gtx, func(gtx C) D {
			entry := func(btn *widget.Clickable, ic *Icon, txt string) layout.FlexChild {
				return layout.Rigid(func(gtx C) D {
					return btn.Layout(gtx, func(gtx C) D {
						pointer.CursorPointer.Add(gtx.Ops)
						gtx.Constraints.Min.X = gtx.Constraints.Max.X
						bg := rgb(0x1E2125)
						if btn.Hovered() {
							bg = rgb(0x2A2D33)
						}
						return surface(gtx, bg, 12, func(gtx C) D {
							gtx.Constraints.Min.Y = gtx.Dp(50)
							return layout.Inset{Left: 12, Right: 12}.Layout(gtx, func(gtx C) D {
								return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
									layout.Rigid(func(gtx C) D { return drawIcon(gtx, ic, 20, white, false) }),
									layout.Rigid(layout.Spacer{Width: 12}.Layout),
									layout.Flexed(1, func(gtx C) D { return layout.W.Layout(gtx, u.label(txt, 14, font.Bold, white).Layout) }),
								)
							})
						})
					})
				})
			}
			return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
				entry(&u.menuCmp, IcPictureInPicture, i18n.T("compact")),
				entry(&u.menuAlbum, IcVinylRecord, i18n.T("go_album")),
			)
		})
	})
}

func (u *UI) vidroPanel(gtx C, st player.State, white, soft color.NRGBA) D {
	t := st.Current
	return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
		layout.Rigid(func(gtx C) D {
			return layout.Flex{Alignment: layout.Start}.Layout(gtx,
				layout.Flexed(1, func(gtx C) D {
					return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
						layout.Rigid(func(gtx C) D {
							l := u.label(t.Title, 40, font.ExtraBold, white)
							l.MaxLines = 2
							return l.Layout(gtx)
						}),
						layout.Rigid(layout.Spacer{Height: 4}.Layout),
						layout.Rigid(u.label(joinNonEmpty(" · ", t.DisplayArtist(), t.Album), 18, font.SemiBold, soft).Layout),
					)
				}),
				layout.Rigid(func(gtx C) D {
					return glass(gtx, 26, 0x1A, func(gtx C) D {
						return u.iconButton(gtx, &u.likeBtn, IcHeart, 52, 22, white, u.ctl.IsFavorite(t.ID))
					})
				}),
			)
		}),
		layout.Rigid(layout.Spacer{Height: 22}.Layout),
		layout.Rigid(func(gtx C) D {
			return glass(gtx, 28, 0x0E, func(gtx C) D {
				return layout.Inset{Top: 22, Bottom: 22, Left: 26, Right: 26}.Layout(gtx, func(gtx C) D {
					return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
						layout.Rigid(func(gtx C) D { return u.seekBar(gtx, st, color.NRGBA{R: 255, G: 255, B: 255, A: 0x30}, white) }),
						layout.Rigid(func(gtx C) D {
							return layout.Flex{}.Layout(gtx,
								layout.Rigid(u.timeLabel(u.ctl.PositionMs(), soft)),
								layout.Flexed(1, layout.Spacer{}.Layout),
								layout.Rigid(u.timeLabel(t.DurationMs, soft)),
							)
						}),
						layout.Rigid(layout.Spacer{Height: 12}.Layout),
						layout.Rigid(func(gtx C) D {
							return layout.Flex{Alignment: layout.Middle, Spacing: layout.SpaceBetween}.Layout(gtx,
								layout.Rigid(func(gtx C) D { return u.iconButton(gtx, &u.shufBtn, IcShuffle, 48, 22, soft, st.Shuffle) }),
								layout.Rigid(func(gtx C) D { return u.iconButton(gtx, &u.prevBtn, IcSkipBack, 60, 30, white, false) }),
								layout.Rigid(func(gtx C) D {
									return u.playButton(gtx, &u.playBtn, st.Playing, 84, white, mix(u.coverTone(t), rgb(0), 0.75))
								}),
								layout.Rigid(func(gtx C) D { return u.iconButton(gtx, &u.nextBtn, IcSkipForward, 60, 30, white, false) }),
								layout.Rigid(func(gtx C) D {
									return u.iconButton(gtx, &u.repBtn, u.repeatIcon(st.Repeat), 48, 22, soft, st.Repeat != player.RepeatOff)
								}),
							)
						}),
						layout.Rigid(layout.Spacer{Height: 12}.Layout),
						layout.Rigid(func(gtx C) D {
							return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
								layout.Rigid(func(gtx C) D { return drawIcon(gtx, IcSpeakerLow, 20, soft, false) }),
								layout.Rigid(layout.Spacer{Width: 10}.Layout),
								layout.Flexed(1, func(gtx C) D {
									d, changed := u.progressBar(gtx, &u.volume, float32(st.Volume), 5, color.NRGBA{R: 255, G: 255, B: 255, A: 0x30}, soft)
									if changed {
										u.ctl.SetVolume(float64(u.volume.Value))
									}
									return d
								}),
								layout.Rigid(layout.Spacer{Width: 10}.Layout),
								layout.Rigid(func(gtx C) D { return drawIcon(gtx, IcSpeakerHigh, 20, soft, false) }),
							)
						}),
					)
				})
			})
		}),
		layout.Rigid(layout.Spacer{Height: 18}.Layout),
		layout.Rigid(func(gtx C) D {
			var chips []layout.FlexChild
			u.mu.Lock()
			server := u.audioServer
			u.mu.Unlock()
			info := []string{t.Quality(), server}
			if t.Genre != "" {
				info = append(info, t.Genre)
			}
			for _, s := range info {
				if s == "" {
					continue
				}
				s := s
				chips = append(chips, layout.Rigid(func(gtx C) D {
					return layout.Inset{Right: 8}.Layout(gtx, func(gtx C) D {
						return glass(gtx, 17, 0x12, func(gtx C) D {
							return layout.Inset{Top: 8, Bottom: 8, Left: 14, Right: 14}.Layout(gtx, u.label(s, 12, font.ExtraBold, white).Layout)
						})
					})
				}))
			}
			return layout.Flex{}.Layout(gtx, chips...)
		}),
		layout.Rigid(layout.Spacer{Height: 18}.Layout),
		layout.Rigid(func(gtx C) D {
			if len(st.Queue) < 2 {
				return D{}
			}
			return glass(gtx, 22, 0x0A, func(gtx C) D {
				return layout.Inset{Top: 14, Bottom: 10, Left: 18, Right: 18}.Layout(gtx, func(gtx C) D {
					ch := []layout.FlexChild{layout.Rigid(func(gtx C) D {
						return layout.Inset{Bottom: 8}.Layout(gtx, u.label(upper(i18n.T("up_next")), 12, font.ExtraBold, soft).Layout)
					})}
					for i := 1; i < len(st.Queue) && i <= 3; i++ {
						q := st.Queue[i]
						i := i
						ch = append(ch, layout.Rigid(func(gtx C) D {
							btn := u.rows.get(fmt.Sprintf("vq:%d:%d", i, q.ID))
							if btn.Clicked(gtx) {
								u.ctl.JumpTo(i)
							}
							return btn.Layout(gtx, func(gtx C) D {
								pointer.CursorPointer.Add(gtx.Ops)
								return layout.Inset{Top: 4, Bottom: 4}.Layout(gtx, func(gtx C) D {
									return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
										layout.Rigid(func(gtx C) D { return u.coverArt(gtx, q, 36, 10) }),
										layout.Rigid(layout.Spacer{Width: 12}.Layout),
										layout.Flexed(1, func(gtx C) D {
											return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
												layout.Rigid(u.label(q.Title, 14, font.Bold, white).Layout),
												layout.Rigid(u.label(q.DisplayArtist(), 12, font.Medium, soft).Layout),
											)
										}),
										layout.Rigid(u.label(core.FormatTime(q.DurationMs), 12, font.Medium, soft).Layout),
									)
								})
							})
						}))
					}
					return layout.Flex{Axis: layout.Vertical}.Layout(gtx, ch...)
				})
			})
		}),
	)
}

func joinNonEmpty(sep string, parts ...string) string {
	out := ""
	for _, p := range parts {
		if p == "" {
			continue
		}
		if out != "" {
			out += sep
		}
		out += p
	}
	return out
}

// --- modo compacto (inspirado no Windows Media Player do Windows 7: só capa e controles) ---

func (u *UI) compactView(gtx C, st player.State) D {
	u.handleControls(gtx, st)
	t := st.Current
	sz := gtx.Constraints.Max
	paint.FillShape(gtx.Ops, u.pal.Bg, clip.Rect{Max: sz}.Op())
	if t != nil {
		glow(gtx, sz.X/3, sz.Y/5, sz.X*7/10, u.coverTone(t), 0.3)
	}
	white := u.pal.Text
	return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
		layout.Rigid(u.compactBar),
		layout.Flexed(1, func(gtx C) D {
			return layout.Inset{Top: 10, Left: 28, Right: 28, Bottom: 26}.Layout(gtx, func(gtx C) D {
				return layout.Flex{Axis: layout.Vertical, Alignment: layout.Middle}.Layout(gtx,
					layout.Flexed(1, func(gtx C) D {
						return layout.Center.Layout(gtx, func(gtx C) D {
							s := gtx.Metric.PxToDp(gtx.Constraints.Max.Y)
							if w := gtx.Metric.PxToDp(gtx.Constraints.Max.X); w < s {
								s = w
							}
							return u.coverArt(gtx, t, s, 24)
						})
					}),
					layout.Rigid(layout.Spacer{Height: 18}.Layout),
					layout.Rigid(func(gtx C) D {
						if t == nil {
							return centered(u.label(i18n.T("nothing_playing"), 18, font.ExtraBold, white)).Layout(gtx)
						}
						return layout.Flex{Axis: layout.Vertical, Alignment: layout.Middle}.Layout(gtx,
							layout.Rigid(centered(u.label(t.Title, 18, font.ExtraBold, white)).Layout),
							layout.Rigid(layout.Spacer{Height: 2}.Layout),
							layout.Rigid(centered(u.label(t.DisplayArtist(), 13, font.Medium, u.pal.Text2)).Layout),
						)
					}),
					layout.Rigid(layout.Spacer{Height: 12}.Layout),
					layout.Rigid(func(gtx C) D {
						return u.seekBar(gtx, st, color.NRGBA{R: 255, G: 255, B: 255, A: 0x24}, white)
					}),
					layout.Rigid(layout.Spacer{Height: 8}.Layout),
					layout.Rigid(func(gtx C) D {
						gtx.Constraints.Min.X = gtx.Constraints.Max.X
						return layout.Flex{Alignment: layout.Middle, Spacing: layout.SpaceSides}.Layout(gtx,
							layout.Rigid(func(gtx C) D { return u.iconButton(gtx, &u.prevBtn, IcSkipBack, 48, 24, white, false) }),
							layout.Rigid(layout.Spacer{Width: 18}.Layout),
							layout.Rigid(func(gtx C) D { return u.playButton(gtx, &u.playBtn, st.Playing, 60, u.pal.Accent, u.pal.OnAccent) }),
							layout.Rigid(layout.Spacer{Width: 18}.Layout),
							layout.Rigid(func(gtx C) D { return u.iconButton(gtx, &u.nextBtn, IcSkipForward, 48, 24, white, false) }),
						)
					}),
				)
			})
		}),
	)
}
