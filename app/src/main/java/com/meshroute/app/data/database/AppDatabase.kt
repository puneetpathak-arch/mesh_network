package com.meshroute.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.meshroute.app.data.database.dao.PacketDao
import com.meshroute.app.data.database.dao.SeenMessageDao
import com.meshroute.app.data.database.entity.QueuedPacketEntity
import com.meshroute.app.data.database.entity.SeenMessageEntity

@Database(
    entities = [
        QueuedPacketEntity::class,
        SeenMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun packetDao(): PacketDao
    abstract fun seenMessageDao(): SeenMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meshroute.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
