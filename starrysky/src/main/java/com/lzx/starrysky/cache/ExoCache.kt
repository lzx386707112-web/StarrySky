@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.lzx.starrysky.cache

import android.content.Context
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.lzx.starrysky.SongInfo
import com.lzx.starrysky.utils.StarrySkyConstant
import java.io.File

/**
 * 基于 Media3 [SimpleCache] 的边播边缓存实现，与 [com.lzx.starrysky.playback.ExoPlayback] 的
 * [androidx.media3.datasource.cache.CacheDataSource] 配合使用。
 */
class ExoCache(
    private val context: Context,
    private val cacheDir: String?,
    private val cacheMaxBytes: Long
) : ICache {

    private var cacheFile: File? = null
    private var exoCache: Cache? = null

    override fun getProxyUrl(url: String, songInfo: SongInfo): String? {
        return null
    }

    override fun isOpenCache(): Boolean {
        return StarrySkyConstant.KEY_CACHE_SWITCH
    }

    @Synchronized
    override fun getCacheDirectory(context: Context, destFileDir: String?): File? {
        if (cacheFile == null && !destFileDir.isNullOrEmpty()) {
            cacheFile = File(destFileDir).also { dir ->
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
        }
        if (cacheFile == null) {
            cacheFile = context.getExternalFilesDir(null) ?: context.filesDir
        }
        return cacheFile
    }

    @Synchronized
    fun getDownloadCache(): Cache? {
        if (exoCache == null) {
            val dir = getCacheDirectory(context, cacheDir) ?: return null
            val maxBytes = effectiveMaxBytes()
            val cacheEvictor = LeastRecentlyUsedCacheEvictor(maxBytes)
            exoCache = SimpleCache(dir, cacheEvictor, StandaloneDatabaseProvider(context))
        }
        return exoCache
    }

    private fun effectiveMaxBytes(): Long =
        if (cacheMaxBytes > 0L) cacheMaxBytes else DEFAULT_CACHE_MAX_BYTES

    @Synchronized
    override fun isCache(url: String): Boolean {
        if (url.isEmpty()) return false
        val cache = getDownloadCache() ?: return false
        val cachedSpans: Set<CacheSpan> = cache.getCachedSpans(url)
        if (cachedSpans.isEmpty()) return false
        val contentLength = cache.getContentMetadata(url)
            .get(ContentMetadata.KEY_CONTENT_LENGTH, C.LENGTH_UNSET.toLong())
        if (contentLength == C.LENGTH_UNSET.toLong() || contentLength <= 0L) {
            return false
        }
        var currentLength = 0L
        for (cachedSpan in cachedSpans) {
            currentLength += cache.getCachedLength(url, cachedSpan.position, cachedSpan.length)
        }
        return currentLength >= contentLength
    }

    @Synchronized
    override fun release() {
        try {
            exoCache?.release()
        } catch (_: Exception) {
            // 已 release 或 IO 异常时忽略，避免二次退出崩溃
        } finally {
            exoCache = null
        }
    }

    companion object {
        private const val DEFAULT_CACHE_MAX_BYTES = 512L * 1024 * 1024
    }
}
