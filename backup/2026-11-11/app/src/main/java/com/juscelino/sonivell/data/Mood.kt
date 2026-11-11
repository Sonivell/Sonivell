package com.juscelino.sonivell.data

import androidx.annotation.StringRes
import com.juscelino.sonivell.R

/**
 * Mixes por clima ("Para o momento"). Cada um é uma busca por descrição no SMART: o motor
 * compara o texto com o som das faixas, então funciona mesmo sem gênero nas tags.
 * As descrições ficam em inglês porque é a língua em que o modelo de texto foi treinado.
 */
enum class Mood(@StringRes val label: Int, val query: String, val color: Long) {
    Calmo(R.string.mood_calm, "calm relaxing soft acoustic music", 0xFF2F4A44),
    Animado(R.string.mood_upbeat, "happy upbeat energetic dance music", 0xFF5A3A1E),
    Foco(R.string.mood_focus, "instrumental ambient music for concentration", 0xFF2C3550),
    Treino(R.string.mood_workout, "intense high energy workout music with strong beat", 0xFF5A2328),
    Noite(R.string.mood_night, "slow night chill dreamy music", 0xFF2A2440),
    Nostalgia(R.string.mood_nostalgia, "nostalgic retro classic songs", 0xFF4A4030),
}
