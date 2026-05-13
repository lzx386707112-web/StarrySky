package com.lzx.starrysky.runtime

import android.util.Log
import com.lzx.starrysky.StarrySkyInstall

/**
 * 组合根：集中提供可替换的运行时依赖（调度、日志等）。
 */
object StarrySkyRuntime {

    @JvmField
    var scheduler: PlayerScheduler = AndroidPlayerScheduler()

    @JvmField
    var logger: StarrySkyLogger = object : StarrySkyLogger {
        override fun i(tag: String, message: String) {
            if (StarrySkyInstall.isDebug) {
                Log.i(tag, message)
            }
        }
    }
}
