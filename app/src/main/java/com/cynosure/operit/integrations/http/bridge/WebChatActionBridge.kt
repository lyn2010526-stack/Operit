package com.cynosure.operit.integrations.http.bridge

import com.cynosure.operit.services.ChatServiceCore

internal class WebChatActionBridge(
    private val core: ChatServiceCore
) {
    fun manuallyUpdateMemory() {
        core.getMessageCoordinationDelegate().manuallyUpdateMemory()
    }

    fun manuallySummarizeConversation() {
        core.getMessageCoordinationDelegate().manuallySummarizeConversation()
    }
}
