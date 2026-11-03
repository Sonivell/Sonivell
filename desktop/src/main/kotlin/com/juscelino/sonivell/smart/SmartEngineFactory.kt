package com.juscelino.sonivell.smart

import com.juscelino.sonivell.compat.Context

/** No PC o motor SMART vem sempre junto (o .deb e o AppImage trazem os modelos). */
@Suppress("UNUSED_PARAMETER")
fun createSmartEngine(context: Context): SmartEngine = LatentJamSmartEngine.get()
