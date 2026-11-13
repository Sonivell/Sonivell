package player

import (
	"testing"

	"github.com/Sonivell/sonivell/desktop-go/internal/core"
)

type fakeOut struct {
	loaded  []string
	playing bool
	pos     int64
	vol     float64
}

func (f *fakeOut) Load(path string, fromMs int64, play bool) {
	f.loaded = append(f.loaded, path)
	f.playing, f.pos = play, fromMs
}
func (f *fakeOut) SetPlaying(p bool)   { f.playing = p }
func (f *fakeOut) PositionMs() int64   { return f.pos }
func (f *fakeOut) SetVolume(v float64) { f.vol = v }
func (f *fakeOut) Stop()               {}
func (f *fakeOut) last() string        { return f.loaded[len(f.loaded)-1] }

func setup(t *testing.T) (*Controller, *fakeOut, []*core.Track) {
	t.Setenv("XDG_CONFIG_HOME", t.TempDir())
	out := &fakeOut{}
	c := New(out, core.LoadStore())
	var list []*core.Track
	for _, p := range []string{"a", "b", "c"} {
		list = append(list, &core.Track{ID: core.TrackID(p), Path: p, Title: p, DurationMs: 60_000})
	}
	return c, out, list
}

func TestPlayNextAndEnd(t *testing.T) {
	c, out, list := setup(t)
	c.PlayList(list, 1)
	if out.last() != "b" || !c.State().Playing {
		t.Fatalf("devia tocar b: %v", out.loaded)
	}
	c.Next()
	if out.last() != "c" {
		t.Fatalf("próxima devia ser c: %v", out.loaded)
	}
	// Fim natural da última sem repetir: para e volta ao começo, pausado.
	c.TrackEnded()
	if st := c.State(); st.Playing || st.Current.Path != "a" {
		t.Fatalf("fim da fila: %+v", st)
	}
}

func TestRepeatOneAndAll(t *testing.T) {
	c, out, list := setup(t)
	c.PlayList(list, 2)
	c.CycleRepeat() // todas
	c.TrackEnded()
	if out.last() != "a" {
		t.Fatalf("repetir todas devia voltar para a: %v", out.loaded)
	}
	c.CycleRepeat() // uma
	c.TrackEnded()
	if out.last() != "a" || !c.State().Playing {
		t.Fatalf("repetir uma devia repetir a: %v", out.loaded)
	}
}

func TestPreviousRestartsAfterThreeSeconds(t *testing.T) {
	c, out, list := setup(t)
	c.PlayList(list, 1)
	out.pos = 10_000
	c.Previous()
	if out.last() != "b" || out.pos != 0 {
		t.Fatalf("devia voltar ao início de b: %v pos=%d", out.loaded, out.pos)
	}
	c.Previous()
	if out.last() != "a" {
		t.Fatalf("devia ir para a: %v", out.loaded)
	}
}

func TestShuffleStartsWithChosen(t *testing.T) {
	c, out, list := setup(t)
	c.ToggleShuffle()
	c.PlayList(list, 2)
	if out.last() != "c" {
		t.Fatalf("aleatório devia começar pela escolhida: %v", out.loaded)
	}
	seen := map[string]bool{"c": true}
	c.Next()
	seen[out.last()] = true
	c.Next()
	seen[out.last()] = true
	if len(seen) != 3 {
		t.Fatalf("aleatório repetiu faixa: %v", out.loaded)
	}
}

func TestFavoritesAndVolumePersist(t *testing.T) {
	c, out, list := setup(t)
	c.ToggleFavorite(list[0].ID)
	c.SetVolume(1.4)
	if !c.IsFavorite(list[0].ID) || out.vol != 1 {
		t.Fatalf("favorita/volume: %v %v", c.IsFavorite(list[0].ID), out.vol)
	}
	again := New(&fakeOut{}, core.LoadStore())
	if !again.IsFavorite(list[0].ID) || again.State().Volume != 1 {
		t.Fatal("favoritas/volume não foram salvos")
	}
}

func TestHistoryAndSource(t *testing.T) {
	c, _, list := setup(t)
	c.PlayFrom("Mix do dia", list, 0)
	c.Next()
	c.SeekTo(0) // buscar não conta
	st := c.State()
	if st.Source != "Mix do dia" {
		t.Fatalf("origem = %q", st.Source)
	}
	s := c.store.Get()
	if len(s.Recent) != 2 || s.Recent[0] != list[1].ID || s.Plays[list[1].ID] != 1 {
		t.Fatalf("histórico: %+v %+v", s.Recent, s.Plays)
	}
}
