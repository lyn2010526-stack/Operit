package com.cynosure.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cynosure.operit.data.model.MessageVariantEntity

@Dao
interface MessageVariantDao {
    @Query(
        "SELECT * FROM message_variants WHERE chatId = :chatId AND deletedAt IS NULL ORDER BY messageTimestamp ASC, variantIndex ASC"
    )
    suspend fun getVariantsForChat(chatId: String): List<MessageVariantEntity>

    @Query(
        "SELECT * FROM message_variants WHERE chatId = :chatId AND messageTimestamp IN (:messageTimestamps) AND deletedAt IS NULL ORDER BY messageTimestamp ASC, variantIndex ASC"
    )
    suspend fun getVariantsForMessages(
        chatId: String,
        messageTimestamps: List<Long>,
    ): List<MessageVariantEntity>

    @Query(
        "SELECT * FROM message_variants WHERE chatId = :chatId AND messageTimestamp = :messageTimestamp AND deletedAt IS NULL ORDER BY variantIndex ASC"
    )
    suspend fun getVariantsForMessage(
        chatId: String,
        messageTimestamp: Long,
    ): List<MessageVariantEntity>

    @Query(
        "SELECT * FROM message_variants WHERE chatId = :chatId AND messageTimestamp = :messageTimestamp AND variantIndex = :variantIndex AND deletedAt IS NULL LIMIT 1"
    )
    suspend fun getVariantForMessage(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
    ): MessageVariantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: MessageVariantEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariants(variants: List<MessageVariantEntity>)

    @Query("SELECT * FROM message_variants")
    suspend fun getAllVariantsIncludingDeleted(): List<MessageVariantEntity>

    @Query("SELECT * FROM message_variants WHERE syncId = :syncId LIMIT 1")
    suspend fun getVariantBySyncIdIncludingDeleted(syncId: String): MessageVariantEntity?

    @Query("UPDATE message_variants SET deletedAt = :deletedAt, updatedAt = :deletedAt, revision = revision + 1 WHERE syncId = :syncId")
    suspend fun tombstoneVariant(syncId: String, deletedAt: Long)

    @Query(
        """
        INSERT INTO message_variants (
            syncId,
            chatId,
            messageTimestamp,
            variantIndex,
            content,
            roleName,
            provider,
            modelName,
            inputTokens,
            outputTokens,
            cachedInputTokens,
            sentAt,
            outputDurationMs,
            waitDurationMs,
            completedAt,
            revision,
            updatedAt,
            deletedAt
        )
        SELECT
            lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(6))),
            :targetChatId,
            messageTimestamp,
            variantIndex,
            content,
            roleName,
            provider,
            modelName,
            inputTokens,
            outputTokens,
            cachedInputTokens,
            sentAt,
            outputDurationMs,
            waitDurationMs,
            completedAt,
            1,
            CAST(strftime('%s','now') AS INTEGER) * 1000,
            NULL
        FROM message_variants
        WHERE chatId = :sourceChatId
            AND (:upToTimestampInclusive IS NULL OR messageTimestamp <= :upToTimestampInclusive)
        """
    )
    suspend fun copyVariantsToChat(
        sourceChatId: String,
        targetChatId: String,
        upToTimestampInclusive: Long?,
    )

    @Update
    suspend fun updateVariant(variant: MessageVariantEntity)

    @Query(
        "UPDATE message_variants SET variantIndex = -variantId, deletedAt = :deletedAt, updatedAt = :deletedAt, revision = revision + 1 WHERE chatId = :chatId AND messageTimestamp = :messageTimestamp AND variantIndex = :variantIndex AND deletedAt IS NULL"
    )
    suspend fun deleteVariant(
        chatId: String,
        messageTimestamp: Long,
        variantIndex: Int,
        deletedAt: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE message_variants SET deletedAt = :deletedAt, updatedAt = :deletedAt, revision = revision + 1 WHERE chatId = :chatId AND messageTimestamp = :messageTimestamp AND deletedAt IS NULL")
    suspend fun deleteVariantsForMessage(chatId: String, messageTimestamp: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE message_variants SET deletedAt = :deletedAt, updatedAt = :deletedAt, revision = revision + 1 WHERE chatId = :chatId AND messageTimestamp >= :messageTimestamp AND deletedAt IS NULL")
    suspend fun deleteVariantsFrom(chatId: String, messageTimestamp: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE message_variants SET deletedAt = :deletedAt, updatedAt = :deletedAt, revision = revision + 1 WHERE chatId = :chatId AND deletedAt IS NULL")
    suspend fun deleteAllVariantsForChat(chatId: String, deletedAt: Long = System.currentTimeMillis())
}
