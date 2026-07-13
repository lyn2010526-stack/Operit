package com.cynosure.operit.plugins.coordinator

import com.cynosure.operit.core.agent.AgentExecutionSource
import com.cynosure.operit.core.agent.AgentExecutionState
import com.cynosure.operit.core.agent.AgentExecutionStore
import com.cynosure.operit.core.events.BusinessEvent
import com.cynosure.operit.core.events.BusinessEventBus
import com.cynosure.operit.core.events.BusinessEventType
import com.cynosure.operit.util.AppLogger
import java.util.PriorityQueue
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

enum class PluginTaskState {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

data class PluginTaskRequest<T>(
    val pluginId: String,
    val priority: Int = 0,
    val parentTaskId: String? = null,
    val maxAttempts: Int = 1,
    val retryDelayMs: Long = 1_000L,
    val timeoutMs: Long = 300_000L,
    val action: suspend () -> T
)

data class PluginTaskHandle<T>(
    val taskId: String,
    val result: CompletableDeferred<T>
)

object PluginTaskCoordinator {
    private const val TAG = "PluginTaskCoordinator"

    private data class QueuedTask<T>(
        val id: String,
        val sequence: Long,
        val request: PluginTaskRequest<T>,
        val result: CompletableDeferred<T>
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queueMutex = Mutex()
    private val queue = PriorityQueue<QueuedTask<*>>(
        compareByDescending<QueuedTask<*>> { it.request.priority }
            .thenBy { it.sequence }
    )
    private val sequence = AtomicLong(0)
    private val taskJobs = ConcurrentHashMap<String, Job>()
    private val cancelledTaskIds = ConcurrentHashMap.newKeySet<String>()
    private var worker: Job? = null

    fun <T> submit(request: PluginTaskRequest<T>): PluginTaskHandle<T> {
        require(request.maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(request.timeoutMs > 0) { "timeoutMs must be positive" }

        val task = QueuedTask(
            id = UUID.randomUUID().toString(),
            sequence = sequence.getAndIncrement(),
            request = request,
            result = CompletableDeferred()
        )
        scope.launch {
            queueMutex.withLock { queue.add(task) }
            AgentExecutionStore.start(
                taskId = task.id,
                source = AgentExecutionSource.PLUGIN,
                title = "插件任务: ${request.pluginId}",
                owner = request.pluginId,
                initialState = AgentExecutionState.PLANNING,
                detail = request.parentTaskId?.let { "父任务: $it" }
            )
            publishState(task, PluginTaskState.QUEUED)
            ensureWorker()
        }
        return PluginTaskHandle(task.id, task.result)
    }

    fun cancel(taskId: String) {
        cancelledTaskIds.add(taskId)
        taskJobs.remove(taskId)?.cancel(CancellationException("Plugin task cancelled"))
        scope.launch {
            val removed = queueMutex.withLock {
                val queued = queue.firstOrNull { it.id == taskId } ?: return@withLock null
                queue.remove(queued)
                queued
            }
            if (removed != null) {
                removed.result.cancel(CancellationException("Plugin task cancelled"))
                AgentExecutionStore.cancel(removed.id, "插件任务已取消")
                publishState(removed, PluginTaskState.CANCELLED)
            }
        }
    }

    @Synchronized
    private fun ensureWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (true) {
                val task = queueMutex.withLock { queue.poll() } ?: break
                execute(task)
            }
        }.also { job ->
            job.invokeOnCompletion {
                synchronized(this) {
                    if (worker === job) worker = null
                }
                scope.launch {
                    if (queueMutex.withLock { queue.isNotEmpty() }) ensureWorker()
                }
            }
        }
    }

    private suspend fun <T> execute(task: QueuedTask<T>) {
        if (cancelledTaskIds.remove(task.id)) {
            task.result.cancel(CancellationException("Plugin task cancelled"))
            AgentExecutionStore.cancel(task.id, "插件任务已取消")
            publishState(task, PluginTaskState.CANCELLED)
            return
        }

        val job = scope.launch {
            AgentExecutionStore.transition(task.id, AgentExecutionState.RUNNING, attempt = 1)
            publishState(task, PluginTaskState.RUNNING)
            var attempt = 1
            while (attempt <= task.request.maxAttempts) {
                try {
                    val value = withTimeout(task.request.timeoutMs) { task.request.action() }
                    task.result.complete(value)
                    AgentExecutionStore.transition(
                        task.id,
                        AgentExecutionState.COMPLETED,
                        detail = "插件任务执行完成",
                        attempt = attempt
                    )
                    publishState(task, PluginTaskState.SUCCEEDED, attempt)
                    return@launch
                } catch (error: TimeoutCancellationException) {
                    if (attempt == task.request.maxAttempts) {
                        task.result.completeExceptionally(error)
                        AgentExecutionStore.transition(
                            task.id,
                            AgentExecutionState.FAILED,
                            detail = error.message ?: "插件任务超时",
                            attempt = attempt
                        )
                        publishState(task, PluginTaskState.FAILED, attempt, error.message)
                        AppLogger.e(TAG, "插件任务超时: ${task.id}", error)
                        return@launch
                    }
                    AgentExecutionStore.transition(
                        task.id,
                        AgentExecutionState.RETRYING,
                        detail = error.message ?: "插件任务超时，准备重试",
                        attempt = attempt + 1
                    )
                    delay(task.request.retryDelayMs * attempt)
                    attempt++
                    AgentExecutionStore.transition(
                        task.id,
                        AgentExecutionState.RUNNING,
                        detail = "插件任务重试中",
                        attempt = attempt
                    )
                } catch (error: CancellationException) {
                    task.result.cancel(error)
                    AgentExecutionStore.cancel(task.id, error.message ?: "插件任务已取消")
                    publishState(task, PluginTaskState.CANCELLED, attempt)
                    return@launch
                } catch (error: Exception) {
                    if (attempt == task.request.maxAttempts) {
                        task.result.completeExceptionally(error)
                        AgentExecutionStore.transition(
                            task.id,
                            AgentExecutionState.FAILED,
                            detail = error.message ?: "插件任务失败",
                            attempt = attempt
                        )
                        publishState(task, PluginTaskState.FAILED, attempt, error.message)
                        AppLogger.e(TAG, "插件任务失败: ${task.id}", error)
                        return@launch
                    }
                    AgentExecutionStore.transition(
                        task.id,
                        AgentExecutionState.RETRYING,
                        detail = error.message ?: "插件任务失败，准备重试",
                        attempt = attempt + 1
                    )
                    delay(task.request.retryDelayMs * attempt)
                    attempt++
                    AgentExecutionStore.transition(
                        task.id,
                        AgentExecutionState.RUNNING,
                        detail = "插件任务重试中",
                        attempt = attempt
                    )
                }
            }
        }
        taskJobs[task.id] = job
        try {
            job.join()
        } finally {
            taskJobs.remove(task.id, job)
            cancelledTaskIds.remove(task.id)
        }
    }

    private fun publishState(
        task: QueuedTask<*>,
        state: PluginTaskState,
        attempt: Int = 0,
        error: String? = null
    ) {
        val attributes = mutableMapOf(
            "state" to state.name,
            "priority" to task.request.priority.toString(),
            "attempt" to attempt.toString()
        )
        task.request.parentTaskId?.let { attributes["parentTaskId"] = it }
        error?.let { attributes["error"] = it.take(500) }
        BusinessEventBus.publish(
            BusinessEvent(
                type = BusinessEventType.PLUGIN_TASK_STATE_CHANGED,
                source = task.request.pluginId,
                entityId = task.id,
                attributes = attributes
            )
        )
    }
}
