package com.cynosure.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cynosure.operit.data.model.LanSyncConflictEntity
import com.cynosure.operit.data.model.LanSyncCursorEntity
import com.cynosure.operit.data.model.LanSyncEntityStateEntity
import com.cynosure.operit.data.model.LanSyncJournalEntity
import com.cynosure.operit.data.model.LanSyncPeerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LanSyncDao {
    @Query("SELECT * FROM lan_sync_peers ORDER BY deviceName COLLATE NOCASE")
    fun observePeers(): Flow<List<LanSyncPeerEntity>>

    @Query("SELECT * FROM lan_sync_peers ORDER BY deviceName COLLATE NOCASE")
    suspend fun getPeers(): List<LanSyncPeerEntity>

    @Query("SELECT * FROM lan_sync_peers WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getPeer(deviceId: String): LanSyncPeerEntity?

    @Query("SELECT * FROM lan_sync_peers WHERE incomingToken = :token LIMIT 1")
    suspend fun getPeerByIncomingToken(token: String): LanSyncPeerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeer(peer: LanSyncPeerEntity)

    @Query("UPDATE lan_sync_peers SET lastSeenAt = :now WHERE deviceId = :deviceId")
    suspend fun markPeerSeen(deviceId: String, now: Long)

    @Query("UPDATE lan_sync_peers SET lastSyncAt = :now, lastError = :error WHERE deviceId = :deviceId")
    suspend fun updatePeerSyncResult(deviceId: String, now: Long, error: String?)

    @Query("SELECT * FROM lan_sync_cursors WHERE peerDeviceId = :peerDeviceId AND collection = :collection LIMIT 1")
    suspend fun getCursor(peerDeviceId: String, collection: String): LanSyncCursorEntity?

    @Query("SELECT * FROM lan_sync_cursors WHERE peerDeviceId = :peerDeviceId")
    suspend fun getCursors(peerDeviceId: String): List<LanSyncCursorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCursor(cursor: LanSyncCursorEntity)

    @Query("SELECT * FROM lan_sync_entity_state")
    suspend fun getAllEntityStates(): List<LanSyncEntityStateEntity>

    @Query("SELECT * FROM lan_sync_entity_state WHERE collection = :collection AND entityId = :entityId LIMIT 1")
    suspend fun getEntityState(collection: String, entityId: String): LanSyncEntityStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntityState(state: LanSyncEntityStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournal(entry: LanSyncJournalEntity): Long

    @Query("SELECT * FROM lan_sync_journal WHERE sequence > :afterSequence AND collection IN (:collections) ORDER BY sequence ASC LIMIT :limit")
    suspend fun getJournalAfter(afterSequence: Long, collections: List<String>, limit: Int): List<LanSyncJournalEntity>

    @Query("SELECT * FROM lan_sync_journal WHERE sequence > :afterSequence AND collection = :collection ORDER BY sequence ASC LIMIT :limit")
    suspend fun getJournalAfterForCollection(afterSequence: Long, collection: String, limit: Int): List<LanSyncJournalEntity>

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM lan_sync_journal")
    suspend fun getLatestSequence(): Long

    @Insert
    suspend fun insertConflict(conflict: LanSyncConflictEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConflict(conflict: LanSyncConflictEntity): Long

    @Query("SELECT * FROM lan_sync_conflicts WHERE id = :id LIMIT 1")
    suspend fun getConflict(id: Long): LanSyncConflictEntity?

    @Query("SELECT * FROM lan_sync_conflicts WHERE peerDeviceId = :peerDeviceId AND collection = :collection AND entityId = :entityId AND resolvedAt IS NULL LIMIT 1")
    suspend fun getOpenConflict(peerDeviceId: String, collection: String, entityId: String): LanSyncConflictEntity?

    @Query("SELECT * FROM lan_sync_conflicts WHERE peerDeviceId = :peerDeviceId AND collection = :collection AND entityId = :entityId AND remoteRevision = :remoteRevision AND remoteHash = :remoteHash AND resolvedAt IS NOT NULL ORDER BY resolvedAt DESC LIMIT 1")
    suspend fun getResolvedConflict(
        peerDeviceId: String,
        collection: String,
        entityId: String,
        remoteRevision: Long,
        remoteHash: String,
    ): LanSyncConflictEntity?

    @Query("UPDATE lan_sync_conflicts SET resolvedAt = :resolvedAt, resolution = :resolution WHERE id = :id AND resolvedAt IS NULL")
    suspend fun markConflictResolved(id: Long, resolvedAt: Long, resolution: String): Int

    @Query("SELECT * FROM lan_sync_conflicts WHERE resolvedAt IS NULL ORDER BY createdAt DESC")
    fun observeOpenConflicts(): Flow<List<LanSyncConflictEntity>>

    @Query("SELECT COUNT(*) FROM lan_sync_conflicts WHERE resolvedAt IS NULL")
    suspend fun countOpenConflicts(): Int
}
