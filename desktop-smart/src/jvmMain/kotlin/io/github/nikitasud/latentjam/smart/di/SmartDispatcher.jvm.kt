package io.github.nikitasud.latentjam.smart.di

import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

internal actual fun createPlatformSmartEngineDispatcher(): CoroutineDispatcher =
    lowPrioritySingleThreadDispatcher("smart-engine")

internal actual fun createPlatformMapLayoutDispatcher(): CoroutineDispatcher =
    lowPrioritySingleThreadDispatcher("smart-map-layout")

private fun lowPrioritySingleThreadDispatcher(name: String): CoroutineDispatcher =
    Executors.newSingleThreadExecutor { task ->
        Thread(task, name).apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }.asCoroutineDispatcher()
