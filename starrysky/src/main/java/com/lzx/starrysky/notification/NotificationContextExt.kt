package com.lzx.starrysky.notification

import android.content.Context
import com.lzx.starrysky.playback.Playback
import com.lzx.starrysky.service.MusicService

/**
 * 通知实现约定由 [MusicService] 作为 Context；用可空转换避免误用 Context 时的 ClassCastException。
 */
internal fun Context?.playbackFromMusicService(): Playback? =
    (this as? MusicService)?.binder?.player

internal fun Context.musicServiceOrNull(): MusicService? = this as? MusicService
