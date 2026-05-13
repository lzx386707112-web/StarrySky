package com.lzx.starrysky.runtime

/**
 * 可插拔线程调度：替代分散的 MainLooper / AsyncTask 调用，便于测试与替换。
 */
interface PlayerScheduler {

    fun runOnMain(action: Runnable)

    fun runOnIo(action: Runnable)
}
