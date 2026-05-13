package com.lzx.starrysky.service

import android.app.Notification
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.common.util.concurrent.Futures
import com.lzx.starrysky.StarrySky
import com.lzx.starrysky.notification.utils.NotificationUtils
import com.lzx.starrysky.utils.MainLooper
import com.lzx.starrysky.utils.TimerTaskManager
import com.lzx.starrysky.utils.orDef


class MusicService : Service() {

    var binder: MusicServiceBinder? = null
    private var noisyReceiver: BecomingNoisyReceiver? = null
    private var timerTaskManager: TimerTaskManager? = null
    private val timedOffLock = Any()
    private var timedOffDuration = -1L
    private var isPauseByTimedOff = true
    private var timedOffFinishCurrSong = false
    /** 定时已到、需等当前曲自然播完再执行暂停/停止 */
    private var timedOffPendingAfterSongEnd = false
    private var mustShowNotification = false
    private var foregroundFallbackRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        binder = MusicServiceBinder(this)
        initPlayerService()
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            this.mustShowNotification = intent.getBooleanExtra("flag_must_to_show_notification", false)
        } else {
            this.mustShowNotification = false
        }
        initPlayerService()
        return START_STICKY
    }

    /**
     * 自定义启动前台服务。
     * Android 12+ 从后台启动前台服务受限时，可由宿主开启 [MusicPlaybackHost.startForegroundByWorkManager] 走 WorkManager 兜底。
     * Android 14+（target 34）须带 [ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK]，与 manifest 中
     * `android:foregroundServiceType="mediaPlayback"` 一致。
     */
    fun customStartForeground(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && binder?.isStartForegroundByWorkManager() == true) {
            startForegroundByWorkManager(id, notification)
        } else {
            startForegroundWithCompat(id, notification)
        }
    }

    private fun startForegroundWithCompat(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            @Suppress("DEPRECATION")
            startForeground(id, notification)
        }
    }

    /**
     * 通过 WorkManager 触发 expedited worker 的前台通知，缓解部分机型上后台 startForeground 抛异常的问题。
     * 使用 [WorkForegroundBridge] 传递与常规路径相同的 [notification]。
     */
    private fun startForegroundByWorkManager(id: Int, notification: Notification) {
        WorkForegroundBridge.offer(id, notification)
        val uploadWorkRequest: WorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf("id" to id))
            .build()
        WorkManager
            .getInstance(this)
            .enqueue(uploadWorkRequest)
    }

    /**
     * 停止前台：API 24+ 使用 [stopForeground] 标志位；与 `stopForeground(true)` 语义对应为 [Service.STOP_FOREGROUND_REMOVE]。
     */
    fun stopForegroundCompat(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val flags = if (removeNotification) {
                Service.STOP_FOREGROUND_REMOVE
            } else {
                Service.STOP_FOREGROUND_DETACH
            }
            stopForeground(flags)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
    }

    /**
     * 定时结束且选择了「播完当前曲再停」时，在引擎进入 [com.lzx.starrysky.playback.Playback.STATE_IDLE] 后消费并执行暂停/停止，
     * 并阻止自动切歌逻辑。由 [PlaybackManager] 在 [onPlaybackCompletion] 之前调用。
     */
    fun consumeTimedOffAfterSongEndIdle(): Boolean = synchronized(timedOffLock) {
        if (!timedOffPendingAfterSongEnd) return false
        timedOffPendingAfterSongEnd = false
        if (isPauseByTimedOff) {
            binder?.player?.pause()
        } else {
            binder?.player?.stop()
        }
        true
    }

    private fun initPlayerService() {
        if (noisyReceiver == null) {
            noisyReceiver = BecomingNoisyReceiver(this)
            noisyReceiver?.register()
        }
        if (timerTaskManager == null) {
            timerTaskManager = TimerTaskManager()
            //计时回调
            timerTaskManager?.setUpdateProgressTask {
                val reachedZero = synchronized(timedOffLock) {
                    timedOffDuration -= 1000
                    timedOffDuration <= 0
                }
                if (!reachedZero) return@setUpdateProgressTask
                timerTaskManager?.stopToUpdateProgress()
                val (waitSongEnd, pause) = synchronized(timedOffLock) {
                    val wait = timedOffFinishCurrSong
                    val p = isPauseByTimedOff
                    if (!wait) {
                        timedOffDuration = -1
                        timedOffFinishCurrSong = false
                    } else {
                        timedOffPendingAfterSongEnd = true
                        timedOffDuration = -1
                        timedOffFinishCurrSong = false
                    }
                    wait to p
                }
                if (!waitSongEnd) {
                    if (pause) {
                        binder?.player?.pause()
                    } else {
                        binder?.player?.stop()
                    }
                }
            }
        }

        // https://developer.android.com/about/versions/oreo/background?hl=zh-cn#services 防止后台启动service后导致崩溃问题
        val notification: Notification = NotificationUtils.createNoCrashNotification(this)
        if (this.applicationInfo.targetSdkVersion >= 26 && this.mustShowNotification) {
            foregroundFallbackRunnable?.let { MainLooper.instance.removeCallbacks(it) }
            val r = Runnable {
                foregroundFallbackRunnable = null
                if (binder?.notification == null) {
                    try {
                        customStartForeground(10000, notification)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
            foregroundFallbackRunnable = r
            MainLooper.instance.postDelayed(r, 3500L)
        }
    }


    /**
     * 定时关闭功能实现
     */
    fun onStopByTimedOffImpl(time: Long, isPause: Boolean, finishCurrSong: Boolean) {
        if (time == 0L) {
            synchronized(timedOffLock) {
                timedOffDuration = -1
                timedOffFinishCurrSong = false
                timedOffPendingAfterSongEnd = false
            }
            timerTaskManager?.stopToUpdateProgress()
            return
        }
        synchronized(timedOffLock) {
            timedOffDuration = time
            isPauseByTimedOff = isPause
            timedOffFinishCurrSong = finishCurrSong
            timedOffPendingAfterSongEnd = false
        }
        timerTaskManager?.startToUpdateProgress()
    }


    /**
     * 耳机拔出广播接收器
     */
    private inner class BecomingNoisyReceiver(private val context: Context) : BroadcastReceiver() {
        var bluetoothAdapter: BluetoothAdapter? = null
        private var intentFilter: IntentFilter? = null

        init {
            bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                ?: @Suppress("DEPRECATION")
                BluetoothAdapter.getDefaultAdapter()
            intentFilter = IntentFilter().apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)  //有线耳机拔出变化
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED) //蓝牙耳机连接变化
            }
        }

        private var registered = false

        fun register() {
            if (!registered) {
                val filter = intentFilter ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.registerReceiver(
                        context,
                        this,
                        filter,
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.registerReceiver(this, filter)
                }
                registered = true
            }
        }

        fun unregister() {
            if (registered) {
                runCatching { context.unregisterReceiver(this) }
                registered = false
            }
        }

        override fun onReceive(context: Context?, intent: Intent?) {
            //当前是正在运行的时候才能通过媒体按键来操作音频
            val isPlaying = binder?.player?.isPlaying().orDef()
            when (intent?.action) {
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    StarrySky.log("蓝牙耳机插拔状态改变")
                    val state = runCatching {
                        // Android 12+ 需 BLUETOOTH_CONNECT，未授权时可能抛 SecurityException
                        bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.HEADSET)
                    }.getOrNull()
                    if (BluetoothProfile.STATE_DISCONNECTED == state && isPlaying) {
                        //蓝牙耳机断开连接 同时当前音乐正在播放 则将其暂停
                        binder?.player?.pause()
                    }
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    StarrySky.log("有线耳机插拔状态改变")
                    if (isPlaying) {
                        //有线耳机断开连接 同时当前音乐正在播放 则将其暂停
                        binder?.player?.pause()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        foregroundFallbackRunnable?.let { MainLooper.instance.removeCallbacks(it) }
        foregroundFallbackRunnable = null
        WorkForegroundBridge.clear()
        synchronized(timedOffLock) {
            timedOffPendingAfterSongEnd = false
        }
        super.onDestroy()
        timerTaskManager?.removeUpdateProgressTask()
        noisyReceiver?.unregister()
        binder?.player?.stop()
        binder?.player?.setCallback(null)
        binder?.notification?.stopNotification()
    }

    class UploadWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

        override fun getForegroundInfo(): ForegroundInfo {
            val dataId = inputData.getInt("id", 10000)
            val (notifId, notification) = WorkForegroundBridge.consume(applicationContext, dataId)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ForegroundInfo(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                ForegroundInfo(notifId, notification)
            }
        }

        override fun doWork(): Result {
            val foregroundInfo = getForegroundInfo()
            return try {
                Futures.getUnchecked(setForegroundAsync(foregroundInfo))
                Result.success()
            } catch (t: Throwable) {
                Result.failure()
            }
        }
    }
}
