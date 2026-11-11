package com.juscelino.sonivell.smart

import android.content.Context

/** Edição lite: sem motor SMART. */
@Suppress("UNUSED_PARAMETER")
fun createSmartEngine(context: Context): SmartEngine = NoSmartEngine
