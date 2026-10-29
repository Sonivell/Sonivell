package com.juscelino.sonivell.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.juscelino.sonivell.MainActivity
import com.juscelino.sonivell.R
import com.juscelino.sonivell.playback.PlaybackService
import java.util.concurrent.Executors

/** Widget grande (4×2): capa, título, artista e anterior / tocar / próxima. */
class SonivellWidgetLarge : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = SonivellWidgets.refresh(context)
}

/** Widget pequeno (2×1): capa, título e tocar/pausar. */
class SonivellWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = SonivellWidgets.refresh(context)
}

/**
 * Toques nos botões do widget: conecta à sessão do player por um instante e manda o comando.
 * Assim o comando chega mesmo com o app fechado (o serviço sobe e continua de onde parou).
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull()
            if (controller != null) {
                when (action) {
                    ACTION_TOGGLE -> {
                        if (controller.mediaItemCount == 0) openApp(app)
                        else if (controller.isPlaying) controller.pause() else controller.play()
                    }
                    ACTION_NEXT -> controller.seekToNextMediaItem()
                    ACTION_PREVIOUS -> controller.seekToPrevious()
                }
                controller.release()
            } else {
                openApp(app)
            }
            pending.finish()
        }, MoreExecutors.directExecutor())
    }

    /** Sem nada na fila não há o que tocar: abre o app (quando o sistema deixa). */
    private fun openApp(context: Context) {
        runCatching { context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    companion object {
        const val ACTION_TOGGLE = "com.juscelino.sonivell.widget.TOGGLE"
        const val ACTION_NEXT = "com.juscelino.sonivell.widget.NEXT"
        const val ACTION_PREVIOUS = "com.juscelino.sonivell.widget.PREVIOUS"
    }
}

/** Estado mostrado nos widgets, guardado para redesenhar sem o serviço rodando. */
object SonivellWidgets {
    private const val PREFS = "sonivell_widget"
    private val io = Executors.newSingleThreadExecutor()

    /** Chamado pelo serviço quando a faixa ou o tocar/pausar mudam. */
    fun publish(context: Context, title: String?, artist: String?, artUri: Uri?, playing: Boolean) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("title", title)
            .putString("artist", artist)
            .putString("art", artUri?.toString())
            .putBoolean("playing", playing)
            .apply()
        refresh(app)
    }

    /** Redesenha todos os widgets com o último estado salvo. A capa é lida fora da thread principal. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val large = manager.getAppWidgetIds(ComponentName(app, SonivellWidgetLarge::class.java))
        val small = manager.getAppWidgetIds(ComponentName(app, SonivellWidgetSmall::class.java))
        if (large.isEmpty() && small.isEmpty()) return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val title = prefs.getString("title", null)
        val artist = prefs.getString("artist", null)
        val art = prefs.getString("art", null)?.let(Uri::parse)
        val playing = prefs.getBoolean("playing", false)
        io.execute {
            val cover = art?.let { loadCover(app, it) }
            if (large.isNotEmpty()) manager.updateAppWidget(large, views(app, R.layout.widget_large, title, artist, cover, playing, withSkip = true))
            if (small.isNotEmpty()) manager.updateAppWidget(small, views(app, R.layout.widget_small, title, null, cover, playing, withSkip = false))
        }
    }

    private fun views(
        context: Context,
        layout: Int,
        title: String?,
        artist: String?,
        cover: Bitmap?,
        playing: Boolean,
        withSkip: Boolean,
    ): RemoteViews = RemoteViews(context.packageName, layout).apply {
        setTextViewText(R.id.widget_title, title ?: context.getString(R.string.widget_idle_title))
        if (withSkip) setTextViewText(R.id.widget_artist, artist ?: context.getString(R.string.widget_idle_sub))
        if (cover != null) setImageViewBitmap(R.id.widget_cover, cover) else setImageViewResource(R.id.widget_cover, R.drawable.ic_w_note)
        setImageViewResource(R.id.widget_play, if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        setContentDescription(R.id.widget_play, context.getString(if (playing) R.string.cd_pause else R.string.cd_play))
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Sem faixa ainda (app nunca tocou nada): o botão abre o app em vez de tentar tocar.
        setOnClickPendingIntent(R.id.widget_play, if (title == null) open else action(context, WidgetActionReceiver.ACTION_TOGGLE, 1))
        if (withSkip) {
            setOnClickPendingIntent(R.id.widget_next, action(context, WidgetActionReceiver.ACTION_NEXT, 2))
            setOnClickPendingIntent(R.id.widget_prev, action(context, WidgetActionReceiver.ACTION_PREVIOUS, 3))
        }
        setOnClickPendingIntent(R.id.widget_root, open)
    }

    private fun action(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code,
        Intent(context, WidgetActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Capa reduzida para ~256 px: widgets têm limite de memória para bitmaps. */
    private fun loadCover(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= COVER_PX && bounds.outHeight / (sample * 2) >= COVER_PX) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }?.let { bmp ->
            if (bmp.width <= COVER_PX * 2) bmp else Bitmap.createScaledBitmap(bmp, COVER_PX, COVER_PX * bmp.height / bmp.width, true)
        }
    }.getOrNull()

    private const val COVER_PX = 256
}
