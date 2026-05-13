package com.lzx.starrysky.intercept

import com.lzx.starrysky.SongInfo

object InterceptorThread {
    const val UI = "UI"
    const val IO = "IO"
}

abstract class StarrySkyInterceptor {
    abstract fun getTag(): String
    open fun process(songInfo: SongInfo?, callback: InterceptCallback) {}
}

interface InterceptCallback {
    fun onNext(songInfo: SongInfo?)

    fun onInterrupt(msg: String?)
}
