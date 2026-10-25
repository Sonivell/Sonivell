package com.juscelino.sonivell.smart

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.juscelino.sonivell.MainActivity
import com.juscelino.sonivell.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Mantém o app vivo enquanto o SMART analisa a biblioteca, mesmo com a tela desligada ou o
 * app em segundo plano, e mostra o progresso numa notificação discreta.
 *
 * O trabalho em si roda no [LatentJamSmartEngine]; este serviço só garante que o Android não
 * encerre o processo no meio e some sozinho quando a análise termina.
 */
class SmartAnalysisService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = LatentJamSmartEngine.get(this)
        val started = runCatching {
            startForegroundCompat(buildNotification(engine.progress.value))
        }
        if (started.isFailure) {
            // Ex.: Android 12+ recusa iniciar em primeiro plano vindo do segundo plano.
            Log.w(TAG, "Não foi possível manter a análise em primeiro plano", started.exceptionOrNull())
            stopSelf()
            return START_NOT_STICKY
        }
        if (watchJob?.isActive != true) {
            watchJob = scope.launch {
                engine.progress.distinctUntilChanged().collect { p ->
                    if (!p.running) {
                        stopForegroundCompat()
                        stopSelf()
                    } else {
                        notificationManager().notify(NOTIFICATION_ID, buildNotification(p))
                    }
                }
            }
        }
        // Se o Android matar o serviço, não precisa recriar: a análise retoma ao abrir o app.
        return START_NOT_STICKY
    }

    /** Android 15+: serviços "dataSync" têm limite diário; ao estourar, paramos com elegância. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopForegroundCompat()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(p: SmartProgress): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_smart)
            .setContentTitle(getString(R.string.smart_analysing))
            .setContentText(getString(R.string.smart_progress, p.analysed, p.total))
            .setProgress(p.total.coerceAtLeast(1), p.analysed, p.total == 0)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = notificationManager()
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.smart_channel), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    companion object {
        private const val TAG = "SonivellSmart"
        private const val CHANNEL_ID = "smart_analysis"
        private const val NOTIFICATION_ID = 7301

        /** Chamado pelo motor quando há faixas para analisar. Seguro chamar várias vezes. */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, SmartAnalysisService::class.java))
            }.onFailure { Log.w(TAG, "Serviço de análise não iniciado", it) }
        }
    }
}
