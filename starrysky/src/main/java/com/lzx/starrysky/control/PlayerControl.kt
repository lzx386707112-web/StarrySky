package com.lzx.starrysky.control

import android.app.Activity
import android.content.Context
import android.provider.MediaStore
import androidx.lifecycle.MutableLiveData
import com.lzx.starrysky.GlobalPlaybackStageListener
import com.lzx.starrysky.OnPlayProgressListener
import com.lzx.starrysky.OnPlayerEventListener
import com.lzx.starrysky.SongInfo
import com.lzx.starrysky.StarrySky
import com.lzx.starrysky.StarrySkyInstall
import com.lzx.starrysky.intercept.InterceptorThread
import com.lzx.starrysky.intercept.StarrySkyInterceptor
import com.lzx.starrysky.manager.PlaybackManager
import com.lzx.starrysky.manager.PlaybackStage
import com.lzx.starrysky.playback.FocusInfo
import com.lzx.starrysky.playback.Playback
import com.lzx.starrysky.queue.MediaSourceProvider
import com.lzx.starrysky.service.MusicPlaybackHost
import com.lzx.starrysky.utils.MainLooper
import com.lzx.starrysky.utils.StarrySkyConstant
import com.lzx.starrysky.utils.TimerTaskManager
import com.lzx.starrysky.utils.data
import com.lzx.starrysky.utils.duration
import com.lzx.starrysky.utils.isIndexPlayable
import com.lzx.starrysky.utils.md5
import com.lzx.starrysky.utils.orDef
import com.lzx.starrysky.utils.title
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow


/**
 * 对外播放 API 门面；实现 [PlaybackEventsSink]，供 [PlaybackManager] 经 [PlaybackEngineCallbackBridge] 回传引擎状态。
 *
 * 播放阶段对外推荐订阅 [playbackStageFlow]（冷订阅可通过 replay 拿到最近一次状态）；[playbackState] 仍为 [MutableLiveData] 以兼容现有 `observe` 代码。
 */
class PlayerControl(
    appInterceptors: MutableList<Pair<StarrySkyInterceptor, String>>,
    private val globalPlaybackStageListener: GlobalPlaybackStageListener?,
    private val playbackHost: MusicPlaybackHost?
) : PlaybackEventsSink {

    private val focusChangeState = MutableLiveData<FocusInfo>()
    private val playbackState = MutableLiveData<PlaybackStage>()

    /**
     * 主线程上投递的播放阶段流：replay=1 便于新订阅方立即拿到当前阶段；与 [playbackState] 同源更新。
     */
    private val _playbackStageFlow = MutableSharedFlow<PlaybackStage>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * 音频焦点变化流；与 [focusChangeState] 同源更新（后者仍用 postValue 兼容后台线程回调）。
     */
    private val _focusChangeFlow = MutableSharedFlow<FocusInfo>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val playerEventListener = hashMapOf<String, OnPlayerEventListener>()
    private val progressListener = hashMapOf<String, OnPlayProgressListener>()

    private var timerTaskManager: TimerTaskManager? = null
    private var isRunningTimeTask = false

    private val provider = MediaSourceProvider()

    private val interceptors = mutableListOf<Pair<StarrySkyInterceptor, String>>() //局部拦截器，用完会自动清理
    private var isSkipMediaQueue = false

    /** 协调层只依赖 [PlaybackEventsSink]，由本类作为 Sink 实现并接收引擎回调。 */
    private val playbackManager = PlaybackManager(provider, appInterceptors, this, playbackHost)

    init {
        timerTaskManager = TimerTaskManager()
        timerTaskManager?.setUpdateProgressTask {
            isRunningTimeTask = true
            val position = getPlayingPosition()
            val duration = getDuration()
            progressListener.forEach {
                it.value.onPlayProgress(position, duration)
            }
        }
    }

    /**
     * 是否跳过播放队列，false的话，播放将不经过播放队列，直接走播放器，当前Activity结束后恢复false状态
     */
    fun skipMediaQueue(isSkipMediaQueue: Boolean) = apply {
        this.isSkipMediaQueue = isSkipMediaQueue
        playbackManager.attachSkipMediaQueue(isSkipMediaQueue)
    }

    /**
     * 是否需要状态回调，false的话将收不到回调，即使你已经设置了，当前Activity结束后恢复true状态
     */
    fun setWithOutCallback(withOutCallback: Boolean) = apply {
        playbackManager.attachWithOutCallback(withOutCallback)
    }

    /**
     * 根据 songId 播放,调用前请确保已经设置了播放列表
     * skipMediaQueue 模式下不能使用
     */
    fun playMusicById(songId: String?) {
        if (songId == null) return
        if (isSkipMediaQueue) {
            throw IllegalStateException("skipMediaQueue 模式下不能使用该方法")
        }
        if (!provider.hasSongInfo(songId)) return
        val songInfo = provider.getSongInfoById(songId)
        playMusicImpl(songInfo)
    }

    /**
     * 根据songUrl播放，songId 默认为 url 的 md5
     */
    fun playMusicByUrl(url: String) {
        val songInfo = SongInfo.create(url)
        if (!isSkipMediaQueue) {
            provider.addSongInfo(songInfo)
        }
        playMusicImpl(songInfo)
    }

    /**
     * 根据 SongInfo 播放,songId 或 songUrl 不能为空
     */
    fun playMusicByInfo(info: SongInfo?) {
        if (info == null) return
        if (!isSkipMediaQueue) {
            provider.addSongInfo(info)
        }
        playMusicImpl(info)
    }

    /**
     * 播放
     * @param mediaList 播放列表
     * @param index     要播放的歌曲在播放列表中的下标
     */
    fun playMusic(mediaList: MutableList<SongInfo>, index: Int) {
        if (mediaList.isEmpty()) {
            throw IllegalStateException("播放列表不能为空")
        }
        if (!index.isIndexPlayable(mediaList)) {
            throw IllegalStateException("请检查下标合法性")
        }
        if (!isSkipMediaQueue) {
            updatePlayList(mediaList)
        }
        playMusicImpl(mediaList.getOrNull(index))
    }

    /**
     * 重播当前音频
     */
    fun replayCurrMusic() {
        playbackManager.replayCurrMusic()
    }

    private fun playMusicImpl(songInfo: SongInfo?) {
        if (songInfo == null) return
        playbackManager
            .attachInterceptors(interceptors)
            .onPlayMusicImpl(songInfo, true)
        interceptors.clear()
        isSkipMediaQueue = false
    }

    /**
     * 添加局部拦截器，执行顺序是先执行局部拦截器再执行全局拦截器，当前Activity结束后局部拦截器会清空
     */
    fun addInterceptor(interceptor: StarrySkyInterceptor, thread: String = InterceptorThread.UI): PlayerControl {
        val noSame = interceptors.none { it.first.getTag() == interceptor.getTag() }
        if (noSame) { //如果没有相同的才添加
            interceptors += Pair(interceptor, thread)
        }
        return this
    }

    /**
     * 暂停
     */
    fun pauseMusic() {
        playbackManager.onPause()
    }

    /**
     * 恢复播放（暂停后恢复）
     */
    fun restoreMusic() {
        playbackManager.onRestoreMusic()
    }

    /**
     * 停止播放
     */
    fun stopMusic() {
        playbackManager.onStop()
    }

    /**
     * 准备播放，准备的是队列当前下标的音频
     */
    fun prepare() {
        if (isSkipMediaQueue) {
            throw IllegalStateException("skipMediaQueue 模式下不能使用该方法")
        }
        playbackManager.onPrepare()
    }

    /**
     * 根据songId准备,调用前请确保已经设置了播放列表
     */
    fun prepareById(songId: String) {
        if (isSkipMediaQueue) {
            throw IllegalStateException("skipMediaQueue 模式下不能使用该方法")
        }
        playbackManager.onPrepareById(songId)
    }

    /**
     * 根据songUrl准备
     */
    fun prepareByUrl(songUrl: String) {
        playbackManager.onPrepareByUrl(songUrl)
    }

    /**
     * 根据 SongInfo 准备
     */
    fun prepareByInfo(info: SongInfo) {
        playbackManager.onPrepareByInfo(info)
    }

    /**
     * 下一首
     */
    fun skipToNext() {
        playbackManager.onSkipToNext()
    }

    /**
     * 上一首
     */
    fun skipToPrevious() {
        playbackManager.onSkipToPrevious()
    }

    /**
     * 快进 每调一次加 speed 倍
     */
    fun fastForward(speed: Float) {
        if (speed <= 0) {
            throw IllegalStateException("speed 必须大于0")
        }
        playbackManager.onFastForward(speed)
    }

    /**
     * 快退 每调一次减 speed 倍，最小为 0
     */
    fun rewind(speed: Float) {
        playbackManager.onRewind(speed)
    }

    /**
     * 指定语速,通过此方法可配置任意倍速，注意结果要大于0
     *
     * @param refer    refer 是否已当前速度为基数
     * @param multiple multiple 倍率
     */
    fun onDerailleur(refer: Boolean, multiple: Float) {
        playbackManager.onDerailleur(refer, multiple)
    }

    /**
     * 移动到媒体流中的新位置,以毫秒为单位。
     * isPlayWhenPaused：如果是暂停状态下 seekTo 后是否马上播放
     */
    fun seekTo(pos: Long, isPlayWhenPaused: Boolean = true) {
        playbackManager.onSeekTo(pos, isPlayWhenPaused)
    }

    /**
     * 设置播放模式
     * 必须是以下之一：
     * REPEAT_MODE_NONE      顺序播放
     * REPEAT_MODE_ONE       单曲播放
     * REPEAT_MODE_SHUFFLE   随机播放
     * REPEAT_MODE_REVERSE   倒序播放
     *
     * isLoop 播放倒最后一首时是否从第一首开始循环播放,该参数对随机播放无效
     */
    fun setRepeatMode(@RepeatModeFlag repeatMode: Int, isLoop: Boolean) {
        if (isSkipMediaQueue && repeatMode != RepeatMode.REPEAT_MODE_ONE) {
            throw IllegalStateException("isSkipMediaQueue 模式下只能设置单曲模式")
        }
        RepeatMode.saveRepeatMode(repeatMode, isLoop)
        playbackManager.setRepeatMode(repeatMode, isLoop)
    }

    /**
     * 获取播放模式,默认顺序播放
     * REPEAT_MODE_NONE      顺序播放
     * REPEAT_MODE_ONE       单曲播放
     * REPEAT_MODE_SHUFFLE   随机播放
     * REPEAT_MODE_REVERSE   倒序播放
     */
    fun getRepeatMode(): RepeatMode = RepeatMode.with

    /**
     * 获取播放列表
     */
    fun getPlayList(): MutableList<SongInfo> = provider.songList

    /**
     * 更新播放列表
     */
    fun updatePlayList(songInfos: MutableList<SongInfo>) {
        provider.songList = songInfos
    }

    /**
     * 添加更多播放列表
     */
    fun addPlayList(infos: MutableList<SongInfo>) {
        provider.addSongInfos(infos)
    }

    /**
     * 添加一首歌
     */
    fun addSongInfo(info: SongInfo) {
        provider.addSongInfo(info)
    }

    /**
     * 添加一首歌,指定位置
     */
    fun addSongInfo(index: Int, info: SongInfo) {
        provider.addSongInfo(index, info)
    }

    /**
     * 删除歌曲
     * 正在播放删除后下一首开始播，暂停删除下一首暂停，跟随播放模式，删除后触发歌曲改变回调
     */
    fun removeSongInfo(songId: String?) {
        if (isSkipMediaQueue) {
            throw IllegalStateException("skipMediaQueue 模式下不能使用该方法")
        }
        songId?.let { playbackManager.removeSongInfo(it) }
    }

    /**
     * 清除播放列表
     */
    fun clearPlayList() {
        provider.clearSongInfos()
    }

    /**
     * 刷新随机列表
     */
    fun updateShuffleSongList() {
        playbackManager.refreshShuffleOrder()
    }

    /**
     * 根据当前播放信息更新下标
     */
    fun updateCurrIndex() {
        playbackManager.updateCurrIndex()
    }

    /**
     * 获取当前播放的歌曲信息
     */
    fun getNowPlayingSongInfo(): SongInfo? = playbackManager.player()?.getCurrPlayInfo()

    /**
     * 获取当前播放的歌曲songId
     */
    fun getNowPlayingSongId(): String = getNowPlayingSongInfo()?.songId.orEmpty()

    /**
     *  获取当前播放的歌曲url
     */
    fun getNowPlayingSongUrl(): String = getNowPlayingSongInfo()?.songUrl.orEmpty()

    /**
     * 获取当前播放歌曲的下标
     */
    fun getNowPlayingIndex(): Int {
        val songId = getNowPlayingSongId()
        return playbackManager.getPlaybackQueueIndex(songId)
    }

    /**
     * 以ms为单位获取当前缓冲的位置。
     */
    fun getBufferedPosition(): Long = playbackManager.player()?.bufferedPosition().orDef()

    /**
     * 获取播放位置 毫秒为单位。
     */
    fun getPlayingPosition(): Long = playbackManager.player()?.currentStreamPosition().orDef()

    /**
     * 是否有下一首
     */
    fun isSkipToNextEnabled(): Boolean = playbackManager.isSkipToNextEnabled()

    /**
     * 是否有上一首
     */
    fun isSkipToPreviousEnabled(): Boolean = playbackManager.isSkipToPreviousEnabled()

    /**
     * 将当前播放速度作为正常播放的倍数。 倒带时这应该是负数， 值为1表示正常播放，0表示暂停。
     */
    fun getPlaybackSpeed(): Float = playbackManager.player()?.getPlaybackSpeed().orDef()

    /**
     * 是否在「正在出声播放」阶段（与 [Playback.STATE_PLAYING] 对齐）。
     *
     * 优先读引擎 [Playback.playbackState]，避免仅依赖 [playbackState] LiveData 时（回调尚未绑定、
     * 或 UI 状态与 Exo 短暂不同步）出现「其实在播但返回 false」。
     */
    fun isPlaying(): Boolean {
        playbackManager.player()?.let { return it.playbackState() == Playback.STATE_PLAYING }
        return playbackState.value?.stage == PlaybackStage.PLAYING
    }

    /**
     * 是否处于暂停态（与 [Playback.STATE_PAUSED] 对齐）；引擎不可用时回退 LiveData。
     */
    fun isPaused(): Boolean {
        playbackManager.player()?.let { return it.playbackState() == Playback.STATE_PAUSED }
        return playbackState.value?.stage == PlaybackStage.PAUSE
    }

    /**
     * 是否空闲（与 [Playback.STATE_IDLE] 对齐）；引擎不可用时回退 LiveData。
     */
    fun isIdle(): Boolean {
        playbackManager.player()?.let { return it.playbackState() == Playback.STATE_IDLE }
        return playbackState.value?.stage == PlaybackStage.IDLE
    }

    /**
     * 是否缓冲中（与 [Playback.STATE_BUFFERING] 对齐）；引擎不可用时回退 LiveData。
     */
    fun isBuffering(): Boolean {
        playbackManager.player()?.let { return it.playbackState() == Playback.STATE_BUFFERING }
        return playbackState.value?.stage == PlaybackStage.BUFFERING
    }

    /**
     * 判断传入的音乐是不是正在播放的音乐
     */
    fun isCurrMusicIsPlayingMusic(songId: String?): Boolean {
        return if (songId.isNullOrEmpty()) {
            false
        } else {
            val playingMusic = getNowPlayingSongInfo()
            playingMusic != null && songId == playingMusic.songId
        }
    }

    /**
     * 判断传入的音乐是否正在播放
     */
    fun isCurrMusicIsPlaying(songId: String?): Boolean = isCurrMusicIsPlayingMusic(songId) && isPlaying()

    /**
     * 判断传入的音乐是否正在暂停
     */
    fun isCurrMusicIsPaused(songId: String?): Boolean = isCurrMusicIsPlayingMusic(songId) && isPaused()

    /**
     * 判断传入的音乐是否空闲
     */
    fun isCurrMusicIsIdea(songId: String?): Boolean = isCurrMusicIsPlayingMusic(songId) && isIdle()

    /**
     * 判断传入的音乐是否缓冲
     */
    fun isCurrMusicIsBuffering(songId: String?): Boolean = isCurrMusicIsPlayingMusic(songId) && isBuffering()

    /**
     * 设置音量, 范围 0到1
     */
    fun setVolume(audioVolume: Float) {
        var volume = audioVolume
        if (audioVolume > 1 && audioVolume < 100) {
            volume = audioVolume / 100f
        }
        playbackManager.player()?.setVolume(volume)
    }

    /**
     * 获取音量
     */
    fun getVolume(): Float = playbackManager.player()?.getVolume().orDef()

    /**
     * 获取媒体时长，单位毫秒
     */
    fun getDuration(): Long = playbackManager.player()?.duration().orDef()

    /**
     * 获取 AudioSessionId
     */
    fun getAudioSessionId(): Int = playbackManager.player()?.getAudioSessionId().orDef()

    /**
     * 扫描本地媒体信息
     */
    fun querySongInfoInLocal(context: Context): List<SongInfo> {
        val songInfos = mutableListOf<SongInfo>()
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null,
            null, MediaStore.Audio.AudioColumns.IS_MUSIC
        ) ?: return songInfos
        while (cursor.moveToNext()) {
            val song = SongInfo()
            song.songUrl = cursor.data
            song.songName = cursor.title
            song.duration = cursor.duration
            val songId =
                if (song.songUrl.isNotEmpty()) song.songUrl.md5() else System.currentTimeMillis().toString().md5()
            song.songId = songId
            songInfos.add(song)
        }
        cursor.close()
        return songInfos
    }

    /**
     * 缓存开关，可控制是否使用缓存功能
     */
    fun cacheSwitch(switch: Boolean) {
        StarrySkyConstant.KEY_CACHE_SWITCH = switch
    }

    /**
     * 定时停止
     * time 时间，单位毫秒，传 0 为不开启
     * isPause 到时间了是否暂停，如果为false，则到时间后会调用stop
     * isFinishCurrSong 时间到后是否播放完当前歌曲再停
     */
    fun stopByTimedOff(time: Long, isPause: Boolean, isFinishCurrSong: Boolean) {
        if (time <= 0) {
            return
        }
        if (isSkipMediaQueue) {
            throw IllegalStateException("skipMediaQueue 模式下不能使用该方法")
        }
        playbackManager.onStopByTimedOff(time, isPause, isFinishCurrSong)
    }

    /**
     * 添加一个状态监听
     * tag: 给每个播放器添加一个tag 可以根据 tag 来删除，用 tag 的原因是在使用的时候发现，
     * 如果靠对象来remove，不是很方便。所以该用tag
     */
    fun addPlayerEventListener(listener: OnPlayerEventListener?, tag: String) {
        listener?.let {
            if (!playerEventListener.containsKey(tag)) {
                playerEventListener[tag] = it
            }
        }
    }

    /**
     * 删除一个状态监听
     */
    fun removePlayerEventListener(tag: String) {
        playerEventListener.remove(tag)
    }

    /**
     * 删除所有状态监听
     */
    fun clearPlayerEventListener() {
        playerEventListener.clear()
    }

    /**
     * 状态监听，[SharedFlow] 方式；推荐使用 `lifecycleScope.launch { repeatOnLifecycle { playbackStageFlow().collect { } } }` 等与生命周期绑定。
     */
    fun playbackStageFlow(): SharedFlow<PlaybackStage> = _playbackStageFlow.asSharedFlow()

    /**
     * 焦点变化，[SharedFlow] 方式；与 [focusStateChange] 二选一或组合使用均可。
     */
    fun focusChangeFlow(): SharedFlow<FocusInfo> = _focusChangeFlow.asSharedFlow()

    /**
     * 焦点变化监听,LiveData 方式
     */
    fun focusStateChange(): MutableLiveData<FocusInfo> = focusChangeState

    /**
     * 状态监听,LiveData 方式
     */
    fun playbackState(): MutableLiveData<PlaybackStage> = playbackState

    /**
     * 设置进度监听。
     *
     * **tag 说明**：默认取当前栈顶 Activity 的 `toString()`。在 Activity [android.app.Activity.onCreate]
     * 里注册时，该 Activity 往往尚未进入生命周期栈（`onActivityCreated` 晚于 `onCreate`），栈顶可能为空，
     * 若再用 `tag?.let` 注册会导致监听根本未加入、进度永不回调。因此当 tag 为空时会回退到
     * [DEFAULT_PLAY_PROGRESS_TAG]；仍建议在 [android.app.Activity.onResume] 中注册并显式传入 `this.toString()`。
     */
    fun setOnPlayProgressListener(
        listener: OnPlayProgressListener,
        tag: String? = StarrySky.getStackTopActivity()?.toString()
    ) {
        val key = tag ?: DEFAULT_PLAY_PROGRESS_TAG
        progressListener[key] = listener
        if (!isRunningTimeTask && (isPlaying() || isBuffering())) {
            timerTaskManager?.startToUpdateProgress()
        }
    }

    /**
     * 移除进度监听
     * tag:进度标记
     */
    fun removeProgressListener(tag: String) {
        progressListener.remove(tag)
    }

    override fun onPlaybackStateUpdated(playbackStage: PlaybackStage) {
        // 与 LiveData / 进度定时器一致，在主线程上合并处理，避免与 UI observe 竞态。
        MainLooper.instance.runOnUiThread {
            when (playbackStage.stage) {
                PlaybackStage.PLAYING -> {
                    timerTaskManager?.startToUpdateProgress()
                    val effectSwitch = StarrySkyConstant.keyEffectSwitch
                    if (effectSwitch) {
                        StarrySkyInstall.voiceEffect.attachAudioEffect(getAudioSessionId())
                    }
                }
                PlaybackStage.BUFFERING -> {
                    timerTaskManager?.startToUpdateProgress()
                }
                PlaybackStage.PAUSE,
                PlaybackStage.ERROR,
                PlaybackStage.IDLE -> {
                    timerTaskManager?.stopToUpdateProgress()
                    isRunningTimeTask = false
                }
            }
            _playbackStageFlow.tryEmit(playbackStage)
            globalPlaybackStageListener?.onPlaybackStageChange(playbackStage)
            playbackState.value = playbackStage
            playerEventListener.forEach {
                it.value.onPlaybackStageChange(playbackStage)
            }
        }
    }

    override fun onFocusStateChange(info: FocusInfo) {
        _focusChangeFlow.tryEmit(info)
        focusChangeState.postValue(info)
    }

    fun resetVariable(activity: Activity?) {
        interceptors.clear()
        playbackManager.resetVariable(activity)
    }

    fun release() {
        timerTaskManager?.removeUpdateProgressTask()
        isRunningTimeTask = false
        timerTaskManager = null
    }

    companion object {
        private const val DEFAULT_PLAY_PROGRESS_TAG = "StarrySky.DefaultPlayProgress"
    }
}