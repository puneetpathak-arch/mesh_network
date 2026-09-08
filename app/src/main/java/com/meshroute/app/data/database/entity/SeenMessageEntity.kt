package com.meshroute.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "seen_messages")
data class SeenMessageEntity(
    @PrimaryKey
    val messageId: String,
    val seenAt: Long = System.currentTimeMillis()
)
