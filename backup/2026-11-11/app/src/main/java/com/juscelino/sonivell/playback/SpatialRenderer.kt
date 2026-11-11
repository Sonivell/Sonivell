package com.juscelino.sonivell.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Som espacial do Sonivell: leva a música estéreo para fora da cabeça no fone.
 *
 * Em vez de tocar o canal esquerdo só no ouvido esquerdo, coloca "caixas virtuais" em volta da cabeça
 * (frente a ±30°, como num par de caixas de som, e o ambiente do disco atrás, a ±110°) e calcula o que
 * cada ouvido ouviria delas, com o modelo de cabeça esférica de Brown e Duda (1998):
 *  - o atraso entre os ouvidos (fórmula de Woodworth), até ~0,7 ms;
 *  - a sombra da cabeça, que abafa os agudos no ouvido do lado oposto (filtro de 1ª ordem);
 *  - o ouvido do lado oposto mais baixo que na sala de verdade (como num crossfeed), para a voz do meio
 *    não ganhar o "buraco" em ~1,5 kHz que a soma das duas caixas cria;
 *  - poucas reflexões curtas de uma sala pequena, que ajudam o som a "sair" da cabeça.
 *
 * Tudo é calculado aqui, sem banco de dados de HRTF de terceiros (sem licença a cumprir) e com custo
 * baixo: alguns filtros de 1ª ordem e linhas de atraso por amostra. Desligado, devolve o sinal intacto
 * (com uma rampa curta na troca, para não estalar).
 */
class SpatialRenderer(private val sampleRate: Int) {

    /** Uma caixa virtual vista por um ouvido: atraso em amostras e o filtro da sombra da cabeça. */
    private class EarPath(val delay: Int, val b0: Float, val b1: Float, val a1: Float) {
        var x1 = 0f
        var y1 = 0f

        fun filter(x: Float): Float {
            val y = b0 * x + b1 * x1 - a1 * y1
            x1 = x
            y1 = y
            return y
        }

        fun clear() { x1 = 0f; y1 = 0f }
    }

    // Fontes: 0 = frente esquerda, 1 = frente direita, 2 = ambiente atrás à esquerda, 3 = atrás à direita.
    private val azimuths = doubleArrayOf(-30.0, 30.0, -110.0, 110.0)
    private val toLeft = Array(4) { path(azimuths[it], ear = -90.0) }
    private val toRight = Array(4) { path(azimuths[it], ear = 90.0) }

    // Reflexões: (fonte, atraso em ms, ganho, vai para o ouvido esquerdo?).
    private val reflections = listOf(
        Reflection(0, 7.3, 0.20f, left = true), Reflection(1, 7.9, 0.20f, left = false),
        Reflection(1, 11.6, 0.14f, left = true), Reflection(0, 12.4, 0.14f, left = false),
        Reflection(0, 17.1, 0.09f, left = true), Reflection(1, 18.3, 0.09f, left = false),
    ).map { it.copy(samples = (it.ms * sampleRate / 1000.0).roundToInt()) }

    private data class Reflection(val source: Int, val ms: Double, val gain: Float, val left: Boolean, val samples: Int = 0)

    // Linhas de atraso das fontes (potência de 2 para o índice circular sair com &).
    private val size = Integer.highestOneBit(((reflections.maxOf { it.samples } + 64) * 2).coerceAtLeast(256))
    private val mask = size - 1
    private val lines = Array(4) { FloatArray(size) }
    private var write = 0

    // Passa-baixas das reflexões (paredes abafam os agudos).
    private val reflectionLp = (1.0 - kotlin.math.exp(-2.0 * PI * 5_000.0 / sampleRate)).toFloat()
    private var reflL = 0f
    private var reflR = 0f

    /** Quanto do som espacial entra agora (0 = original, 1 = espacial); anda até [target] aos poucos. */
    private var mix = 0f
    private val mixStep = 1f / (sampleRate * 0.08f) // ~80 ms de rampa

    var outLeft = 0f
        private set
    var outRight = 0f
        private set

    /** Processa uma amostra estéreo; o resultado fica em [outLeft]/[outRight]. */
    fun render(left: Float, right: Float, enabled: Boolean) {
        val target = if (enabled) 1f else 0f
        if (mix != target) mix = if (mix < target) minOf(target, mix + mixStep) else maxOf(target, mix - mixStep)

        // O ambiente vem da diferença entre os canais (o que é só "largura" no disco), bem mais baixo.
        val side = (left - right) * 0.5f * AMBIENCE
        lines[0][write] = left
        lines[1][write] = right
        lines[2][write] = side
        lines[3][write] = -side

        if (mix == 0f) {
            // Desligado: só mantém as linhas cheias (para ligar sem buraco) e devolve o original.
            write = (write + 1) and mask
            outLeft = left
            outRight = right
            return
        }

        var l = 0f
        var r = 0f
        for (s in 0 until 4) {
            val line = lines[s]
            l += toLeft[s].filter(line[(write - toLeft[s].delay) and mask])
            r += toRight[s].filter(line[(write - toRight[s].delay) and mask])
        }
        var rl = 0f
        var rr = 0f
        for (f in reflections) {
            val v = lines[f.source][(write - f.samples) and mask] * f.gain
            if (f.left) rl += v else rr += v
        }
        reflL += reflectionLp * (rl - reflL)
        reflR += reflectionLp * (rr - reflR)
        write = (write + 1) and mask

        val wetL = soft((l + reflL) * OUTPUT_GAIN)
        val wetR = soft((r + reflR) * OUTPUT_GAIN)
        outLeft = left + (wetL - left) * mix
        outRight = right + (wetR - right) * mix
    }

    /** Esvazia atrasos e filtros (troca de faixa, busca). */
    fun clear() {
        lines.forEach { it.fill(0f) }
        toLeft.forEach { it.clear() }
        toRight.forEach { it.clear() }
        reflL = 0f
        reflR = 0f
    }

    /**
     * Caminho de uma fonte em [azimuth] graus (0 = frente, positivo = direita) até o ouvido em [ear]
     * graus (-90 esquerdo, +90 direito).
     */
    private fun path(azimuth: Double, ear: Double): EarPath {
        // Ângulo entre a fonte e o eixo do ouvido, de 0 (de frente para o ouvido) a 180 (do outro lado).
        var theta = abs(azimuth - ear) % 360.0
        if (theta > 180.0) theta = 360.0 - theta

        // Atraso (Woodworth), deslocado para nunca ser negativo.
        val t = Math.toRadians(theta)
        val seconds = if (theta < 90.0) -HEAD_RADIUS / SOUND_SPEED * cos(t) else HEAD_RADIUS / SOUND_SPEED * (t - PI / 2)
        val delay = ((seconds + HEAD_RADIUS / SOUND_SPEED) * sampleRate).roundToInt()

        // Sombra da cabeça: H(s) = (αs + β) / (s + β), β = 2c/a, α de ~2 (realce) a 0,1 (abafado).
        val alpha = (1 + ALPHA_MIN / 2) + (1 - ALPHA_MIN / 2) * cos(theta / THETA_MIN * PI)
        val beta = 2 * SOUND_SPEED / HEAD_RADIUS
        val k = 2.0 * sampleRate // transformação bilinear
        val norm = k + beta
        val gain = if (theta > 90.0) CONTRA_GAIN else 1.0
        return EarPath(
            delay = delay,
            b0 = (gain * (alpha * k + beta) / norm).toFloat(),
            b1 = (gain * (beta - alpha * k) / norm).toFloat(),
            a1 = ((beta - k) / norm).toFloat(),
        )
    }

    /** Limita sem cortar seco quando a soma passa do teto. */
    private fun soft(x: Float): Float {
        val a = abs(x)
        if (a <= KNEE) return x
        val over = (a - KNEE) / (1f - KNEE)
        val y = KNEE + (1f - KNEE) * (over / (1f + over))
        return if (x < 0) -y else y
    }

    private companion object {
        const val HEAD_RADIUS = 0.0875 // m
        const val SOUND_SPEED = 343.0 // m/s
        const val ALPHA_MIN = 0.1
        const val THETA_MIN = 150.0
        const val AMBIENCE = 0.55f
        /** Ouvido do lado oposto: ~-9 dB (resposta dentro de ~3 dB de 60 Hz a 12 kHz). */
        const val CONTRA_GAIN = 0.35
        // Duas caixas somam no ouvido; este ganho mantém o volume parecido com o original.
        const val OUTPUT_GAIN = 0.74f
        const val KNEE = 0.9f
    }
}
