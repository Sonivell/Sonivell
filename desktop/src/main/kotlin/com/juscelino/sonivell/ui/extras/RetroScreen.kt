package com.juscelino.sonivell.ui.extras

import com.juscelino.sonivell.compat.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import com.juscelino.sonivell.compat.LocalContext
import com.juscelino.sonivell.compat.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.StatsState
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.theme.SonivellTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.juscelino.sonivell.ui.theme.cardSurface

/** Retrospectiva dos últimos 30 dias: horas ouvidas, música, artista e gênero do mês. */
@Composable
fun RetroScreen(stats: StatsState, contentPadding: PaddingValues, onBack: () -> Unit, onPlayTop: () -> Unit) {
    val c = SonivellTheme.colors
    val context = LocalContext.current
    val hours = stats.listenedMs / 3_600_000.0
    val month = remember { SimpleDateFormat("LLLL", Locale.getDefault()).format(Date()).replaceFirstChar { it.titlecase() } }
    val topTrack = stats.topTracks.firstOrNull()?.item
    val topArtist = stats.topArtists.firstOrNull()
    val topGenre = stats.topGenres.firstOrNull()?.item

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
            Text(stringResource(R.string.retro_title), style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
        }
        if (stats.isEmpty) {
            Text(stringResource(R.string.stats_empty), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
            return@Column
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.55f), c.surface)))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(month.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = c.textPrimary)
            Text(stringResource(R.string.retro_hours, String.format(Locale.getDefault(), "%.1f", hours)), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = c.textPrimary)
            Text(stringResource(R.string.retro_tracks, stats.distinctTracks, stats.firstListens), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        }
        if (topTrack != null) {
            Row(
                Modifier.fillMaxWidth().cardSurface(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onPlayTop).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AlbumArt(topTrack, Modifier.size(72.dp), corner = 16.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.retro_top_track), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = c.accent)
                    Text(topTrack.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(topTrack.artist, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            topArtist?.let { Tile(stringResource(R.string.retro_top_artist), it.item, stringResource(R.string.retro_plays, it.plays), Modifier.weight(1f)) }
            topGenre?.let { Tile(stringResource(R.string.retro_top_genre), it, null, Modifier.weight(1f)) }
        }
        Box(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(999.dp)).background(c.accent)
                .clickable(role = Role.Button) {
                    shareRetro(
                        context, month, hours, topTrack?.let { "${it.title} · ${it.artist}" }, topArtist?.item, topGenre,
                        accent = c.accent.toArgb(),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.retro_share), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = c.onAccent)
        }
    }
}

@Composable
private fun Tile(label: String, value: String, extra: String?, modifier: Modifier) {
    val c = SonivellTheme.colors
    Column(modifier.cardSurface(RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = c.accent)
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        extra?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = c.textSecondary) }
    }
}

/** Desenha um cartão 1080×1350 e pergunta onde salvar a imagem (no PC, "compartilhar" é salvar). */
private fun shareRetro(
    context: Context,
    month: String,
    hours: Double,
    track: String?,
    artist: String?,
    genre: String?,
    accent: Int,
) {
    val w = 1080
    val h = 1350
    val img = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.paint = java.awt.GradientPaint(0f, 0f, java.awt.Color(accent, true), 0f, h.toFloat(), java.awt.Color(0xFF0F1012.toInt(), true))
    g.fillRect(0, 0, w, h)
    val family = com.juscelino.sonivell.ui.theme.manropeAwtFont()
    fun font(size: Float) = family.deriveFont(java.awt.Font.BOLD, size)
    val white = java.awt.Color(0xF2, 0xF2, 0xEE)
    val soft = java.awt.Color(0xC9, 0xCB, 0xC6)
    fun fit(s: String, f: java.awt.Font, max: Int): String {
        val m = g.getFontMetrics(f)
        if (m.stringWidth(s) <= max) return s
        var t = s
        while (t.isNotEmpty() && m.stringWidth("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }
    fun draw(text: String, size: Float, color: java.awt.Color, x: Int, y: Int) {
        g.font = font(size); g.color = color; g.drawString(text, x, y)
    }
    val left = 90
    val maxW = w - 2 * left
    draw(month.uppercase(), 48f, white, left, 200)
    draw(context.getString(R.string.retro_hours, String.format(Locale.getDefault(), "%.1f", hours)), 120f, white, left, 340)
    var y = 560
    listOfNotNull(
        track?.let { context.getString(R.string.retro_top_track) to it },
        artist?.let { context.getString(R.string.retro_top_artist) to it },
        genre?.let { context.getString(R.string.retro_top_genre) to it },
    ).forEach { (label, value) ->
        g.color = java.awt.Color(0, 0, 0, 0x33)
        g.fillRoundRect(left - 30, y - 70, w - 2 * left + 60, 190, 80, 80)
        draw(label.uppercase(), 34f, soft, left, y)
        draw(fit(value, font(58f), maxW), 58f, white, left, y + 80)
        y += 230
    }
    draw("Sonivell", 44f, white, left, h - 90)
    g.dispose()
    Thread {
        val file = com.juscelino.sonivell.system.DesktopSystem.chooseSaveFile(context.getString(R.string.share), "sonivell-retrospectiva.png")
            ?: return@Thread
        runCatching { javax.imageio.ImageIO.write(img, "png", file) }
    }.start()
}
