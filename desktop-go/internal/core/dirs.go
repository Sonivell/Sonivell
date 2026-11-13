package core

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

// AppName é a pasta do Sonivell GO nas pastas do usuário (separada da do Sonivell em Kotlin).
const AppName = "sonivell-go"

func xdg(env, fallback string) string {
	if v := os.Getenv(env); v != "" && filepath.IsAbs(v) {
		return filepath.Join(v, AppName)
	}
	home, _ := os.UserHomeDir()
	return filepath.Join(home, fallback, AppName)
}

// ConfigDir é ~/.config/sonivell-go.
func ConfigDir() string { return xdg("XDG_CONFIG_HOME", ".config") }

// CacheDir é ~/.cache/sonivell-go (índice da biblioteca e capas extraídas).
func CacheDir() string { return xdg("XDG_CACHE_HOME", ".cache") }

// DefaultMusicDir é a pasta de música do usuário (xdg-user-dir MUSIC, senão ~/Música ou ~/Music).
func DefaultMusicDir() string {
	if out, err := exec.Command("xdg-user-dir", "MUSIC").Output(); err == nil {
		dir := strings.TrimSpace(string(out))
		if home, _ := os.UserHomeDir(); dir != "" && dir != home {
			return dir
		}
	}
	home, _ := os.UserHomeDir()
	for _, name := range []string{"Música", "Músicas", "Music"} {
		p := filepath.Join(home, name)
		if st, err := os.Stat(p); err == nil && st.IsDir() {
			return p
		}
	}
	return filepath.Join(home, "Music")
}
