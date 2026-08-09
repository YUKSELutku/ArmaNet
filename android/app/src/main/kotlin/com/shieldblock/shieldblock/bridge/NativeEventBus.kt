package com.shieldblock.shieldblock.bridge

import java.util.concurrent.CopyOnWriteArraySet

object NativeEventBus {
    private val listeners = CopyOnWriteArraySet<(Map<String, Any?>) -> Unit>()

    fun subscribe(listener: (Map<String, Any?>) -> Unit) {
        listeners += listener
    }

    fun unsubscribe(listener: (Map<String, Any?>) -> Unit) {
        listeners -= listener
    }

    fun emit(type: String, payload: Map<String, Any?> = emptyMap()) {
        val event = linkedMapOf<String, Any?>(
            "type" to type,
            "timestamp" to System.currentTimeMillis(),
        ).apply { putAll(payload) }
        listeners.forEach { listener -> runCatching { listener(event) } }
    }
}
