// Package core reúne o que o Sonivell GO sabe sobre as músicas: faixas, biblioteca,
// capas, pastas do sistema e ajustes salvos.
package core

import (
	"fmt"
	"path/filepath"
	"strings"
	"unicode"

	"golang.org/x/text/unicode/norm"
)

// Track é uma faixa da biblioteca, lida das etiquetas do arquivo.
type Track struct {
	ID          uint64
	Path        string
	Title       string
	Artist      string
	AlbumArtist string
	Album       string
	Genre       string
	Year        int
	TrackNo     int
	DurationMs  int64
	Format      string // "FLAC", "MP3", "OPUS"…
	BitrateKbps int
	AddedMs     int64 // data de modificação do arquivo
}

// Folder é a pasta do arquivo.
func (t *Track) Folder() string { return filepath.Dir(t.Path) }

// AlbumKey agrupa as faixas de um mesmo álbum (o mesmo nome em pastas diferentes não se mistura).
func (t *Track) AlbumKey() string {
	artist := t.AlbumArtist
	if artist == "" {
		artist = t.Artist
	}
	return Fold(t.Album) + "\x00" + Fold(artist)
}

// DisplayArtist é o artista para mostrar (o da faixa ou, sem ele, o do álbum).
func (t *Track) DisplayArtist() string {
	if t.Artist != "" {
		return t.Artist
	}
	return t.AlbumArtist
}

// TrackID é o mesmo hash FNV-1a de 63 bits do app Kotlin, para os IDs baterem entre as versões.
func TrackID(path string) uint64 {
	h := uint64(0xcbf29ce484222325)
	for _, b := range []byte(path) {
		h ^= uint64(b)
		h *= 0x100000001b3
	}
	return h & 0x7fffffffffffffff
}

// Fold deixa o texto em minúsculas e sem acentos, para buscar "musica" e achar "Música".
func Fold(s string) string {
	var b strings.Builder
	for _, r := range norm.NFD.String(strings.ToLower(s)) {
		if unicode.Is(unicode.Mn, r) {
			continue
		}
		b.WriteRune(r)
	}
	return b.String()
}

// SortKey ignora pontuação inicial e acentos na ordenação ("¡Hola" fica no H).
func SortKey(s string) string {
	t := strings.TrimLeftFunc(s, func(r rune) bool { return !unicode.IsLetter(r) && !unicode.IsDigit(r) })
	if t == "" {
		t = s
	}
	return Fold(t)
}

// FormatTime escreve 3:07 ou 1:02:03.
func FormatTime(ms int64) string {
	total := ms / 1000
	if total < 0 {
		total = 0
	}
	h, m, s := total/3600, (total%3600)/60, total%60
	if h > 0 {
		return fmt.Sprintf("%d:%02d:%02d", h, m, s)
	}
	return fmt.Sprintf("%d:%02d", m, s)
}

// Initials são as iniciais mostradas na capa quando a faixa não tem arte.
func Initials(s string) string {
	var out []rune
	for _, w := range strings.Fields(s) {
		for _, r := range w {
			if unicode.IsLetter(r) || unicode.IsDigit(r) {
				out = append(out, unicode.ToUpper(r))
				break
			}
		}
		if len(out) == 2 {
			break
		}
	}
	if len(out) == 0 {
		return "♪"
	}
	return string(out)
}
