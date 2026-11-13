// Sonivell GO: o Sonivell para Linux reescrito em Go (interface Gio, som pelo ffmpeg).
package main

import (
	"flag"
	"fmt"
	"log"
	"os"
	"os/exec"
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
	go func() {
		if err := run(); err != nil {
			log.Println(err)
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

	var out player.Output = silent{}
	eng, err := audio.New()
	if err != nil {
		log.Printf("sem saída de som: %v", err)
	} else {
		out = eng
	}
	ctl := player.New(out, store)
	if eng != nil {
		eng.OnEnd = ctl.TrackEnded
		eng.OnError = func(err error) { log.Println(err) }
	}

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
			if eng != nil {
				eng.Stop()
			}
			return e.Err
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

// silent é a saída usada quando não há placa de som (o app abre e mostra a biblioteca).
type silent struct{}

func (silent) Load(string, int64, bool) {}
func (silent) SetPlaying(bool)          {}
func (silent) PositionMs() int64        { return 0 }
func (silent) SetVolume(float64)        {}
func (silent) Stop()                    {}

var _ = unit.Dp(0)
