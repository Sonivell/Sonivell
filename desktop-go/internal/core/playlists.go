package core

import (
	"bufio"
	"io/fs"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
)

// Playlist é uma lista .m3u/.m3u8 encontrada nas pastas de música.
type Playlist struct {
	Name   string
	Path   string
	Tracks []*Track
}

// FindPlaylists procura arquivos .m3u e .m3u8 nas pastas e liga cada linha à faixa da biblioteca.
// Caminhos relativos são lidos a partir da pasta da playlist; linhas sem faixa conhecida são puladas.
func FindPlaylists(roots []string, lib *Library) []Playlist {
	byPath := make(map[string]*Track, len(lib.Tracks))
	for _, t := range lib.Tracks {
		byPath[filepath.Clean(t.Path)] = t
	}
	var out []Playlist
	seen := map[string]bool{}
	for _, root := range roots {
		_ = filepath.WalkDir(root, func(p string, d fs.DirEntry, err error) error {
			if err != nil {
				return nil
			}
			if d.IsDir() {
				if strings.HasPrefix(d.Name(), ".") && p != root {
					return filepath.SkipDir
				}
				return nil
			}
			ext := strings.ToLower(filepath.Ext(p))
			if (ext != ".m3u" && ext != ".m3u8") || seen[p] {
				return nil
			}
			seen[p] = true
			if pl, ok := readM3U(p, byPath); ok {
				out = append(out, pl)
			}
			return nil
		})
	}
	sort.Slice(out, func(i, j int) bool { return SortKey(out[i].Name) < SortKey(out[j].Name) })
	return out
}

func readM3U(path string, byPath map[string]*Track) (Playlist, bool) {
	f, err := os.Open(path)
	if err != nil {
		return Playlist{}, false
	}
	defer f.Close()
	pl := Playlist{Name: strings.TrimSuffix(filepath.Base(path), filepath.Ext(path)), Path: path}
	dir := filepath.Dir(path)
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		line := strings.TrimSpace(strings.TrimPrefix(sc.Text(), "\uFEFF"))
		if line == "" {
			continue
		}
		if strings.HasPrefix(line, "#PLAYLIST:") {
			pl.Name = strings.TrimSpace(strings.TrimPrefix(line, "#PLAYLIST:"))
			continue
		}
		if strings.HasPrefix(line, "#") {
			continue
		}
		line = strings.TrimPrefix(line, "file://")
		line = strings.ReplaceAll(line, "\\", "/")
		if !filepath.IsAbs(line) {
			line = filepath.Join(dir, line)
		}
		if t := byPath[filepath.Clean(line)]; t != nil {
			pl.Tracks = append(pl.Tracks, t)
		}
	}
	return pl, len(pl.Tracks) > 0
}

// Decades agrupa as faixas por década ("1990s"), da mais antiga para a mais nova.
func (l *Library) Decades() []Group {
	m := map[int][]*Track{}
	for _, t := range l.Tracks {
		if t.Year >= 1900 {
			d := t.Year / 10 * 10
			m[d] = append(m[d], t)
		}
	}
	keys := make([]int, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Ints(keys)
	out := make([]Group, 0, len(keys))
	for _, k := range keys {
		out = append(out, Group{Name: strconv.Itoa(k) + "s", Tracks: m[k]})
	}
	return out
}
