@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lzx.starrysky.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import com.lzx.starrysky.SongInfo
import com.lzx.starrysky.StarrySky
import com.lzx.starrysky.cache.ExoCache
import com.lzx.starrysky.cache.ICache
import com.lzx.starrysky.playback.Playback.Companion.STATE_BUFFERING
import com.lzx.starrysky.playback.Playback.Companion.STATE_ERROR
import com.lzx.starrysky.playback.Playback.Companion.STATE_IDLE
import com.lzx.starrysky.playback.Playback.Companion.STATE_PAUSED
import com.lzx.starrysky.playback.Playback.Companion.STATE_PLAYING
import com.lzx.starrysky.utils.isRTMP
import com.lzx.starrysky.utils.orDef

/**
 * 基于 Media3 [ExoPlayer] 的实现。
 *
 * 主路径使用官方推荐的 [MediaItem] + [DefaultMediaSourceFactory]（由 [ExoPlayer.Builder.setMediaSourceFactory] 注入），
 * 由 Media3 根据 URI 选择 DASH / HLS / SS / 渐进式 等实现，避免手写各类型 [androidx.media3.exoplayer.source.MediaSource]。
 * `rtmp://` 仍使用 [RtmpDataSource] + [ProgressiveMediaSource]，因默认工厂不处理 RTMP。
 *
 * @param isAutoManagerFocus 是否让播放器自动管理焦点
 */
class ExoPlayback(
    val context: Context,
    private val cache: ICache?,
    private val isAutoManagerFocus: Boolean
) : Playback, FocusManager.OnFocusStateChangeListener {

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var trackSelectorParameters: DefaultTrackSelector.Parameters? = null

    private var currSongInfo: SongInfo? = null
    private var callback: Playback.Callback? = null
    private val eventListener by lazy { ExoPlayerEventListener() }
    private var sourceTypeErrorInfo: SourceTypeErrorInfo = SourceTypeErrorInfo()
    private var focusManager = FocusManager(context)
    private var hasError = false

    init {
        focusManager.listener = this
    }

    override fun playbackState(): Int {
        return if (player == null) {
            STATE_IDLE
        } else {
            when (player?.playbackState) {
                Player.STATE_IDLE -> STATE_IDLE
                Player.STATE_BUFFERING -> STATE_BUFFERING
                Player.STATE_READY -> {
                    if (player?.playWhenReady == true) STATE_PLAYING else STATE_PAUSED
                }
                Player.STATE_ENDED -> STATE_IDLE
                else -> STATE_IDLE
            }
        }
    }

    override fun isPlaying(): Boolean = player?.playWhenReady == true

    override fun currentStreamPosition(): Long = player?.currentPosition.orDef()

    override fun bufferedPosition(): Long = player?.bufferedPosition.orDef()

    override fun duration(): Long = if (player?.duration.orDef() > 0) player?.duration.orDef() else 0

    override var currentMediaId: String = ""

    override fun setVolume(audioVolume: Float) {
        var volume = audioVolume
        if (volume < 0) {
            volume = 0f
        }
        if (volume > 1) {
            volume = 1f
        }
        player?.volume = volume
    }

    override fun getVolume(): Float = player?.volume ?: -1f

    override fun getCurrPlayInfo(): SongInfo? = currSongInfo

    override fun getAudioSessionId(): Int = player?.audioSessionId ?: 0

    private fun getPlayWhenReady() = player?.playWhenReady ?: false

    /**
     * 与 [FocusManager.updateAudioFocus] 约定：第二参数须为 StarrySky [Playback] 的状态常量
     *（与 [playbackState] 返回值一致），不能传 Media3 [Player] 的 playbackState 数值
     *（例如 [Player.STATE_ENDED] 与 [Playback.STATE_PAUSED] 均为 4，语义不同）。
     */
    private fun updateManualAudioFocus(playbackStateForFocus: Int = playbackState()) {
        if (!isAutoManagerFocus) {
            focusManager.updateAudioFocus(getPlayWhenReady(), playbackStateForFocus)
        }
    }

    override fun play(songInfo: SongInfo, isPlayWhenReady: Boolean) {
        val mediaId = songInfo.songId
        if (mediaId.isEmpty()) {
            return
        }
        currSongInfo = songInfo
        val mediaHasChanged = mediaId != currentMediaId
        if (mediaHasChanged) {
            currentMediaId = mediaId
        }
        StarrySky.log(
            "title = " + songInfo.songName +
                " \n音频是否有改变 = " + mediaHasChanged +
                " \n是否立即播放 = " + isPlayWhenReady +
                " \nurl = " + songInfo.songUrl
        )

        var source = songInfo.songUrl
        if (source.isEmpty()) {
            callback?.onPlaybackError(currSongInfo, "播放 url 为空")
            return
        }
        source = source.replace(" ".toRegex(), "%20")
        val proxyUrl = cache?.getProxyUrl(source, songInfo)
        source = if (proxyUrl.isNullOrEmpty()) source else proxyUrl
        val uri = Uri.parse(source)

        val needReload =
            mediaHasChanged || player == null || (sourceTypeErrorInfo.happenSourceError && !mediaHasChanged)

        if (mediaHasChanged || player == null) {
            createExoPlayer()
        }

        if (needReload) {
            runCatching {
                if (source.isRTMP()) {
                    val rtmpSource = ProgressiveMediaSource.Factory(RtmpDataSource.Factory())
                        .createMediaSource(MediaItem.fromUri(uri))
                    player?.setMediaSource(rtmpSource, /* resetPosition = */ true)
                } else {
                    player?.setMediaItem(MediaItem.fromUri(uri), /* resetPosition = */ true)
                }
                player?.prepare()
            }.onFailure { e ->
                callback?.onPlaybackError(currSongInfo, "无法加载媒体: ${e.message ?: e.javaClass.simpleName}")
                return
            }
            updateManualAudioFocus(STATE_BUFFERING)
            if (sourceTypeErrorInfo.happenSourceError && !mediaHasChanged) {
                if (sourceTypeErrorInfo.currPositionWhenError != 0L) {
                    if (sourceTypeErrorInfo.seekToPositionWhenError != 0L) {
                        player?.seekTo(sourceTypeErrorInfo.seekToPositionWhenError)
                    } else {
                        player?.seekTo(sourceTypeErrorInfo.currPositionWhenError)
                    }
                }
            }
        }
        StarrySky.log("isPlayWhenReady = $isPlayWhenReady")
        StarrySky.log("---------------------------------------")
        if (isPlayWhenReady) {
            player?.playWhenReady = true
            hasError = false
            updateManualAudioFocus()
        }
    }

    @Synchronized
    private fun createExoPlayer() {
        if (player == null) {
            val extensionRendererMode = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            val renderersFactory = DefaultRenderersFactory(context)
                .setExtensionRendererMode(extensionRendererMode)

            trackSelectorParameters = DefaultTrackSelector.Parameters.Builder(context).build()
            trackSelector = DefaultTrackSelector(context)
            trackSelector?.parameters = trackSelectorParameters!!

            val mediaSourceFactory = DefaultMediaSourceFactory(
                buildPrimaryDataSourceFactory(),
                DefaultExtractorsFactory()
            )

            player = ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector!!)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()

            player?.addListener(eventListener)
            player?.setAudioAttributes(AudioAttributes.DEFAULT, isAutoManagerFocus)
            updateManualAudioFocus()
        }
    }

    /**
     * 供 [DefaultMediaSourceFactory] 使用的上游 [DataSource.Factory]；在开启 [ExoCache] 时包一层 [CacheDataSource]。
     * 与原先「仅非流媒体走缓存」相比，此处对 HLS/DASH 等也可走缓存（Media3 常规用法）；RTMP 仍走独立分支。
     */
    @Synchronized
    private fun buildPrimaryDataSourceFactory(): DataSource.Factory {
        val userAgent = Util.getUserAgent(context, "StarrySky")
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setAllowCrossProtocolRedirects(true)
        val upstream = DefaultDataSource.Factory(context, httpDataSourceFactory)
        return if (cache?.isOpenCache() == true && cache is ExoCache) {
            buildCacheDataSource(upstream, cache.getDownloadCache()) ?: upstream
        } else {
            upstream
        }
    }

    @Synchronized
    private fun buildCacheDataSource(upstreamFactory: DataSource.Factory?, cache: Cache?): CacheDataSource.Factory? {
        return cache?.let {
            CacheDataSource.Factory()
                .setCache(it)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
    }

    override fun stop() {
        player?.removeListener(eventListener)
        player?.stop()
        player?.release()
        player = null
        if (!isAutoManagerFocus) {
            focusManager.release()
        }
    }

    override fun pause() {
        player?.playWhenReady = false
        updateManualAudioFocus()
    }

    override fun seekTo(position: Long) {
        player?.seekTo(position)
        sourceTypeErrorInfo.seekToPosition = position
        if (sourceTypeErrorInfo.happenSourceError) {
            sourceTypeErrorInfo.seekToPositionWhenError = position
        }
    }

    override fun onFastForward(speed: Float) {
        player?.let {
            val currSpeed = it.playbackParameters.speed
            val currPitch = it.playbackParameters.pitch
            val newSpeed = currSpeed + speed
            it.playbackParameters = PlaybackParameters(newSpeed, currPitch)
        }
    }

    override fun onRewind(speed: Float) {
        player?.let {
            val currSpeed = it.playbackParameters.speed
            val currPitch = it.playbackParameters.pitch
            var newSpeed = currSpeed - speed
            if (newSpeed <= 0) {
                newSpeed = 0f
            }
            it.playbackParameters = PlaybackParameters(newSpeed, currPitch)
        }
    }

    override fun onDerailleur(refer: Boolean, multiple: Float) {
        player?.let {
            val currSpeed = it.playbackParameters.speed
            val currPitch = it.playbackParameters.pitch
            val newSpeed = if (refer) currSpeed * multiple else multiple
            if (newSpeed > 0) {
                it.playbackParameters = PlaybackParameters(newSpeed, currPitch)
            }
        }
    }

    override fun getPlaybackSpeed(): Float {
        return player?.playbackParameters?.speed ?: 1.0f
    }

    override fun skipToNext() {
        callback?.skipToNext()
    }

    override fun skipToPrevious() {
        callback?.skipToPrevious()
    }

    override fun setCallback(callback: Playback.Callback?) {
        this.callback = callback
    }

    private inner class ExoPlayerEventListener : Player.Listener {

        private fun dispatchPlaybackStateChanged() {
            val playbackState = player?.playbackState ?: Player.STATE_IDLE
            val playWhenReady = player?.playWhenReady == true
            var newState = STATE_IDLE
            when (playbackState) {
                Player.STATE_IDLE -> {
                    newState = if (hasError) STATE_ERROR else STATE_IDLE
                }
                Player.STATE_READY -> {
                    newState = if (player?.playWhenReady == true) STATE_PLAYING else STATE_PAUSED
                }
                Player.STATE_ENDED -> newState = STATE_IDLE
                Player.STATE_BUFFERING -> newState = STATE_BUFFERING
            }
            if (!hasError) {
                callback?.onPlayerStateChanged(currSongInfo, playWhenReady, newState)
            }
            if (playbackState == Player.STATE_READY) {
                sourceTypeErrorInfo.clear()
            }
            if (playbackState == Player.STATE_IDLE) {
                currentMediaId = ""
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            dispatchPlaybackStateChanged()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            dispatchPlaybackStateChanged()
        }

        override fun onPlayerError(error: PlaybackException) {
            error.printStackTrace()
            hasError = true
            val what = error.message ?: "errorCode=${error.errorCode}"
            val sourceLike = error.cause is java.io.IOException ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
            if (sourceLike) {
                sourceTypeErrorInfo.happenSourceError = true
                sourceTypeErrorInfo.seekToPositionWhenError = sourceTypeErrorInfo.seekToPosition
                sourceTypeErrorInfo.currPositionWhenError = currentStreamPosition()
            }
            callback?.onPlaybackError(currSongInfo, "ExoPlayer error $what")
        }
    }

    override fun focusStateChange(info: FocusInfo) {
        if (isAutoManagerFocus) {
            return
        }
        callback?.onFocusStateChange(FocusInfo(currSongInfo, info.audioFocusState, info.playerCommand, info.volume))
    }
}

/**
 * 发生错误时保存的信息
 */
class SourceTypeErrorInfo {
    var seekToPosition = 0L
    var happenSourceError = false
    var seekToPositionWhenError = 0L
    var currPositionWhenError = 0L

    fun clear() {
        happenSourceError = false
        seekToPosition = 0L
        seekToPositionWhenError = 0L
        currPositionWhenError = 0L
    }
}
