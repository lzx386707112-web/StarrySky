package com.lzx.starrysky.service

import android.content.Context
import android.os.Binder

/**
 * Service 绑定返回的 IBinder：只做 IPC 壳，全部能力委托给 [MusicPlaybackHost] 实现（默认 [MusicPlaybackFacade]）。
 *
 * 与「connService(false) 时直接使用 [MusicPlaybackFacade]」形成对照，对应 Strategy 的两条实现路径。
 */
class MusicServiceBinder private constructor(
    host: MusicPlaybackHost
) : Binder(), MusicPlaybackHost by host {

    constructor(context: Context) : this(
        MusicPlaybackFacade(context, TimedOffHandlerFactory.create(context))
    )
}
