package com.cynosure.operit.integrations.lansync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LanSyncModelsTest {
    @Test
    fun mergePagesAllowsLaggingCollectionToProgress() {
        val chats = (101L..105L).map { change(LanSyncCollections.CHATS, it) }
        val messages = (1L..3L).map { change(LanSyncCollections.MESSAGES, it) }

        val merged = mergeLanSyncPages(listOf(chats, messages), limit = 5)

        assertEquals(listOf(1L, 2L, 3L, 101L, 102L), merged.map { it.sequence })
        assertEquals(setOf(LanSyncCollections.CHATS, LanSyncCollections.MESSAGES), merged.map { it.collection }.toSet())
    }

    @Test
    fun envelopeValidationRejectsWrongSource() {
        val envelope = LanSyncEnvelope(1, "other-device", "batch", listOf(change(LanSyncCollections.CHATS, 1L)))

        assertThrows(IllegalArgumentException::class.java) {
            validateLanSyncEnvelope(envelope, 1, "peer-device", setOf(LanSyncCollections.CHATS))
        }
    }

    @Test
    fun envelopeValidationRejectsWrongProtocolVersion() {
        val envelope = LanSyncEnvelope(2, "peer-device", "batch", listOf(change(LanSyncCollections.CHATS, 1L)))

        assertThrows(IllegalArgumentException::class.java) {
            validateLanSyncEnvelope(envelope, 1, "peer-device", setOf(LanSyncCollections.CHATS))
        }
    }

    @Test
    fun envelopeValidationRejectsDisabledCollection() {
        val envelope = LanSyncEnvelope(1, "peer-device", "batch", listOf(change(LanSyncCollections.MESSAGES, 1L)))

        assertThrows(IllegalArgumentException::class.java) {
            validateLanSyncEnvelope(envelope, 1, "peer-device", setOf(LanSyncCollections.CHATS))
        }
    }

    @Test
    fun envelopeValidationAcceptsEnabledKnownCollection() {
        val envelope = LanSyncEnvelope(1, "peer-device", "batch", listOf(change(LanSyncCollections.CHATS, 1L)))

        validateLanSyncEnvelope(envelope, 1, "peer-device", setOf(LanSyncCollections.CHATS))
    }

    private fun change(collection: String, sequence: Long) = LanSyncChange(
        collection = collection,
        entityId = "$collection-$sequence",
        operation = LanSyncOperation.UPSERT,
        sequence = sequence,
        revision = 1L,
        updatedAt = sequence,
        payloadHash = "hash-$sequence",
        payload = "{}",
    )
}
