package com.cynosure.operit.core.agent

import com.cynosure.operit.core.events.BusinessEvent
import com.cynosure.operit.core.events.BusinessEventBus
import com.cynosure.operit.core.events.BusinessEventType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay

enum class AgentExecutionState {
    PLANNING,
    RUNNING,
    WAITING_USER,
    VERIFYING,
    CORRECTING,
    RETRYING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class AgentExecutionSource {
    CHAT,
    GROUP,
    TOOL,
    PHONE_AGENT,
    PLUGIN,
    WORKFLOW
}

data class AgentExecutionStep(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val state: AgentExecutionState,
    val owner: String? = null,
    val detail: String? = null,
    val attempt: Int = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = startedAt
)

data class AgentExecutionSnapshot(
    val taskId: String,
    val source: AgentExecutionSource,
    val state: AgentExecutionState,
    val title: String,
    val chatId: String? = null,
    val owner: String? = null,
    val detail: String? = null,
    val attempt: Int = 0,
    val steps: List<AgentExecutionStep> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

object AgentExecutionStore {
    private val snapshotsById = ConcurrentHashMap<String, AgentExecutionSnapshot>()
    private val mutableSnapshots = MutableStateFlow<Map<String, AgentExecutionSnapshot>>(emptyMap())
    val snapshots: StateFlow<Map<String, AgentExecutionSnapshot>> = mutableSnapshots.asStateFlow()

    @Synchronized
    fun start(
        taskId: String = UUID.randomUUID().toString(),
        source: AgentExecutionSource,
        title: String,
        chatId: String? = null,
        owner: String? = null,
        initialState: AgentExecutionState = AgentExecutionState.PLANNING,
        detail: String? = null
    ): String {
        require(taskId.isNotBlank()) { "taskId must not be blank" }
        require(title.isNotBlank()) { "title must not be blank" }
        val snapshot = AgentExecutionSnapshot(
            taskId = taskId,
            source = source,
            state = initialState,
            title = title,
            chatId = chatId,
            owner = owner,
            detail = detail
        )
        snapshotsById[taskId] = snapshot
        publish(snapshot)
        return taskId
    }

    @Synchronized
    fun transition(
        taskId: String,
        state: AgentExecutionState,
        detail: String? = null,
        owner: String? = null,
        attempt: Int? = null
    ): AgentExecutionSnapshot {
        val current = snapshotsById[taskId]
            ?: throw IllegalStateException("Agent task does not exist: $taskId")
        require(isTransitionAllowed(current.state, state)) {
            "Illegal agent task transition: ${current.state} -> $state"
        }
        val updated = current.copy(
            state = state,
            detail = detail ?: current.detail,
            owner = owner ?: current.owner,
            attempt = attempt ?: current.attempt,
            updatedAt = System.currentTimeMillis()
        )
        snapshotsById[taskId] = updated
        publish(updated)
        return updated
    }

    @Synchronized
    fun upsertStep(
        taskId: String,
        stepId: String,
        title: String,
        state: AgentExecutionState,
        owner: String? = null,
        detail: String? = null,
        attempt: Int = 0
    ): AgentExecutionSnapshot {
        val current = snapshotsById[taskId]
            ?: throw IllegalStateException("Agent task does not exist: $taskId")
        val now = System.currentTimeMillis()
        val existing = current.steps.firstOrNull { it.id == stepId }
        val step = AgentExecutionStep(
            id = stepId,
            title = title,
            state = state,
            owner = owner,
            detail = detail,
            attempt = attempt,
            startedAt = existing?.startedAt ?: now,
            updatedAt = now
        )
        val steps = current.steps.filterNot { it.id == stepId } + step
        val updated = current.copy(steps = steps, updatedAt = now)
        snapshotsById[taskId] = updated
        publish(updated, stepId)
        return updated
    }

    fun pause(taskId: String, detail: String? = null) =
        transition(taskId, AgentExecutionState.PAUSED, detail)

    fun resume(taskId: String, detail: String? = null) =
        transition(taskId, AgentExecutionState.RUNNING, detail)

    fun cancel(taskId: String, detail: String? = null) =
        transition(taskId, AgentExecutionState.CANCELLED, detail)

    fun get(taskId: String): AgentExecutionSnapshot? = snapshotsById[taskId]

    suspend fun awaitIfPaused(taskId: String) {
        while (snapshotsById[taskId]?.state == AgentExecutionState.PAUSED) {
            delay(100)
        }
        if (snapshotsById[taskId]?.state == AgentExecutionState.CANCELLED) {
            throw kotlinx.coroutines.CancellationException("Agent task cancelled: $taskId")
        }
    }

    private fun publish(snapshot: AgentExecutionSnapshot, stepId: String? = null) {
        mutableSnapshots.value = snapshotsById.toMap()
        val attributes = mutableMapOf(
            "state" to snapshot.state.name,
            "source" to snapshot.source.name,
            "title" to snapshot.title,
            "attempt" to snapshot.attempt.toString(),
            "stepCount" to snapshot.steps.size.toString()
        )
        snapshot.chatId?.let { attributes["chatId"] = it }
        snapshot.owner?.let { attributes["owner"] = it }
        snapshot.detail?.let { attributes["detail"] = it.take(500) }
        stepId?.let { attributes["stepId"] = it }
        BusinessEventBus.publish(
            BusinessEvent(
                type = BusinessEventType.AGENT_TASK_STATE_CHANGED,
                source = snapshot.source.name,
                entityId = snapshot.taskId,
                attributes = attributes
            )
        )
    }

    private fun isTransitionAllowed(
        from: AgentExecutionState,
        to: AgentExecutionState
    ): Boolean {
        if (from == to) return true
        if (to == AgentExecutionState.CANCELLED) return from !in terminalStates
        if (to == AgentExecutionState.PAUSED) return from in pausableStates
        if (from == AgentExecutionState.PAUSED) return to == AgentExecutionState.RUNNING
        return to in allowedTransitions.getValue(from)
    }

    private val terminalStates = setOf(
        AgentExecutionState.COMPLETED,
        AgentExecutionState.FAILED,
        AgentExecutionState.CANCELLED
    )

    private val pausableStates = setOf(
        AgentExecutionState.PLANNING,
        AgentExecutionState.RUNNING,
        AgentExecutionState.WAITING_USER,
        AgentExecutionState.VERIFYING,
        AgentExecutionState.CORRECTING,
        AgentExecutionState.RETRYING
    )

    private val allowedTransitions = mapOf(
        AgentExecutionState.PLANNING to setOf(AgentExecutionState.RUNNING, AgentExecutionState.FAILED),
        AgentExecutionState.RUNNING to setOf(
            AgentExecutionState.WAITING_USER,
            AgentExecutionState.VERIFYING,
            AgentExecutionState.CORRECTING,
            AgentExecutionState.RETRYING,
            AgentExecutionState.COMPLETED,
            AgentExecutionState.FAILED
        ),
        AgentExecutionState.WAITING_USER to setOf(AgentExecutionState.RUNNING, AgentExecutionState.FAILED),
        AgentExecutionState.VERIFYING to setOf(
            AgentExecutionState.CORRECTING,
            AgentExecutionState.RETRYING,
            AgentExecutionState.COMPLETED,
            AgentExecutionState.FAILED
        ),
        AgentExecutionState.CORRECTING to setOf(
            AgentExecutionState.RUNNING,
            AgentExecutionState.VERIFYING,
            AgentExecutionState.RETRYING,
            AgentExecutionState.FAILED
        ),
        AgentExecutionState.RETRYING to setOf(AgentExecutionState.RUNNING, AgentExecutionState.FAILED),
        AgentExecutionState.PAUSED to setOf(AgentExecutionState.RUNNING),
        AgentExecutionState.COMPLETED to emptySet(),
        AgentExecutionState.FAILED to setOf(AgentExecutionState.RETRYING),
        AgentExecutionState.CANCELLED to emptySet()
    )
}
