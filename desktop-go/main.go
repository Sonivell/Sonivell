// Sonivell GO: o Sonivell para Linux reescrito em Go (interface Gio, som pelo ffmpeg).
package main

import (
	"flag"
	"fmt"
	"io"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"runtime/debug"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"gioui.org/app"
	"gioui.org/io/system"
	"gioui.org/op"
	"gioui.org/unit"

	"github.com/Sonivell/sonivell/desktop-go/internal/audio"
	"github.com/Sonivell/sonivell/desktop-go/internal/core"
	"github.com/Sonivell/sonivell/desktop-go/internal/mpris"
	"github.com/Sonivell/sonivell/desktop-go/internal/player"
	"github.com/Sonivell/sonivell/desktop-go/internal/ui"
)

// version vem do build (-ldflags "-X main.version=1.3.1").
var version = "dev"

func main() {
	showVersion := flag.Bool("version", false, "mostra a versão e sai")
	flag.Parse()
	if *showVersion {
		fmt.Println("Sonivell GO", version)
		return
	}
	ui.Version = version
	app.ID = "sonivell-go"
	setupLog()
	log.Printf("Sonivell GO %s iniciando (sessão %q, WAYLAND_DISPLAY=%q, DISPLAY=%q)",
		version, os.Getenv("XDG_SESSION_TYPE"), os.Getenv("WAYLAND_DISPLAY"), os.Getenv("DISPLAY"))
	go func() {
		defer func() {
			if r := recover(); r != nil {
				log.Printf("pânico: %v\n%s", r, debug.Stack())
				os.Exit(2)
			}
		}()
		if err := run(); err != nil {
			log.Println(err)
			notifyFailure(err)
			os.Exit(1)
		}
		os.Exit(0)
	}()
	app.Main()
}

// Tamanhos da janela (dp): normal e compacto.
const (
	fullW, fullH       = 1280, 820
	compactW, compactH = 380, 600
)

func run() error {
	store := core.LoadStore()

	w := new(app.Window)
	w.Option(app.Title("Sonivell GO"), app.Size(fullW, fullH), app.MinSize(360, 520))

	// O som abre em segundo plano: se o ALSA demorar (ou travar, com o dispositivo ocupado),
	// a janela abre do mesmo jeito e o som entra assim que ficar pronto.
	out := &lazyOutput{}
	ctl := player.New(out, store)
	go func() {
		done := make(chan struct{})
		go func() {
			select {
			case <-done:
			case <-time.After(4 * time.Second):
				log.Printf("som: o ALSA ainda não respondeu depois de 4 s (dispositivo ocupado?)")
			}
		}()
		eng, err := audio.New()
		close(done)
		if err != nil {
			log.Printf("sem saída de som: %v", err)
			return
		}
		eng.OnEnd = ctl.TrackEnded
		eng.OnError = func(err error) { log.Println(err) }
		out.set(eng)
		log.Printf("som pronto")
	}()

	covers := core.NewCovers(w.Invalidate)
	var scanning atomic.Bool
	var demoOnce sync.Once
	var view *ui.UI
	rescan := func() {
		if !scanning.CompareAndSwap(false, true) {
			return
		}
		go func() {
			defer scanning.Store(false)
			view.SetScanProgress(true, 0, 0)
			w.Invalidate()
			var last time.Time
			var mu sync.Mutex
			tracks := core.Scan(store.Get().MusicDirs, func(done, total int) {
				mu.Lock()
				defer mu.Unlock()
				if time.Since(last) > 150*time.Millisecond || done == total {
					last = time.Now()
					view.SetScanProgress(true, done, total)
					w.Invalidate()
				}
			})
			view.SetLibrary(core.NewLibrary(tracks))
			if demo := os.Getenv("SONIVELL_GO_DEMO"); demo != "" {
				demoOnce.Do(func() { view.Demo(demo) })
			}
			w.Invalidate()
		}()
	}

	view = ui.New(ctl, store, covers, ui.Hooks{
		Invalidate: w.Invalidate,
		Rescan:     rescan,
		PickFolder: pickFolder(),
		SetCompact: func(compact bool) {
			if compact {
				w.Perform(system.ActionUnmaximize)
				w.Option(app.Size(compactW, compactH))
			} else {
				w.Option(app.Size(fullW, fullH))
			}
		},
	})
	ctl.OnChange(w.Invalidate)

	if srv, err := mpris.Start(ctl, func() { w.Perform(system.ActionRaise) }); err != nil {
		log.Printf("MPRIS indisponível: %v", err)
	} else {
		defer srv.Close()
	}

	if core.ToolsAvailable() {
		rescan()
	} else {
		view.SetNoFFmpeg()
	}

	var ops op.Ops
	for {
		switch e := w.Event().(type) {
		case app.DestroyEvent:
			out.Stop()
			if e.Err != nil {
				return fmt.Errorf("janela: %w", e.Err)
			}
			return nil
		case app.FrameEvent:
			gtx := app.NewContext(&ops, e)
			view.Layout(gtx)
			e.Frame(gtx.Ops)
		}
	}
}

// pickFolder usa o seletor de pastas do sistema (zenity no GNOME, kdialog no KDE), se houver.
func pickFolder() func() (string, bool) {
	var cmd []string
	if p, err := exec.LookPath("zenity"); err == nil {
		cmd = []string{p, "--file-selection", "--directory", "--title=Sonivell GO"}
	} else if p, err := exec.LookPath("kdialog"); err == nil {
		home, _ := os.UserHomeDir()
		cmd = []string{p, "--getexistingdirectory", home}
	} else {
		return nil
	}
	return func() (string, bool) {
		out, err := exec.Command(cmd[0], cmd[1:]...).Output()
		if err != nil {
			return "", false
		}
		p := strings.TrimSpace(string(out))
		return p, p != ""
	}
}

// lazyOutput repassa para o motor de som quando ele estiver pronto (até lá, fica em silêncio).
type lazyOutput struct {
	mu     sync.Mutex
	eng    *audio.Engine
	volume float64
}

func (o *lazyOutput) set(e *audio.Engine) {
	o.mu.Lock()
	o.eng = e
	v := o.volume
	o.mu.Unlock()
	e.SetVolume(v)
}

func (o *lazyOutput) get() *audio.Engine {
	o.mu.Lock()
	defer o.mu.Unlock()
	return o.eng
}

func (o *lazyOutput) Load(p string, ms int64, play bool) {
	if e := o.get(); e != nil {
		e.Load(p, ms, play)
	}
}

func (o *lazyOutput) SetPlaying(p bool) {
	if e := o.get(); e != nil {
		e.SetPlaying(p)
	}
}

func (o *lazyOutput) PositionMs() int64 {
	if e := o.get(); e != nil {
		return e.PositionMs()
	}
	return 0
}

func (o *lazyOutput) SetVolume(v float64) {
	o.mu.Lock()
	o.volume = v
	e := o.eng
	o.mu.Unlock()
	if e != nil {
		e.SetVolume(v)
	}
}

func (o *lazyOutput) Stop() {
	if e := o.get(); e != nil {
		e.Stop()
	}
}

// setupLog grava o log em ~/.cache/sonivell-go/sonivell-go.log além do terminal.
func setupLog() {
	dir := core.CacheDir()
	if os.MkdirAll(dir, 0o755) != nil {
		return
	}
	f, err := os.OpenFile(filepath.Join(dir, "sonivell-go.log"), os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o644)
	if err != nil {
		return
	}
	log.SetOutput(io.MultiWriter(os.Stderr, f))
	log.SetFlags(log.Ltime | log.Lmicroseconds)
}

var _ = unit.Dp(0)

// notifyFailure mostra o erro numa janelinha do sistema (quando a nossa janela nem abriu).
func notifyFailure(err error) {
	msg := fmt.Sprintf("O Sonivell GO não conseguiu abrir:\n%v\n\nDetalhes em %s", err, filepath.Join(core.CacheDir(), "sonivell-go.log"))
	if p, e := exec.LookPath("zenity"); e == nil {
		_ = exec.Command(p, "--error", "--title=Sonivell GO", "--text="+msg).Run()
	} else if p, e := exec.LookPath("kdialog"); e == nil {
		_ = exec.Command(p, "--error", msg, "--title", "Sonivell GO").Run()
	}
}
