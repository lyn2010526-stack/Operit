package com.cynosure.operit.core.evolution

import android.content.Context
import com.cynosure.operit.core.diagnostics.DiagnosticsRegistry
import com.cynosure.operit.core.diagnostics.ValidationDiagnostic
import com.cynosure.operit.data.db.AppDatabase
import com.cynosure.operit.data.model.TaskTraceEntity
import com.cynosure.operit.data.preferences.ApiPreferences
import com.cynosure.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 自进化引擎：在任务完成时执行复盘、验证和固化。
 *
 * 流程：
 * 1. 记录任务轨迹到 Room task_traces 表
 * 2. 对文件操作工具结果做后置验证
 * 3. 验证通过且任务成功时，固化为 Skill
 * 4. 验证结果同步到 DiagnosticsRegistry
 *
 * 所有操作在后台协程中执行，不阻塞主流程。
 */
object SelfEvolutionEngine {
    private const val TAG = "SelfEvolutionEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class TaskCompletionInfo(
        val chatId: String,
        val userMessage: String,
        val assistantReply: String,
        val toolsUsed: List<String>,
        val success: Boolean,
        val toolValidationInputs: List<ValidationRunner.ValidationInput>
    )

    /**
     * 在任务完成时调用，异步执行复盘和固化。
     * 如果自进化开关关闭，直接返回。
     */
    fun onTaskCompleted(context: Context, info: TaskCompletionInfo) {
        if (!ApiPreferences.getInstance(context).isSelfEvolutionEnabledBlocking()) {
            return
        }

        scope.launch {
            runCatching {
                val db = AppDatabase.getDatabase(context)
                val traceId = db.taskTraceDao().insert(
                    TaskTraceEntity(
                        chatId = info.chatId,
                        userMessage = info.userMessage.take(2000),
                        assistantReply = info.assistantReply.take(4000),
                        toolsUsed = info.toolsUsed.joinToString(","),
                        success = info.success,
                        createdAt = System.currentTimeMillis()
                    )
                )

                db.taskTraceDao().trim(200)

                var allPassed = info.toolValidationInputs.isNotEmpty()
                for (input in info.toolValidationInputs) {
                    val output = ValidationRunner.validate(
                        context,
                        traceId,
                        input
                    )
                    DiagnosticsRegistry.recordValidation(
                        ValidationDiagnostic(
                            timestamp = System.currentTimeMillis(),
                            subject = input.toolName,
                            success = output.passed,
                            summary = output.detail
                        )
                    )
                    if (!output.passed) {
                        allPassed = false
                    }
                }

                if (info.success && allPassed) {
                    val trace = TaskTraceEntity(
                        id = traceId,
                        chatId = info.chatId,
                        userMessage = info.userMessage.take(2000),
                        assistantReply = info.assistantReply.take(4000),
                        toolsUsed = info.toolsUsed.joinToString(","),
                        success = info.success,
                        createdAt = System.currentTimeMillis()
                    )
                    BuiltinSkillManager.solidify(context, trace, allPassed)
                }

                AppLogger.d(
                    TAG,
                    "任务复盘完成: traceId=$traceId, validationCount=${info.toolValidationInputs.size}, validationPassed=$allPassed"
                )
            }.onFailure { e ->
                AppLogger.e(TAG, "自进化复盘失败", e)
            }
        }
    }
}
