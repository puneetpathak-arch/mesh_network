package com.meshroute.app.data.database.dao

import androidx.room.*
import com.meshroute.app.data.database.entity.SeenMessageEntity

@Dao
interface SeenMessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SeenMessageEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM seen_messages WHERE messageId = :messageId)")
    suspend fun isSeen(messageId: String): Boolean

    @Query("SELECT messageId FROM seen_messages ORDER BY seenAt DESC LIMIT :limit")
    suspend fun getRecentSeenIds(limit: Int): List<String>

    @Query("DELETE FROM seen_messages WHERE seenAt < :olderThan")
    suspend fun pruneExpired(olderThan: Long)

    @Query("SELECT COUNT(*) FROM seen_messages")
    suspend fun count(): Int

    @Query("DELETE FROM seen_messages")
    suspend fun clearAll()
}
