package com.lzx.starrysky.service

import android.content.Context

/**
 * 「是否使用 Service」在结构上的落点：只负责**不经 Service** 时如何得到 [MusicPlaybackHost]。
 *
 * Service 场景下宿主由系统回调 [MusicService.onBind] 创建 [MusicServiceBinder]，不经过本工厂，
 * 从而保证 IBinder 类型与生命周期仍由 Framework 管理。
 */
object MusicRuntimeFactory {

    /**
     * 进程内直连运行时（connService(false)）：无 IPC、无前台 Service 义务，定时关闭无 Service 实现故为 no-op。
     */
    fun createInProcessHost(context: Context): MusicPlaybackHost {
        return MusicPlaybackFacade(context, TimedOffHandlerFactory.createForNonService())
    }
}
