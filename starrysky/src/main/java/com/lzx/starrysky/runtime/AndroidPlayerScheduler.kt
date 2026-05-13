package com.lzx.starrysky.runtime

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * 默认主线程 / IO 线程调度实现。
 */
class AndroidPlayerScheduler : PlayerScheduler {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newCachedThreadPool()

    override fun runOnMain(action: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run()
        } else {
            mainHandler.post(action)
        }
    }

    override fun runOnIo(action: Runnable) {
        ioExecutor.execute(action)
    }
}
