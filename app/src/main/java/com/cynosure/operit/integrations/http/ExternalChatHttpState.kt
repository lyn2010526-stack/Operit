package com.cynosure.operit.integrations.http

data class ExternalChatHttpState(
    val isRunning: Boolean = false,
    val port: Int? = null,
    val lastError: String? = null
)
