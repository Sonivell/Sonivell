package com.juscelino.sonivell.smart

import android.content.Context

/** Edição smart: um único motor por processo (os modelos ocupam memória e carregam devagar). */
fun createSmartEngine(context: Context): SmartEngine = LatentJamSmartEngine.get(context)
