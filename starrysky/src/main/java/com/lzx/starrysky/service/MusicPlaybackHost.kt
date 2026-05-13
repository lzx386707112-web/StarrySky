package com.lzx.starrysky.service

import android.support.v4.media.session.MediaSessionCompat
import com.lzx.starrysky.SongInfo
import com.lzx.starrysky.cache.ICache
import com.lzx.starrysky.notification.INotification
import com.lzx.starrysky.notification.NotificationConfig
import com.lzx.starrysky.notification.NotificationManager
import com.lzx.starrysky.playback.Playback
import com.lzx.starrysky.playback.SoundPoolPlayback

/**
 * 播放「运行时」对外的统一契约：无论走 Service 绑定还是进程内直连，上层只依赖本接口。
 *
 * 对应 Facade 背后的实现类 [MusicPlaybackFacade]；Service 场景下由 [MusicServiceBinder] 包装后通过 IPC 返回。
 */
interface MusicPlaybackHost {

    var player: Playback?
    var soundPool: SoundPoolPlayback?
    var notification: INotification?

    fun setNotificationConfig(
        openNotification: Boolean,
        notificationType: Int,
        notificationConfig: NotificationConfig?,
        notificationFactory: NotificationManager.NotificationFactory?
    )

    fun setIsOpenNotification(open: Boolean)

    fun changeNotification(notificationType: Int)

    fun getNotificationType(): Int

    /**
     * 播放状态变化时驱动通知栏 UI（按钮可用性、文案等），不负责启动前台。
     */
    fun onChangedNotificationState(
        songInfo: SongInfo?,
        playbackState: String,
        hasNextSong: Boolean,
        hasPreSong: Boolean
    )

    fun openNotification()

    /**
     * 根据业务状态启动或刷新通知；并在非 IDLE 时持有 WiFi 锁、IDLE 时释放。
     */
    fun startNotification(currPlayInfo: SongInfo?, state: String)

    fun stopNotification()

    fun setSessionToken(mediaSession: MediaSessionCompat.Token?)

    fun setPlayerCache(cache: ICache?, cacheDestFileDir: String, cacheMaxBytes: Long)

    fun setAutoManagerFocus(isAutoManagerFocus: Boolean)

    /**
     * 创建 [Playback] / [SoundPoolPlayback]；若未注入缓存则在此按配置创建默认缓存。
     */
    fun initPlaybackManager(playback: Playback?)

    fun getPlayerCache(): ICache?

    /**
     * 定时停止/暂停：仅在 [android.app.Service] 宿主下由 [TimedOffHandler] 转发到 Service 实现；进程内模式为 no-op。
     */
    fun onStopByTimedOff(time: Long, pause: Boolean, finishCurrSong: Boolean)

    fun isStartForegroundByWorkManager(): Boolean

    fun startForegroundByWorkManager(value: Boolean)

    /**
     * 播放进入 IDLE 时由 [com.lzx.starrysky.manager.PlaybackManager] 调用；用于「定时到时再等当前曲播完」等 Service 侧逻辑。
     * 默认不处理；[MusicPlaybackFacade] 在 [Context] 为 [MusicService] 时委托给 Service。
     */
    fun consumeTimedOffAfterSongEndIdle(): Boolean = false
}
