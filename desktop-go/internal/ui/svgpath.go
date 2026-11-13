package ui

import (
	"math"
	"strconv"
	"sync"

	"gioui.org/f32"
	"gioui.org/op"
	"gioui.org/op/clip"
)

// Um segmento já em coordenadas absolutas: M, L, C ou Z (arcos e quadráticas viram cúbicas).
type seg struct {
	kind byte
	pts  [3]f32.Point
}

// Icon é um ícone do Phosphor nos pesos Regular (traço) e Fill (preenchido, item ativo).
type Icon struct {
	Regular, Fill string

	once        sync.Once
	reg, filled []seg
}

func (ic *Icon) parsed(fill bool) []seg {
	ic.once.Do(func() {
		ic.reg = parseSVGPath(ic.Regular)
		ic.filled = parseSVGPath(ic.Fill)
	})
	if fill {
		return ic.filled
	}
	return ic.reg
}

// Path monta o ícone como contorno de size×size px a partir de (0,0).
func (ic *Icon) Path(ops *op.Ops, size float32, fill bool) clip.PathSpec {
	return buildPath(ops, ic.parsed(fill), size/256)
}

func buildPath(ops *op.Ops, segs []seg, s float32) clip.PathSpec {
	var p clip.Path
	p.Begin(ops)
	sc := func(pt f32.Point) f32.Point { return f32.Pt(pt.X*s, pt.Y*s) }
	for _, sg := range segs {
		switch sg.kind {
		case 'M':
			p.MoveTo(sc(sg.pts[0]))
		case 'L':
			p.LineTo(sc(sg.pts[0]))
		case 'C':
			p.CubeTo(sc(sg.pts[0]), sc(sg.pts[1]), sc(sg.pts[2]))
		case 'Z':
			p.Close()
		}
	}
	return p.End()
}

// --- leitor de caminhos SVG (o subconjunto que o Phosphor usa, mais S/Q/T) ---

type pathLexer struct {
	s string
	i int
}

func (l *pathLexer) skip() {
	for l.i < len(l.s) {
		c := l.s[l.i]
		if c == ' ' || c == ',' || c == '\n' || c == '\t' || c == '\r' {
			l.i++
			continue
		}
		break
	}
}

func (l *pathLexer) hasNumber() bool {
	l.skip()
	if l.i >= len(l.s) {
		return false
	}
	c := l.s[l.i]
	return c == '-' || c == '+' || c == '.' || (c >= '0' && c <= '9')
}

func (l *pathLexer) number() float32 {
	l.skip()
	start := l.i
	if l.i < len(l.s) && (l.s[l.i] == '-' || l.s[l.i] == '+') {
		l.i++
	}
	dot, exp := false, false
	for l.i < len(l.s) {
		c := l.s[l.i]
		switch {
		case c >= '0' && c <= '9':
		case c == '.' && !dot && !exp:
			dot = true
		case (c == 'e' || c == 'E') && !exp:
			exp = true
			if l.i+1 < len(l.s) && (l.s[l.i+1] == '-' || l.s[l.i+1] == '+') {
				l.i++
			}
		default:
			goto done
		}
		l.i++
	}
done:
	v, _ := strconv.ParseFloat(l.s[start:l.i], 32)
	return float32(v)
}

// flag lê uma bandeira de arco, que pode vir colada ("a8,8,0,01-8,8").
func (l *pathLexer) flag() bool {
	l.skip()
	if l.i < len(l.s) {
		c := l.s[l.i]
		l.i++
		return c == '1'
	}
	return false
}

func parseSVGPath(d string) []seg {
	l := &pathLexer{s: d}
	var out []seg
	var cur, start, lastCtrl f32.Point
	var cmd, prev byte
	for {
		l.skip()
		if l.i >= len(l.s) {
			break
		}
		c := l.s[l.i]
		if (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') {
			cmd = c
			l.i++
		} else if cmd == 0 {
			break
		}
		rel := cmd >= 'a'
		abs := func(p f32.Point) f32.Point {
			if rel {
				return p.Add(cur)
			}
			return p
		}
		switch cmd | 0x20 { // minúscula
		case 'm':
			p := abs(f32.Pt(l.number(), l.number()))
			out = append(out, seg{kind: 'M', pts: [3]f32.Point{p}})
			cur, start = p, p
			// pares seguintes de um M são L
			if rel {
				cmd = 'l'
			} else {
				cmd = 'L'
			}
		case 'l':
			p := abs(f32.Pt(l.number(), l.number()))
			out = append(out, seg{kind: 'L', pts: [3]f32.Point{p}})
			cur = p
		case 'h':
			x := l.number()
			if rel {
				x += cur.X
			}
			cur = f32.Pt(x, cur.Y)
			out = append(out, seg{kind: 'L', pts: [3]f32.Point{cur}})
		case 'v':
			y := l.number()
			if rel {
				y += cur.Y
			}
			cur = f32.Pt(cur.X, y)
			out = append(out, seg{kind: 'L', pts: [3]f32.Point{cur}})
		case 'c':
			c1 := abs(f32.Pt(l.number(), l.number()))
			c2 := abs(f32.Pt(l.number(), l.number()))
			p := abs(f32.Pt(l.number(), l.number()))
			out = append(out, seg{kind: 'C', pts: [3]f32.Point{c1, c2, p}})
			lastCtrl, cur = c2, p
		case 's':
			c1 := cur
			if p := prev | 0x20; p == 'c' || p == 's' {
				c1 = cur.Mul(2).Sub(lastCtrl)
			}
			c2 := abs(f32.Pt(l.number(), l.number()))
			p := abs(f32.Pt(l.number(), l.number()))
			out = append(out, seg{kind: 'C', pts: [3]f32.Point{c1, c2, p}})
			lastCtrl, cur = c2, p
		case 'q':
			q := abs(f32.Pt(l.number(), l.number()))
			p := abs(f32.Pt(l.number(), l.number()))
			out = append(out, quadToCubic(cur, q, p))
			lastCtrl, cur = q, p
		case 't':
			q := cur
			if pp := prev | 0x20; pp == 'q' || pp == 't' {
				q = cur.Mul(2).Sub(lastCtrl)
			}
			p := abs(f32.Pt(l.number(), l.number()))
			out = append(out, quadToCubic(cur, q, p))
			lastCtrl, cur = q, p
		case 'a':
			rx, ry := l.number(), l.number()
			rot := l.number()
			large, sweep := l.flag(), l.flag()
			p := abs(f32.Pt(l.number(), l.number()))
			out = append(out, arcToCubics(cur, p, rx, ry, rot, large, sweep)...)
			cur = p
		case 'z':
			out = append(out, seg{kind: 'Z'})
			cur = start
		default:
			return out
		}
		prev = cmd
		if cmd|0x20 == 'z' {
			continue
		}
		if !l.hasNumber() {
			continue
		}
		// mais números sem letra: repete o comando (já tratado no topo do laço)
	}
	return out
}

func quadToCubic(p0, q, p f32.Point) seg {
	c1 := p0.Add(q.Sub(p0).Mul(2.0 / 3))
	c2 := p.Add(q.Sub(p).Mul(2.0 / 3))
	return seg{kind: 'C', pts: [3]f32.Point{c1, c2, p}}
}

// arcToCubics converte um arco elíptico SVG (forma de pontas) em cúbicas de até 90°.
func arcToCubics(p0, p1 f32.Point, rx, ry, rotDeg float32, large, sweep bool) []seg {
	if p0 == p1 {
		return nil
	}
	if rx == 0 || ry == 0 {
		return []seg{{kind: 'L', pts: [3]f32.Point{p1}}}
	}
	x1, y1 := float64(p0.X), float64(p0.Y)
	x2, y2 := float64(p1.X), float64(p1.Y)
	rX, rY := math.Abs(float64(rx)), math.Abs(float64(ry))
	phi := float64(rotDeg) * math.Pi / 180
	cosP, sinP := math.Cos(phi), math.Sin(phi)

	dx, dy := (x1-x2)/2, (y1-y2)/2
	x1p := cosP*dx + sinP*dy
	y1p := -sinP*dx + cosP*dy

	lambda := x1p*x1p/(rX*rX) + y1p*y1p/(rY*rY)
	if lambda > 1 {
		s := math.Sqrt(lambda)
		rX *= s
		rY *= s
	}
	num := rX*rX*rY*rY - rX*rX*y1p*y1p - rY*rY*x1p*x1p
	den := rX*rX*y1p*y1p + rY*rY*x1p*x1p
	coef := 0.0
	if den != 0 && num > 0 {
		coef = math.Sqrt(num / den)
	}
	if large == sweep {
		coef = -coef
	}
	cxp := coef * rX * y1p / rY
	cyp := -coef * rY * x1p / rX
	cx := cosP*cxp - sinP*cyp + (x1+x2)/2
	cy := sinP*cxp + cosP*cyp + (y1+y2)/2

	angle := func(ux, uy, vx, vy float64) float64 {
		a := math.Atan2(ux*vy-uy*vx, ux*vx+uy*vy)
		return a
	}
	th1 := angle(1, 0, (x1p-cxp)/rX, (y1p-cyp)/rY)
	dth := angle((x1p-cxp)/rX, (y1p-cyp)/rY, (-x1p-cxp)/rX, (-y1p-cyp)/rY)
	if !sweep && dth > 0 {
		dth -= 2 * math.Pi
	} else if sweep && dth < 0 {
		dth += 2 * math.Pi
	}

	n := int(math.Ceil(math.Abs(dth) / (math.Pi / 2)))
	if n < 1 {
		n = 1
	}
	step := dth / float64(n)
	k := 4.0 / 3.0 * math.Tan(step/4)
	pt := func(t float64) (float64, float64) {
		x := rX * math.Cos(t)
		y := rY * math.Sin(t)
		return cosP*x - sinP*y + cx, sinP*x + cosP*y + cy
	}
	der := func(t float64) (float64, float64) {
		x := -rX * math.Sin(t)
		y := rY * math.Cos(t)
		return cosP*x - sinP*y, sinP*x + cosP*y
	}
	var out []seg
	t := th1
	for i := 0; i < n; i++ {
		ax, ay := pt(t)
		bx, by := pt(t + step)
		dax, day := der(t)
		dbx, dby := der(t + step)
		c1 := f32.Pt(float32(ax+k*dax), float32(ay+k*day))
		c2 := f32.Pt(float32(bx-k*dbx), float32(by-k*dby))
		end := f32.Pt(float32(bx), float32(by))
		if i == n-1 {
			end = p1
		}
		out = append(out, seg{kind: 'C', pts: [3]f32.Point{c1, c2, end}})
		t += step
	}
	return out
}
