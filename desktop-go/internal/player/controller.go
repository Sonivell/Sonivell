// Package player é o controle da reprodução: fila, aleatório, repetir, favoritas e volume.
package player

import (
	"math/rand"
	"sync"

	"github.com/Sonivell/sonivell/desktop-go/internal/core"
)

// Output é o que toca o som (o audio.Engine; nos testes, um falso).
type Output interface {
	Load(path string, fromMs int64, play bool)
	SetPlaying(play bool)
	PositionMs() int64
	SetVolume(v float64)
	Stop()
}

// Repeat é o modo de repetição.
type Repeat int

const (
	RepeatOff Repeat = iota
	RepeatAll
	RepeatOne
)

// State é uma foto do player para a interface e o MPRIS.
type State struct {
	Current *core.Track
	Playing bool
	Shuffle bool
	Repeat  Repeat
	Volume  float64
	Queue   []*core.Track // na ordem em que vão tocar, a partir da atual
	Index   int
}

// Controller cuida da fila e manda o Output tocar.
type Controller struct {
	mu       sync.Mutex
	out      Output
	store    *core.Store
	queue    []*core.Track // ordem original
	order    []int         // ordem de tocar (embaralhada ou não)
	pos      int           // posição em order
	playing  bool
	shuffle  bool
	repeat   Repeat
	volume   float64
	favs     map[uint64]bool
	onChange []func()
}

// New cria o controle com os ajustes salvos.
func New(out Output, store *core.Store) *Controller {
	s := store.Get()
	c := &Controller{out: out, store: store, shuffle: s.Shuffle, repeat: Repeat(s.Repeat), volume: s.Volume, favs: map[uint64]bool{}}
	for _, id := range s.Favorites {
		c.favs[id] = true
	}
	out.SetVolume(c.volume)
	return c
}

// OnChange registra quem quer saber das mudanças (a janela, o MPRIS).
func (c *Controller) OnChange(f func()) {
	c.mu.Lock()
	c.onChange = append(c.onChange, f)
	c.mu.Unlock()
}

func (c *Controller) notify() {
	c.mu.Lock()
	fs := append([]func(){}, c.onChange...)
	c.mu.Unlock()
	for _, f := range fs {
		f()
	}
}

// State devolve o estado atual.
func (c *Controller) State() State {
	c.mu.Lock()
	defer c.mu.Unlock()
	st := State{Playing: c.playing, Shuffle: c.shuffle, Repeat: c.repeat, Volume: c.volume}
	if cur := c.currentLocked(); cur != nil {
		st.Current = cur
		for i := c.pos; i < len(c.order); i++ {
			st.Queue = append(st.Queue, c.queue[c.order[i]])
		}
	}
	return st
}

// PositionMs é a posição da faixa atual.
func (c *Controller) PositionMs() int64 { return c.out.PositionMs() }

func (c *Controller) currentLocked() *core.Track {
	if c.pos < 0 || c.pos >= len(c.order) {
		return nil
	}
	return c.queue[c.order[c.pos]]
}

func (c *Controller) buildOrder(start int) {
	c.order = make([]int, len(c.queue))
	for i := range c.order {
		c.order[i] = i
	}
	c.pos = start
	if c.shuffle && len(c.order) > 1 {
		// A faixa escolhida toca primeiro; o resto vem embaralhado.
		c.order[0], c.order[start] = c.order[start], c.order[0]
		rest := c.order[1:]
		rand.Shuffle(len(rest), func(i, j int) { rest[i], rest[j] = rest[j], rest[i] })
		c.pos = 0
	}
}

// PlayList toca a lista a partir de start.
func (c *Controller) PlayList(list []*core.Track, start int) {
	if len(list) == 0 {
		return
	}
	if start < 0 || start >= len(list) {
		start = 0
	}
	c.mu.Lock()
	c.queue = append([]*core.Track(nil), list...)
	c.buildOrder(start)
	c.playing = true
	t := c.currentLocked()
	c.mu.Unlock()
	c.out.Load(t.Path, 0, true)
	c.notify()
}

// PlayShuffled toca a lista toda embaralhada (o "Mix" da tela Para você).
func (c *Controller) PlayShuffled(list []*core.Track) {
	if len(list) == 0 {
		return
	}
	c.mu.Lock()
	c.shuffle = true
	c.mu.Unlock()
	c.store.Update(func(s *core.Settings) { s.Shuffle = true })
	c.PlayList(list, rand.Intn(len(list)))
}

// TogglePlay pausa ou toca.
func (c *Controller) TogglePlay() { c.SetPlaying(!c.State().Playing) }

// SetPlaying toca ou pausa a faixa atual.
func (c *Controller) SetPlaying(play bool) {
	c.mu.Lock()
	t := c.currentLocked()
	if t == nil {
		c.mu.Unlock()
		return
	}
	c.playing = play
	c.mu.Unlock()
	c.out.SetPlaying(play)
	c.notify()
}

// Next pula para a próxima.
func (c *Controller) Next() { c.advance(false) }

// TrackEnded é chamado pelo Output quando a faixa termina sozinha.
func (c *Controller) TrackEnded() { c.advance(true) }

func (c *Controller) advance(natural bool) {
	c.mu.Lock()
	if len(c.order) == 0 {
		c.mu.Unlock()
		return
	}
	if natural && c.repeat == RepeatOne {
		t := c.currentLocked()
		c.mu.Unlock()
		c.out.Load(t.Path, 0, true)
		c.notify()
		return
	}
	next := c.pos + 1
	if next >= len(c.order) {
		if c.repeat == RepeatOff && natural {
			c.playing = false
			c.pos = 0
			t := c.currentLocked()
			c.mu.Unlock()
			c.out.Load(t.Path, 0, false)
			c.notify()
			return
		}
		next = 0
	}
	c.pos = next
	c.playing = true
	t := c.currentLocked()
	c.mu.Unlock()
	c.out.Load(t.Path, 0, true)
	c.notify()
}

// Previous volta para o começo da faixa (depois de 3 s) ou para a anterior.
func (c *Controller) Previous() {
	if c.out.PositionMs() > 3000 {
		c.SeekTo(0)
		return
	}
	c.mu.Lock()
	if len(c.order) == 0 {
		c.mu.Unlock()
		return
	}
	c.pos--
	if c.pos < 0 {
		c.pos = len(c.order) - 1
	}
	c.playing = true
	t := c.currentLocked()
	c.mu.Unlock()
	c.out.Load(t.Path, 0, true)
	c.notify()
}

// JumpTo toca o item i da fila mostrada (0 = a atual).
func (c *Controller) JumpTo(i int) {
	c.mu.Lock()
	if c.pos+i >= len(c.order) || i < 0 {
		c.mu.Unlock()
		return
	}
	c.pos += i
	c.playing = true
	t := c.currentLocked()
	c.mu.Unlock()
	c.out.Load(t.Path, 0, true)
	c.notify()
}

// SeekTo leva a faixa atual para ms.
func (c *Controller) SeekTo(ms int64) {
	c.mu.Lock()
	t := c.currentLocked()
	playing := c.playing
	c.mu.Unlock()
	if t == nil {
		return
	}
	if ms < 0 {
		ms = 0
	}
	if t.DurationMs > 0 && ms > t.DurationMs-500 {
		ms = t.DurationMs - 500
	}
	c.out.Load(t.Path, ms, playing)
	c.notify()
}

// SetVolume muda o volume (0..1) e salva.
func (c *Controller) SetVolume(v float64) {
	if v < 0 {
		v = 0
	}
	if v > 1 {
		v = 1
	}
	c.mu.Lock()
	c.volume = v
	c.mu.Unlock()
	c.out.SetVolume(v)
	c.store.Update(func(s *core.Settings) { s.Volume = v })
	c.notify()
}

// ToggleShuffle liga ou desliga o aleatório (refazendo a ordem a partir da faixa atual).
func (c *Controller) ToggleShuffle() {
	c.mu.Lock()
	c.shuffle = !c.shuffle
	if len(c.order) > 0 {
		cur := c.order[c.pos]
		c.buildOrder(cur)
		if !c.shuffle {
			c.pos = cur
		}
	}
	on := c.shuffle
	c.mu.Unlock()
	c.store.Update(func(s *core.Settings) { s.Shuffle = on })
	c.notify()
}

// CycleRepeat passa por desligado → todas → uma.
func (c *Controller) CycleRepeat() {
	c.mu.Lock()
	c.repeat = (c.repeat + 1) % 3
	r := c.repeat
	c.mu.Unlock()
	c.store.Update(func(s *core.Settings) { s.Repeat = int(r) })
	c.notify()
}

// IsFavorite diz se a faixa está nas favoritas.
func (c *Controller) IsFavorite(id uint64) bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.favs[id]
}

// ToggleFavorite marca ou desmarca a faixa.
func (c *Controller) ToggleFavorite(id uint64) {
	c.mu.Lock()
	if c.favs[id] {
		delete(c.favs, id)
	} else {
		c.favs[id] = true
	}
	ids := make([]uint64, 0, len(c.favs))
	for k := range c.favs {
		ids = append(ids, k)
	}
	c.mu.Unlock()
	c.store.Update(func(s *core.Settings) { s.Favorites = ids })
	c.notify()
}

// Stop para tudo (o "Parar" do MPRIS).
func (c *Controller) Stop() {
	c.mu.Lock()
	c.playing = false
	c.mu.Unlock()
	c.out.SetPlaying(false)
	c.SeekTo(0)
}
