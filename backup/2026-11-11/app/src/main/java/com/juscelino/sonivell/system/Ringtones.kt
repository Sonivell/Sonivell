package com.juscelino.sonivell.system

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.juscelino.sonivell.data.Track

/** "Definir como toque": precisa da permissão "Alterar configurações do sistema". */
object Ringtones {
    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    /** Tela do sistema onde a pessoa libera a permissão para o Sonivell. */
    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun setAsRingtone(context: Context, track: Track): Boolean = runCatching {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id)
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
        true
    }.getOrDefault(false)
}
