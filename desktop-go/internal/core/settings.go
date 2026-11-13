package core

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sync"
)

// Settings são as preferências salvas em ~/.config/sonivell-go/settings.json.
type Settings struct {
	MusicDirs []string       `json:"musicDirs"`
	Volume    float64        `json:"volume"`
	Accent    string         `json:"accent"` // Lima, Azul, Laranja, Lilas, Rosa, Turquesa
	Shuffle   bool           `json:"shuffle"`
	Repeat    int            `json:"repeat"` // 0 desligado, 1 todas, 2 uma
	Favorites []uint64       `json:"favorites"`
	Recent    []uint64       `json:"recent"` // tocadas recentemente, da mais nova para a mais antiga
	Plays     map[uint64]int `json:"plays"`  // quantas vezes cada faixa começou a tocar
}

// Store guarda os ajustes e grava no disco a cada mudança.
type Store struct {
	mu   sync.Mutex
	path string
	s    Settings
}

// LoadStore lê os ajustes (ou usa os padrões na primeira execução).
func LoadStore() *Store {
	st := &Store{path: filepath.Join(ConfigDir(), "settings.json")}
	st.s = Settings{Volume: 0.8, Accent: "Lima"}
	if data, err := os.ReadFile(st.path); err == nil {
		_ = json.Unmarshal(data, &st.s)
	}
	if len(st.s.MusicDirs) == 0 {
		st.s.MusicDirs = []string{DefaultMusicDir()}
	}
	if st.s.Volume < 0 || st.s.Volume > 1 {
		st.s.Volume = 0.8
	}
	return st
}

// Get devolve uma cópia dos ajustes atuais.
func (st *Store) Get() Settings {
	st.mu.Lock()
	defer st.mu.Unlock()
	s := st.s
	s.MusicDirs = append([]string(nil), st.s.MusicDirs...)
	s.Favorites = append([]uint64(nil), st.s.Favorites...)
	s.Recent = append([]uint64(nil), st.s.Recent...)
	s.Plays = make(map[uint64]int, len(st.s.Plays))
	for k, v := range st.s.Plays {
		s.Plays[k] = v
	}
	return s
}

// Update muda os ajustes e grava (num arquivo temporário renomeado, para nunca ficar pela metade).
func (st *Store) Update(f func(*Settings)) {
	st.mu.Lock()
	f(&st.s)
	data, err := json.MarshalIndent(st.s, "", "  ")
	st.mu.Unlock()
	if err != nil {
		return
	}
	if err := os.MkdirAll(filepath.Dir(st.path), 0o755); err != nil {
		return
	}
	tmp := st.path + ".tmp"
	if os.WriteFile(tmp, data, 0o644) == nil {
		_ = os.Rename(tmp, st.path)
	}
}
