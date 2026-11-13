package core

import (
	"bufio"
	"context"
	"encoding/json"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// Prazo do ffprobe por arquivo: um arquivo que o trave é pulado, não prende a varredura.
const probeTimeout = 20 * time.Second

var audioExt = map[string]bool{
	"mp3": true, "flac": true, "ogg": true, "oga": true, "opus": true, "m4a": true, "aac": true,
	"wav": true, "wma": true, "alac": true, "ape": true, "wv": true, "aiff": true, "aif": true,
}

// ToolsAvailable diz se o ffmpeg/ffprobe estão instalados (o app depende deles).
func ToolsAvailable() bool {
	_, e1 := exec.LookPath("ffprobe")
	_, e2 := exec.LookPath("ffmpeg")
	return e1 == nil && e2 == nil
}

const cacheName = "library-v1.tsv"

// Scan varre as pastas e devolve as faixas. Arquivos já conhecidos (mesma data de modificação)
// vêm do índice em cache; só os novos passam pelo ffprobe. progress recebe (feitas, total).
func Scan(roots []string, progress func(done, total int)) []Track {
	var files []string
	mtimes := map[string]int64{}
	for _, root := range roots {
		_ = filepath.WalkDir(root, func(p string, d fs.DirEntry, err error) error {
			if err != nil {
				return nil // pasta sem permissão: segue com o resto
			}
			if d.IsDir() {
				if strings.HasPrefix(d.Name(), ".") && p != root {
					return filepath.SkipDir
				}
				return nil
			}
			ext := strings.ToLower(strings.TrimPrefix(filepath.Ext(p), "."))
			if !audioExt[ext] {
				return nil
			}
			info, err := d.Info()
			if err != nil {
				return nil
			}
			if _, seen := mtimes[p]; !seen {
				files = append(files, p)
			}
			mtimes[p] = info.ModTime().UnixMilli()
			return nil
		})
	}

	cache := loadCache()
	result := make(map[string]Track, len(files))
	var pending []string
	for _, f := range files {
		if c, ok := cache[f]; ok && c.mtime == mtimes[f] {
			result[f] = c.track
		} else {
			pending = append(pending, f)
		}
	}

	if len(pending) > 0 {
		var mu sync.Mutex
		var done atomic.Int32
		jobs := make(chan string)
		var wg sync.WaitGroup
		workers := runtime.NumCPU()
		if workers > 6 {
			workers = 6
		}
		if workers < 2 {
			workers = 2
		}
		for i := 0; i < workers; i++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				for f := range jobs {
					if t, ok := Probe(f, mtimes[f]); ok {
						mu.Lock()
						result[f] = t
						mu.Unlock()
					}
					if progress != nil {
						progress(int(done.Add(1)), len(pending))
					}
				}
			}()
		}
		for _, f := range pending {
			jobs <- f
		}
		close(jobs)
		wg.Wait()
	}

	tracks := make([]Track, 0, len(files))
	for _, f := range files {
		if t, ok := result[f]; ok {
			tracks = append(tracks, t)
		}
	}
	saveCache(tracks, mtimes)
	return tracks
}

type probeOut struct {
	Format struct {
		Duration string            `json:"duration"`
		BitRate  string            `json:"bit_rate"`
		Tags     map[string]string `json:"tags"`
	} `json:"format"`
	Streams []struct {
		CodecType string            `json:"codec_type"`
		CodecName string            `json:"codec_name"`
		Tags      map[string]string `json:"tags"`
	} `json:"streams"`
}

var (
	reYear   = regexp.MustCompile(`\d{4}`)
	reNumber = regexp.MustCompile(`\d+`)
)

// Probe lê as etiquetas de um arquivo com o ffprobe.
func Probe(path string, mtime int64) (Track, bool) {
	ctx, cancel := context.WithTimeout(context.Background(), probeTimeout)
	defer cancel()
	out, err := exec.CommandContext(ctx, "ffprobe", "-v", "error",
		"-show_entries", "format=duration,bit_rate:format_tags:stream=codec_type,codec_name:stream_tags",
		"-of", "json", path).Output()
	if err != nil {
		return Track{}, false
	}
	var p probeOut
	if json.Unmarshal(out, &p) != nil {
		return Track{}, false
	}
	tags := map[string]string{}
	codec := ""
	for _, s := range p.Streams {
		if s.CodecType != "audio" {
			continue
		}
		if codec == "" {
			codec = s.CodecName
		}
		for k, v := range s.Tags { // Ogg/Opus guardam as etiquetas no fluxo
			tags[strings.ToLower(k)] = strings.TrimSpace(v)
		}
	}
	for k, v := range p.Format.Tags {
		tags[strings.ToLower(k)] = strings.TrimSpace(v)
	}
	if codec == "" {
		return Track{}, false // sem áudio (um vídeo, uma imagem com extensão errada…)
	}
	dur, _ := strconv.ParseFloat(p.Format.Duration, 64)
	br, _ := strconv.ParseInt(p.Format.BitRate, 10, 64)

	base := strings.TrimSuffix(filepath.Base(path), filepath.Ext(path))
	title := tags["title"]
	if title == "" {
		title = base
	}
	albumArtist := tags["album_artist"]
	if albumArtist == "" {
		albumArtist = tags["albumartist"]
	}
	artist := tags["artist"]
	if artist == "" {
		artist = albumArtist
	}
	year := 0
	if y := reYear.FindString(firstNonEmpty(tags["date"], tags["year"])); y != "" {
		year, _ = strconv.Atoi(y)
	}
	trackNo := 0
	if n := reNumber.FindString(tags["track"]); n != "" {
		trackNo, _ = strconv.Atoi(n)
	}
	return Track{
		ID: TrackID(path), Path: path, Title: title, Artist: artist, AlbumArtist: albumArtist,
		Album: tags["album"], Genre: tags["genre"], Year: year, TrackNo: trackNo,
		DurationMs: int64(dur * 1000), Format: formatName(codec), BitrateKbps: int(br / 1000), AddedMs: mtime,
	}, true
}

func firstNonEmpty(a, b string) string {
	if a != "" {
		return a
	}
	return b
}

func formatName(codec string) string {
	switch codec {
	case "mp3":
		return "MP3"
	case "flac":
		return "FLAC"
	case "opus":
		return "OPUS"
	case "vorbis":
		return "OGG"
	case "aac":
		return "AAC"
	case "alac":
		return "ALAC"
	case "wmav1", "wmav2":
		return "WMA"
	}
	if strings.HasPrefix(codec, "pcm") {
		return "WAV"
	}
	return strings.ToUpper(codec)
}

// --- índice em cache (TSV, uma faixa por linha) ---

type cached struct {
	mtime int64
	track Track
}

func clean(s string) string {
	return strings.NewReplacer("\t", " ", "\n", " ", "\r", " ").Replace(s)
}

func loadCache() map[string]cached {
	out := map[string]cached{}
	f, err := os.Open(filepath.Join(CacheDir(), cacheName))
	if err != nil {
		return out
	}
	defer f.Close()
	sc := bufio.NewScanner(f)
	sc.Buffer(make([]byte, 64*1024), 1024*1024)
	for sc.Scan() {
		c := strings.Split(sc.Text(), "\t")
		if len(c) < 13 {
			continue
		}
		mt, _ := strconv.ParseInt(c[1], 10, 64)
		year, _ := strconv.Atoi(c[7])
		no, _ := strconv.Atoi(c[8])
		dur, _ := strconv.ParseInt(c[9], 10, 64)
		br, _ := strconv.Atoi(c[11])
		added, _ := strconv.ParseInt(c[12], 10, 64)
		out[c[0]] = cached{mt, Track{
			ID: TrackID(c[0]), Path: c[0], Title: c[2], Artist: c[3], AlbumArtist: c[4], Album: c[5], Genre: c[6],
			Year: year, TrackNo: no, DurationMs: dur, Format: c[10], BitrateKbps: br, AddedMs: added,
		}}
	}
	return out
}

func saveCache(tracks []Track, mtimes map[string]int64) {
	dir := CacheDir()
	if os.MkdirAll(dir, 0o755) != nil {
		return
	}
	tmp := filepath.Join(dir, cacheName+".tmp")
	f, err := os.Create(tmp)
	if err != nil {
		return
	}
	w := bufio.NewWriter(f)
	for _, t := range tracks {
		fields := []string{
			t.Path, strconv.FormatInt(mtimes[t.Path], 10), t.Title, t.Artist, t.AlbumArtist, t.Album, t.Genre,
			strconv.Itoa(t.Year), strconv.Itoa(t.TrackNo), strconv.FormatInt(t.DurationMs, 10), t.Format,
			strconv.Itoa(t.BitrateKbps), strconv.FormatInt(t.AddedMs, 10),
		}
		for i, s := range fields {
			if i > 0 {
				w.WriteByte('\t')
			}
			w.WriteString(clean(s))
		}
		w.WriteByte('\n')
	}
	if w.Flush() == nil && f.Close() == nil {
		_ = os.Rename(tmp, filepath.Join(dir, cacheName))
	} else {
		f.Close()
		os.Remove(tmp)
	}
}
