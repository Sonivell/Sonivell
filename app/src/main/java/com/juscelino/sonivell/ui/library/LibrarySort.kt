package com.juscelino.sonivell.ui.library

import androidx.compose.runtime.Composable
import com.juscelino.sonivell.ALBUM_SORTS
import com.juscelino.sonivell.ARTIST_SORTS
import com.juscelino.sonivell.FOLDER_SORTS
import com.juscelino.sonivell.GENRE_SORTS
import com.juscelino.sonivell.GroupSort
import com.juscelino.sonivell.LibrarySorts
import com.juscelino.sonivell.LibraryState
import com.juscelino.sonivell.LibraryTab
import com.juscelino.sonivell.PlaylistSort
import com.juscelino.sonivell.SortOrder
import com.juscelino.sonivell.SortTarget
import com.juscelino.sonivell.data.Playlist
import com.juscelino.sonivell.data.SmartPlaylist
import com.juscelino.sonivell.data.SortKeys
import com.juscelino.sonivell.ui.components.SortMenuButton

/**
 * Botão "Ordenar por" de uma aba da Biblioteca, igual em todos os temas: cada aba mostra as
 * opções que fazem sentido para ela (Todas e Playlists ordenam as playlists).
 */
@Composable
fun LibrarySortButton(state: LibraryState, tab: LibraryTab, actions: LibraryActions) {
    val s = state.sorts
    fun group(options: List<GroupSort>, selected: GroupSort, target: SortTarget) = SortMenuButton(
        options, selected, { it.label }, target in s.reversed,
        { actions.onSetGroupSort(target, it) }, { actions.onToggleSortReversed(target) },
    )
    when (tab) {
        LibraryTab.Musicas -> SortMenuButton(
            SortOrder.entries, state.sort, { it.label }, SortTarget.Musicas in s.reversed,
            actions.onSetSort, { actions.onToggleSortReversed(SortTarget.Musicas) },
        )
        LibraryTab.Albuns -> group(ALBUM_SORTS, s.albums, SortTarget.Albuns)
        LibraryTab.Artistas -> group(ARTIST_SORTS, s.artists, SortTarget.Artistas)
        LibraryTab.Generos -> group(GENRE_SORTS, s.genres, SortTarget.Generos)
        LibraryTab.Pastas -> group(FOLDER_SORTS, s.folders, SortTarget.Pastas)
        LibraryTab.Tudo, LibraryTab.Playlists -> SortMenuButton(
            PlaylistSort.entries, s.playlists, { it.label }, SortTarget.Playlists in s.reversed,
            actions.onSetPlaylistSort, { actions.onToggleSortReversed(SortTarget.Playlists) },
        )
    }
}

/** Playlists na ordem escolhida (a lista é curta: ordena na hora). */
fun sortPlaylists(playlists: List<Playlist>, sorts: LibrarySorts): List<Playlist> {
    val keys = SortKeys()
    val sorted = when (sorts.playlists) {
        PlaylistSort.Padrao -> playlists
        PlaylistSort.Nome -> playlists.sortedBy { keys.of(it.name) }
        PlaylistSort.Criadas -> playlists.sortedByDescending { it.createdAt }
        PlaylistSort.Atualizadas -> playlists.sortedByDescending { it.updatedAt }
        PlaylistSort.Faixas -> playlists.sortedByDescending { it.trackIds.size }
    }
    return if (SortTarget.Playlists in sorts.reversed) sorted.asReversed() else sorted
}

/**
 * Playlists inteligentes na ordem escolhida: não têm datas nem contagem fixa, então seguem o nome
 * na ordem por nome e a de criação nas outras.
 */
fun sortSmartPlaylists(smart: List<SmartPlaylist>, sorts: LibrarySorts): List<SmartPlaylist> {
    val keys = SortKeys()
    val sorted = if (sorts.playlists == PlaylistSort.Nome) smart.sortedBy { keys.of(it.name) } else smart
    return if (SortTarget.Playlists in sorts.reversed) sorted.asReversed() else sorted
}

/** A lista da aba está em ordem alfabética crescente? Só aí o índice A–Z lateral faz sentido. */
fun LibraryState.alphabetical(tab: LibraryTab): Boolean {
    val s = sorts
    return when (tab) {
        LibraryTab.Musicas -> sort in setOf(SortOrder.Titulo, SortOrder.Artista, SortOrder.Album) && SortTarget.Musicas !in s.reversed
        LibraryTab.Albuns -> s.albums == GroupSort.Nome && SortTarget.Albuns !in s.reversed
        LibraryTab.Artistas -> s.artists == GroupSort.Nome && SortTarget.Artistas !in s.reversed
        LibraryTab.Generos -> s.genres == GroupSort.Nome && SortTarget.Generos !in s.reversed
        LibraryTab.Pastas -> s.folders == GroupSort.Nome && SortTarget.Pastas !in s.reversed
        LibraryTab.Tudo, LibraryTab.Playlists -> false
    }
}
