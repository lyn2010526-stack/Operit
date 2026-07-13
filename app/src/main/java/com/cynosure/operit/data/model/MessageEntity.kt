package com.cynosure.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.Serializable

/** 消息实体类，用于Room数据库存储聊天消息 */
@Entity(
        tableName = "messages",
        foreignKeys =
                [
                        ForeignKey(
                                entity = ChatEntity::class,
                                parentColumns = ["id"],
                                childColumns = ["chatId"],
                                onDelete = ForeignKey.CASCADE
                        )],
        indices = [
                Index("chatId"),
                Index(value = ["chatId", "timestamp"]),
                Index(value = ["syncId"], unique = true),
                Index(value = ["updatedAt"])
        ]
)
@Serializable
data class MessageEntity(
        @PrimaryKey(autoGenerate = true) val messageId: Long = 0,
        val syncId: String = UUID.randomUUID().toString(),
        val chatId: String,
        val sender: String,
        val content: String,
        val timestamp: Long = System.currentTimeMillis(),
        val orderIndex: Int, // 保持消息顺序
        val roleName: String = "", // 角色名字段
        val selectedVariantIndex: Int = 0,
        val provider: String = "", // 供应商
        val modelName: String = "", // 模型名称
        val inputTokens: Int = 0,
        val outputTokens: Int = 0,
        val cachedInputTokens: Int = 0,
        val sentAt: Long = 0L,
        val outputDurationMs: Long = 0L,
        val waitDurationMs: Long = 0L,
        val completedAt: Long = 0L,
        val displayMode: String = ChatMessageDisplayMode.NORMAL.name,
        val isFavorite: Boolean = false,
        val revision: Long = 1L,
        val updatedAt: Long = System.currentTimeMillis(),
        val deletedAt: Long? = null,
) {
    /** 转换为ChatMessage对象（供UI层使用） */
    fun toChatMessage(): ChatMessage {
        return ChatMessage(
            sender = sender, 
            content = content, 
            timestamp = timestamp,
            roleName = roleName,
            selectedVariantIndex = selectedVariantIndex,
            provider = provider,
            modelName = modelName,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cachedInputTokens = cachedInputTokens,
            sentAt = sentAt,
            outputDurationMs = outputDurationMs,
            waitDurationMs = waitDurationMs,
            completedAt = completedAt,
            displayMode =
                runCatching { ChatMessageDisplayMode.valueOf(displayMode) }
                    .getOrDefault(ChatMessageDisplayMode.NORMAL),
            isFavorite = isFavorite,
        )
    }

    companion object {
        /** 从ChatMessage创建MessageEntity */
        fun fromChatMessage(
            chatId: String,
            message: ChatMessage,
            orderIndex: Int,
            messageId: Long = 0,
            syncId: String = UUID.randomUUID().toString(),
            revision: Long = 1L,
            updatedAt: Long = System.currentTimeMillis(),
            deletedAt: Long? = null
        ): MessageEntity {
            return MessageEntity(
                    messageId = messageId,
                    syncId = syncId,
                    chatId = chatId,
                    sender = message.sender,
                    content = message.content,
                    timestamp = message.timestamp,
                    orderIndex = orderIndex,
                    roleName = message.roleName,
                    selectedVariantIndex = message.selectedVariantIndex,
                    provider = message.provider,
                    modelName = message.modelName,
                    inputTokens = message.inputTokens,
                    outputTokens = message.outputTokens,
                    cachedInputTokens = message.cachedInputTokens,
                    sentAt = message.sentAt,
                    outputDurationMs = message.outputDurationMs,
                    waitDurationMs = message.waitDurationMs,
                    completedAt = message.completedAt,
                    displayMode = message.displayMode.name,
                    isFavorite = message.isFavorite,
                    revision = revision,
                    updatedAt = updatedAt,
                    deletedAt = deletedAt,
            )
        }
    }
}
