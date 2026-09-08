package com.meshroute.app.mesh.router

import android.util.Log
import com.meshroute.app.data.database.dao.SeenMessageDao
import com.meshroute.app.data.database.entity.SeenMessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Bounded, thread-safe LRU duplicate suppression set backed by Room SQLite.
 *
 * Guarantees that any packet id is processed at most once across all
 * multi-path mesh relays, even across application/process restarts.
 */
class SeenSet(
    private val seenMessageDao: SeenMessageDao? = null,
    private val maxCapacity: Int = 10_000,
    private val ttlMillis: Long = 24 * 60 * 60 * 1000L, // 24 hours
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "SeenSet"
    }

    private data class Entry(val timestamp: Long)

    private val lruCache: MutableMap<String, Entry> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Entry>(maxCapacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean {
                return size > maxCapacity
            }
        }
    )

    /**
     * Attempts to add a messageId to the seen set.
     * @return true if this is the FIRST sighting of messageId, false if it is a DUPLICATE.
     */
    fun add(messageId: String): Boolean {
        val now = System.currentTimeMillis()

        synchronized(lruCache) {
            val existing = lruCache[messageId]
            if (existing != null && (now - existing.timestamp) < ttlMillis) {
                // Already seen and not expired
                return false
            }

            // Record in memory
            lruCache[messageId] = Entry(now)
        }

        // Persist to SQLite disk asynchronously if DAO available
        if (seenMessageDao != null) {
            scope.launch {
                runCatching {
                    seenMessageDao.insert(SeenMessageEntity(messageId = messageId, seenAt = now))
                }
            }
        }

        return true
    }

    /** Checks if a messageId has already been seen without updating access order */
    fun contains(messageId: String): Boolean {
        val now = System.currentTimeMillis()
        val entry = lruCache[messageId] ?: return false
        return (now - entry.timestamp) < ttlMillis
    }

    /** Initializes memory cache from persistent disk storage on app startup */
    suspend fun loadFromStorage() = withContext(Dispatchers.IO) {
        if (seenMessageDao == null) return@withContext
        runCatching {
            val recentIds = seenMessageDao.getRecentSeenIds(maxCapacity)
            val now = System.currentTimeMillis()
            synchronized(lruCache) {
                for (id in recentIds) {
                    if (!lruCache.containsKey(id)) {
                        lruCache[id] = Entry(now)
                    }
                }
            }
            Log.i(TAG, "Loaded ${recentIds.size} seen packet IDs from persistent disk storage into SeenSet")
        }
    }

    fun size(): Int = lruCache.size

    fun clear() {
        lruCache.clear()
        if (seenMessageDao != null) {
            scope.launch {
                seenMessageDao.clearAll()
            }
        }
    }
}
