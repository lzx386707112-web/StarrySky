package com.lzx.starrysky.service

import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import com.lzx.starrysky.SongInfo
import com.lzx.starrysky.cache.ExoCache
import com.lzx.starrysky.cache.ICache
import com.lzx.starrysky.manager.PlaybackStage
import com.lzx.starrysky.manager.changePlaybackState
import com.lzx.starrysky.notification.INotification
import com.lzx.starrysky.notification.NotificationConfig
import com.lzx.starrysky.notification.NotificationManager
import com.lzx.starrysky.playback.ExoPlayback
import com.lzx.starrysky.playback.Playback
import com.lzx.starrysky.playback.SoundPoolPlayback

/**
 * 播放运行时的门面实现：聚合播放器、通知、缓存与 WiFi 锁等与「播」直接相关的协作逻辑。
 *
 * - 与 [android.os.Binder] 无关，可在 Application 进程内单例持有（connService(false)）。
 * - 与 [MusicService] 的定时关闭等差异通过 [TimedOffHandler] 注入，避免在此处判断 Context 类型。
 */
class MusicPlaybackFacade(
    private val context: Context,
    private val timedOffHandler: TimedOffHandler
) : MusicPlaybackHost {

    override var player: Playback? = null
    override var soundPool: SoundPoolPlayback? = null

    private var isOpenNotification: Boolean = false
    private var notificationType: Int = INotification.SYSTEM_NOTIFICATION
    private var notificationConfig: NotificationConfig? = null
    private val notificationManager = NotificationManager()
    override var notification: INotification? = null
    private var isShowNotification = false
    private var notificationFactory: NotificationManager.NotificationFactory? = null
    private var playerCache: ICache? = null
    private var cacheDestFileDir: String = ""
    private var cacheMaxBytes: Long = 512 * 1024 * 1024
    private var isAutoManagerFocus: Boolean = true
    private var isStartForegroundByWorkManager = false

    override fun setNotificationConfig(
        openNotification: Boolean,
        notificationType: Int,
        notificationConfig: NotificationConfig?,
        notificationFactory: NotificationManager.NotificationFactory?
    ) {
        this.isOpenNotification = openNotification
        this.notificationType = notificationType
        this.notificationConfig = notificationConfig
        this.notificationFactory = notificationFactory
        if (isOpenNotification) {
            createNotification()
        }
    }

    override fun setIsOpenNotification(open: Boolean) {
        isOpenNotification = open
    }

    /**
     * 按当前 [notificationType] 与工厂配置构建 [INotification] 实例；仅在通知开关开启时由配置入口调用。
     */
    private fun createNotification() {
        notification = if (notificationType == INotification.SYSTEM_NOTIFICATION) {
            notificationManager.getSystemNotification(context, notificationConfig)
        } else {
            if (this.notificationFactory != null) {
                this.notificationFactory?.build(context, notificationConfig)
            } else {
                notificationManager.getCustomNotification(context, notificationConfig)
            }
        }
    }

    override fun changeNotification(notificationType: Int) {
        if (!isOpenNotification) return
        if (this.notificationType == notificationType) return
        notification?.stopNotification()
        this.notificationType = notificationType
        createNotification()
        player?.let {
            notification?.startNotification(it.getCurrPlayInfo(), it.playbackState().changePlaybackState())
        }
    }

    override fun getNotificationType() = notificationType

    override fun onChangedNotificationState(
        songInfo: SongInfo?, playbackState: String,
        hasNextSong: Boolean, hasPreSong: Boolean
    ) {
        if (isOpenNotification) {
            notification?.onPlaybackStateChanged(songInfo, playbackState, hasNextSong, hasPreSong)
            isShowNotification = true
        }
    }

    override fun openNotification() {
        val info = player?.getCurrPlayInfo()
        val state = player?.playbackState()?.changePlaybackState() ?: PlaybackStage.IDLE
        startNotification(info, state)
    }

    override fun startNotification(currPlayInfo: SongInfo?, state: String) {
        if (isOpenNotification) {
            notification?.startNotification(currPlayInfo, state)
            isShowNotification = true
        }

        if (state == PlaybackStage.IDLE) {
            WifiLockHelper.release()
        } else {
            WifiLockHelper.acquire(context)
        }
    }

    override fun stopNotification() {
        if (isOpenNotification) {
            notification?.stopNotification()
            isShowNotification = false
        }
    }

    override fun setSessionToken(mediaSession: MediaSessionCompat.Token?) {
        if (isOpenNotification) {
            notification?.setSessionToken(mediaSession)
        }
    }

    override fun setPlayerCache(cache: ICache?, cacheDestFileDir: String, cacheMaxBytes: Long) {
        playerCache = cache
        this.cacheDestFileDir = cacheDestFileDir
        this.cacheMaxBytes = cacheMaxBytes
    }

    override fun setAutoManagerFocus(isAutoManagerFocus: Boolean) {
        this.isAutoManagerFocus = isAutoManagerFocus
    }

    override fun initPlaybackManager(playback: Playback?) {
        if (playerCache == null) {
            playerCache = ExoCache(context, cacheDestFileDir, cacheMaxBytes)
        }
        player = playback ?: ExoPlayback(context, playerCache, isAutoManagerFocus)
        soundPool = SoundPoolPlayback(context)
    }

    override fun getPlayerCache(): ICache? = playerCache

    override fun onStopByTimedOff(time: Long, pause: Boolean, finishCurrSong: Boolean) {
        timedOffHandler.onStopByTimedOff(time, pause, finishCurrSong)
    }

    override fun isStartForegroundByWorkManager() = isStartForegroundByWorkManager

    override fun startForegroundByWorkManager(value: Boolean) {
        isStartForegroundByWorkManager = value
    }

    override fun consumeTimedOffAfterSongEndIdle(): Boolean {
        val svc = context as? MusicService ?: return false
        return svc.consumeTimedOffAfterSongEndIdle()
    }
}
