package com.lzx.starrysky.queue

import com.lzx.starrysky.SongInfo
import com.lzx.starrysky.utils.isIndexPlayable

/**
 * 曲库：按插入顺序保存 [SongInfo]（以 songId 唯一），不承载「播放顺序 / 随机置换」逻辑。
 * 结构变更时通过 [addPlaylistChangedListener] 通知 [MediaQueueManager] 等同步播放序缓存。
 */
class MediaSourceProvider {

    private val songSources = linkedMapOf<String, SongInfo>()

    private val playlistChangedListeners = mutableListOf<() -> Unit>()

    fun addPlaylistChangedListener(listener: () -> Unit) {
        playlistChangedListeners.add(listener)
    }

    private fun notifyPlaylistStructureChanged() {
        val snapshot = playlistChangedListeners.toList()
        snapshot.forEach { runCatching { it() }.getOrDefault(Unit) }
    }

    /** 与 [songList] 相同的顺序，只读视图，避免无谓分配时可优先使用 */
    fun orderedSongs(): List<SongInfo> = songSources.values.toList()

    var songList: MutableList<SongInfo>
        get() = orderedSongs().toMutableList()
        set(value) {
            songSources.clear()
            value.forEach {
                songSources[it.songId] = it
            }
            notifyPlaylistStructureChanged()
        }

    fun getSourceSize() = songSources.size

    fun addSongInfo(info: SongInfo) {
        if (!hasSongInfo(info.songId)) {
            songSources[info.songId] = info
            notifyPlaylistStructureChanged()
        }
    }

    fun addSongInfo(index: Int, info: SongInfo) {
        if (!hasSongInfo(info.songId)) {
            val list = mutableListOf<Pair<String, SongInfo>>()
            songSources.forEach {
                list.add(Pair(it.key, it.value))
            }
            if (index.isIndexPlayable(list)) {
                list.add(index, Pair(info.songId, info))
            }
            songSources.clear()
            list.forEach {
                songSources[it.first] = it.second
            }
            notifyPlaylistStructureChanged()
        }
    }

    fun addSongInfos(infos: MutableList<SongInfo>) {
        infos.forEach {
            addSongInfo(it)
        }
    }

    fun clearSongInfos() {
        songSources.clear()
        notifyPlaylistStructureChanged()
    }

    fun deleteSongInfoById(songId: String): Boolean {
        if (hasSongInfo(songId)) {
            songSources.remove(songId)
            notifyPlaylistStructureChanged()
            return true
        }
        return false
    }

    fun hasSongInfo(songId: String): Boolean {
        return songSources.containsKey(songId)
    }

    fun getSongInfoById(songId: String): SongInfo? {
        if (songId.isEmpty()) {
            return null
        }
        return songSources[songId]
    }

    fun getSongInfoByIndex(index: Int): SongInfo? {
        return orderedSongs().elementAtOrNull(index)
    }

    /** 在「曲库插入顺序」中的下标 */
    fun getIndexById(songId: String): Int {
        val info = getSongInfoById(songId) ?: return -1
        return orderedSongs().indexOf(info)
    }

    /**
     * 仅更新曲库中的条目（如封面），不改变曲目顺序与数量；
     * 不触发 [notifyPlaylistStructureChanged]，由 [MediaQueueManager] 同步随机序中的同一引用。
     */
    fun updateMusicArt(songInfo: SongInfo) {
        songSources[songInfo.songId] = songInfo
    }
}
