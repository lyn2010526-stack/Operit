package com.cynosure.operit.core.events

import java.util.UUID
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class BusinessEventType {
    TOOL_REQUESTED,
    TOOL_STARTED,
    TOOL_COMPLETED,
    TOOL_FAILED,
    ENTITY_CHANGED,
    SYNC_STATE_CHANGED,
    PLUGIN_TASK_STATE_CHANGED,
    AGENT_TASK_STATE_CHANGED
}

data class BusinessEvent(
    val id: String = UUID.randomUUID().toString(),
    val type: BusinessEventType,
    val source: String,
    val entityId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val attributes: Map<String, String> = emptyMap()
)

object BusinessEventBus {
    private val mutableEvents = MutableSharedFlow<BusinessEvent>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<BusinessEvent> = mutableEvents.asSharedFlow()

    fun publish(event: BusinessEvent) {
        mutableEvents.tryEmit(event)
    }
}
