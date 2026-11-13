package ui

import (
	"image"
	"image/color"

	"gioui.org/f32"
	"gioui.org/font"
	"gioui.org/io/pointer"
	"gioui.org/layout"
	"gioui.org/op"
	"gioui.org/op/clip"
	"gioui.org/op/paint"
	"gioui.org/text"
	"gioui.org/unit"
	"gioui.org/widget"
	"gioui.org/widget/material"

	"github.com/Sonivell/sonivell/desktop-go/internal/core"
)

type (
	C = layout.Context
	D = layout.Dimensions
)

// fillRRect pinta um retângulo arredondado do tamanho mínimo das restrições.
func fillRRect(gtx C, col color.NRGBA, radius unit.Dp) D {
	sz := gtx.Constraints.Min
	rr := clip.UniformRRect(image.Rectangle{Max: sz}, gtx.Dp(radius))
	paint.FillShape(gtx.Ops, col, rr.Op(gtx.Ops))
	return D{Size: sz}
}

// surface desenha w sobre um fundo arredondado.
func surface(gtx C, col color.NRGBA, radius unit.Dp, w layout.Widget) D {
	return layout.Background{}.Layout(gtx, func(gtx C) D { return fillRRect(gtx, col, radius) }, w)
}

// box ocupa exatamente w×h dp.
func box(gtx C, w, h unit.Dp, f layout.Widget) D {
	gtx.Constraints = layout.Exact(image.Pt(gtx.Dp(w), gtx.Dp(h)))
	return f(gtx)
}

// label é um texto de uma linha (com reticências) na Manrope.
func (u *UI) label(txt string, size unit.Sp, weight font.Weight, col color.NRGBA) material.LabelStyle {
	l := material.Label(u.th, size, txt)
	l.Font = font.Font{Typeface: typeface, Weight: weight}
	l.Color = col
	l.MaxLines = 1
	return l
}

// para é um texto de várias linhas.
func (u *UI) para(txt string, size unit.Sp, col color.NRGBA) material.LabelStyle {
	l := material.Label(u.th, size, txt)
	l.Font = font.Font{Typeface: typeface, Weight: font.Medium}
	l.Color = col
	l.LineHeightScale = 1.35
	return l
}

func centered(l material.LabelStyle) material.LabelStyle { l.Alignment = text.Middle; return l }

// drawIcon pinta um ícone do Phosphor de size dp na cor col.
func drawIcon(gtx C, ic *Icon, size unit.Dp, col color.NRGBA, fill bool) D {
	px := gtx.Dp(size)
	spec := ic.Path(gtx.Ops, float32(px), fill)
	paint.FillShape(gtx.Ops, col, clip.Outline{Path: spec}.Op())
	return D{Size: image.Pt(px, px)}
}

// drawWave é a onda do Sonivell (o ícone da aba Para você e do app), num quadrado de size dp.
func drawWave(gtx C, size unit.Dp, col color.NRGBA, width float32) D {
	px := float32(gtx.Dp(size))
	s := px / 24
	pts := []f32.Point{{X: 3, Y: 12}}
	var p clip.Path
	p.Begin(gtx.Ops)
	p.MoveTo(f32.Pt(3*s, 12*s))
	// "M3 12c2-5 4-5 6 0s4 5 6 0 4-5 6 0" (o mesmo desenho do app Android)
	p.CubeTo(f32.Pt(5*s, 7*s), f32.Pt(7*s, 7*s), f32.Pt(9*s, 12*s))
	p.CubeTo(f32.Pt(11*s, 17*s), f32.Pt(13*s, 17*s), f32.Pt(15*s, 12*s))
	p.CubeTo(f32.Pt(17*s, 7*s), f32.Pt(19*s, 7*s), f32.Pt(21*s, 12*s))
	spec := p.End()
	w := width * s
	paint.FillShape(gtx.Ops, col, clip.Stroke{Path: spec, Width: w}.Op())
	// pontas arredondadas
	for _, c := range append(pts, f32.Point{X: 21, Y: 12}) {
		r := w / 2
		rect := image.Rect(int(c.X*s-r), int(c.Y*s-r), int(c.X*s+r+0.5), int(c.Y*s+r+0.5))
		paint.FillShape(gtx.Ops, col, clip.Ellipse(rect).Op(gtx.Ops))
	}
	return D{Size: image.Pt(int(px), int(px))}
}

// iconButton é um botão redondo só com ícone (com fundo ao passar o mouse).
func (u *UI) iconButton(gtx C, btn *widget.Clickable, ic *Icon, size, iconSize unit.Dp, col color.NRGBA, active bool) D {
	return btn.Layout(gtx, func(gtx C) D {
		pointer.CursorPointer.Add(gtx.Ops)
		return box(gtx, size, size, func(gtx C) D {
			if btn.Hovered() {
				paint.FillShape(gtx.Ops, withAlpha(u.pal.Text, 0x14), clip.Ellipse(image.Rectangle{Max: gtx.Constraints.Min}).Op(gtx.Ops))
			}
			c := col
			if active {
				c = u.pal.Accent
			}
			return layout.Center.Layout(gtx, func(gtx C) D { return drawIcon(gtx, ic, iconSize, c, active) })
		})
	})
}

// playButton é o botão grande de tocar/pausar (um "squircle" na cor de destaque).
func (u *UI) playButton(gtx C, btn *widget.Clickable, playing bool, size unit.Dp, bg, fg color.NRGBA) D {
	return btn.Layout(gtx, func(gtx C) D {
		pointer.CursorPointer.Add(gtx.Ops)
		return box(gtx, size, size, func(gtx C) D {
			c := bg
			if btn.Hovered() {
				c = mix(bg, rgb(0xFFFFFF), 0.12)
			}
			fillRRect(gtx, c, size*0.36)
			ic := IcPlay
			if playing {
				ic = IcPause
			}
			return layout.Center.Layout(gtx, func(gtx C) D { return drawIcon(gtx, ic, size*0.42, fg, true) })
		})
	})
}

// pill é um rótulo pequeno (formato do arquivo, contagem…).
func (u *UI) pill(gtx C, txt string, bg, fg color.NRGBA) D {
	return surface(gtx, bg, 8, func(gtx C) D {
		return layout.Inset{Top: 3, Bottom: 3, Left: 8, Right: 8}.Layout(gtx, u.label(txt, 11, font.ExtraBold, fg).Layout)
	})
}

// mix mistura duas cores (t = 0 → a, 1 → b).
func mix(a, b color.NRGBA, t float32) color.NRGBA {
	f := func(x, y uint8) uint8 { return uint8(float32(x) + (float32(y)-float32(x))*t) }
	return color.NRGBA{R: f(a.R, b.R), G: f(a.G, b.G), B: f(a.B, b.B), A: f(a.A, b.A)}
}

// coverArt desenha a capa da faixa (ou um bloco colorido com as iniciais) num quadrado.
func (u *UI) coverArt(gtx C, t *core.Track, size unit.Dp, radius unit.Dp) D {
	return box(gtx, size, size, func(gtx C) D {
		sz := gtx.Constraints.Min
		defer clip.UniformRRect(image.Rectangle{Max: sz}, gtx.Dp(radius)).Push(gtx.Ops).Pop()
		if t != nil {
			if img, ok := u.coverImage(t); ok {
				return widget.Image{Src: img, Fit: widget.Cover, Position: layout.Center}.Layout(gtx)
			}
		}
		if t == nil {
			paint.FillShape(gtx.Ops, u.pal.Surface, clip.Rect{Max: sz}.Op())
			return layout.Center.Layout(gtx, func(gtx C) D {
				return drawWave(gtx, unit.Dp(gtx.Metric.PxToDp(sz.X))*0.5, u.pal.Text2, 2)
			})
		}
		tone := coverTones[int(t.ID%uint64(len(coverTones)))]
		initials := core.Initials(firstNonEmpty(t.Album, t.Title))
		paint.FillShape(gtx.Ops, tone, clip.Rect{Max: sz}.Op())
		ts := unit.Sp(float32(gtx.Metric.PxToDp(sz.X)) * 0.28)
		return layout.Center.Layout(gtx, u.label(initials, ts, font.ExtraBold, withAlpha(u.pal.Bg, 0xD0)).Layout)
	})
}

func firstNonEmpty(a, b string) string {
	if a != "" {
		return a
	}
	return b
}

// coverImage devolve a ImageOp da capa (criada uma vez por capa).
func (u *UI) coverImage(t *core.Track) (paint.ImageOp, bool) {
	cv := u.covers.Snapshot(u.covers.Get(t))
	if !cv.Ready || cv.Img == nil {
		return paint.ImageOp{}, false
	}
	if op, ok := u.imgOps[cv.Img]; ok {
		return op, true
	}
	if len(u.imgOps) > 200 {
		u.imgOps = map[any]paint.ImageOp{}
	}
	op := paint.NewImageOp(cv.Img)
	op.Filter = paint.FilterLinear
	u.imgOps[cv.Img] = op
	return op, true
}

// coverTone é a cor média da capa (ou o tom das iniciais), base do player Vidro.
func (u *UI) coverTone(t *core.Track) color.NRGBA {
	if t == nil {
		return u.pal.Surface
	}
	cv := u.covers.Snapshot(u.covers.Get(t))
	if cv.Ready && cv.Img != nil {
		return cv.Tone
	}
	return coverTones[int(t.ID%uint64(len(coverTones)))]
}

// progressBar é a barra de posição: um trilho com a parte tocada e uma bolinha ao passar o mouse.
// Devolve true quando o usuário arrastou/clicou (o valor fica em f.Value).
func (u *UI) progressBar(gtx C, f *widget.Float, value float32, height unit.Dp, track, fill color.NRGBA) (D, bool) {
	changed := f.Update(gtx)
	if !f.Dragging() && !changed {
		f.Value = value
	}
	h := gtx.Dp(height)
	area := gtx.Dp(18)
	size := image.Pt(gtx.Constraints.Max.X, area)
	gtx.Constraints = layout.Exact(size)

	y := (area - h) / 2
	r := h / 2
	paint.FillShape(gtx.Ops, track, clip.UniformRRect(image.Rect(0, y, size.X, y+h), r).Op(gtx.Ops))
	x := int(f.Value * float32(size.X))
	if x > 0 {
		paint.FillShape(gtx.Ops, fill, clip.UniformRRect(image.Rect(0, y, x, y+h), r).Op(gtx.Ops))
	}
	if f.Dragging() {
		kr := gtx.Dp(7)
		paint.FillShape(gtx.Ops, fill, clip.Ellipse(image.Rect(x-kr, area/2-kr, x+kr, area/2+kr)).Op(gtx.Ops))
	}
	pointer.CursorPointer.Add(gtx.Ops)
	f.Layout(gtx, layout.Horizontal, 0)
	return D{Size: size}, changed
}

// opacity aplica transparência ao que for desenhado em w.
func opacity(gtx C, alpha float32, w layout.Widget) D {
	defer paint.PushOpacity(gtx.Ops, alpha).Pop()
	return w(gtx)
}

// offset desenha w deslocado.
func offset(gtx C, x, y int, w layout.Widget) D {
	defer op.Offset(image.Pt(x, y)).Push(gtx.Ops).Pop()
	return w(gtx)
}

// minHeight desenha w centralizado na vertical numa altura mínima de h (sem esticar os filhos:
// num Flex, a altura mínima passada adiante deixaria os textos colados no topo).
func minHeight(gtx C, h unit.Dp, w layout.Widget) D {
	min := gtx.Dp(h)
	gtx.Constraints.Min.Y = 0
	m := op.Record(gtx.Ops)
	d := w(gtx)
	call := m.Stop()
	if d.Size.Y >= min {
		call.Add(gtx.Ops)
		return d
	}
	st := op.Offset(image.Pt(0, (min-d.Size.Y)/2)).Push(gtx.Ops)
	call.Add(gtx.Ops)
	st.Pop()
	d.Size.Y = min
	return d
}
