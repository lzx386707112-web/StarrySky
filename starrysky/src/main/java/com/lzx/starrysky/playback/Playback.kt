package com.lzx.starrysky.playback

import com.lzx.starrysky.SongInfo

/**
 * 播放器接口，如果要实现其他播放器，实现该接口即可
 */
interface Playback {

    companion object {
        const val STATE_IDLE = 1       //空闲(默认状态，播放完成，停止播放后 也会回调)
        const val STATE_BUFFERING = 2  //正在缓冲
        const val STATE_PLAYING = 3    //正在播放
        const val STATE_PAUSED = 4     //暂停
        const val STATE_ERROR = 6      //出错
    }

    fun playbackState(): Int

    fun isPlaying(): Boolean

    fun currentStreamPosition(): Long

    fun bufferedPosition(): Long

    fun duration(): Long

    var currentMediaId: String

    fun setVolume(volume: Float)

    fun getVolume(): Float

    fun getCurrPlayInfo(): SongInfo?

    fun getAudioSessionId(): Int

    fun stop()

    fun play(songInfo: SongInfo, isPlayWhenReady: Boolean)

    fun pause()

    fun seekTo(position: Long)

    fun onFastForward(speed: Float)

    fun onRewind(speed: Float)

    fun onDerailleur(refer: Boolean, multiple: Float)

    fun getPlaybackSpeed(): Float

    fun skipToNext()

    fun skipToPrevious()

    interface Callback {
        fun onPlayerStateChanged(songInfo: SongInfo?, playWhenReady: Boolean, playbackState: Int)

        fun onPlaybackError(songInfo: SongInfo?, error: String)

        fun onFocusStateChange(info: FocusInfo)

        fun skipToNext()

        fun skipToPrevious()
    }

    fun setCallback(callback: Callback?)
}

/**
 *  songInfo : 当前播放的音频信息
 *
 *  audioFocusState：焦点状态，4 个值：
 *  STATE_NO_FOCUS            -> 当前没有音频焦点
 *  STATE_HAVE_FOCUS          -> 所请求的音频焦点当前处于保持状态
 *  STATE_LOSS_TRANSIENT      -> 音频焦点已暂时丢失
 *  STATE_LOSS_TRANSIENT_DUCK -> 音频焦点已暂时丢失，但播放时音量可能会降低
 *
 *  playerCommand：播放指令，3 个值：
 *  DO_NOT_PLAY       -> 不要播放
 *  WAIT_FOR_CALLBACK -> 等待回调播放
 *  PLAY_WHEN_READY   -> 可以播放
 *
 *  volume：焦点变化后推荐设置的音量，两个值：
 *  VOLUME_DUCK   -> 0.2f
 *  VOLUME_NORMAL ->  1.0f
 */
data class FocusInfo(var songInfo: SongInfo?, var audioFocusState: Int, var playerCommand: Int, var volume: Float)
