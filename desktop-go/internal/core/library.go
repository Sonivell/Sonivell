package core

import (
	"sort"
	"strings"
)

// Album é um álbum montado a partir das faixas.
type Album struct {
	Key    string
	Title  string
	Artist string
	Year   int
	Tracks []*Track
}

// Group é um artista ou gênero com suas faixas.
type Group struct {
	Name   string
	Tracks []*Track
}

// Library é a biblioteca organizada: álbuns, artistas, gêneros e a busca.
type Library struct {
	Tracks  []*Track
	ByID    map[uint64]*Track
	Albums  []*Album
	Artists []Group
	Genres  []Group

	hay map[uint64]string
}

// NewLibrary organiza as faixas.
func NewLibrary(tracks []Track) *Library {
	l := &Library{ByID: map[uint64]*Track{}, hay: map[uint64]string{}}
	for i := range tracks {
		t := &tracks[i]
		l.Tracks = append(l.Tracks, t)
		l.ByID[t.ID] = t
		l.hay[t.ID] = Fold(t.Title + " " + t.Artist + " " + t.Album + " " + t.Genre)
	}
	sort.SliceStable(l.Tracks, func(i, j int) bool { return SortKey(l.Tracks[i].Title) < SortKey(l.Tracks[j].Title) })

	albums := map[string]*Album{}
	artists := map[string][]*Track{}
	genres := map[string][]*Track{}
	for _, t := range l.Tracks {
		if t.Album != "" {
			k := t.AlbumKey()
			a := albums[k]
			if a == nil {
				artist := t.AlbumArtist
				if artist == "" {
					artist = t.Artist
				}
				a = &Album{Key: k, Title: t.Album, Artist: artist}
				albums[k] = a
			}
			a.Tracks = append(a.Tracks, t)
			if t.Year > a.Year {
				a.Year = t.Year
			}
		}
		if t.Artist != "" {
			artists[t.Artist] = append(artists[t.Artist], t)
		}
		if t.Genre != "" {
			genres[t.Genre] = append(genres[t.Genre], t)
		}
	}
	for _, a := range albums {
		sort.SliceStable(a.Tracks, func(i, j int) bool {
			if a.Tracks[i].TrackNo != a.Tracks[j].TrackNo {
				return a.Tracks[i].TrackNo < a.Tracks[j].TrackNo
			}
			return SortKey(a.Tracks[i].Title) < SortKey(a.Tracks[j].Title)
		})
		l.Albums = append(l.Albums, a)
	}
	sort.Slice(l.Albums, func(i, j int) bool { return SortKey(l.Albums[i].Title) < SortKey(l.Albums[j].Title) })
	for n, ts := range artists {
		l.Artists = append(l.Artists, Group{n, ts})
	}
	sort.Slice(l.Artists, func(i, j int) bool { return SortKey(l.Artists[i].Name) < SortKey(l.Artists[j].Name) })
	for n, ts := range genres {
		l.Genres = append(l.Genres, Group{n, ts})
	}
	sort.Slice(l.Genres, func(i, j int) bool { return len(l.Genres[i].Tracks) > len(l.Genres[j].Tracks) })
	return l
}

// RecentlyAdded são as faixas da mais nova para a mais antiga.
func (l *Library) RecentlyAdded(n int) []*Track {
	out := append([]*Track(nil), l.Tracks...)
	sort.SliceStable(out, func(i, j int) bool { return out[i].AddedMs > out[j].AddedMs })
	if n > 0 && len(out) > n {
		out = out[:n]
	}
	return out
}

// RecentAlbums são os álbuns com faixas adicionadas mais recentemente.
func (l *Library) RecentAlbums(n int) []*Album {
	out := append([]*Album(nil), l.Albums...)
	newest := func(a *Album) int64 {
		var m int64
		for _, t := range a.Tracks {
			if t.AddedMs > m {
				m = t.AddedMs
			}
		}
		return m
	}
	sort.SliceStable(out, func(i, j int) bool { return newest(out[i]) > newest(out[j]) })
	if n > 0 && len(out) > n {
		out = out[:n]
	}
	return out
}

// Search acha faixas que tenham todas as palavras (sem diferenciar acentos).
func (l *Library) Search(query string) []*Track {
	tokens := strings.Fields(Fold(query))
	if len(tokens) == 0 {
		return nil
	}
	var out []*Track
	for _, t := range l.Tracks {
		h := l.hay[t.ID]
		ok := true
		for _, tok := range tokens {
			if !strings.Contains(h, tok) {
				ok = false
				break
			}
		}
		if ok {
			out = append(out, t)
		}
	}
	first := tokens[0]
	sort.SliceStable(out, func(i, j int) bool {
		a := strings.HasPrefix(Fold(out[i].Title), first)
		b := strings.HasPrefix(Fold(out[j].Title), first)
		return a && !b
	})
	return out
}
