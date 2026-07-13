package com.cynosure.operit.services

import com.cynosure.operit.data.model.ChatMessage

interface ChatServiceUiBridge {
    fun updateWebServerForCurrentChat(chatId: String)
    fun resetAttachmentPanelState()
    fun clearReplyToMessage()
    fun getReplyToMessage(): ChatMessage?
}

object EmptyChatServiceUiBridge : ChatServiceUiBridge {
    override fun updateWebServerForCurrentChat(chatId: String) = Unit

    override fun resetAttachmentPanelState() = Unit

    override fun clearReplyToMessage() = Unit

    override fun getReplyToMessage(): ChatMessage? = null
}
