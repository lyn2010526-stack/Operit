package com.cynosure.operit.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lan_sync_peers",
    indices = [Index(value = ["deviceId"], unique = true), Index(value = ["incomingToken"], unique = true)]
)
data class LanSyncPeerEntity(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val host: String,
    val port: Int,
    val outgoingToken: String,
    val incomingToken: String,
    val enabledCollections: String,
    val lastSeenAt: Long = 0L,
    val lastSyncAt: Long = 0L,
    val lastError: String? = null,
)

@Entity(tableName = "lan_sync_cursors", primaryKeys = ["peerDeviceId", "collection"])
data class LanSyncCursorEntity(
    val peerDeviceId: String,
    val collection: String,
    val lastSequence: Long,
    val lastSyncAt: Long,
)

@Entity(
    tableName = "lan_sync_entity_state",
    primaryKeys = ["collection", "entityId"],
    indices = [Index(value = ["updatedAt"])]
)
data class LanSyncEntityStateEntity(
    val collection: String,
    val entityId: String,
    val revision: Long,
    val payloadHash: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "lan_sync_journal",
    indices = [Index(value = ["collection", "sequence"], unique = true)]
)
data class LanSyncJournalEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0L,
    val collection: String,
    val entityId: String,
    val operation: String,
    val revision: Long,
    val baseHash: String?,
    val payloadHash: String,
    val payload: String?,
    val updatedAt: Long,
)

@Entity(
    tableName = "lan_sync_conflicts",
    indices = [
        Index(value = ["peerDeviceId", "resolvedAt"]),
        Index(value = ["peerDeviceId", "collection", "entityId", "resolvedAt"]),
    ]
)
data class LanSyncConflictEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val peerDeviceId: String,
    val collection: String,
    val entityId: String,
    val localRevision: Long,
    val remoteRevision: Long,
    val localHash: String?,
    val remoteHash: String,
    val remoteSequence: Long,
    val remoteOperation: String,
    val remoteUpdatedAt: Long,
    val remotePayload: String?,
    val reason: String,
    val createdAt: Long,
    val resolvedAt: Long? = null,
    val resolution: String? = null,
)
