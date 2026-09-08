package com.meshroute.app.data.database.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.meshroute.app.data.database.entity.QueuedPacketEntity;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Float;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class PacketDao_Impl implements PacketDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<QueuedPacketEntity> __insertionAdapterOfQueuedPacketEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateStatus;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  public PacketDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfQueuedPacketEntity = new EntityInsertionAdapter<QueuedPacketEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `queued_packets` (`packetId`,`originatorId`,`senderId`,`targetId`,`latitude`,`longitude`,`accuracy`,`priority`,`payload`,`ttl`,`hops`,`hopPathJson`,`timestamp`,`expiresAt`,`persistedAt`,`status`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final QueuedPacketEntity entity) {
        statement.bindString(1, entity.getPacketId());
        statement.bindString(2, entity.getOriginatorId());
        statement.bindString(3, entity.getSenderId());
        if (entity.getTargetId() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getTargetId());
        }
        if (entity.getLatitude() == null) {
          statement.bindNull(5);
        } else {
          statement.bindDouble(5, entity.getLatitude());
        }
        if (entity.getLongitude() == null) {
          statement.bindNull(6);
        } else {
          statement.bindDouble(6, entity.getLongitude());
        }
        if (entity.getAccuracy() == null) {
          statement.bindNull(7);
        } else {
          statement.bindDouble(7, entity.getAccuracy());
        }
        statement.bindString(8, entity.getPriority());
        statement.bindString(9, entity.getPayload());
        statement.bindLong(10, entity.getTtl());
        statement.bindLong(11, entity.getHops());
        statement.bindString(12, entity.getHopPathJson());
        statement.bindLong(13, entity.getTimestamp());
        statement.bindLong(14, entity.getExpiresAt());
        statement.bindLong(15, entity.getPersistedAt());
        statement.bindString(16, entity.getStatus());
      }
    };
    this.__preparedStmtOfUpdateStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE queued_packets SET status = ? WHERE packetId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM queued_packets WHERE packetId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM queued_packets";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final QueuedPacketEntity packet,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfQueuedPacketEntity.insert(packet);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<QueuedPacketEntity> packets,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfQueuedPacketEntity.insert(packets);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateStatus(final String packetId, final String newStatus,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateStatus.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, newStatus);
        _argIndex = 2;
        _stmt.bindString(_argIndex, packetId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final String packetId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, packetId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDelete.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getById(final String packetId,
      final Continuation<? super QueuedPacketEntity> $completion) {
    final String _sql = "SELECT * FROM queued_packets WHERE packetId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, packetId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<QueuedPacketEntity>() {
      @Override
      @Nullable
      public QueuedPacketEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPacketId = CursorUtil.getColumnIndexOrThrow(_cursor, "packetId");
          final int _cursorIndexOfOriginatorId = CursorUtil.getColumnIndexOrThrow(_cursor, "originatorId");
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderId");
          final int _cursorIndexOfTargetId = CursorUtil.getColumnIndexOrThrow(_cursor, "targetId");
          final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
          final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
          final int _cursorIndexOfAccuracy = CursorUtil.getColumnIndexOrThrow(_cursor, "accuracy");
          final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
          final int _cursorIndexOfPayload = CursorUtil.getColumnIndexOrThrow(_cursor, "payload");
          final int _cursorIndexOfTtl = CursorUtil.getColumnIndexOrThrow(_cursor, "ttl");
          final int _cursorIndexOfHops = CursorUtil.getColumnIndexOrThrow(_cursor, "hops");
          final int _cursorIndexOfHopPathJson = CursorUtil.getColumnIndexOrThrow(_cursor, "hopPathJson");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfExpiresAt = CursorUtil.getColumnIndexOrThrow(_cursor, "expiresAt");
          final int _cursorIndexOfPersistedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "persistedAt");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final QueuedPacketEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpPacketId;
            _tmpPacketId = _cursor.getString(_cursorIndexOfPacketId);
            final String _tmpOriginatorId;
            _tmpOriginatorId = _cursor.getString(_cursorIndexOfOriginatorId);
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpTargetId;
            if (_cursor.isNull(_cursorIndexOfTargetId)) {
              _tmpTargetId = null;
            } else {
              _tmpTargetId = _cursor.getString(_cursorIndexOfTargetId);
            }
            final Double _tmpLatitude;
            if (_cursor.isNull(_cursorIndexOfLatitude)) {
              _tmpLatitude = null;
            } else {
              _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
            }
            final Double _tmpLongitude;
            if (_cursor.isNull(_cursorIndexOfLongitude)) {
              _tmpLongitude = null;
            } else {
              _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
            }
            final Float _tmpAccuracy;
            if (_cursor.isNull(_cursorIndexOfAccuracy)) {
              _tmpAccuracy = null;
            } else {
              _tmpAccuracy = _cursor.getFloat(_cursorIndexOfAccuracy);
            }
            final String _tmpPriority;
            _tmpPriority = _cursor.getString(_cursorIndexOfPriority);
            final String _tmpPayload;
            _tmpPayload = _cursor.getString(_cursorIndexOfPayload);
            final int _tmpTtl;
            _tmpTtl = _cursor.getInt(_cursorIndexOfTtl);
            final int _tmpHops;
            _tmpHops = _cursor.getInt(_cursorIndexOfHops);
            final String _tmpHopPathJson;
            _tmpHopPathJson = _cursor.getString(_cursorIndexOfHopPathJson);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final long _tmpExpiresAt;
            _tmpExpiresAt = _cursor.getLong(_cursorIndexOfExpiresAt);
            final long _tmpPersistedAt;
            _tmpPersistedAt = _cursor.getLong(_cursorIndexOfPersistedAt);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            _result = new QueuedPacketEntity(_tmpPacketId,_tmpOriginatorId,_tmpSenderId,_tmpTargetId,_tmpLatitude,_tmpLongitude,_tmpAccuracy,_tmpPriority,_tmpPayload,_tmpTtl,_tmpHops,_tmpHopPathJson,_tmpTimestamp,_tmpExpiresAt,_tmpPersistedAt,_tmpStatus);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getByStatus(final String status,
      final Continuation<? super List<QueuedPacketEntity>> $completion) {
    final String _sql = "SELECT * FROM queued_packets WHERE status = ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, status);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<QueuedPacketEntity>>() {
      @Override
      @NonNull
      public List<QueuedPacketEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPacketId = CursorUtil.getColumnIndexOrThrow(_cursor, "packetId");
          final int _cursorIndexOfOriginatorId = CursorUtil.getColumnIndexOrThrow(_cursor, "originatorId");
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderId");
          final int _cursorIndexOfTargetId = CursorUtil.getColumnIndexOrThrow(_cursor, "targetId");
          final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
          final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
          final int _cursorIndexOfAccuracy = CursorUtil.getColumnIndexOrThrow(_cursor, "accuracy");
          final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
          final int _cursorIndexOfPayload = CursorUtil.getColumnIndexOrThrow(_cursor, "payload");
          final int _cursorIndexOfTtl = CursorUtil.getColumnIndexOrThrow(_cursor, "ttl");
          final int _cursorIndexOfHops = CursorUtil.getColumnIndexOrThrow(_cursor, "hops");
          final int _cursorIndexOfHopPathJson = CursorUtil.getColumnIndexOrThrow(_cursor, "hopPathJson");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfExpiresAt = CursorUtil.getColumnIndexOrThrow(_cursor, "expiresAt");
          final int _cursorIndexOfPersistedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "persistedAt");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final List<QueuedPacketEntity> _result = new ArrayList<QueuedPacketEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final QueuedPacketEntity _item;
            final String _tmpPacketId;
            _tmpPacketId = _cursor.getString(_cursorIndexOfPacketId);
            final String _tmpOriginatorId;
            _tmpOriginatorId = _cursor.getString(_cursorIndexOfOriginatorId);
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpTargetId;
            if (_cursor.isNull(_cursorIndexOfTargetId)) {
              _tmpTargetId = null;
            } else {
              _tmpTargetId = _cursor.getString(_cursorIndexOfTargetId);
            }
            final Double _tmpLatitude;
            if (_cursor.isNull(_cursorIndexOfLatitude)) {
              _tmpLatitude = null;
            } else {
              _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
            }
            final Double _tmpLongitude;
            if (_cursor.isNull(_cursorIndexOfLongitude)) {
              _tmpLongitude = null;
            } else {
              _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
            }
            final Float _tmpAccuracy;
            if (_cursor.isNull(_cursorIndexOfAccuracy)) {
              _tmpAccuracy = null;
            } else {
              _tmpAccuracy = _cursor.getFloat(_cursorIndexOfAccuracy);
            }
            final String _tmpPriority;
            _tmpPriority = _cursor.getString(_cursorIndexOfPriority);
            final String _tmpPayload;
            _tmpPayload = _cursor.getString(_cursorIndexOfPayload);
            final int _tmpTtl;
            _tmpTtl = _cursor.getInt(_cursorIndexOfTtl);
            final int _tmpHops;
            _tmpHops = _cursor.getInt(_cursorIndexOfHops);
            final String _tmpHopPathJson;
            _tmpHopPathJson = _cursor.getString(_cursorIndexOfHopPathJson);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final long _tmpExpiresAt;
            _tmpExpiresAt = _cursor.getLong(_cursorIndexOfExpiresAt);
            final long _tmpPersistedAt;
            _tmpPersistedAt = _cursor.getLong(_cursorIndexOfPersistedAt);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            _item = new QueuedPacketEntity(_tmpPacketId,_tmpOriginatorId,_tmpSenderId,_tmpTargetId,_tmpLatitude,_tmpLongitude,_tmpAccuracy,_tmpPriority,_tmpPayload,_tmpTtl,_tmpHops,_tmpHopPathJson,_tmpTimestamp,_tmpExpiresAt,_tmpPersistedAt,_tmpStatus);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getPendingQueuedPackets(
      final Continuation<? super List<QueuedPacketEntity>> $completion) {
    final String _sql = "SELECT * FROM queued_packets WHERE status = 'QUEUED' ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<QueuedPacketEntity>>() {
      @Override
      @NonNull
      public List<QueuedPacketEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPacketId = CursorUtil.getColumnIndexOrThrow(_cursor, "packetId");
          final int _cursorIndexOfOriginatorId = CursorUtil.getColumnIndexOrThrow(_cursor, "originatorId");
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderId");
          final int _cursorIndexOfTargetId = CursorUtil.getColumnIndexOrThrow(_cursor, "targetId");
          final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
          final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
          final int _cursorIndexOfAccuracy = CursorUtil.getColumnIndexOrThrow(_cursor, "accuracy");
          final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
          final int _cursorIndexOfPayload = CursorUtil.getColumnIndexOrThrow(_cursor, "payload");
          final int _cursorIndexOfTtl = CursorUtil.getColumnIndexOrThrow(_cursor, "ttl");
          final int _cursorIndexOfHops = CursorUtil.getColumnIndexOrThrow(_cursor, "hops");
          final int _cursorIndexOfHopPathJson = CursorUtil.getColumnIndexOrThrow(_cursor, "hopPathJson");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfExpiresAt = CursorUtil.getColumnIndexOrThrow(_cursor, "expiresAt");
          final int _cursorIndexOfPersistedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "persistedAt");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final List<QueuedPacketEntity> _result = new ArrayList<QueuedPacketEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final QueuedPacketEntity _item;
            final String _tmpPacketId;
            _tmpPacketId = _cursor.getString(_cursorIndexOfPacketId);
            final String _tmpOriginatorId;
            _tmpOriginatorId = _cursor.getString(_cursorIndexOfOriginatorId);
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpTargetId;
            if (_cursor.isNull(_cursorIndexOfTargetId)) {
              _tmpTargetId = null;
            } else {
              _tmpTargetId = _cursor.getString(_cursorIndexOfTargetId);
            }
            final Double _tmpLatitude;
            if (_cursor.isNull(_cursorIndexOfLatitude)) {
              _tmpLatitude = null;
            } else {
              _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
            }
            final Double _tmpLongitude;
            if (_cursor.isNull(_cursorIndexOfLongitude)) {
              _tmpLongitude = null;
            } else {
              _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
            }
            final Float _tmpAccuracy;
            if (_cursor.isNull(_cursorIndexOfAccuracy)) {
              _tmpAccuracy = null;
            } else {
              _tmpAccuracy = _cursor.getFloat(_cursorIndexOfAccuracy);
            }
            final String _tmpPriority;
            _tmpPriority = _cursor.getString(_cursorIndexOfPriority);
            final String _tmpPayload;
            _tmpPayload = _cursor.getString(_cursorIndexOfPayload);
            final int _tmpTtl;
            _tmpTtl = _cursor.getInt(_cursorIndexOfTtl);
            final int _tmpHops;
            _tmpHops = _cursor.getInt(_cursorIndexOfHops);
            final String _tmpHopPathJson;
            _tmpHopPathJson = _cursor.getString(_cursorIndexOfHopPathJson);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final long _tmpExpiresAt;
            _tmpExpiresAt = _cursor.getLong(_cursorIndexOfExpiresAt);
            final long _tmpPersistedAt;
            _tmpPersistedAt = _cursor.getLong(_cursorIndexOfPersistedAt);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            _item = new QueuedPacketEntity(_tmpPacketId,_tmpOriginatorId,_tmpSenderId,_tmpTargetId,_tmpLatitude,_tmpLongitude,_tmpAccuracy,_tmpPriority,_tmpPayload,_tmpTtl,_tmpHops,_tmpHopPathJson,_tmpTimestamp,_tmpExpiresAt,_tmpPersistedAt,_tmpStatus);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<QueuedPacketEntity>> observeAll() {
    final String _sql = "SELECT * FROM queued_packets ORDER BY persistedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"queued_packets"}, new Callable<List<QueuedPacketEntity>>() {
      @Override
      @NonNull
      public List<QueuedPacketEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPacketId = CursorUtil.getColumnIndexOrThrow(_cursor, "packetId");
          final int _cursorIndexOfOriginatorId = CursorUtil.getColumnIndexOrThrow(_cursor, "originatorId");
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderId");
          final int _cursorIndexOfTargetId = CursorUtil.getColumnIndexOrThrow(_cursor, "targetId");
          final int _cursorIndexOfLatitude = CursorUtil.getColumnIndexOrThrow(_cursor, "latitude");
          final int _cursorIndexOfLongitude = CursorUtil.getColumnIndexOrThrow(_cursor, "longitude");
          final int _cursorIndexOfAccuracy = CursorUtil.getColumnIndexOrThrow(_cursor, "accuracy");
          final int _cursorIndexOfPriority = CursorUtil.getColumnIndexOrThrow(_cursor, "priority");
          final int _cursorIndexOfPayload = CursorUtil.getColumnIndexOrThrow(_cursor, "payload");
          final int _cursorIndexOfTtl = CursorUtil.getColumnIndexOrThrow(_cursor, "ttl");
          final int _cursorIndexOfHops = CursorUtil.getColumnIndexOrThrow(_cursor, "hops");
          final int _cursorIndexOfHopPathJson = CursorUtil.getColumnIndexOrThrow(_cursor, "hopPathJson");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfExpiresAt = CursorUtil.getColumnIndexOrThrow(_cursor, "expiresAt");
          final int _cursorIndexOfPersistedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "persistedAt");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final List<QueuedPacketEntity> _result = new ArrayList<QueuedPacketEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final QueuedPacketEntity _item;
            final String _tmpPacketId;
            _tmpPacketId = _cursor.getString(_cursorIndexOfPacketId);
            final String _tmpOriginatorId;
            _tmpOriginatorId = _cursor.getString(_cursorIndexOfOriginatorId);
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpTargetId;
            if (_cursor.isNull(_cursorIndexOfTargetId)) {
              _tmpTargetId = null;
            } else {
              _tmpTargetId = _cursor.getString(_cursorIndexOfTargetId);
            }
            final Double _tmpLatitude;
            if (_cursor.isNull(_cursorIndexOfLatitude)) {
              _tmpLatitude = null;
            } else {
              _tmpLatitude = _cursor.getDouble(_cursorIndexOfLatitude);
            }
            final Double _tmpLongitude;
            if (_cursor.isNull(_cursorIndexOfLongitude)) {
              _tmpLongitude = null;
            } else {
              _tmpLongitude = _cursor.getDouble(_cursorIndexOfLongitude);
            }
            final Float _tmpAccuracy;
            if (_cursor.isNull(_cursorIndexOfAccuracy)) {
              _tmpAccuracy = null;
            } else {
              _tmpAccuracy = _cursor.getFloat(_cursorIndexOfAccuracy);
            }
            final String _tmpPriority;
            _tmpPriority = _cursor.getString(_cursorIndexOfPriority);
            final String _tmpPayload;
            _tmpPayload = _cursor.getString(_cursorIndexOfPayload);
            final int _tmpTtl;
            _tmpTtl = _cursor.getInt(_cursorIndexOfTtl);
            final int _tmpHops;
            _tmpHops = _cursor.getInt(_cursorIndexOfHops);
            final String _tmpHopPathJson;
            _tmpHopPathJson = _cursor.getString(_cursorIndexOfHopPathJson);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final long _tmpExpiresAt;
            _tmpExpiresAt = _cursor.getLong(_cursorIndexOfExpiresAt);
            final long _tmpPersistedAt;
            _tmpPersistedAt = _cursor.getLong(_cursorIndexOfPersistedAt);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            _item = new QueuedPacketEntity(_tmpPacketId,_tmpOriginatorId,_tmpSenderId,_tmpTargetId,_tmpLatitude,_tmpLongitude,_tmpAccuracy,_tmpPriority,_tmpPayload,_tmpTtl,_tmpHops,_tmpHopPathJson,_tmpTimestamp,_tmpExpiresAt,_tmpPersistedAt,_tmpStatus);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Integer> observePendingCount() {
    final String _sql = "SELECT COUNT(*) FROM queued_packets WHERE status = 'QUEUED'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"queued_packets"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Integer> observeTotalCount() {
    final String _sql = "SELECT COUNT(*) FROM queued_packets";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"queued_packets"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
