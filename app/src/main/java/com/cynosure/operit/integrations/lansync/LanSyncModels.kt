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

object LanSyncCollections {
    const val MEMORIES = "memories"
    const val SKILLS = "skills"
    const val CHARACTER_CARDS = "character_cards"
    const val CHARACTER_GROUPS = "character_groups"
    const val CHATS = "chats"
    const val MESSAGES = "messages"
    const val MESSAGE_VARIANTS = "message_variants"

    val all = listOf(MEMORIES, SKILLS, CHARACTER_CARDS, CHARACTER_GROUPS, CHATS, MESSAGES, MESSAGE_VARIANTS)
}

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
enum class LanSyncConflictResolution {
    LOCAL,
    REMOTE,
}

@Serializable
data class LanSyncChange(
    val collection: String,
    val entityId: String,
    val operation: LanSyncOperation,
    val sequence: Long,
    val revision: Long,
    val updatedAt: Long,
    val baseHash: String? = null,
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
    val conflicts: List<LanSyncConflict>,
    val acceptedSequences: Map<String, Long> = emptyMap(),
)

@Serializable
data class LanSyncConflict(
    val collection: String,
    val entityId: String,
    val localRevision: Long,
    val remoteRevision: Long,
    val reason: String
)

@Serializable
data class LanSyncPairRequest(
    val pairingCode: String,
    val deviceId: String,
    val deviceName: String,
    val host: String,
    val port: Int,
    val tokenForRequester: String,
    val enabledCollections: List<String>,
)

@Serializable
data class LanSyncPairResponse(
    val deviceId: String,
    val deviceName: String,
    val host: String,
    val port: Int,
    val tokenForServer: String,
)

@Serializable
data class LanSyncPullRequest(
    val cursors: Map<String, Long>,
    val limit: Int = 500,
)

@Serializable
data class LanSyncPullResponse(
    val envelope: LanSyncEnvelope,
    val latestSequences: Map<String, Long>,
)

@Serializable
data class LanSyncAckRequest(val sequences: Map<String, Long>)

internal fun validateLanSyncEnvelope(
    envelope: LanSyncEnvelope,
    expectedProtocolVersion: Int,
    peerDeviceId: String,
    enabledCollections: Set<String>,
) {
    require(envelope.protocolVersion == expectedProtocolVersion) { "Unsupported protocol version ${envelope.protocolVersion}" }
    require(envelope.sourceDeviceId == peerDeviceId) { "Envelope source does not match authenticated peer" }
    envelope.changes.forEach { change ->
        require(change.collection in LanSyncCollections.all) { "Unknown collection ${change.collection}" }
        require(change.collection in enabledCollections) { "Collection ${change.collection} is disabled for this peer" }
        require(change.sequence > 0L) { "Invalid sequence for ${change.collection}/${change.entityId}" }
        require(change.revision > 0L) { "Invalid revision for ${change.collection}/${change.entityId}" }
        require(change.entityId.isNotBlank()) { "Blank entity id in ${change.collection}" }
        require(change.payloadHash.isNotBlank()) { "Blank payload hash for ${change.collection}/${change.entityId}" }
        require(change.operation == LanSyncOperation.DELETE || change.payload != null) {
            "Missing payload for ${change.collection}/${change.entityId}"
        }
    }
}

internal fun mergeLanSyncPages(
    pages: Collection<List<LanSyncChange>>,
    limit: Int,
): List<LanSyncChange> = pages.flatten().sortedBy { it.sequence }.take(limit)

@Serializable
data class LanSyncFileEntry(val relativePath: String, val contentBase64: String)

@Serializable
data class LanSyncFileBundle(val files: List<LanSyncFileEntry>)

enum class LanSyncSessionState {
    IDLE,
    DISCOVERING,
    CONNECTING,
    SYNCING,
    COMPLETED,
    FAILED
}
