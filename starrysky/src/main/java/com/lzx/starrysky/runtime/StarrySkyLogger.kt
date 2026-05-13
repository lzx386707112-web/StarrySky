package com.lzx.starrysky.runtime

/**
 * 可插拔日志，后续可接入宿主埋点或关闭输出。
 */
interface StarrySkyLogger {

    fun i(tag: String, message: String)
}
