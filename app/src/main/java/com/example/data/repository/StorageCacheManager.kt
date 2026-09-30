package com.example.data.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CacheStorageStats(
    val internalCacheBytes: Long = 0L,
    val externalCacheBytes: Long = 0L,
    val totalCacheBytes: Long = 0L,
    val isOverThreshold: Boolean = false, // > 200 MB
    val lastCleanupTimestamp: Long = 0L,
    val lastFreedBytes: Long = 0L
)

class StorageCacheManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs = context.getSharedPreferences("netra_cache_storage_prefs", Context.MODE_PRIVATE)

    private val _cacheStats = MutableStateFlow(
        CacheStorageStats(
            lastCleanupTimestamp = prefs.getLong("last_cleanup_timestamp", 0L),
            lastFreedBytes = prefs.getLong("last_freed_bytes", 0L)
        )
    )
    val cacheStats: StateFlow<CacheStorageStats> = _cacheStats.asStateFlow()

    init {
        refreshCacheStats()
    }

    fun refreshCacheStats() {
        scope.launch {
            calculateCacheSize()
        }
    }

    suspend fun calculateCacheSize(): CacheStorageStats = withContext(Dispatchers.IO) {
        val internalBytes = getDirSize(context.cacheDir) + getDirSize(context.codeCacheDir)
        val externalBytes = context.externalCacheDir?.let { getDirSize(it) } ?: 0L
        val total = internalBytes + externalBytes
        val isOver = total >= MAX_CACHE_SIZE_BYTES

        val updated = _cacheStats.value.copy(
            internalCacheBytes = internalBytes,
            externalCacheBytes = externalBytes,
            totalCacheBytes = total,
            isOverThreshold = isOver
        )
        _cacheStats.value = updated
        updated
    }

    /**
     * Cleans temporary application cache safely.
     * Guarantees USER DATA PROTECTION:
     * - Only touches context.cacheDir and context.externalCacheDir
     * - Never deletes databases, shared preferences, or user documents
     */
    suspend fun cleanCache(force: Boolean = false): Long = withContext(Dispatchers.IO) {
        val currentStats = calculateCacheSize()
        if (!force && !currentStats.isOverThreshold && currentStats.totalCacheBytes < CHARGING_CLEAN_MIN_BYTES) {
            return@withContext 0L
        }

        var freedBytes = 0L

        // 1. Clean Internal Cache
        freedBytes += deleteDirectoryContents(context.cacheDir)

        // 2. Clean External Cache if available
        context.externalCacheDir?.let {
            freedBytes += deleteDirectoryContents(it)
        }

        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong("last_cleanup_timestamp", now)
            .putLong("last_freed_bytes", freedBytes)
            .apply()

        val newStats = calculateCacheSize().copy(
            lastCleanupTimestamp = now,
            lastFreedBytes = freedBytes
        )
        _cacheStats.value = newStats
        freedBytes
    }

    /**
     * Automatically evaluates cache cleanup policy:
     * - Triggers cleanup when cache exceeds 200 MB
     * - Triggers maintenance cleanup during charging if cache exceeds 50 MB
     */
    suspend fun autoCleanIfAppropriate(isCharging: Boolean) = withContext(Dispatchers.IO) {
        val stats = calculateCacheSize()
        val now = System.currentTimeMillis()
        val timeSinceLastCleanup = now - stats.lastCleanupTimestamp

        when {
            // Priority 1: Exceeds 200 MB threshold
            stats.isOverThreshold -> {
                cleanCache(force = true)
            }
            // Priority 2: Charging maintenance (if > 50 MB and at least 6 hours since last clean)
            isCharging && stats.totalCacheBytes >= CHARGING_CLEAN_MIN_BYTES && timeSinceLastCleanup >= SIX_HOURS_MS -> {
                cleanCache(force = true)
            }
        }
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return size
    }

    private fun deleteDirectoryContents(dir: File?): Long {
        if (dir == null || !dir.exists() || !dir.isDirectory) return 0L
        var freed = 0L
        dir.listFiles()?.forEach { file ->
            // Safety check: ensure file is inside cache directory and not protected
            if (isSafeToDelete(file)) {
                freed += if (file.isDirectory) {
                    deleteDirectoryContents(file) + if (file.delete()) 4096L else 0L
                } else {
                    val len = file.length()
                    if (file.delete()) len else 0L
                }
            }
        }
        return freed
    }

    private fun isSafeToDelete(file: File): Boolean {
        val path = file.absolutePath
        // Safety guard: Never touch database or preferences directories
        if (path.contains("/databases") || path.contains("/shared_prefs") || path.endsWith(".db") || path.endsWith(".xml")) {
            return false
        }
        return true
    }

    companion object {
        const val MAX_CACHE_SIZE_BYTES = 200L * 1024L * 1024L // 200 MB threshold
        const val CHARGING_CLEAN_MIN_BYTES = 50L * 1024L * 1024L // 50 MB during charging
        const val SIX_HOURS_MS = 6 * 3600_000L
    }
}
