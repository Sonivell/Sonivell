package ui

import (
	"os"
	"path/filepath"
	"strings"

	"gioui.org/font"
	"gioui.org/io/pointer"
	"gioui.org/layout"
	"gioui.org/widget"
	"gioui.org/widget/material"

	"github.com/Sonivell/sonivell/desktop-go/internal/core"
	"github.com/Sonivell/sonivell/desktop-go/internal/i18n"
)

// Version é a versão mostrada em Ajustes (preenchida pelo main a partir do build).
var Version = "dev"

func (u *UI) settingsItems(gtx C) []item {
	s := u.store.Get()

	// ações
	addPath := func(p string) {
		p = strings.TrimSpace(p)
		if strings.HasPrefix(p, "~/") {
			if home, err := os.UserHomeDir(); err == nil {
				p = filepath.Join(home, p[2:])
			}
		}
		if st, err := os.Stat(p); p == "" || err != nil || !st.IsDir() {
			return
		}
		for _, d := range s.MusicDirs {
			if d == p {
				return
			}
		}
		u.store.Update(func(x *core.Settings) { x.MusicDirs = append(x.MusicDirs, p) })
		u.folderEd.SetText("")
		if u.hooks.Rescan != nil {
			u.hooks.Rescan()
		}
	}
	for {
		ev, ok := u.folderEd.Update(gtx)
		if !ok {
			break
		}
		if e, ok := ev.(widget.SubmitEvent); ok {
			addPath(e.Text)
		}
	}
	if u.addFolder.Clicked(gtx) {
		addPath(u.folderEd.Text())
	}
	if u.pickFolder.Clicked(gtx) && u.hooks.PickFolder != nil {
		go func() {
			if p, ok := u.hooks.PickFolder(); ok {
				u.mu.Lock()
				u.picked = p
				u.mu.Unlock()
				if u.hooks.Invalidate != nil {
					u.hooks.Invalidate()
				}
			}
		}()
	}
	u.mu.Lock()
	if u.picked != "" {
		u.folderEd.SetText(u.picked)
		u.picked = ""
	}
	u.mu.Unlock()
	if u.rescanBtn.Clicked(gtx) && u.hooks.Rescan != nil {
		u.hooks.Rescan()
	}
	if len(u.rmFolder) < len(s.MusicDirs) {
		u.rmFolder = make([]widget.Clickable, len(s.MusicDirs)+4)
	}
	for i, d := range s.MusicDirs {
		if u.rmFolder[i].Clicked(gtx) {
			dir := d
			u.store.Update(func(x *core.Settings) {
				out := x.MusicDirs[:0]
				for _, e := range x.MusicDirs {
					if e != dir {
						out = append(out, e)
					}
				}
				x.MusicDirs = out
			})
			if u.hooks.Rescan != nil {
				u.hooks.Rescan()
			}
		}
	}
	for i := range u.accentBtns {
		if u.accentBtns[i].Clicked(gtx) {
			key := Accents[i].Key
			u.store.Update(func(x *core.Settings) { x.Accent = key })
			u.pal = PaletteFor(key)
			u.applyPalette()
		}
	}

	card := func(title string, body layout.Widget) item {
		return func(gtx C) D {
			return layout.Inset{Bottom: 18}.Layout(gtx, func(gtx C) D {
				gtx.Constraints.Min.X = gtx.Constraints.Max.X
				return surface(gtx, u.pal.Surface, 22, func(gtx C) D {
					return layout.UniformInset(22).Layout(gtx, func(gtx C) D {
						return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
							layout.Rigid(u.label(title, 16, font.ExtraBold, u.pal.Text).Layout),
							layout.Rigid(layout.Spacer{Height: 14}.Layout),
							layout.Rigid(body),
						)
					})
				})
			})
		}
	}

	folders := func(gtx C) D {
		var ch []layout.FlexChild
		for i, d := range s.MusicDirs {
			i, d := i, d
			ch = append(ch, layout.Rigid(func(gtx C) D {
				return layout.Inset{Bottom: 8}.Layout(gtx, func(gtx C) D {
					return surface(gtx, u.pal.SurfaceLow, 14, func(gtx C) D {
						return layout.Inset{Top: 6, Bottom: 6, Left: 14, Right: 6}.Layout(gtx, func(gtx C) D {
							return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
								layout.Rigid(func(gtx C) D { return drawIcon(gtx, IcFolderOpen, 20, u.pal.Text3, false) }),
								layout.Rigid(layout.Spacer{Width: 12}.Layout),
								layout.Flexed(1, u.label(d, 14, font.SemiBold, u.pal.Text).Layout),
								layout.Rigid(func(gtx C) D { return u.iconButton(gtx, &u.rmFolder[i], IcTrash, 40, 18, u.pal.Text2, false) }),
							)
						})
					})
				})
			}))
		}
		ch = append(ch, layout.Rigid(func(gtx C) D {
			return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
				layout.Flexed(1, func(gtx C) D {
					return surface(gtx, u.pal.SurfaceLow, 14, func(gtx C) D {
						return layout.Inset{Top: 12, Bottom: 12, Left: 14, Right: 14}.Layout(gtx, func(gtx C) D {
							e := material.Editor(u.th, &u.folderEd, i18n.T("settings_add_hint"))
							e.Font = font.Font{Typeface: typeface, Weight: font.Medium}
							e.TextSize = 13
							e.Color, e.HintColor = u.pal.Text, u.pal.Text2
							e.SelectionColor = withAlpha(u.pal.Accent, 0x60)
							return e.Layout(gtx)
						})
					})
				}),
				layout.Rigid(layout.Spacer{Width: 8}.Layout),
				layout.Rigid(func(gtx C) D {
					if u.hooks.PickFolder == nil {
						return D{}
					}
					return layout.Inset{Right: 8}.Layout(gtx, func(gtx C) D {
						return u.iconButton(gtx, &u.pickFolder, IcFolderPlus, 44, 20, u.pal.Text3, false)
					})
				}),
				layout.Rigid(func(gtx C) D { return u.textButton(gtx, &u.addFolder, i18n.T("settings_add"), true) }),
			)
		}))
		ch = append(ch, layout.Rigid(layout.Spacer{Height: 14}.Layout))
		ch = append(ch, layout.Rigid(func(gtx C) D { return u.textButton(gtx, &u.rescanBtn, i18n.T("settings_rescan"), false) }))
		return layout.Flex{Axis: layout.Vertical}.Layout(gtx, ch...)
	}

	accents := func(gtx C) D {
		var ch []layout.FlexChild
		for i, a := range Accents {
			i, a := i, a
			ch = append(ch, layout.Rigid(func(gtx C) D {
				return layout.Inset{Right: 12}.Layout(gtx, func(gtx C) D {
					btn := &u.accentBtns[i]
					return btn.Layout(gtx, func(gtx C) D {
						pointer.CursorPointer.Add(gtx.Ops)
						return box(gtx, 44, 44, func(gtx C) D {
							sel := a.Color == u.pal.Accent
							if sel || btn.Hovered() {
								fillRRect(gtx, u.pal.Text3, 22)
							}
							return layout.UniformInset(4).Layout(gtx, func(gtx C) D {
								gtx.Constraints.Min = gtx.Constraints.Max
								fillRRect(gtx, a.Color, 18)
								if sel {
									return layout.Center.Layout(gtx, func(gtx C) D { return drawIcon(gtx, IcCheck, 20, u.pal.OnAccent, false) })
								}
								return D{Size: gtx.Constraints.Max}
							})
						})
					})
				})
			}))
		}
		return layout.Flex{}.Layout(gtx, ch...)
	}

	about := func(gtx C) D {
		return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
			layout.Rigid(func(gtx C) D {
				return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
					layout.Rigid(func(gtx C) D {
						return box(gtx, 56, 56, func(gtx C) D {
							fillRRect(gtx, u.pal.SurfaceHigh, 16)
							return layout.Center.Layout(gtx, func(gtx C) D { return drawWave(gtx, 40, u.pal.Accent, 2.4) })
						})
					}),
					layout.Rigid(layout.Spacer{Width: 14}.Layout),
					layout.Rigid(func(gtx C) D {
						return layout.Flex{Axis: layout.Vertical}.Layout(gtx,
							layout.Rigid(u.label(i18n.T("app_name"), 20, font.ExtraBold, u.pal.Text).Layout),
							layout.Rigid(u.label(i18n.T("about_version", Version), 13, font.Medium, u.pal.Text2).Layout),
						)
					}),
				)
			}),
			layout.Rigid(layout.Spacer{Height: 14}.Layout),
			layout.Rigid(u.para(i18n.T("about_body"), 14, u.pal.Text3).Layout),
			layout.Rigid(layout.Spacer{Height: 10}.Layout),
			layout.Rigid(u.para(i18n.T("about_thanks"), 13, u.pal.Text2).Layout),
		)
	}

	return []item{
		func(gtx C) D {
			return layout.Inset{Bottom: 20}.Layout(gtx, u.label(i18n.T("nav_settings"), 34, font.ExtraBold, u.pal.Text).Layout)
		},
		card(i18n.T("settings_folders"), folders),
		card(i18n.T("settings_accent"), accents),
		card(i18n.T("settings_about"), about),
	}
}
