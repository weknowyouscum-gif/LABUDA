package com.labuda.app

import android.content.Context
import android.util.Log
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicBoolean

class XrayCoreBridge(private val context: Context) {
    private var controller: CoreController? = null
    private val initialized = AtomicBoolean(false)

    private val callback = object : CoreCallbackHandler {
        override fun startup(): Long { Log.i(TAG, "Xray started"); return 0L }
        override fun shutdown(): Long { Log.i(TAG, "Xray stopped"); return 0L }
        override fun onEmitStatus(code: Long, message: String): Long {
            Log.i(TAG, "Xray[$code] $message")
            return 0L
        }
    }

    fun start(config: String, tunFd: Int): Result<Unit> = runCatching {
        if (initialized.compareAndSet(false, true)) {
            Seq.setContext(context.applicationContext)
            Libv2ray.initCoreEnv(context.filesDir.absolutePath, "labuda")
            controller = Libv2ray.newCoreController(callback)
        }
        controller?.startLoop(config, tunFd) ?: error("Xray controller is not initialized")
    }

    fun stop() {
        runCatching { controller?.stopLoop() }
        controller = null
        initialized.set(false)
    }

    fun isRunning(): Boolean = controller?.isRunning == true

    companion object { private const val TAG = "LABUDA-XRAY" }
}
