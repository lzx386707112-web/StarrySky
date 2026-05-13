@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lzx.starrysky.playback

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.common.util.Util
import com.lzx.starrysky.StarrySky

/**
 * 焦点管理
 */
class FocusManager(val context: Context) {
    companion object {
        const val VOLUME_DUCK = 0.2f
        const val VOLUME_NORMAL = 1.0f

        /** 当前没有音频焦点. */
        const val STATE_NO_FOCUS = 0

        /** 所请求的音频焦点当前处于保持状态. */
        const val STATE_HAVE_FOCUS = 1

        /** 音频焦点已暂时丢失. */
        const val STATE_LOSS_TRANSIENT = 2

        /** 音频焦点已暂时丢失，但播放时音量可能会降低 */
        const val STATE_LOSS_TRANSIENT_DUCK = 3


        /** 不要播放 */
        const val DO_NOT_PLAY = -1

        /** 等待回调播放 */
        const val WAIT_FOR_CALLBACK = 0

        /** 可以播放 */
        const val PLAY_WHEN_READY = 1
    }

    private val audioAttributes: AudioAttributes = AudioAttributes.DEFAULT
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var focusListener: AudioFocusListener = AudioFocusListener()
    private var focusGain = 0
    private var rebuildAudioFocusRequest = false

    private var playerCommand = PLAY_WHEN_READY
    private var audioFocusState = STATE_NO_FOCUS
    private var volumeMultiplier = VOLUME_NORMAL

    var listener: OnFocusStateChangeListener? = null

    init {
        val applicationContext: Context = context.applicationContext
        audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
        focusGain = convertAudioAttributesToFocusGain(audioAttributes)
    }

    /**
     * @param playbackState 须为业务层 [Playback] 的状态常量（如 [Playback.STATE_IDLE]），须与
     * [com.lzx.starrysky.playback.ExoPlayback.playbackState] 的映射一致；**不要**传入
     * Media3 [androidx.media3.common.Player] 的 `playbackState` 原始值（与上述常量数值不完全对应）。
     */
    fun updateAudioFocus(playWhenReady: Boolean, playbackState: Int): Int {
        if (shouldAbandonAudioFocus(playbackState)) {
            abandonAudioFocus()
            return if (playWhenReady) PLAY_WHEN_READY else DO_NOT_PLAY
        }
        return if (playWhenReady) requestAudioFocus() else DO_NOT_PLAY
    }

    fun release() {
        abandonAudioFocus()
    }

    /**
     * 判断是否需要放弃焦点
     */
    private fun shouldAbandonAudioFocus(playbackState: Int): Boolean {
        return playbackState == Playback.STATE_IDLE || focusGain != AudioManager.AUDIOFOCUS_GAIN
    }

    /**
     * 请求焦点
     */
    private fun requestAudioFocus(): Int {
        if (audioFocusState == STATE_HAVE_FOCUS) {
            return PLAY_WHEN_READY
        }
        val requestResult: Int = if (Util.SDK_INT >= 26) requestAudioFocusV26() else requestAudioFocusDefault()
        return if (requestResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            setAudioFocusState(STATE_HAVE_FOCUS)
            PLAY_WHEN_READY
        } else {
            setAudioFocusState(STATE_NO_FOCUS)
            DO_NOT_PLAY
        }
    }

    /**
     * 放弃焦点
     */
    private fun abandonAudioFocus() {
        if (audioFocusState == STATE_NO_FOCUS) {
            return
        }
        if (Util.SDK_INT >= 26) {
            abandonAudioFocusV26()
        } else {
            abandonAudioFocusDefault()
        }
        setAudioFocusState(STATE_NO_FOCUS)
    }

    /**
     * 获取焦点具体实现
     */
    private fun requestAudioFocusDefault(): Int {
        return audioManager?.requestAudioFocus(
            focusListener,
            Util.getStreamTypeForAudioUsage(audioAttributes.usage),
            focusGain) ?: 0
    }

    /**
     * 获取焦点具体实现
     */
    @RequiresApi(26)
    private fun requestAudioFocusV26(): Int {
        if (audioFocusRequest == null || rebuildAudioFocusRequest) {
            val builder = if (audioFocusRequest == null) {
                AudioFocusRequest.Builder(focusGain)
            } else {
                AudioFocusRequest.Builder(audioFocusRequest!!)
            }
            val willPauseWhenDucked: Boolean = willPauseWhenDucked()
            audioFocusRequest = builder
                .setAudioAttributes(audioAttributes.getAudioAttributesV21().audioAttributes)
                .setWillPauseWhenDucked(willPauseWhenDucked)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            rebuildAudioFocusRequest = false
        }
        return audioManager?.requestAudioFocus(audioFocusRequest!!) ?: 0
    }

    /**
     * 放弃焦点具体实现
     */
    private fun abandonAudioFocusDefault() {
        audioManager?.abandonAudioFocus(focusListener)
    }

    /**
     * 放弃焦点具体实现
     */
    @RequiresApi(26)
    private fun abandonAudioFocusV26() {
        audioFocusRequest?.let {
            audioManager?.abandonAudioFocusRequest(it)
        }
    }

    private fun willPauseWhenDucked(): Boolean {
        return audioAttributes.contentType == C.CONTENT_TYPE_SPEECH
    }

    private fun convertAudioAttributesToFocusGain(audioAttributes: AudioAttributes?): Int {
        return if (audioAttributes == null) {
            0
        } else when (audioAttributes.usage) {
            C.USAGE_VOICE_COMMUNICATION_SIGNALLING -> 0
            C.USAGE_GAME, C.USAGE_MEDIA -> AudioManager.AUDIOFOCUS_GAIN
            C.USAGE_UNKNOWN -> AudioManager.AUDIOFOCUS_GAIN
            C.USAGE_ALARM, C.USAGE_VOICE_COMMUNICATION -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            C.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
            C.USAGE_ASSISTANCE_SONIFICATION,
            C.USAGE_NOTIFICATION,
            C.USAGE_NOTIFICATION_COMMUNICATION_DELAYED,
            C.USAGE_NOTIFICATION_COMMUNICATION_INSTANT,
            C.USAGE_NOTIFICATION_COMMUNICATION_REQUEST,
            C.USAGE_NOTIFICATION_EVENT,
            C.USAGE_NOTIFICATION_RINGTONE -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            C.USAGE_ASSISTANT -> if (Util.SDK_INT >= 19) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            C.USAGE_ASSISTANCE_ACCESSIBILITY -> {
                if (audioAttributes.contentType == C.CONTENT_TYPE_SPEECH) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            }
            else -> 0
        }
    }

    private fun setAudioFocusState(audioFocusState: Int) {
        if (this.audioFocusState == audioFocusState) {
            return
        }
        this.audioFocusState = audioFocusState
        val volumeMultiplier = if (audioFocusState == STATE_LOSS_TRANSIENT_DUCK) VOLUME_DUCK else VOLUME_NORMAL
        if (this.volumeMultiplier == volumeMultiplier) {
            return
        }
        this.volumeMultiplier = volumeMultiplier
    }

    private fun handlePlatformAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                setAudioFocusState(STATE_HAVE_FOCUS)
                playerCommand = PLAY_WHEN_READY
                listener?.focusStateChange(FocusInfo(null, audioFocusState, playerCommand, volumeMultiplier))
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                playerCommand = DO_NOT_PLAY
                abandonAudioFocus()
                listener?.focusStateChange(FocusInfo(null, audioFocusState, playerCommand, volumeMultiplier))
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    playerCommand = WAIT_FOR_CALLBACK
                    setAudioFocusState(STATE_LOSS_TRANSIENT)
                } else {
                    setAudioFocusState(STATE_LOSS_TRANSIENT_DUCK)
                }
                listener?.focusStateChange(FocusInfo(null, audioFocusState, playerCommand, volumeMultiplier))
            }
            else -> StarrySky.log("Unknown focus change type: $focusChange")
        }
    }

    private inner class AudioFocusListener : AudioManager.OnAudioFocusChangeListener {
        override fun onAudioFocusChange(focusChange: Int) {
            handlePlatformAudioFocusChange(focusChange)
        }
    }

    interface OnFocusStateChangeListener {
        fun focusStateChange(info: FocusInfo)
    }
}