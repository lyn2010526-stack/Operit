package com.cynosure.operit.integrations.lansync

import android.content.Context
import android.util.Base64
import com.cynosure.operit.core.tools.skill.SkillManager
import com.cynosure.operit.data.db.AppDatabase
import com.cynosure.operit.data.model.CharacterCard
import com.cynosure.operit.data.model.CharacterGroupCard
import com.cynosure.operit.data.model.ChatEntity
import com.cynosure.operit.data.model.ImportStrategy
import com.cynosure.operit.data.model.LanSyncConflictEntity
import com.cynosure.operit.data.model.LanSyncCursorEntity
import com.cynosure.operit.data.model.LanSyncEntityStateEntity
import com.cynosure.operit.data.model.LanSyncJournalEntity
import com.cynosure.operit.data.model.MemoryExportData
import com.cynosure.operit.data.model.MessageEntity
import com.cynosure.operit.data.model.MessageVariantEntity
import com.cynosure.operit.data.preferences.CharacterCardManager
import com.cynosure.operit.data.preferences.CharacterGroupCardManager
import com.cynosure.operit.data.preferences.UserPreferencesManager
import com.cynosure.operit.data.repository.MemoryRepository
import com.cynosure.operit.data.skill.SkillRepository
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class LanSyncEngine private constructor(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val syncDao = database.lanSyncDao()
    private val chatDao = database.chatDao()
    private val messageDao = database.messageDao()
    private val variantDao = database.messageVariantDao()
    private val characterCardManager = CharacterCardManager.getInstance(context)
    private val characterGroupManager = CharacterGroupCardManager.getInstance(context)
    private val skillRepository = SkillRepository.getInstance(context)
    private val skillManager = SkillManager.getInstance(context)
    private val preferencesManager = UserPreferencesManager.getInstance(context)
    private val gson = Gson()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }
    private val syncMutex = Mutex()

    suspend fun scan(collections: Set<String>) = syncMutex.withLock {
        scanLocked(collections)
    }

    private suspend fun scanLocked(collections: Set<String>) = withContext(Dispatchers.IO) {
        val snapshots = linkedMapOf<String, Map<String, Snapshot>>()
        if (LanSyncCollections.CHATS in collections) snapshots[LanSyncCollections.CHATS] = scanChats()
        if (LanSyncCollections.MESSAGES in collections) snapshots[LanSyncCollections.MESSAGES] = scanMessages()
        if (LanSyncCollections.MESSAGE_VARIANTS in collections) snapshots[LanSyncCollections.MESSAGE_VARIANTS] = scanVariants()
        if (LanSyncCollections.MEMORIES in collections) snapshots[LanSyncCollections.MEMORIES] = scanMemories()
        if (LanSyncCollections.SKILLS in collections) snapshots[LanSyncCollections.SKILLS] = scanSkills()
        if (LanSyncCollections.CHARACTER_CARDS in collections) snapshots[LanSyncCollections.CHARACTER_CARDS] = scanCharacterCards()
        if (LanSyncCollections.CHARACTER_GROUPS in collections) snapshots[LanSyncCollections.CHARACTER_GROUPS] = scanCharacterGroups()

        val previousStates = syncDao.getAllEntityStates().groupBy { it.collection }
        snapshots.forEach { (collection, current) ->
            val previous = previousStates[collection].orEmpty().associateBy { it.entityId }
            current.forEach { (entityId, snapshot) ->
                val state = previous[entityId]
                if (state?.deletedAt != null && snapshot.deletedAt != null) {
                    return@forEach
                }
                if (state == null || state.payloadHash != snapshot.hash || state.deletedAt != snapshot.deletedAt) {
                    val revision = (state?.revision ?: 0L) + 1L
                    syncDao.insertJournal(
                        LanSyncJournalEntity(
                            collection = collection,
                            entityId = entityId,
                            operation = if (snapshot.deletedAt == null) LanSyncOperation.UPSERT.name else LanSyncOperation.DELETE.name,
                            revision = revision,
                            baseHash = state?.payloadHash,
                            payloadHash = snapshot.hash,
                            payload = snapshot.payload,
                            updatedAt = snapshot.updatedAt,
                        )
                    )
                    syncDao.upsertEntityState(
                        LanSyncEntityStateEntity(collection, entityId, revision, snapshot.hash, snapshot.updatedAt, snapshot.deletedAt)
                    )
                }
            }
            previous.values.filter { it.entityId !in current && it.deletedAt == null }.forEach { state ->
                val now = System.currentTimeMillis()
                val tombstoneHash = sha256("$collection:${state.entityId}:$now")
                val revision = state.revision + 1L
                syncDao.insertJournal(
                    LanSyncJournalEntity(
                        collection = collection,
                        entityId = state.entityId,
                        operation = LanSyncOperation.DELETE.name,
                        revision = revision,
                        baseHash = state.payloadHash,
                        payloadHash = tombstoneHash,
                        payload = null,
                        updatedAt = now,
                    )
                )
                syncDao.upsertEntityState(state.copy(revision = revision, payloadHash = tombstoneHash, updatedAt = now, deletedAt = now))
            }
        }
    }

    suspend fun pull(peerDeviceId: String, request: LanSyncPullRequest): LanSyncPullResponse {
        val peer = requireNotNull(syncDao.getPeer(peerDeviceId)) { "Unknown peer" }
        val enabled = decodeCollections(peer.enabledCollections)
        val limit = request.limit.coerceIn(1, 1000)
        val changes = syncMutex.withLock {
            scanLocked(enabled)
            mergeLanSyncPages(
                enabled.map { collection ->
                    syncDao.getJournalAfterForCollection(request.cursors[collection] ?: 0L, collection, limit)
                        .map(::journalToChange)
                },
                limit,
            )
        }
        return LanSyncPullResponse(
            envelope = LanSyncEnvelope(PROTOCOL_VERSION, LanSyncPreferences(context).deviceId, UUID.randomUUID().toString(), changes),
            latestSequences = changes.groupBy { it.collection }.mapValues { (_, values) -> values.maxOf { it.sequence } },
        )
    }

    private fun journalToChange(entry: LanSyncJournalEntity) =
        LanSyncChange(
                collection = entry.collection,
                entityId = entry.entityId,
                operation = LanSyncOperation.valueOf(entry.operation),
                sequence = entry.sequence,
                revision = entry.revision,
                updatedAt = entry.updatedAt,
                baseHash = entry.baseHash,
                payloadHash = entry.payloadHash,
                payload = entry.payload,
            )

    suspend fun apply(peerDeviceId: String, envelope: LanSyncEnvelope): LanSyncApplyResult = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val peer = requireNotNull(syncDao.getPeer(peerDeviceId)) { "Unknown peer" }
            val enabled = decodeCollections(peer.enabledCollections)
            validateLanSyncEnvelope(envelope, PROTOCOL_VERSION, peerDeviceId, enabled)
            applyLocked(peerDeviceId, envelope)
        }
    }

    private suspend fun applyLocked(peerDeviceId: String, envelope: LanSyncEnvelope): LanSyncApplyResult {
        val accepted = mutableListOf<String>()
        val conflicts = mutableListOf<LanSyncConflict>()
        val acceptedSequences = mutableMapOf<String, Long>()
        val blockedCollections = mutableSetOf<String>()
        envelope.changes.sortedWith(compareBy<LanSyncChange> { collectionOrder(it.collection) }.thenBy { it.sequence }).forEach { change ->
            if (change.collection in blockedCollections) return@forEach
            val local = syncDao.getEntityState(change.collection, change.entityId)
            val alreadyApplied = local?.revision == change.revision && local.payloadHash == change.payloadHash
            val resolved = syncDao.getResolvedConflict(
                peerDeviceId,
                change.collection,
                change.entityId,
                change.revision,
                change.payloadHash,
            )
            if (alreadyApplied || resolved != null) {
                accepted += change.entityId
                acceptedSequences[change.collection] = maxOf(acceptedSequences[change.collection] ?: 0L, change.sequence)
                return@forEach
            }
            val baseMatches = local == null || local.payloadHash == change.baseHash
            if (!baseMatches && local?.payloadHash != change.payloadHash) {
                val conflict = LanSyncConflict(
                    collection = change.collection,
                    entityId = change.entityId,
                    localRevision = local?.revision ?: 0L,
                    remoteRevision = change.revision,
                    reason = "baseHash mismatch",
                )
                conflicts += conflict
                blockedCollections += change.collection
                val existingConflict = syncDao.getOpenConflict(peerDeviceId, change.collection, change.entityId)
                syncDao.upsertConflict(
                    LanSyncConflictEntity(
                        id = existingConflict?.id ?: 0L,
                        peerDeviceId = peerDeviceId,
                        collection = change.collection,
                        entityId = change.entityId,
                        localRevision = local?.revision ?: 0L,
                        remoteRevision = change.revision,
                        localHash = local?.payloadHash,
                        remoteHash = change.payloadHash,
                        remoteSequence = change.sequence,
                        remoteOperation = change.operation.name,
                        remoteUpdatedAt = change.updatedAt,
                        remotePayload = change.payload,
                        reason = conflict.reason,
                        createdAt = existingConflict?.createdAt ?: System.currentTimeMillis(),
                    )
                )
                return@forEach
            }
            applyChange(change)
            syncDao.upsertEntityState(change.toEntityState())
            accepted += change.entityId
            acceptedSequences[change.collection] = maxOf(acceptedSequences[change.collection] ?: 0L, change.sequence)
        }
        return LanSyncApplyResult(envelope.batchId, accepted, conflicts, acceptedSequences)
    }

    suspend fun resolveConflict(conflictId: Long, resolution: LanSyncConflictResolution): Map<String, Long> = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val conflict = requireNotNull(syncDao.getConflict(conflictId)) { "Conflict not found" }
            require(conflict.resolvedAt == null) { "Conflict already resolved" }
            require(conflict.remoteSequence > 0L) { "Conflict has no remote sequence" }
            if (resolution == LanSyncConflictResolution.REMOTE) {
                val change = conflict.toRemoteChange()
                applyChange(change)
                syncDao.upsertEntityState(change.toEntityState())
            }
            val resolvedAt = System.currentTimeMillis()
            check(syncDao.markConflictResolved(conflictId, resolvedAt, resolution.name) == 1) { "Conflict resolution raced" }
            acknowledge(conflict.peerDeviceId, "in", mapOf(conflict.collection to conflict.remoteSequence))
            mapOf(conflict.collection to conflict.remoteSequence)
        }
    }

    private fun LanSyncChange.toEntityState() =
        LanSyncEntityStateEntity(
            collection = collection,
            entityId = entityId,
            revision = revision,
            payloadHash = payloadHash,
            updatedAt = updatedAt,
            deletedAt = updatedAt.takeIf { operation == LanSyncOperation.DELETE },
        )

    private fun LanSyncConflictEntity.toRemoteChange() =
        LanSyncChange(
            collection = collection,
            entityId = entityId,
            operation = LanSyncOperation.valueOf(remoteOperation),
            sequence = remoteSequence,
            revision = remoteRevision,
            updatedAt = remoteUpdatedAt,
            baseHash = localHash,
            payloadHash = remoteHash,
            payload = remotePayload,
        )

    suspend fun acknowledge(peerDeviceId: String, sequences: Map<String, Long>) {
        val now = System.currentTimeMillis()
        sequences.forEach { (collection, sequence) ->
            val current = syncDao.getCursor(peerDeviceId, collection)?.lastSequence ?: 0L
            syncDao.upsertCursor(LanSyncCursorEntity(peerDeviceId, collection, maxOf(current, sequence), now))
        }
    }

    suspend fun cursors(peerDeviceId: String, direction: String): Map<String, Long> {
        val prefix = "$direction:"
        return syncDao.getCursors(peerDeviceId)
            .filter { it.collection.startsWith(prefix) }
            .associate { it.collection.removePrefix(prefix) to it.lastSequence }
    }

    suspend fun acknowledge(peerDeviceId: String, direction: String, sequences: Map<String, Long>) {
        acknowledge(peerDeviceId, sequences.mapKeys { "$direction:${it.key}" })
    }

    private suspend fun applyChange(change: LanSyncChange) {
        if (change.operation == LanSyncOperation.DELETE) {
            applyDelete(change)
            return
        }
        val payload = requireNotNull(change.payload) { "Missing payload for ${change.collection}/${change.entityId}" }
        when (change.collection) {
            LanSyncCollections.CHATS -> chatDao.insertChat(json.decodeFromString<ChatEntity>(payload))
            LanSyncCollections.MESSAGES -> {
                val remote = json.decodeFromString<MessageEntity>(payload)
                val localId = messageDao.getMessageBySyncIdIncludingDeleted(remote.syncId)?.messageId ?: 0L
                messageDao.insertMessage(remote.copy(messageId = localId))
            }
            LanSyncCollections.MESSAGE_VARIANTS -> {
                val remote = json.decodeFromString<MessageVariantEntity>(payload)
                val localId = variantDao.getVariantBySyncIdIncludingDeleted(remote.syncId)?.variantId ?: 0L
                variantDao.insertVariant(remote.copy(variantId = localId))
            }
            LanSyncCollections.CHARACTER_CARDS -> characterCardManager.upsertSyncedCharacterCard(gson.fromJson(payload, CharacterCard::class.java))
            LanSyncCollections.CHARACTER_GROUPS -> characterGroupManager.upsertSyncedCharacterGroupCard(gson.fromJson(payload, CharacterGroupCard::class.java))
            LanSyncCollections.MEMORIES -> activeMemoryRepository().importMemoriesFromJson(payload, ImportStrategy.UPDATE)
            LanSyncCollections.SKILLS -> applySkillBundle(change.entityId, json.decodeFromString(payload))
            else -> error("Unsupported collection ${change.collection}")
        }
    }

    private suspend fun applyDelete(change: LanSyncChange) {
        when (change.collection) {
            LanSyncCollections.CHATS -> chatDao.getChatByIdIncludingDeleted(change.entityId)?.let {
                chatDao.insertChat(it.copy(deletedAt = change.updatedAt, updatedAt = change.updatedAt, revision = change.revision))
            }
            LanSyncCollections.MESSAGES -> messageDao.getMessageBySyncIdIncludingDeleted(change.entityId)?.let {
                messageDao.insertMessage(it.copy(deletedAt = change.updatedAt, updatedAt = change.updatedAt, revision = change.revision))
            }
            LanSyncCollections.MESSAGE_VARIANTS -> variantDao.getVariantBySyncIdIncludingDeleted(change.entityId)?.let {
                variantDao.insertVariant(
                    it.copy(
                        variantIndex = -it.variantId.toInt(),
                        deletedAt = change.updatedAt,
                        updatedAt = change.updatedAt,
                        revision = change.revision,
                    )
                )
            }
            LanSyncCollections.CHARACTER_CARDS -> characterCardManager.deleteCharacterCard(change.entityId)
            LanSyncCollections.CHARACTER_GROUPS -> characterGroupManager.deleteCharacterGroupCard(change.entityId)
            LanSyncCollections.MEMORIES -> activeMemoryRepository().findMemoryByUuid(change.entityId)?.let { activeMemoryRepository().deleteMemory(it.id) }
            LanSyncCollections.SKILLS -> File(skillRepository.getSkillsDirectoryPath(), change.entityId).deleteRecursively()
        }
    }

    private suspend fun scanChats(): Map<String, Snapshot> = chatDao.getAllChatsIncludingDeleted().associate { entity ->
        entity.id to snapshot(json.encodeToString(entity), entity.updatedAt, entity.deletedAt)
    }

    private suspend fun scanMessages(): Map<String, Snapshot> = messageDao.getAllMessagesIncludingDeleted().associate { entity ->
        entity.syncId to snapshot(json.encodeToString(entity.copy(messageId = 0L)), entity.updatedAt, entity.deletedAt)
    }

    private suspend fun scanVariants(): Map<String, Snapshot> = variantDao.getAllVariantsIncludingDeleted().associate { entity ->
        entity.syncId to snapshot(json.encodeToString(entity.copy(variantId = 0L)), entity.updatedAt, entity.deletedAt)
    }

    private suspend fun scanCharacterCards(): Map<String, Snapshot> = characterCardManager.getAllCharacterCards().associate { card ->
        val payload = canonicalize(gson.toJson(card))
        card.id to Snapshot(payload, sha256(payload), card.updatedAt, null)
    }

    private suspend fun scanCharacterGroups(): Map<String, Snapshot> = characterGroupManager.getAllCharacterGroupCards().associate { group ->
        val payload = canonicalize(gson.toJson(group))
        group.id to Snapshot(payload, sha256(payload), group.updatedAt, null)
    }

    private suspend fun scanMemories(): Map<String, Snapshot> {
        val export = json.decodeFromString<MemoryExportData>(activeMemoryRepository().exportMemoriesToJson())
        return export.memories.associate { memory ->
            val relatedLinks = export.links.filter { it.sourceUuid == memory.uuid || it.targetUuid == memory.uuid }
            val payload = canonicalize(
                json.encodeToString(MemoryExportData(listOf(memory), relatedLinks, memory.updatedAt, export.version))
            )
            memory.uuid to Snapshot(payload, sha256(payload), memory.updatedAt.time, null)
        }
    }

    private fun scanSkills(): Map<String, Snapshot> {
        val root = File(skillRepository.getSkillsDirectoryPath())
        return root.listFiles().orEmpty().filter { it.isDirectory }.associate { directory ->
            val files = directory.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(directory).invariantSeparatorsPath }.map { file ->
                LanSyncFileEntry(
                    relativePath = file.relativeTo(directory).invariantSeparatorsPath,
                    contentBase64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP),
                )
            }.toList()
            val payload = canonicalize(json.encodeToString(LanSyncFileBundle(files)))
            directory.name to Snapshot(payload, sha256(payload), directory.lastModified(), null)
        }
    }

    private fun applySkillBundle(skillId: String, bundle: LanSyncFileBundle) {
        require(skillId.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid skill id" }
        val directory = File(skillRepository.getSkillsDirectoryPath(), skillId)
        if (directory.exists()) directory.deleteRecursively()
        directory.mkdirs()
        bundle.files.forEach { entry ->
            val file = File(directory, entry.relativePath).canonicalFile
            require(file.path.startsWith(directory.canonicalPath + File.separator)) { "Invalid skill path" }
            file.parentFile?.mkdirs()
            file.writeBytes(Base64.decode(entry.contentBase64, Base64.DEFAULT))
        }
        skillManager.refreshAvailableSkills()
    }

    private suspend fun activeMemoryRepository(): MemoryRepository {
        val profileId = preferencesManager.activeProfileIdFlow.first()
        return MemoryRepository(context, profileId)
    }

    private fun snapshot(payload: String, updatedAt: Long, deletedAt: Long?): Snapshot {
        val canonical = canonicalize(payload)
        return Snapshot(canonical, sha256(canonical), updatedAt, deletedAt)
    }

    private fun canonicalize(raw: String): String = canonicalElement(json.parseToJsonElement(raw)).toString()

    private fun canonicalElement(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.entries.sortedBy { it.key }.associate { it.key to canonicalElement(it.value) })
        is JsonArray -> JsonArray(element.map(::canonicalElement))
        else -> element
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun decodeCollections(raw: String): Set<String> = raw.split(',').filter { it in LanSyncCollections.all }.toSet()

    private fun collectionOrder(collection: String): Int = when (collection) {
        LanSyncCollections.CHATS -> 0
        LanSyncCollections.MESSAGES -> 1
        LanSyncCollections.MESSAGE_VARIANTS -> 2
        else -> 3
    }

    private data class Snapshot(val payload: String?, val hash: String, val updatedAt: Long, val deletedAt: Long?)

    companion object {
        const val PROTOCOL_VERSION = 1
        @Volatile private var instance: LanSyncEngine? = null

        fun getInstance(context: Context): LanSyncEngine = instance ?: synchronized(this) {
            instance ?: LanSyncEngine(context.applicationContext).also { instance = it }
        }
    }
}
