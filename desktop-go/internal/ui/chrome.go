package ui

import (
	"image"
	"image/color"
	"time"

	"gioui.org/f32"
	"gioui.org/font"
	"gioui.org/io/event"
	"gioui.org/io/pointer"
	"gioui.org/io/system"
	"gioui.org/layout"
	"gioui.org/op/clip"
	"gioui.org/op/paint"
	"gioui.org/unit"
	"gioui.org/widget"

	"github.com/Sonivell/sonivell/desktop-go/internal/i18n"
)

// chromeState é o estado da barra de título própria (a do mockup) e das bordas de redimensionar.
type chromeState struct {
	bar       int // alvo dos eventos de ponteiro da barra
	edges     [8]int
	pressed   bool
	pressPos  f32.Point
	lastPress time.Duration
	minBtn    widget.Clickable
	maxBtn    widget.Clickable
	closeBtn  widget.Clickable
}

// dragArea faz da área atual uma alça de mover a janela (duplo clique maximiza).
func (u *UI) dragArea(gtx C, size image.Point) {
	defer clip.Rect{Max: size}.Push(gtx.Ops).Pop()
	if !u.x11 || u.hooks.StartMove == nil {
		// Wayland: o próprio Gio move a janela.
		system.ActionInputOp(system.ActionMove).Add(gtx.Ops)
		return
	}
	c := &u.chrome
	for {
		ev, ok := gtx.Event(pointer.Filter{Target: &c.bar, Kinds: pointer.Press | pointer.Drag | pointer.Release | pointer.Cancel})
		if !ok {
			break
		}
		e, ok := ev.(pointer.Event)
		if !ok {
			continue
		}
		switch e.Kind {
		case pointer.Press:
			if e.Buttons != pointer.ButtonPrimary {
				continue
			}
			if e.Time-c.lastPress < 400*time.Millisecond && !u.compact {
				c.pressed = false
				c.lastPress = 0
				if u.hooks.ToggleMaximize != nil {
					u.hooks.ToggleMaximize()
				}
				continue
			}
			c.lastPress = e.Time
			c.pressed, c.pressPos = true, e.Position
		case pointer.Drag:
			d := e.Position.Sub(c.pressPos)
			if c.pressed && d.X*d.X+d.Y*d.Y > float32(gtx.Dp(4)*gtx.Dp(4)) {
				c.pressed = false
				c.lastPress = 0
				u.hooks.StartMove()
			}
		case pointer.Release, pointer.Cancel:
			c.pressed = false
		}
	}
	event.Op(gtx.Ops, &c.bar)
}

func (u *UI) handleWindowButtons(gtx C) {
	c := &u.chrome
	if c.minBtn.Clicked(gtx) && u.hooks.Minimize != nil {
		u.hooks.Minimize()
	}
	if c.maxBtn.Clicked(gtx) && u.hooks.ToggleMaximize != nil {
		u.hooks.ToggleMaximize()
	}
	if c.closeBtn.Clicked(gtx) && u.hooks.Close != nil {
		u.hooks.Close()
	}
}

// roundButton é o botão redondo da barra de título (fundo de superfície, ícone pequeno).
func (u *UI) roundButton(gtx C, btn *widget.Clickable, ic *Icon, size, iconSize unit.Dp, bg, hover, fg color.NRGBA) D {
	return btn.Layout(gtx, func(gtx C) D {
		pointer.CursorPointer.Add(gtx.Ops)
		return box(gtx, size, size, func(gtx C) D {
			c := bg
			if btn.Hovered() {
				c = hover
			}
			paint.FillShape(gtx.Ops, c, clip.Ellipse(image.Rectangle{Max: gtx.Constraints.Min}).Op(gtx.Ops))
			return layout.Center.Layout(gtx, func(gtx C) D { return drawIcon(gtx, ic, iconSize, fg, false) })
		})
	})
}

// titleBar é a barra de 44 dp do mockup: ícone, "Sonivell GO · Reescrito em Go…" e os três botões.
func (u *UI) titleBar(gtx C) D {
	u.handleWindowButtons(gtx)
	h := gtx.Dp(44)
	size := image.Pt(gtx.Constraints.Max.X, h)
	gtx.Constraints = layout.Exact(size)
	paint.FillShape(gtx.Ops, u.pal.Bg, clip.Rect{Max: size}.Op())
	paint.FillShape(gtx.Ops, u.pal.Surface, clip.Rect{Min: image.Pt(0, h-gtx.Dp(1)), Max: size}.Op())
	u.dragArea(gtx, size)

	return minHeight(gtx, 44, func(gtx C) D {
		return layout.Inset{Left: 18, Right: 14}.Layout(gtx, func(gtx C) D {
			return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
				layout.Rigid(func(gtx C) D {
					return box(gtx, 20, 20, func(gtx C) D {
						fillRRect(gtx, u.pal.SurfaceHigh, 6)
						return layout.Center.Layout(gtx, func(gtx C) D { return drawWave(gtx, 16, u.pal.Accent, 2.4) })
					})
				}),
				layout.Rigid(layout.Spacer{Width: 10}.Layout),
				layout.Rigid(u.label(i18n.T("app_name"), 13, font.Bold, u.pal.Text).Layout),
				layout.Rigid(layout.Spacer{Width: 6}.Layout),
				layout.Flexed(1, u.label("· "+i18n.T("app_tagline"), 12, font.Medium, u.pal.Text2).Layout),
				layout.Rigid(func(gtx C) D {
					return u.windowButtons(gtx, 28, 12, u.pal.Surface, u.pal.SurfaceHigh, u.pal.Text2, true)
				}),
			)
		})
	})
}

func (u *UI) windowButtons(gtx C, size, iconSize unit.Dp, bg, hover, fg color.NRGBA, withMax bool) D {
	c := &u.chrome
	maxIcon := IcSquare
	if u.maximized {
		maxIcon = IcCopy
	}
	ch := []layout.FlexChild{
		layout.Rigid(func(gtx C) D { return u.roundButton(gtx, &c.minBtn, IcMinus, size, iconSize, bg, hover, fg) }),
		layout.Rigid(layout.Spacer{Width: 6}.Layout),
	}
	if withMax {
		ch = append(ch,
			layout.Rigid(func(gtx C) D { return u.roundButton(gtx, &c.maxBtn, maxIcon, size, iconSize, bg, hover, fg) }),
			layout.Rigid(layout.Spacer{Width: 6}.Layout),
		)
	}
	ch = append(ch, layout.Rigid(func(gtx C) D {
		return u.roundButton(gtx, &c.closeBtn, IcX, size, iconSize, bg, rgb(0xE5484D), fg)
	}))
	return layout.Flex{Alignment: layout.Middle}.Layout(gtx, ch...)
}

// windowEdges são as bordas de 6 dp para redimensionar a janela (só no X11, fora de maximizada).
func (u *UI) windowEdges(gtx C) {
	if !u.x11 || u.hooks.StartResize == nil || u.maximized {
		return
	}
	sz := gtx.Constraints.Max
	m := gtx.Dp(6)
	k := gtx.Dp(14) // cantos
	type edge struct {
		r      image.Rectangle
		dir    int
		cursor pointer.Cursor
	}
	edges := []edge{
		{image.Rect(k, 0, sz.X-k, m), 1, pointer.CursorNorthSouthResize},
		{image.Rect(sz.X-m, k, sz.X, sz.Y-k), 3, pointer.CursorEastWestResize},
		{image.Rect(k, sz.Y-m, sz.X-k, sz.Y), 5, pointer.CursorNorthSouthResize},
		{image.Rect(0, k, m, sz.Y-k), 7, pointer.CursorEastWestResize},
		{image.Rect(0, 0, k, m), 0, pointer.CursorNorthWestResize},
		{image.Rect(0, 0, m, k), 0, pointer.CursorNorthWestResize},
		{image.Rect(sz.X-k, 0, sz.X, m), 2, pointer.CursorNorthEastResize},
		{image.Rect(sz.X-m, 0, sz.X, k), 2, pointer.CursorNorthEastResize},
		{image.Rect(sz.X-k, sz.Y-m, sz.X, sz.Y), 4, pointer.CursorSouthEastResize},
		{image.Rect(sz.X-m, sz.Y-k, sz.X, sz.Y), 4, pointer.CursorSouthEastResize},
		{image.Rect(0, sz.Y-m, k, sz.Y), 6, pointer.CursorSouthWestResize},
		{image.Rect(0, sz.Y-k, m, sz.Y), 6, pointer.CursorSouthWestResize},
	}
	for _, e := range edges {
		tag := &u.chrome.edges[e.dir]
		for {
			ev, ok := gtx.Event(pointer.Filter{Target: tag, Kinds: pointer.Press})
			if !ok {
				break
			}
			if pe, ok := ev.(pointer.Event); ok && pe.Buttons == pointer.ButtonPrimary {
				u.hooks.StartResize(e.dir)
			}
		}
		st := clip.Rect(e.r).Push(gtx.Ops)
		e.cursor.Add(gtx.Ops)
		event.Op(gtx.Ops, tag)
		st.Pop()
	}
	// borda fina em volta (sem a moldura do sistema, separa a janela do fundo da área de trabalho)
	paint.FillShape(gtx.Ops, u.pal.Outline, clip.Stroke{Path: clip.Rect{Max: sz}.Path(), Width: float32(gtx.Dp(1))}.Op())
}

// compactBar é a barra do modo compacto: "Sonivell GO", voltar à janela completa e fechar.
func (u *UI) compactBar(gtx C) D {
	u.handleWindowButtons(gtx)
	h := gtx.Dp(40)
	size := image.Pt(gtx.Constraints.Max.X, h)
	gtx.Constraints = layout.Exact(size)
	u.dragArea(gtx, size)
	glassBg := color.NRGBA{R: 255, G: 255, B: 255, A: 0x14}
	glassHover := color.NRGBA{R: 255, G: 255, B: 255, A: 0x28}
	return minHeight(gtx, 40, func(gtx C) D {
		return layout.Inset{Left: 14, Right: 10}.Layout(gtx, func(gtx C) D {
			return layout.Flex{Alignment: layout.Middle}.Layout(gtx,
				layout.Flexed(1, u.label(i18n.T("app_name"), 12, font.Bold, u.pal.Text2).Layout),
				layout.Rigid(func(gtx C) D {
					return u.roundButton(gtx, &u.compactBtn, IcArrowsOutSimple, 32, 14, glassBg, glassHover, u.pal.Text)
				}),
				layout.Rigid(layout.Spacer{Width: 4}.Layout),
				layout.Rigid(func(gtx C) D {
					return u.roundButton(gtx, &u.chrome.closeBtn, IcX, 32, 12, glassBg, rgb(0xE5484D), u.pal.Text)
				}),
			)
		})
	})
}
