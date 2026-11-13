// Package i18n guarda os textos da interface em português e inglês (escolhidos pelo idioma do
// sistema). Para outro idioma, acrescente um mapa com as mesmas chaves.
package i18n

import (
	"fmt"
	"os"
	"strings"
)

var pt = map[string]string{
	"app_name":          "Sonivell GO",
	"app_tagline":       "Reescrito em Go, mais leve e mais rápido",
	"nav_for_you":       "Para você",
	"nav_library":       "Biblioteca",
	"nav_favorites":     "Favoritas",
	"nav_settings":      "Ajustes",
	"search_hint":       "Buscar na biblioteca",
	"greeting_morning":  "Bom dia",
	"greeting_after":    "Boa tarde",
	"greeting_night":    "Boa noite",
	"mix_all":           "Mix do dia",
	"mix_all_sub":       "Toda a biblioteca, embaralhada",
	"mix_recent":        "Novidades",
	"mix_recent_sub":    "O que entrou por último",
	"mix_favs":          "Favoritas",
	"mix_favs_sub":      "As que você marcou",
	"tracks_n":          "%d faixas",
	"recent_albums":     "Álbuns recentes",
	"up_next":           "A seguir",
	"see_all":           "Ver tudo",
	"tab_tracks":        "Faixas",
	"tab_albums":        "Álbuns",
	"tab_artists":       "Artistas",
	"search_results":    "Resultados para “%s”",
	"no_results":        "Nada encontrado.",
	"scanning":          "Lendo a biblioteca… %d de %d",
	"scanning_start":    "Procurando músicas…",
	"empty_title":       "Nenhuma música por aqui",
	"empty_body":        "Adicione a pasta das suas músicas em Ajustes.",
	"open_settings":     "Abrir Ajustes",
	"no_ffmpeg":         "O ffmpeg não está instalado. Instale com: sudo apt install ffmpeg",
	"nothing_playing":   "Nada tocando",
	"playing_from":      "Tocando de",
	"queue":             "Fila",
	"compact":           "Modo compacto",
	"expand":            "Voltar à janela completa",
	"back":              "Voltar",
	"play_all":          "Tocar tudo",
	"shuffle":           "Aleatório",
	"settings_folders":  "Pastas de música",
	"settings_add":      "Adicionar pasta",
	"settings_add_hint": "Caminho da pasta (ex.: /home/voce/Música)",
	"settings_rescan":   "Ler a biblioteca de novo",
	"settings_accent":   "Cor de destaque",
	"settings_about":    "Sobre",
	"about_body":        "Sonivell GO é o Sonivell para Linux reescrito em Go: um binário só, sem Java, que abre mais rápido e usa menos memória. Interface em Gio, som pelo ffmpeg e ícones Phosphor.",
	"about_thanks":      "Agradecimentos: Nikita-sud e o LatentJam, que inspiram o motor SMART do Sonivell.",
	"about_version":     "Versão %s",
	"remove":            "Remover",
	"cd_play":           "Tocar",
	"cd_pause":          "Pausar",
	"cd_next":           "Próxima",
	"cd_previous":       "Anterior",
	"cd_shuffle":        "Aleatório",
	"cd_repeat":         "Repetir",
	"cd_like":           "Favoritar",
	"cd_volume":         "Volume",
	"albums_n":          "%d álbuns",
	"unknown_artist":    "Artista desconhecido",
}

var en = map[string]string{
	"app_name":          "Sonivell GO",
	"app_tagline":       "Rewritten in Go, lighter and faster",
	"nav_for_you":       "For you",
	"nav_library":       "Library",
	"nav_favorites":     "Favorites",
	"nav_settings":      "Settings",
	"search_hint":       "Search your library",
	"greeting_morning":  "Good morning",
	"greeting_after":    "Good afternoon",
	"greeting_night":    "Good evening",
	"mix_all":           "Daily mix",
	"mix_all_sub":       "Your whole library, shuffled",
	"mix_recent":        "New arrivals",
	"mix_recent_sub":    "The latest additions",
	"mix_favs":          "Favorites",
	"mix_favs_sub":      "The ones you liked",
	"tracks_n":          "%d tracks",
	"recent_albums":     "Recent albums",
	"up_next":           "Up next",
	"see_all":           "See all",
	"tab_tracks":        "Tracks",
	"tab_albums":        "Albums",
	"tab_artists":       "Artists",
	"search_results":    "Results for “%s”",
	"no_results":        "Nothing found.",
	"scanning":          "Reading your library… %d of %d",
	"scanning_start":    "Looking for music…",
	"empty_title":       "No music here yet",
	"empty_body":        "Add your music folder in Settings.",
	"open_settings":     "Open Settings",
	"no_ffmpeg":         "ffmpeg is not installed. Install it with: sudo apt install ffmpeg",
	"nothing_playing":   "Nothing playing",
	"playing_from":      "Playing from",
	"queue":             "Queue",
	"compact":           "Compact mode",
	"expand":            "Back to the full window",
	"back":              "Back",
	"play_all":          "Play all",
	"shuffle":           "Shuffle",
	"settings_folders":  "Music folders",
	"settings_add":      "Add folder",
	"settings_add_hint": "Folder path (e.g. /home/you/Music)",
	"settings_rescan":   "Rescan the library",
	"settings_accent":   "Accent color",
	"settings_about":    "About",
	"about_body":        "Sonivell GO is Sonivell for Linux rewritten in Go: a single binary, no Java, that starts faster and uses less memory. Gio interface, ffmpeg audio and Phosphor icons.",
	"about_thanks":      "Thanks to Nikita-sud and LatentJam, the inspiration behind Sonivell's SMART engine.",
	"about_version":     "Version %s",
	"remove":            "Remove",
	"cd_play":           "Play",
	"cd_pause":          "Pause",
	"cd_next":           "Next",
	"cd_previous":       "Previous",
	"cd_shuffle":        "Shuffle",
	"cd_repeat":         "Repeat",
	"cd_like":           "Like",
	"cd_volume":         "Volume",
	"albums_n":          "%d albums",
	"unknown_artist":    "Unknown artist",
}

var current = pick()

func pick() map[string]string {
	for _, env := range []string{"LC_ALL", "LC_MESSAGES", "LANG", "LANGUAGE"} {
		if v := os.Getenv(env); v != "" {
			if strings.HasPrefix(strings.ToLower(v), "pt") {
				return pt
			}
			return en
		}
	}
	return pt
}

// T devolve o texto da chave (com fmt.Sprintf se houver argumentos).
func T(key string, args ...any) string {
	s, ok := current[key]
	if !ok {
		s = en[key]
	}
	if len(args) > 0 {
		return fmt.Sprintf(s, args...)
	}
	return s
}

// Keys devolve as chaves de cada idioma (para o teste que confere se nenhuma falta).
func Keys() (ptKeys, enKeys map[string]string) { return pt, en }
