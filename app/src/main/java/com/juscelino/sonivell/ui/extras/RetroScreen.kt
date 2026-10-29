package com.juscelino.sonivell.ui.extras

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.StatsState
import com.juscelino.sonivell.ui.components.AlbumArt
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.theme.SonivellTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.surface).clickable(role = Role.Button, onClick = onPlayTop).padding(14.dp),
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
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(c.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = c.accent)
        Text(value, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        extra?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = c.textSecondary) }
    }
}

/** Desenha um cartão 1080×1350 e abre o compartilhar do Android. */
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
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), accent, 0xFF0F1012.toInt(), Shader.TileMode.CLAMP) }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
    val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    fun text(size: Float, color: Int, typeface: Typeface = bold) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; this.typeface = typeface
    }
    val white = 0xFFF2F2EE.toInt()
    val soft = 0xFFC9CBC6.toInt()
    fun fit(s: String, p: Paint, max: Float): String {
        if (p.measureText(s) <= max) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }
    val left = 90f
    val maxW = w - 2 * left
    canvas.drawText(month.uppercase(), left, 200f, text(48f, white))
    canvas.drawText(context.getString(R.string.retro_hours, String.format(Locale.getDefault(), "%.1f", hours)), left, 340f, text(120f, white))
    var y = 560f
    listOfNotNull(
        track?.let { context.getString(R.string.retro_top_track) to it },
        artist?.let { context.getString(R.string.retro_top_artist) to it },
        genre?.let { context.getString(R.string.retro_top_genre) to it },
    ).forEach { (label, value) ->
        val card = RectF(left - 30f, y - 70f, w - left + 30f, y + 120f)
        canvas.drawRoundRect(card, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 })
        canvas.drawText(label.uppercase(), left, y, text(34f, soft))
        val vp = text(58f, white)
        canvas.drawText(fit(value, vp, maxW), left, y + 80f, vp)
        y += 230f
    }
    canvas.drawText("Sonivell", left, h - 90f, text(44f, white))

    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "retrospectiva.png")
    runCatching { file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    bmp.recycle()
    val uri = FileProvider.getUriForFile(context, context.packageName + ".share", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.share_chooser))) }
}
