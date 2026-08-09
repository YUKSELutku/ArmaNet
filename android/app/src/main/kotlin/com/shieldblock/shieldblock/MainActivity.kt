package com.shieldblock.shieldblock

import android.content.Intent
import com.shieldblock.shieldblock.bridge.FlutterBridge
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {
    private var flutterBridge: FlutterBridge? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        flutterBridge = FlutterBridge(
            activity = this,
            messenger = flutterEngine.dartExecutor.binaryMessenger,
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (flutterBridge?.handleActivityResult(requestCode, resultCode) == true) return
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        if (flutterBridge?.handleRequestPermissionsResult(requestCode, grantResults) == true) return
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onDestroy() {
        flutterBridge?.dispose()
        flutterBridge = null
        super.onDestroy()
    }
}
