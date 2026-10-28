package com.juscelino.sonivell.ui.library

import com.juscelino.sonivell.LibraryTab
import com.juscelino.sonivell.data.Track

/** As três abas que usam a tela da biblioteca. */
enum class LibraryMode { Inicio, Buscar, Biblioteca }

data class LibraryActions(
    val onQuery: (String) -> Unit,
    val onTab: (LibraryTab) -> Unit,
    val onSort: () -> Unit,
    val onRefresh: () -> Unit,
    val onPlay: (tracks: List<Track>, index: Int, source: String) -> Unit,
    val onShuffleAll: () -> Unit,
    val onPlayMix: (Track) -> Unit = {},
    val onTrackMenu: (Track) -> Unit = {},
    val onOpenAlbum: (Long) -> Unit = {},
    val onOpenArtist: (String) -> Unit = {},
    val onOpenGenre: (String) -> Unit = {},
    val onOpenFolder: (String) -> Unit = {},
    val onOpenPlaylist: (Long) -> Unit = {},
    val onNewPlaylist: () -> Unit = {},
    val onImportPlaylist: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    /** Playlists automáticas: chave como "mais-ouvidas" ou "decada-1990". */
    val onOpenAuto: (String) -> Unit = {},
    val onOpenSmartPlaylist: (Long) -> Unit = {},
    val onNewSmartPlaylist: () -> Unit = {},
    /** "Continuar ouvindo": toca a faixa a partir de onde parou. */
    val onResume: (Track, Long) -> Unit = { _, _ -> },
    val onOpenSearch: () -> Unit = {},
    /** Abre "Organizar biblioteca" (tags faltando ou genéricas). */
    val onOpenOrganize: () -> Unit = {},
)
