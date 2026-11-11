package com.juscelino.sonivell.playback

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Áudio espacial no Android, em duas camadas:
 *  - a do aparelho: o Media3 entrega o som multicanal (5.1, 7.1, Dolby Atmos em E-AC-3 JOC) ao
 *    decodificador e ao áudio espacial do próprio Android (Spatializer, Android 12L+), que faz o 3D no
 *    fone, inclusive com rastreamento de cabeça quando o fone tem. Ligado por padrão; dá para desligar.
 *  - a do Sonivell: [SpatialRenderer] para a música estéreo comum, que é quase toda a biblioteca.
 */
object SpatialAudio {
    /** Som espacial do Sonivell (música estéreo). Lido a cada pedaço de áudio: liga e desliga na hora. */
    @Volatile var enabled: Boolean = false

    /** Atributos de áudio do player: música, com o áudio espacial do aparelho ligado ou desligado. */
    fun attributes(systemSpatial: Boolean): AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setSpatializationBehavior(if (systemSpatial) C.SPATIALIZATION_BEHAVIOR_AUTO else C.SPATIALIZATION_BEHAVIOR_NEVER)
        .build()

    /** Renderizadores com o som espacial do Sonivell na cadeia de áudio (cada player tem o seu). */
    @OptIn(UnstableApi::class)
    fun renderersFactory(context: Context): DefaultRenderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
            DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                // A cadeia padrão (pular silêncio e velocidade) continua depois do som espacial.
                .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(SpatialAudioProcessor()))
                .build()
    }

    /** Como está o áudio espacial do aparelho, para os Ajustes. */
    enum class SystemStatus {
        /** Android anterior ao 12L: não há áudio espacial do sistema. */
        Unsupported,
        /** O aparelho não faz áudio espacial. */
        None,
        /** Faz, mas está desligado nos ajustes do Android (ou a saída atual não aceita). */
        Off,
        /** Ligado. */
        On,
        /** Ligado, com rastreamento de cabeça disponível no fone atual. */
        OnHeadTracking,
    }

    fun systemStatus(context: Context): SystemStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) return SystemStatus.Unsupported
        val spatializer = context.getSystemService(AudioManager::class.java)?.spatializer ?: return SystemStatus.Unsupported
        if (spatializer.immersiveAudioLevel == android.media.Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE) return SystemStatus.None
        if (!spatializer.isEnabled || !spatializer.isAvailable) return SystemStatus.Off
        val headTracking = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && spatializer.isHeadTrackerAvailable
        return if (headTracking) SystemStatus.OnHeadTracking else SystemStatus.On
    }
}

/**
 * Processador do Media3 que aplica o [SpatialRenderer] em PCM 16 bits estéreo. Outros formatos
 * (multicanal, Atmos) passam direto, para o áudio espacial do aparelho cuidar.
 */
@OptIn(UnstableApi::class)
class SpatialAudioProcessor : BaseAudioProcessor() {
    private var renderer: SpatialRenderer? = null
    private var rate = 0

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) return AudioFormat.NOT_SET
        if (renderer == null || rate != inputAudioFormat.sampleRate) {
            rate = inputAudioFormat.sampleRate
            renderer = SpatialRenderer(rate)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val r = renderer ?: return
        val enabled = SpatialAudio.enabled
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val frames = input.remaining() / 4
        val out = replaceOutputBuffer(frames * 4)
        repeat(frames) {
            r.render(input.short / 32768f, input.short / 32768f, enabled)
            out.putShort(toPcm(r.outLeft))
            out.putShort(toPcm(r.outRight))
        }
        out.flip()
    }

    override fun onFlush() {
        renderer?.clear()
    }

    override fun onReset() {
        renderer = null
        rate = 0
    }

    private fun toPcm(x: Float): Short = (x * 32767f).toInt().coerceIn(-32768, 32767).toShort()
}
