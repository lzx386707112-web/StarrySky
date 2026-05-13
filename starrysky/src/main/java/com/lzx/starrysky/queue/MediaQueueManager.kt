package com.lzx.starrysky.queue

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.lzx.starrysky.SongInfo
import com.lzx.starrysky.StarrySky
import com.lzx.starrysky.control.RepeatMode
import com.lzx.starrysky.control.isModeOne
import com.lzx.starrysky.control.isModeReverse
import com.lzx.starrysky.control.isModeShuffle
import com.lzx.starrysky.notification.imageloader.ImageLoaderCallBack
import com.lzx.starrysky.utils.isIndexPlayable

class MediaQueueManager(val provider: MediaSourceProvider) {

    private var currentIndex: Int = 0

    /** 随机模式下对 [MediaSourceProvider.orderedSongs] 的一次置换，失效后重建 */
    private var shuffleOrder: MutableList<SongInfo>? = null

    init {
        provider.addPlaylistChangedListener(::invalidatePlaybackOrderCache)
    }

    internal fun invalidatePlaybackOrderCache() {
        shuffleOrder = null
    }

    private fun baseOrderedSongs(): List<SongInfo> = provider.orderedSongs()

    /**
     * 当前播放模式下的有效队列：顺序 / 倒序 / 随机（随机为稳定置换，与 [currentIndex] 同一坐标系）。
     */
    fun playbackSequence(): List<SongInfo> {
        val base = baseOrderedSongs()
        val mode = RepeatMode.with.repeatMode
        return when {
            mode.isModeShuffle() -> {
                if (shuffleOrder == null) {
                    shuffleOrder = base.toMutableList().apply { shuffle() }
                }
                shuffleOrder!!
            }
            mode.isModeReverse() -> base.asReversed()
            else -> base
        }
    }

    /** 进入随机模式时重建置换，并把游标锚到当前曲 */
    fun enterShuffleMode(anchorSongId: String?) {
        shuffleOrder = null
        val seq = playbackSequence()
        if (anchorSongId != null) {
            val idx = seq.indexOfFirst { it.songId == anchorSongId }
            if (idx >= 0) currentIndex = idx
        }
    }

    /** 保留当前播放曲，重新打乱随机队列 */
    fun refreshShuffleOrder() {
        if (!RepeatMode.with.repeatMode.isModeShuffle()) return
        val anchorId = playbackSequence().elementAtOrNull(currentIndex)?.songId ?: return
        shuffleOrder = null
        val seq = playbackSequence()
        val idx = seq.indexOfFirst { it.songId == anchorId }
        if (idx >= 0) currentIndex = idx
    }

    /**
     * @param ignoreShuffle 为 true 时仍返回「当前游标所指」曲目，但元数据以曲库 [MediaSourceProvider] 为准（如刚写入封面）。
     */
    fun getCurrentSongInfo(ignoreShuffle: Boolean): SongInfo? {
        val current = playbackSequence().elementAtOrNull(currentIndex) ?: return null
        return if (ignoreShuffle) provider.getSongInfoById(current.songId) else current
    }

    fun getCurrSongList(): MutableList<SongInfo> {
        return playbackSequence().toMutableList()
    }

    /** 当前播放顺序中某曲的下标，无则 -1 */
    fun getPlayingIndex(songId: String): Int {
        if (songId.isEmpty()) return -1
        return playbackSequence().indexOfFirst { it.songId == songId }
    }

    fun skipQueuePosition(amount: Int): Boolean {
        val playingQueue = playbackSequence()
        if (playingQueue.isEmpty()) {
            return false
        }
        var index = currentIndex + amount
        if (index < 0) {
            val repeatMode = RepeatMode.with
            index = if (repeatMode.isLoop) {
                playingQueue.size - 1
            } else {
                if (repeatMode.repeatMode.isModeOne() || repeatMode.repeatMode.isModeShuffle()) {
                    playingQueue.lastIndex
                } else {
                    0
                }
            }
        } else {
            index %= playingQueue.size
        }
        if (!index.isIndexPlayable(playingQueue)) {
            return false
        }
        currentIndex = index
        StarrySky.log("skipQueuePosition#mCurrentIndex=$currentIndex")
        return true
    }

    fun currSongIsFirstSong(): Boolean {
        val firstSong = provider.getSongInfoByIndex(0)
        return getCurrentSongInfo(true)?.songId == firstSong?.songId
    }

    fun currSongIsLastSong(): Boolean {
        val lastSong = provider.getSongInfoByIndex(provider.getSourceSize() - 1)
        return getCurrentSongInfo(true)?.songId == lastSong?.songId
    }

    fun updateIndexBySongId(songId: String): Boolean {
        val index = playbackSequence().indexOfFirst { it.songId == songId }
        val list = playbackSequence()
        if (index.isIndexPlayable(list)) {
            currentIndex = index
        }
        return index >= 0
    }

    fun updateIndexByPlayingInfo(currInfo: SongInfo?) {
        currInfo?.let {
            updateIndexBySongId(it.songId)
        }
    }

    fun updateMusicArt(songInfo: SongInfo?) {
        val coverUrl = songInfo?.songCover.orEmpty()
        if (coverUrl.isNotEmpty() && songInfo?.coverBitmap == null) {
            StarrySky.getImageLoader()?.load(coverUrl, object : ImageLoaderCallBack {
                override fun onBitmapLoaded(bitmap: Bitmap?) {
                    songInfo?.let {
                        it.coverBitmap = bitmap
                        provider.updateMusicArt(songInfo)
                        shuffleOrder?.let { cache ->
                            val i = cache.indexOfFirst { s -> s.songId == songInfo.songId }
                            if (i >= 0) cache[i] = songInfo
                        }
                    }
                }

                override fun onBitmapFailed(errorDrawable: Drawable?) {
                }
            })
        }
    }

    fun getCurrIndex() = currentIndex
}
