package com.lzx.starrysky.core.prepare

import com.lzx.starrysky.SongInfo
import com.lzx.starrysky.intercept.InterceptCallback
import com.lzx.starrysky.intercept.InterceptorThread
import com.lzx.starrysky.intercept.StarrySkyInterceptor
import com.lzx.starrysky.runtime.PlayerScheduler

/**
 * 播放前拦截链：与旧 [com.lzx.starrysky.intercept.InterceptorService] 行为一致，
 * 通过 [PlayerScheduler] 调度 UI / IO，避免使用已废弃的 AsyncTask。
 *
 * 后续可扩展为通用 [PrepareStage] 管道，无需改动调用方 [PlaybackManager]。
 */
class InterceptorPrepareChain(
    private val scheduler: PlayerScheduler
) {

    private val interceptors = mutableListOf<Pair<StarrySkyInterceptor, String>>()

    fun attachInterceptors(interceptors: MutableList<Pair<StarrySkyInterceptor, String>>) {
        this.interceptors.clear()
        this.interceptors.addAll(interceptors)
    }

    fun handlerInterceptor(songInfo: SongInfo?, callback: InterceptCallback?) {
        if (interceptors.isEmpty()) {
            callback?.onNext(songInfo)
            return
        }
        runCatching {
            doInterceptor(0, songInfo, callback)
        }.onFailure {
            scheduler.runOnMain(Runnable { callback?.onInterrupt(it.message) })
        }
    }

    private fun doInterceptor(index: Int, songInfo: SongInfo?, callback: InterceptCallback?) {
        if (index < interceptors.size) {
            val pair = interceptors[index]
            val interceptor = pair.first
            val interceptThread = pair.second
            if (interceptThread == InterceptorThread.UI) {
                scheduler.runOnMain(Runnable {
                    doInterceptImpl(interceptor, index, songInfo, callback)
                })
            } else {
                scheduler.runOnIo(Runnable {
                    doInterceptImpl(interceptor, index, songInfo, callback)
                })
            }
        } else {
            scheduler.runOnMain(Runnable { callback?.onNext(songInfo) })
        }
    }

    private fun doInterceptImpl(
        interceptor: StarrySkyInterceptor,
        index: Int,
        songInfo: SongInfo?,
        callback: InterceptCallback?
    ) {
        interceptor.process(songInfo, object : InterceptCallback {
            override fun onNext(songInfo: SongInfo?) {
                doInterceptor(index + 1, songInfo, callback)
            }

            override fun onInterrupt(msg: String?) {
                scheduler.runOnMain(Runnable { callback?.onInterrupt(msg) })
            }
        })
    }
}
