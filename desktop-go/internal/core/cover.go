package core

import (
	"context"
	"image"
	"image/color"
	_ "image/jpeg"
	_ "image/png"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Cover é a capa de uma faixa já decodificada, com a cor média (usada no fundo do player Vidro).
type Cover struct {
	Img   image.Image // nil quando a faixa não tem capa
	Tone  color.NRGBA
	Ready bool
}

// Covers extrai as capas em segundo plano (arte embutida via ffmpeg ou cover.jpg da pasta)
// e guarda em ~/.cache/sonivell-go/covers. onReady é chamado quando uma capa fica pronta.
type Covers struct {
	mu      sync.Mutex
	mem     map[uint64]*Cover
	order   []uint64
	sem     chan struct{}
	onReady func()
}

// Limite de capas decodificadas na memória (512×512 cada).
const maxCoversInMemory = 160

// NewCovers cria o carregador.
func NewCovers(onReady func()) *Covers {
	return &Covers{mem: map[uint64]*Cover{}, sem: make(chan struct{}, 3), onReady: onReady}
}

// Get devolve a capa (ou uma ainda não pronta, que se completa depois e chama onReady).
func (c *Covers) Get(t *Track) *Cover {
	if t == nil {
		return &Cover{Ready: true}
	}
	c.mu.Lock()
	if cv, ok := c.mem[t.ID]; ok {
		c.mu.Unlock()
		return cv
	}
	cv := &Cover{}
	c.mem[t.ID] = cv
	c.order = append(c.order, t.ID)
	if len(c.order) > maxCoversInMemory {
		old := c.order[0]
		c.order = c.order[1:]
		delete(c.mem, old)
	}
	c.mu.Unlock()

	path, id := t.Path, t.ID
	go func() {
		c.sem <- struct{}{}
		img := loadCover(path, id)
		<-c.sem
		var tone color.NRGBA
		if img != nil {
			tone = averageTone(img)
		}
		c.mu.Lock()
		cv.Img, cv.Tone, cv.Ready = img, tone, true
		c.mu.Unlock()
		if c.onReady != nil {
			c.onReady()
		}
	}()
	return cv
}

// Snapshot lê os campos com a trava (a capa é completada por outra goroutine).
func (c *Covers) Snapshot(cv *Cover) Cover {
	c.mu.Lock()
	defer c.mu.Unlock()
	return *cv
}

func loadCover(path string, id uint64) image.Image {
	dir := filepath.Join(CacheDir(), "covers")
	_ = os.MkdirAll(dir, 0o755)
	file := filepath.Join(dir, strconv.FormatUint(id, 16)+".jpg")
	none := file + ".none"
	if img := decode(file); img != nil {
		return img
	}
	if _, err := os.Stat(none); err == nil {
		return folderCover(path)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	err := exec.CommandContext(ctx, "ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-i", path,
		"-an", "-map", "0:v:0", "-frames:v", "1",
		"-vf", "scale=512:512:force_original_aspect_ratio=increase,crop=512:512", file).Run()
	if err == nil {
		if img := decode(file); img != nil {
			return img
		}
	}
	_ = os.Remove(file)
	_ = os.WriteFile(none, nil, 0o644)
	return folderCover(path)
}

func folderCover(track string) image.Image {
	entries, err := os.ReadDir(filepath.Dir(track))
	if err != nil {
		return nil
	}
	for _, want := range []string{"cover", "folder", "front", "album"} {
		for _, e := range entries {
			name := strings.ToLower(e.Name())
			ext := filepath.Ext(name)
			if (ext == ".jpg" || ext == ".jpeg" || ext == ".png") && strings.TrimSuffix(name, ext) == want {
				if img := decode(filepath.Join(filepath.Dir(track), e.Name())); img != nil {
					return img
				}
			}
		}
	}
	return nil
}

func decode(file string) image.Image {
	f, err := os.Open(file)
	if err != nil {
		return nil
	}
	defer f.Close()
	img, _, err := image.Decode(f)
	if err != nil {
		return nil
	}
	return img
}

// averageTone é a cor média da capa, amostrada numa grade de 24×24.
func averageTone(img image.Image) color.NRGBA {
	b := img.Bounds()
	var r, g, bl, n uint64
	for y := 0; y < 24; y++ {
		for x := 0; x < 24; x++ {
			px := b.Min.X + (x*b.Dx()+b.Dx()/2)/24
			py := b.Min.Y + (y*b.Dy()+b.Dy()/2)/24
			cr, cg, cb, _ := img.At(px, py).RGBA()
			r += uint64(cr >> 8)
			g += uint64(cg >> 8)
			bl += uint64(cb >> 8)
			n++
		}
	}
	return color.NRGBA{R: uint8(r / n), G: uint8(g / n), B: uint8(bl / n), A: 255}
}
