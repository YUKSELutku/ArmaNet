package com.shieldblock.shieldblock.bridge

import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.EventChannel

class BridgeEventStream : EventChannel.StreamHandler {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listener: (Map<String, Any?>) -> Unit = { event -> emit(event) }

    @Volatile
    private var eventSink: EventChannel.EventSink? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
        NativeEventBus.subscribe(listener)
    }

    override fun onCancel(arguments: Any?) {
        NativeEventBus.unsubscribe(listener)
        eventSink = null
    }

    fun emit(event: Map<String, Any?>) {
        mainHandler.post { eventSink?.success(event) }
    }

    fun clear() {
        NativeEventBus.unsubscribe(listener)
        eventSink = null
    }
}
