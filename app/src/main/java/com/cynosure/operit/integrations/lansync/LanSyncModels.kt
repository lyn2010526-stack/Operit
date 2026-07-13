package com.cynosure.operit.integrations.lansync

import kotlinx.serialization.Serializable

@Serializable
data class LanSyncDevice(
    val deviceId: String,
    val deviceName: String,
    val protocolVersion: Int,
    val host: String,
    val port: Int,
    val lastSeenAt: Long
)

@Serializable
data class LanSyncCursor(
    val peerDeviceId: String,
    val collection: String,
    val lastSequence: Long,
    val lastSyncAt: Long
)

@Serializable
enum class LanSyncOperation {
    UPSERT,
    DELETE
}

@Serializable
data class LanSyncChange(
    val collection: String,
    val entityId: String,
    val operation: LanSyncOperation,
    val sequence: Long,
    val revision: Long,
    val updatedAt: Long,
    val payloadHash: String,
    val payload: String? = null
)

@Serializable
data class LanSyncEnvelope(
    val protocolVersion: Int,
    val sourceDeviceId: String,
    val batchId: String,
    val changes: List<LanSyncChange>
)

@Serializable
data class LanSyncApplyResult(
    val batchId: String,
    val acceptedEntityIds: List<String>,
    val conflicts: List<LanSyncConflict>
)

@Serializable
data class LanSyncConflict(
    val collection: String,
    val entityId: String,
    val localRevision: Long,
    val remoteRevision: Long,
    val reason: String
)

enum class LanSyncSessionState {
    IDLE,
    DISCOVERING,
    CONNECTING,
    SYNCING,
    COMPLETED,
    FAILED
}
