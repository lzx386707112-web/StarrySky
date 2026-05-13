package com.lzx.starrysky.manager

import com.lzx.starrysky.SongInfo
import com.lzx.starrysky.playback.FocusInfo
import com.lzx.starrysky.playback.Playback

/**
 * 将 [Playback.Callback] 从 [PlaybackManager] 类体中拆出，使「引擎 → 协调层」回调入口集中在一处，便于阅读与单测替换。
 *
 * [PlaybackManager] 实现 [Host] 承载实际逻辑；本类仅做无分支转发。
 */
internal class PlaybackEngineCallbackBridge(
    private val host: Host
) : Playback.Callback {

    /**
     * 由 [PlaybackManager] 实现：处理 Exo 状态、错误、焦点及队列跳转指令。
     */
    interface Host {
        fun onEnginePlayerStateChanged(songInfo: SongInfo?, playWhenReady: Boolean, playbackState: Int)
        fun onEnginePlaybackError(songInfo: SongInfo?, error: String)
        fun onEngineFocusStateChange(info: FocusInfo)
        fun onEngineSkipToNext()
        fun onEngineSkipToPrevious()
    }

    override fun onPlayerStateChanged(songInfo: SongInfo?, playWhenReady: Boolean, playbackState: Int) {
        host.onEnginePlayerStateChanged(songInfo, playWhenReady, playbackState)
    }

    override fun onPlaybackError(songInfo: SongInfo?, error: String) {
        host.onEnginePlaybackError(songInfo, error)
    }

    override fun onFocusStateChange(info: FocusInfo) {
        host.onEngineFocusStateChange(info)
    }

    override fun skipToNext() {
        host.onEngineSkipToNext()
    }

    override fun skipToPrevious() {
        host.onEngineSkipToPrevious()
    }
}
