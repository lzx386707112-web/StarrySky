package com.lzx.starrysky.service

import android.content.Context

/**
 * 定时关闭播放：实现与宿主解耦，避免在播放内核里写 `context is MusicService`。
 */
fun interface TimedOffHandler {

    /**
     * @param time 毫秒；为 0 表示取消定时任务（与原有 Service 行为一致）。
     */
    fun onStopByTimedOff(time: Long, pause: Boolean, finishCurrSong: Boolean)
}

/**
 * 根据 [Context] 选择定时关闭策略：仅 [MusicService] 具备计时与任务实现，其余上下文不处理。
 */
object TimedOffHandlerFactory {

    /**
     * Service 内使用的 Binder / Facade 应传入 Service 自身作为 Context，以便定时逻辑落在 Service。
     */
    fun create(context: Context): TimedOffHandler {
        return if (context is MusicService) {
            ServiceTimedOffHandler(context)
        } else {
            NoOpTimedOffHandler
        }
    }

    /**
     * 不经 Service 初始化（如 Application）时显式使用，语义与 [create] 在非 Service 下一致。
     */
    fun createForNonService(): TimedOffHandler = NoOpTimedOffHandler
}

private class ServiceTimedOffHandler(
    private val musicService: MusicService
) : TimedOffHandler {

    override fun onStopByTimedOff(time: Long, pause: Boolean, finishCurrSong: Boolean) {
        musicService.onStopByTimedOffImpl(time, pause, finishCurrSong)
    }
}

private object NoOpTimedOffHandler : TimedOffHandler {
    override fun onStopByTimedOff(time: Long, pause: Boolean, finishCurrSong: Boolean) = Unit
}
