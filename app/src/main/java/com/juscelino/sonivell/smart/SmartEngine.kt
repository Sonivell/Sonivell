package com.juscelino.sonivell.smart

import com.juscelino.sonivell.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Progresso da análise da biblioteca.
 * [analysed] faixas já "ouvidas" pelo modelo de áudio, de [total].
 */
data class SmartProgress(
    val running: Boolean = false,
    val analysed: Int = 0,
    val total: Int = 0,
    /** Vetores de metadados prontos: o SMART já funciona, mesmo antes de ouvir todo o áudio. */
    val metadataReady: Boolean = false,
    val failed: Boolean = false,
)

/**
 * Aleatório inteligente: monta uma fila de músicas que soam parecidas com a escolhida.
 *
 * O app só conhece esta interface. Na edição "lite" ela é [NoSmartEngine]; na edição
 * "smart" é o motor do LatentJam (veja src/smart). Assim o player continua leve para
 * quem não quer a análise de som.
 */
interface SmartEngine {
    val isAvailable: Boolean
    val progress: StateFlow<SmartProgress>

    /** Analisa (ou continua analisando) a biblioteca em segundo plano. Chamadas repetidas são baratas. */
    fun analyse(library: List<Track>)

    /** Nova fila a partir de [seed]: ids em ordem de reprodução, sem o próprio seed. Vazia se o SMART não puder rodar. */
    suspend fun startQueue(seed: Track, library: List<Track>, length: Int): List<Long>

    /**
     * Continua uma fila SMART a partir da última faixa planejada [seed].
     * [library] só traz o que pode entrar (sem o que já está na fila); [preceding] são as
     * faixas da fila antes de [seed], da mais antiga para a mais recente.
     */
    suspend fun continueQueue(seed: Track, library: List<Track>, length: Int, preceding: List<Long>): List<Long>
}

object NoSmartEngine : SmartEngine {
    override val isAvailable = false
    override val progress: StateFlow<SmartProgress> = MutableStateFlow(SmartProgress())
    override fun analyse(library: List<Track>) = Unit
    override suspend fun startQueue(seed: Track, library: List<Track>, length: Int) = emptyList<Long>()
    override suspend fun continueQueue(seed: Track, library: List<Track>, length: Int, preceding: List<Long>) =
        emptyList<Long>()
}
