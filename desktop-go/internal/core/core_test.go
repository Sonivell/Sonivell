package core

import (
	"os"
	"os/exec"
	"path/filepath"
	"testing"
)

func TestFoldAndSortKey(t *testing.T) {
	if got := Fold("Música Ação"); got != "musica acao" {
		t.Fatalf("Fold = %q", got)
	}
	if got := SortKey("¡Hola"); got != "hola" {
		t.Fatalf("SortKey = %q", got)
	}
}

func TestFormatTime(t *testing.T) {
	cases := map[int64]string{0: "0:00", 61_000: "1:01", 3_723_000: "1:02:03", -5: "0:00"}
	for ms, want := range cases {
		if got := FormatTime(ms); got != want {
			t.Errorf("FormatTime(%d) = %q, quer %q", ms, got, want)
		}
	}
}

func TestInitials(t *testing.T) {
	if got := Initials("maré baixa"); got != "MB" {
		t.Fatalf("Initials = %q", got)
	}
	if got := Initials(""); got != "♪" {
		t.Fatalf("Initials vazio = %q", got)
	}
}

// O ID tem que ser o mesmo do app em Kotlin (FNV-1a de 64 bits, sem o bit de sinal).
func TestTrackIDMatchesKotlin(t *testing.T) {
	// Valor calculado com trackIdOf("/música/a.flac") do Basics.kt.
	h := uint64(0xcbf29ce484222325)
	for _, b := range []byte("/música/a.flac") {
		h ^= uint64(b)
		h *= 0x100000001b3
	}
	if TrackID("/música/a.flac") != h&0x7fffffffffffffff {
		t.Fatal("TrackID diverge do FNV-1a")
	}
	if TrackID("a") == TrackID("b") {
		t.Fatal("IDs iguais para caminhos diferentes")
	}
}

func makeTone(t *testing.T, path string, meta ...string) {
	t.Helper()
	args := []string{"-loglevel", "error", "-y", "-f", "lavfi", "-i", "sine=f=440:d=2"}
	for i := 0; i+1 < len(meta); i += 2 {
		args = append(args, "-metadata", meta[i]+"="+meta[i+1])
	}
	args = append(args, path)
	if out, err := exec.Command("ffmpeg", args...).CombinedOutput(); err != nil {
		t.Fatalf("ffmpeg: %v\n%s", err, out)
	}
}

func TestScanAndLibrary(t *testing.T) {
	if !ToolsAvailable() {
		t.Skip("sem ffmpeg/ffprobe")
	}
	t.Setenv("XDG_CACHE_HOME", t.TempDir())
	dir := t.TempDir()
	makeTone(t, filepath.Join(dir, "01.flac"), "title", "Maré Alta", "artist", "Lua Norte", "album", "Horizonte", "genre", "Indie", "date", "2023", "track", "1/3")
	makeTone(t, filepath.Join(dir, "02.mp3"), "title", "Âncora", "artist", "Lua Norte", "album", "Horizonte", "genre", "Indie", "track", "2")
	makeTone(t, filepath.Join(dir, "sem-etiquetas.ogg"))
	if err := os.WriteFile(filepath.Join(dir, "capa.txt"), []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}

	tracks := Scan([]string{dir}, nil)
	if len(tracks) != 3 {
		t.Fatalf("faixas = %d, quer 3", len(tracks))
	}
	lib := NewLibrary(tracks)
	if len(lib.Albums) != 1 || len(lib.Albums[0].Tracks) != 2 {
		t.Fatalf("álbuns = %+v", lib.Albums)
	}
	a := lib.Albums[0]
	if a.Tracks[0].Title != "Maré Alta" || a.Tracks[0].TrackNo != 1 || a.Year != 2023 {
		t.Fatalf("álbum mal montado: %+v", a.Tracks[0])
	}
	if a.Tracks[0].Format != "FLAC" || a.Tracks[1].Format != "MP3" {
		t.Fatalf("formatos: %s %s", a.Tracks[0].Format, a.Tracks[1].Format)
	}
	if a.Tracks[0].DurationMs < 1900 || a.Tracks[0].DurationMs > 2100 {
		t.Fatalf("duração = %d", a.Tracks[0].DurationMs)
	}
	if res := lib.Search("ancora"); len(res) != 1 || res[0].Title != "Âncora" {
		t.Fatalf("busca sem acento falhou: %v", res)
	}
	var untagged *Track
	for _, tr := range lib.Tracks {
		if tr.Format == "OGG" || tr.Format == "OPUS" || tr.Format == "VORBIS" {
			untagged = tr
		}
	}
	if untagged == nil || untagged.Title != "sem-etiquetas" {
		t.Fatalf("faixa sem etiquetas deveria usar o nome do arquivo: %+v", untagged)
	}

	// Segunda leitura vem do índice em cache, com o mesmo resultado.
	again := Scan([]string{dir}, func(done, total int) { t.Errorf("não devia reler: %d/%d", done, total) })
	if len(again) != 3 {
		t.Fatalf("cache: %d faixas", len(again))
	}
}

func TestStoreRoundTrip(t *testing.T) {
	t.Setenv("XDG_CONFIG_HOME", t.TempDir())
	st := LoadStore()
	st.Update(func(s *Settings) { s.Volume = 0.5; s.Accent = "Azul"; s.Favorites = []uint64{7} })
	again := LoadStore().Get()
	if again.Volume != 0.5 || again.Accent != "Azul" || len(again.Favorites) != 1 {
		t.Fatalf("ajustes não persistiram: %+v", again)
	}
}
