package com.lzx.starrysky.service

import android.app.Notification
import android.content.Context
import com.lzx.starrysky.notification.utils.NotificationUtils

/**
 * [MusicService] 在走 WorkManager 兜底启动前台时，将本次 [Notification] 暂存，供 [MusicService.UploadWorker] 读取。
 * 单次入队消费一次，避免占位通知与真实媒体通知不一致。
 */
internal object WorkForegroundBridge {

    private val lock = Any()
    private var offeredId: Int = 10000
    private var offeredNotification: Notification? = null

    fun offer(id: Int, notification: Notification) = synchronized(lock) {
        offeredId = id
        offeredNotification = notification
    }

    fun consume(appContext: Context, dataId: Int): Pair<Int, Notification> = synchronized(lock) {
        val hadCustom = offeredNotification != null
        val n = offeredNotification ?: NotificationUtils.createNoCrashNotification(appContext)
        val fid = if (hadCustom) offeredId else dataId
        offeredNotification = null
        fid to n
    }

    fun clear() = synchronized(lock) {
        offeredNotification = null
    }
}
