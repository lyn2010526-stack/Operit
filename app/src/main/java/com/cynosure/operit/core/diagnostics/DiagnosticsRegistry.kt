package com.cynosure.operit.core.diagnostics

import android.content.Context
import com.cynosure.operit.core.tools.AIToolHook
import com.cynosure.operit.core.tools.AIToolHandler
import com.cynosure.operit.core.tools.skill.SkillManager
import com.cynosure.operit.data.model.AITool
import com.cynosure.operit.data.model.ToolResult
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ToolDiagnosticStats(
    val toolName: String,
    val callCount: Long,
    val failureCount: Long,
    val averageDurationMs: Long
) {
    val failureRate: Float
        get() = if (callCount == 0L) 0f else failureCount.toFloat() / callCount
}

data class DiagnosticEvent(
    val timestamp: Long,
    val toolName: String,
    val message: String,
    val success: Boolean?
)

data class ValidationDiagnostic(
    val timestamp: Long,
    val subject: String,
    val success: Boolean,
    val summary: String
)

data class DiagnosticsSnapshot(
    val activeExecutions: Map<String, Int> = emptyMap(),
    val toolStats: List<ToolDiagnosticStats> = emptyList(),
    val skillNames: List<String> = emptyList(),
    val validationHistory: List<ValidationDiagnostic> = emptyList(),
    val recentEvents: List<DiagnosticEvent> = emptyList()
)

object DiagnosticsRegistry : AIToolHook {
    private const val MAX_EVENTS = 100
    private const val MAX_VALIDATIONS = 50

    private data class MutableToolStats(
        var callCount: Long = 0,
        var failureCount: Long = 0,
        var totalDurationMs: Long = 0,
        var completedExecutionCount: Long = 0,
        var failureRecordedForCurrentExecution: Boolean = false
    )

    private val lock = Any()
    private val activeExecutions = mutableMapOf<String, Int>()
    private val executionStarts = mutableMapOf<String, ArrayDeque<Long>>()
    private val toolStats = mutableMapOf<String, MutableToolStats>()
    private val recentEvents = ArrayDeque<DiagnosticEvent>()
    private val validationHistory = ArrayDeque<ValidationDiagnostic>()
    private var skillNames: List<String> = emptyList()
    private var initialized = false

    private val mutableSnapshot = MutableStateFlow(DiagnosticsSnapshot())
    val snapshot: StateFlow<DiagnosticsSnapshot> = mutableSnapshot.asStateFlow()

    fun initialize(context: Context, toolHandler: AIToolHandler = AIToolHandler.getInstance(context)) {
        synchronized(lock) {
            if (!initialized) {
                toolHandler.addToolHook(this)
                initialized = true
            }
        }
        refreshSkills(context)
    }

    fun refreshSkills(context: Context) {
        val names = SkillManager.getInstance(context).getAvailableSkills().keys.sorted()
        synchronized(lock) {
            skillNames = names
            publishLocked()
        }
    }

    fun recordValidation(result: ValidationDiagnostic) {
        synchronized(lock) {
            validationHistory.addFirst(result)
            while (validationHistory.size > MAX_VALIDATIONS) {
                validationHistory.removeLast()
            }
            publishLocked()
        }
    }

    override fun onToolCallRequested(tool: AITool) {
        synchronized(lock) {
            val stats = toolStats.getOrPut(tool.name, ::MutableToolStats)
            stats.callCount++
            stats.failureRecordedForCurrentExecution = false
            publishLocked()
        }
    }

    override fun onToolExecutionStarted(tool: AITool) {
        synchronized(lock) {
            activeExecutions[tool.name] = (activeExecutions[tool.name] ?: 0) + 1
            executionStarts.getOrPut(tool.name, ::ArrayDeque).addLast(System.currentTimeMillis())
            toolStats.getOrPut(tool.name, ::MutableToolStats).failureRecordedForCurrentExecution = false
            addEventLocked(tool.name, "开始执行", null)
            publishLocked()
        }
    }

    override fun onToolExecutionResult(tool: AITool, result: ToolResult) {
        synchronized(lock) {
            val stats = toolStats.getOrPut(tool.name, ::MutableToolStats)
            if (!result.success && !stats.failureRecordedForCurrentExecution) {
                stats.failureCount++
                stats.failureRecordedForCurrentExecution = true
            }
            addEventLocked(tool.name, result.error?.take(160) ?: if (result.success) "执行成功" else "执行失败", result.success)
            publishLocked()
        }
    }

    override fun onToolExecutionError(tool: AITool, throwable: Throwable) {
        synchronized(lock) {
            addEventLocked(tool.name, throwable.message?.take(160) ?: throwable.javaClass.simpleName, false)
            publishLocked()
        }
    }

    override fun onToolExecutionFinished(tool: AITool) {
        synchronized(lock) {
            val activeCount = activeExecutions[tool.name] ?: 0
            if (activeCount <= 1) activeExecutions.remove(tool.name) else activeExecutions[tool.name] = activeCount - 1

            val startedAt = executionStarts[tool.name]?.pollFirst()
            if (executionStarts[tool.name]?.isEmpty() == true) executionStarts.remove(tool.name)
            if (startedAt != null) {
                val stats = toolStats.getOrPut(tool.name, ::MutableToolStats)
                stats.totalDurationMs += (System.currentTimeMillis() - startedAt).coerceAtLeast(0)
                stats.completedExecutionCount++
            }
            publishLocked()
        }
    }

    private fun addEventLocked(toolName: String, message: String, success: Boolean?) {
        recentEvents.addFirst(DiagnosticEvent(System.currentTimeMillis(), toolName, message, success))
        while (recentEvents.size > MAX_EVENTS) recentEvents.removeLast()
    }

    private fun publishLocked() {
        mutableSnapshot.value = DiagnosticsSnapshot(
            activeExecutions = activeExecutions.toMap(),
            toolStats = toolStats.map { (name, stats) ->
                ToolDiagnosticStats(
                    toolName = name,
                    callCount = stats.callCount,
                    failureCount = stats.failureCount,
                    averageDurationMs =
                        if (stats.completedExecutionCount == 0L) 0
                        else stats.totalDurationMs / stats.completedExecutionCount
                )
            }.sortedWith(compareByDescending<ToolDiagnosticStats> { it.failureRate }.thenByDescending { it.callCount }),
            skillNames = skillNames,
            validationHistory = validationHistory.toList(),
            recentEvents = recentEvents.toList()
        )
    }
}
