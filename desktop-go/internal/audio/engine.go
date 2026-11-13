// Package audio toca as faixas: o ffmpeg decodifica qualquer formato para PCM e o oto
// entrega ao sistema de som (ALSA, que no Debian/Ubuntu atuais passa pelo PipeWire).
package audio

import (
	"errors"
	"fmt"
	"io"
	"os/exec"
	"sync"
	"sync/atomic"
	"time"

	"github.com/ebitengine/oto/v3"
)

const (
	sampleRate  = 48000
	channels    = 2
	bytesPerSec = sampleRate * channels * 2 // s16le
)

// Engine toca uma faixa por vez.
type Engine struct {
	mu      sync.Mutex
	ctx     *oto.Context
	player  *oto.Player
	src     *pcmSource
	cmd     *exec.Cmd
	startMs int64
	volume  float64
	paused  bool
	gen     int

	// OnEnd é chamado (fora da trava) quando a faixa chega ao fim sozinha.
	OnEnd func()
	// OnError recebe falhas do ffmpeg ou do som.
	OnError func(error)
}

// New abre a saída de som.
func New() (*Engine, error) {
	ctx, ready, err := oto.NewContext(&oto.NewContextOptions{
		SampleRate:   sampleRate,
		ChannelCount: channels,
		Format:       oto.FormatSignedInt16LE,
		BufferSize:   80 * time.Millisecond,
	})
	if err != nil {
		return nil, fmt.Errorf("som: %w", err)
	}
	<-ready
	return &Engine{ctx: ctx, volume: 0.8}, nil
}

// pcmSource lê o PCM do ffmpeg contando os bytes entregues (para saber a posição).
type pcmSource struct {
	r     io.ReadCloser
	read  atomic.Int64
	ended atomic.Bool
}

func (p *pcmSource) Read(b []byte) (int, error) {
	n, err := p.r.Read(b)
	p.read.Add(int64(n))
	if err != nil {
		p.ended.Store(true)
		if errors.Is(err, io.ErrClosedPipe) {
			err = io.EOF
		}
	}
	return n, err
}

// Load começa a tocar path a partir de fromMs (ou deixa pronto, pausado, se play for falso).
func (e *Engine) Load(path string, fromMs int64, play bool) {
	e.mu.Lock()
	e.stopLocked()
	e.gen++
	gen := e.gen

	cmd := exec.Command("ffmpeg", "-nostdin", "-loglevel", "error",
		"-ss", fmt.Sprintf("%.3f", float64(fromMs)/1000), "-i", path,
		"-vn", "-f", "s16le", "-acodec", "pcm_s16le", "-ac", "2", "-ar", fmt.Sprint(sampleRate), "-")
	out, err := cmd.StdoutPipe()
	if err == nil {
		err = cmd.Start()
	}
	if err != nil {
		e.mu.Unlock()
		e.fail(fmt.Errorf("ffmpeg: %w", err))
		return
	}
	src := &pcmSource{r: out}
	pl := e.ctx.NewPlayer(src)
	pl.SetBufferSize(bytesPerSec / 5) // 200 ms: pausa e troca de faixa respondem na hora
	pl.SetVolume(curve(e.volume))
	e.cmd, e.src, e.player, e.startMs, e.paused = cmd, src, pl, fromMs, !play
	if play {
		pl.Play()
	}
	e.mu.Unlock()

	go func() { _ = cmd.Wait() }()
	go e.watch(gen)
}

// watch percebe o fim natural da faixa.
func (e *Engine) watch(gen int) {
	t := time.NewTicker(120 * time.Millisecond)
	defer t.Stop()
	for range t.C {
		e.mu.Lock()
		if gen != e.gen || e.player == nil {
			e.mu.Unlock()
			return
		}
		ended := e.src.ended.Load() && !e.player.IsPlaying() && !e.paused
		e.mu.Unlock()
		if ended {
			if cb := e.OnEnd; cb != nil {
				cb()
			}
			return
		}
	}
}

func (e *Engine) stopLocked() {
	if e.player != nil {
		e.player.Pause()
		_ = e.player.Close()
		e.player = nil
	}
	if e.cmd != nil && e.cmd.Process != nil {
		_ = e.cmd.Process.Kill()
	}
	e.cmd, e.src = nil, nil
}

// Stop para e solta a faixa atual.
func (e *Engine) Stop() {
	e.mu.Lock()
	e.stopLocked()
	e.gen++
	e.mu.Unlock()
}

// SetPlaying pausa ou retoma.
func (e *Engine) SetPlaying(play bool) {
	e.mu.Lock()
	defer e.mu.Unlock()
	if e.player == nil {
		return
	}
	e.paused = !play
	if play {
		e.player.Play()
	} else {
		e.player.Pause()
	}
}

// Loaded diz se há uma faixa carregada.
func (e *Engine) Loaded() bool {
	e.mu.Lock()
	defer e.mu.Unlock()
	return e.player != nil
}

// PositionMs é a posição do que já saiu nas caixas (descontando o que está no buffer).
func (e *Engine) PositionMs() int64 {
	e.mu.Lock()
	defer e.mu.Unlock()
	if e.player == nil {
		return e.startMs
	}
	played := e.src.read.Load() - int64(e.player.BufferedSize())
	if played < 0 {
		played = 0
	}
	return e.startMs + played*1000/bytesPerSec
}

// SetVolume ajusta o volume (0..1).
func (e *Engine) SetVolume(v float64) {
	e.mu.Lock()
	defer e.mu.Unlock()
	e.volume = v
	if e.player != nil {
		e.player.SetVolume(curve(v))
	}
}

// curve deixa o controle de volume mais natural ao ouvido.
func curve(v float64) float64 {
	if v < 0 {
		v = 0
	}
	if v > 1 {
		v = 1
	}
	return v * v
}

func (e *Engine) fail(err error) {
	if cb := e.OnError; cb != nil {
		cb(err)
	}
}
