package com.meshroute.app.data.database;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.meshroute.app.data.database.dao.PacketDao;
import com.meshroute.app.data.database.dao.PacketDao_Impl;
import com.meshroute.app.data.database.dao.SeenMessageDao;
import com.meshroute.app.data.database.dao.SeenMessageDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile PacketDao _packetDao;

  private volatile SeenMessageDao _seenMessageDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `queued_packets` (`packetId` TEXT NOT NULL, `originatorId` TEXT NOT NULL, `senderId` TEXT NOT NULL, `targetId` TEXT, `latitude` REAL, `longitude` REAL, `accuracy` REAL, `priority` TEXT NOT NULL, `payload` TEXT NOT NULL, `ttl` INTEGER NOT NULL, `hops` INTEGER NOT NULL, `hopPathJson` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, `persistedAt` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`packetId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `seen_messages` (`messageId` TEXT NOT NULL, `seenAt` INTEGER NOT NULL, PRIMARY KEY(`messageId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '0ac72777c0ef636d0c73310bd4bd1513')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `queued_packets`");
        db.execSQL("DROP TABLE IF EXISTS `seen_messages`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsQueuedPackets = new HashMap<String, TableInfo.Column>(16);
        _columnsQueuedPackets.put("packetId", new TableInfo.Column("packetId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedPackets.put("originatorId", new TableInfo.Column("originatorId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedPackets.put("senderId", new TableInfo.Column("senderId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedPackets.put("targetId", new TableInfo.Column("targetId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedPackets.put("latitude", new TableInfo.Column("latitude", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedPackets.put("longitude", new TableInfo.Column("longitude", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedPackets.put("accuracy", new TableInfo.Column("accuracy", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedPackets.put("priority", new TableInfo.Column("priority", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedPackets.put("payload", new TableInfo.Column("payload", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedPackets.put("ttl", new TableInfo.Column("ttl", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedPackets.put("hops", new TableInfo.Column("hops", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedPackets.put("hopPathJson", new TableInfo.Column("hopPathJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedPackets.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedPackets.put("expiresAt", new TableInfo.Column("expiresAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedPackets.put("persistedAt", new TableInfo.Column("persistedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsQueuedPackets.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysQueuedPackets = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesQueuedPackets = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoQueuedPackets = new TableInfo("queued_packets", _columnsQueuedPackets, _foreignKeysQueuedPackets, _indicesQueuedPackets);
        final TableInfo _existingQueuedPackets = TableInfo.read(db, "queued_packets");
        if (!_infoQueuedPackets.equals(_existingQueuedPackets)) {
          return new RoomOpenHelper.ValidationResult(false, "queued_packets(com.meshroute.app.data.database.entity.QueuedPacketEntity).\n"
                  + " Expected:\n" + _infoQueuedPackets + "\n"
                  + " Found:\n" + _existingQueuedPackets);
        }
        final HashMap<String, TableInfo.Column> _columnsSeenMessages = new HashMap<String, TableInfo.Column>(2);
        _columnsSeenMessages.put("messageId", new TableInfo.Column("messageId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSeenMessages.put("seenAt", new TableInfo.Column("seenAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSeenMessages = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSeenMessages = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSeenMessages = new TableInfo("seen_messages", _columnsSeenMessages, _foreignKeysSeenMessages, _indicesSeenMessages);
        final TableInfo _existingSeenMessages = TableInfo.read(db, "seen_messages");
        if (!_infoSeenMessages.equals(_existingSeenMessages)) {
          return new RoomOpenHelper.ValidationResult(false, "seen_messages(com.meshroute.app.data.database.entity.SeenMessageEntity).\n"
                  + " Expected:\n" + _infoSeenMessages + "\n"
                  + " Found:\n" + _existingSeenMessages);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "0ac72777c0ef636d0c73310bd4bd1513", "aeccbb9e98c053afe47f2f48df147e8f");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "queued_packets","seen_messages");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `queued_packets`");
      _db.execSQL("DELETE FROM `seen_messages`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(PacketDao.class, PacketDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SeenMessageDao.class, SeenMessageDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public PacketDao packetDao() {
    if (_packetDao != null) {
      return _packetDao;
    } else {
      synchronized(this) {
        if(_packetDao == null) {
          _packetDao = new PacketDao_Impl(this);
        }
        return _packetDao;
      }
    }
  }

  @Override
  public SeenMessageDao seenMessageDao() {
    if (_seenMessageDao != null) {
      return _seenMessageDao;
    } else {
      synchronized(this) {
        if(_seenMessageDao == null) {
          _seenMessageDao = new SeenMessageDao_Impl(this);
        }
        return _seenMessageDao;
      }
    }
  }
}
