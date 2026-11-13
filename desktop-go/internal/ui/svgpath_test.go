package ui

import (
	"image"
	"image/color"
	"image/draw"
	"image/png"
	"math"
	"os"
	"path/filepath"
	"testing"

	"golang.org/x/image/vector"

	"github.com/Sonivell/sonivell/desktop-go/internal/i18n"
)

var allIcons = map[string]*Icon{
	"arrow-left": IcArrowLeft, "caret-down": IcCaretDown, "caret-right": IcCaretRight, "check": IcCheck,
	"folder-open": IcFolderOpen, "folder-plus": IcFolderPlus, "gear-six": IcGearSix, "heart": IcHeart,
	"magnifying-glass": IcMagnifyingGlass, "pause": IcPause, "picture-in-picture": IcPictureInPicture,
	"play": IcPlay, "queue": IcQueue, "repeat": IcRepeat, "repeat-once": IcRepeatOnce, "shuffle": IcShuffle,
	"skip-back": IcSkipBack, "skip-forward": IcSkipForward, "speaker-high": IcSpeakerHigh,
	"speaker-low": IcSpeakerLow, "speaker-x": IcSpeakerX, "trash": IcTrash, "vinyl-record": IcVinylRecord,
	"x": IcX, "arrows-out-simple": IcArrowsOutSimple,
}

// Todos os ícones têm que ser lidos inteiros, dentro da caixa de 256 e sem NaN.
func TestIconsParse(t *testing.T) {
	for name, ic := range allIcons {
		for _, fill := range []bool{false, true} {
			segs := ic.parsed(fill)
			if len(segs) < 3 {
				t.Errorf("%s (fill=%v): só %d segmentos", name, fill, len(segs))
			}
			for _, s := range segs {
				for _, p := range s.pts[:count(s.kind)] {
					if math.IsNaN(float64(p.X)) || math.IsNaN(float64(p.Y)) || p.X < -2 || p.X > 258 || p.Y < -2 || p.Y > 258 {
						t.Errorf("%s (fill=%v): ponto fora da caixa %v", name, fill, p)
					}
				}
			}
		}
	}
}

func count(kind byte) int {
	switch kind {
	case 'C':
		return 3
	case 'Z':
		return 0
	}
	return 1
}

// O arco do círculo do Phosphor (a8,8,0,0,1…) tem que fechar onde o SVG manda.
func TestArcEndsAtTarget(t *testing.T) {
	segs := parseSVGPath("M10,10a8,8,0,0,1,16,0")
	last := segs[len(segs)-1]
	if last.kind != 'C' || last.pts[2].X != 26 || last.pts[2].Y != 10 {
		t.Fatalf("arco terminou em %v", last.pts[2])
	}
	// meio círculo: o ponto mais alto fica a 8 acima do centro (18,10)
	minY := float32(100)
	for _, s := range segs[1:] {
		for _, p := range s.pts {
			if p.Y < minY {
				minY = p.Y
			}
		}
	}
	if minY > 2.5 || minY < -1.5 {
		t.Fatalf("arco com raio errado (y mínimo dos controles = %v)", minY)
	}
}

// Rasteriza os ícones numa folha PNG (SONIVELL_ICON_SHEET=pasta) para conferir a olho.
func TestIconSheet(t *testing.T) {
	dir := os.Getenv("SONIVELL_ICON_SHEET")
	if dir == "" {
		t.Skip("defina SONIVELL_ICON_SHEET para gerar a folha")
	}
	const cell = 64
	names := make([]string, 0, len(allIcons))
	for n := range allIcons {
		names = append(names, n)
	}
	sheet := image.NewRGBA(image.Rect(0, 0, cell*len(names), cell*2))
	draw.Draw(sheet, sheet.Bounds(), &image.Uniform{color.RGBA{14, 15, 17, 255}}, image.Point{}, draw.Src)
	for i, n := range names {
		for row, fill := range []bool{false, true} {
			r := vector.NewRasterizer(cell, cell)
			s := float32(cell-12) / 256
			for _, sg := range allIcons[n].parsed(fill) {
				pt := func(k int) (float32, float32) { return 6 + sg.pts[k].X*s, 6 + sg.pts[k].Y*s }
				switch sg.kind {
				case 'M':
					r.MoveTo(pt(0))
				case 'L':
					r.LineTo(pt(0))
				case 'C':
					ax, ay := pt(0)
					bx, by := pt(1)
					cx, cy := pt(2)
					r.CubeTo(ax, ay, bx, by, cx, cy)
				case 'Z':
					r.ClosePath()
				}
			}
			dst := sheet.SubImage(image.Rect(i*cell, row*cell, (i+1)*cell, (row+1)*cell)).(*image.RGBA)
			r.Draw(dst, dst.Bounds(), &image.Uniform{color.RGBA{200, 240, 74, 255}}, image.Point{})
		}
	}
	f, err := os.Create(filepath.Join(dir, "icones.png"))
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	if err := png.Encode(f, sheet); err != nil {
		t.Fatal(err)
	}
}

// Todo texto existe nos dois idiomas.
func TestStringsComplete(t *testing.T) {
	pt, en := i18n.Keys()
	for k := range pt {
		if _, ok := en[k]; !ok {
			t.Errorf("falta %q em inglês", k)
		}
	}
	for k := range en {
		if _, ok := pt[k]; !ok {
			t.Errorf("falta %q em português", k)
		}
	}
}
