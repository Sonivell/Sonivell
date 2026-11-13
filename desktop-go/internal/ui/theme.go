package ui

import (
	"embed"
	"image/color"

	"gioui.org/font"
	"gioui.org/font/gofont"
	"gioui.org/font/opentype"
	"gioui.org/text"
	"gioui.org/widget/material"
)

//go:embed fonts/*.ttf
var fontFiles embed.FS

// Palette é a paleta Ondas no fundo Grafite (a mesma do app Android e do desktop em Kotlin).
type Palette struct {
	Bg, Surface, SurfaceLow, SurfaceHigh, Outline, Track color.NRGBA
	Text, Text2, Text3                                   color.NRGBA
	Accent, OnAccent                                     color.NRGBA
}

func rgb(c uint32) color.NRGBA {
	return color.NRGBA{R: uint8(c >> 16), G: uint8(c >> 8), B: uint8(c), A: 0xff}
}

func withAlpha(c color.NRGBA, a uint8) color.NRGBA { c.A = a; return c }

// Accent é uma cor de destaque que o usuário pode escolher.
type Accent struct {
	Key   string
	Color color.NRGBA
}

// Accents são as cores do tema escuro do Sonivell.
var Accents = []Accent{
	{"Lima", rgb(0xC8F04A)},
	{"Azul", rgb(0x7CC4FF)},
	{"Laranja", rgb(0xFF9F5A)},
	{"Lilas", rgb(0xE8A6FF)},
	{"Rosa", rgb(0xFF8FB1)},
	{"Turquesa", rgb(0x5FE3D0)},
}

// PaletteFor monta a paleta Grafite com a cor de destaque escolhida.
func PaletteFor(accent string) Palette {
	a := Accents[0].Color
	for _, x := range Accents {
		if x.Key == accent {
			a = x.Color
		}
	}
	return Palette{
		Bg: rgb(0x0E0F11), Surface: rgb(0x1A1C20), SurfaceLow: rgb(0x14161A), SurfaceHigh: rgb(0x24272C),
		Outline: rgb(0x2E3137), Track: rgb(0x2A2D33),
		Text: rgb(0xF2F3F5), Text2: rgb(0x9BA1A9), Text3: rgb(0xC9CDD3),
		Accent: a, OnAccent: rgb(0x0E0F11),
	}
}

// Tons das capas sem arte (os mesmos do desktop em Kotlin).
var coverTones = []color.NRGBA{rgb(0xD4EF55), rgb(0x4FE0C8), rgb(0xFF8A6B), rgb(0xB79BFF), rgb(0xFFC24D), rgb(0x7CC4FF)}

const typeface = "Manrope"

// NewTheme carrega a Manrope (400–800) com a Go font como reserva para símbolos.
func NewTheme() *material.Theme {
	var coll []font.FontFace
	weights := map[string]font.Weight{"400": font.Normal, "500": font.Medium, "600": font.SemiBold, "700": font.Bold, "800": font.ExtraBold}
	for _, w := range []string{"400", "500", "600", "700", "800"} {
		data, err := fontFiles.ReadFile("fonts/manrope-" + w + ".ttf")
		if err != nil {
			continue
		}
		faces, err := opentype.ParseCollection(data)
		if err != nil {
			continue
		}
		for i := range faces {
			faces[i].Font.Typeface = typeface
			faces[i].Font.Weight = weights[w]
		}
		coll = append(coll, faces...)
	}
	coll = append(coll, gofont.Collection()...)
	th := material.NewTheme()
	th.Shaper = text.NewShaper(text.NoSystemFonts(), text.WithCollection(coll))
	th.Face = typeface
	return th
}
