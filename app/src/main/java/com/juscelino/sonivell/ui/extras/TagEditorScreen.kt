package com.juscelino.sonivell.ui.extras

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.TagEdit
import com.juscelino.sonivell.data.Track
import com.juscelino.sonivell.data.isGenericGenre
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.theme.SonivellTheme
import com.juscelino.sonivell.ui.theme.cardSurface

/**
 * Editor de tags: corrige título, artista, álbum, gênero e ano. As correções ficam no
 * Sonivell (valem em todo o app e no SMART); o arquivo de música não é alterado.
 */
@Composable
fun TagEditorScreen(
    original: Track,
    current: TagEdit?,
    contentPadding: PaddingValues,
    onSave: (TagEdit) -> Unit,
    onBack: () -> Unit,
) {
    val c = SonivellTheme.colors
    var title by rememberSaveable { mutableStateOf(current?.title ?: original.rawTitle ?: original.title) }
    var artist by rememberSaveable { mutableStateOf(current?.artist ?: original.rawArtist.orEmpty()) }
    var album by rememberSaveable { mutableStateOf(current?.album ?: original.rawAlbum.orEmpty()) }
    var genre by rememberSaveable { mutableStateOf(current?.genre ?: original.genre.orEmpty()) }
    var year by rememberSaveable { mutableStateOf((current?.year ?: original.year)?.toString().orEmpty()) }

    fun edit(): TagEdit {
        fun changed(value: String, from: String?): String? = value.trim().takeIf { it.isNotEmpty() && it != from.orEmpty() }
        return TagEdit(
            title = changed(title, original.rawTitle ?: original.title),
            artist = changed(artist, original.rawArtist),
            album = changed(album, original.rawAlbum),
            genre = changed(genre, original.genre),
            year = year.trim().toIntOrNull()?.takeIf { it in 1000..2999 && it != original.year },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBtn(SonivellTheme.icons.back, stringResource(R.string.cd_back), onBack, background = c.surface)
            Text(stringResource(R.string.tags_title), style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
            Box(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(999.dp)).background(c.accent)
                    .clickable(role = Role.Button) { onSave(edit()); onBack() }.padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.tags_save), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = c.onAccent)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AlbumArt(original, Modifier.size(72.dp), corner = 16.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(original.fileName ?: original.title, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 2)
                Text(original.folder, style = MaterialTheme.typography.labelMedium, color = c.textSecondary, maxLines = 1)
            }
        }
        Field(stringResource(R.string.tags_field_title), title, warning = null) { title = it }
        Field(stringResource(R.string.tags_field_artist), artist, warning = if (artist.isBlank()) stringResource(R.string.tags_missing) else null) { artist = it }
        Field(stringResource(R.string.tags_field_album), album, warning = if (album.isBlank()) stringResource(R.string.tags_missing) else null) { album = it }
        Field(
            stringResource(R.string.tags_field_genre),
            genre,
            warning = when {
                genre.isBlank() -> stringResource(R.string.tags_missing)
                isGenericGenre(genre) -> stringResource(R.string.tags_generic)
                else -> null
            },
        ) { genre = it }
        Field(stringResource(R.string.tags_field_year), year, warning = if (year.isBlank()) stringResource(R.string.tags_missing) else null, number = true) { year = it.filter(Char::isDigit).take(4) }
        Text(stringResource(R.string.tags_note), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        if (current != null && !current.isEmpty) {
            Text(
                stringResource(R.string.tags_restore),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = c.accent,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onSave(TagEdit()); onBack() }
                    .heightIn(min = 44.dp).padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String, warning: String?, number: Boolean = false, onChange: (String) -> Unit) {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = c.textSecondary)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .cardSurface(shape)
                .border(1.dp, if (warning != null) Color_Warning else c.outline, shape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = c.textPrimary, fontWeight = FontWeight.SemiBold)),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                modifier = Modifier.weight(1f).semantics { contentDescription = label },
            )
            if (warning != null) Text(warning, style = MaterialTheme.typography.labelSmall, color = Color_Warning)
        }
    }
}

private val Color_Warning = androidx.compose.ui.graphics.Color(0xFFFFC46B)
