package com.cynosure.operit.core.tools

import android.content.Context
import com.cynosure.operit.util.AppLogger
import com.cynosure.operit.core.tools.mcp.MCPManager
import com.cynosure.operit.core.tools.packTool.PackageManager
import com.cynosure.operit.data.model.AITool
import com.cynosure.operit.data.model.ToolInvocation
import com.cynosure.operit.data.model.ToolParameter
import com.cynosure.operit.data.model.ToolResult
import com.cynosure.operit.data.model.ToolValidationResult
import com.cynosure.operit.core.config.AntiHallucinationGuard
import com.cynosure.operit.core.events.BusinessEvent
import com.cynosure.operit.core.events.BusinessEventBus
import com.cynosure.operit.core.events.BusinessEventType
import com.cynosure.operit.data.preferences.ApiPreferences
import com.cynosure.operit.ui.common.displays.MessageContentParser
import com.cynosure.operit.ui.permissions.ToolPermissionSystem
import com.cynosure.operit.util.stream.splitBy
import com.cynosure.operit.util.stream.stream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles the extraction and execution of AI tools from responses Supports real-time streaming
 * extraction and execution of tools
 */
class AIToolHandler private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AIToolHandler"
        private const val TOOL_ERROR_COOLDOWN_MS = 5000L
        private const val TOOL_EXECUTION_TIMEOUT_MS = 300_000L

        @Volatile private var INSTANCE: AIToolHandler? = null

        fun getInstance(context: Context): AIToolHandler {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: AIToolHandler(context.applicationContext).also { INSTANCE = it }
                    }
        }
    }

    private val toolErrorDeduplicator = ToolErrorDeduplicator(TOOL_ERROR_COOLDOWN_MS)

    internal fun shouldEmitToolError(tool: AITool, errorMessage: String): Boolean {
        val shouldEmit = toolErrorDeduplicator.shouldEmit(tool, errorMessage)
        if (!shouldEmit) {
            AppLogger.d(TAG, "Collapsed repeated tool error: ${tool.name}")
        }
        return shouldEmit
    }

    private fun collapseToolError(tool: AITool, errorMessage: String): String {
        return if (shouldEmitToolError(tool, errorMessage)) {
            errorMessage
        } else {
            "Repeated identical tool error suppressed."
        }
    }

    // Available tools registry
    private val availableTools = ConcurrentHashMap<String, ToolExecutor>()
    private val toolHooks = CopyOnWriteArrayList<AIToolHook>()

    private val defaultToolsRegistered = AtomicBoolean(false)
    private val registrationLock = Any()

    // Tool permission system
    private val toolPermissionSystem = ToolPermissionSystem.getInstance(context)

    /** Get the tool permission system for UI use */
    fun getToolPermissionSystem(): ToolPermissionSystem {
        return toolPermissionSystem
    }
    
    fun unregisterTool(toolName: String) {
        availableTools.remove(toolName)
    }

    /**
     * Registers a hook for tool call lifecycle events and pre-execution interception.
     * Hook callbacks must be lightweight.
     */
    fun addToolHook(hook: AIToolHook) {
        if (!toolHooks.contains(hook)) {
            toolHooks.add(hook)
        }
    }

    /** Removes a previously registered tool hook. */
    fun removeToolHook(hook: AIToolHook) {
        toolHooks.remove(hook)
    }

    /** Clears all registered tool hooks. */
    fun clearToolHooks() {
        toolHooks.clear()
    }

    /** Dispatches lifecycle hook callbacks safely. */
    private inline fun notifyHooks(eventName: String, action: (AIToolHook) -> Unit) {
        toolHooks.forEach { hook ->
            try {
                action(hook)
            } catch (e: Exception) {
                AppLogger.w(TAG, "AIToolHook callback failed at $eventName", e)
            }
        }
    }

    /** Notify that a tool call request is received. */
    fun notifyToolCallRequested(tool: AITool) {
        notifyHooks("onToolCallRequested") { it.onToolCallRequested(tool) }
        BusinessEventBus.publish(
            BusinessEvent(
                type = BusinessEventType.TOOL_REQUESTED,
                source = tool.name
            )
        )
    }

    /** Ask hooks whether the tool call should continue. */
    fun checkToolInterception(tool: AITool): AIToolHookDecision {
        // 防删代码层：当防幻觉开关打开时，检查命令类工具的删除风险
        if (ApiPreferences.getInstance(context).isFileGuardEnabledBlocking()) {
            val command = tool.parameters.firstOrNull { it.name == "command" }?.value
            if (!command.isNullOrBlank()) {
                val risk = AntiHallucinationGuard.analyzeCommandRisk(command)
                if (risk.requiresConfirmation) {
                    val reason = risk.reasons.joinToString("; ")
                    AppLogger.w(TAG, "FileGuard blocked tool ${tool.name}: $reason")
                    return AIToolHookDecision.Block("防删代码层拦截: $reason")
                }
            }
        }

        toolHooks.forEach { hook ->
            val decision =
                    try {
                        hook.onToolCallIntercept(tool)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "AIToolHook interception failed at onToolCallIntercept", e)
                        return AIToolHookDecision.Block(
                                "Tool hook callback failed at onToolCallIntercept."
                        )
                    }
            when (decision) {
                AIToolHookDecision.Allow -> Unit
                is AIToolHookDecision.Block -> return decision
            }
        }
        return AIToolHookDecision.Allow
    }

    /** Build the standard result returned when a hook blocks a tool call. */
    fun buildToolInterceptionResult(
            toolName: String,
            decision: AIToolHookDecision.Block
    ): ToolResult {
        return ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = decision.reason
        )
    }

    /** Notify that permission check finished for a tool call. */
    fun notifyToolPermissionChecked(tool: AITool, granted: Boolean, reason: String? = null) {
        notifyHooks("onToolPermissionChecked") { it.onToolPermissionChecked(tool, granted, reason) }
    }

    /** Notify that actual tool execution starts. */
    fun notifyToolExecutionStarted(tool: AITool) {
        notifyHooks("onToolExecutionStarted") { it.onToolExecutionStarted(tool) }
        BusinessEventBus.publish(
            BusinessEvent(
                type = BusinessEventType.TOOL_STARTED,
                source = tool.name
            )
        )
    }

    /** Notify that a tool execution result is produced. */
    fun notifyToolExecutionResult(tool: AITool, result: ToolResult) {
        notifyHooks("onToolExecutionResult") { it.onToolExecutionResult(tool, result) }
        BusinessEventBus.publish(
            BusinessEvent(
                type = if (result.success) {
                    BusinessEventType.TOOL_COMPLETED
                } else {
                    BusinessEventType.TOOL_FAILED
                },
                source = tool.name,
                attributes = result.error?.let { mapOf("error" to it.take(500)) }.orEmpty()
            )
        )
    }

    /** Notify that tool execution throws an exception. */
    fun notifyToolExecutionError(tool: AITool, throwable: Throwable) {
        notifyHooks("onToolExecutionError") { it.onToolExecutionError(tool, throwable) }
    }

    /** Notify that a tool request lifecycle is finished. */
    fun notifyToolExecutionFinished(tool: AITool) {
        notifyHooks("onToolExecutionFinished") { it.onToolExecutionFinished(tool) }
    }

    /**
     * Get all registered tool names
     */
    fun getAllToolNames(): List<String> {
        return availableTools.keys.toList().sorted()
    }
    /**
     * Get human-readable description for a tool by name.
     * Used by the tool selector UI to help users understand each tool's purpose.
     */
    fun getToolDescription(toolName: String): String {
        return toolPermissionSystem.getOperationDescription(AITool(name = toolName))
    }



    /** Force refresh permission request state Can be called if permission dialog is not showing */
    fun refreshPermissionState(): Boolean {
        return toolPermissionSystem.refreshPermissionRequestState()
    }

    // 工具注册的唯一方法 - 提供完整信息的注册
    fun registerTool(
            name: String,
            descriptionGenerator: ((AITool) -> String)? = null,
            executor: ToolExecutor
    ) {
        availableTools[name] = executor

        // 注册描述生成器（如果提供）
        if (descriptionGenerator != null) {
            toolPermissionSystem.registerOperationDescription(name, descriptionGenerator)
        }
    }

    // 添加重载方法接受函数式接口作为executor的便捷写法
    fun registerTool(
            name: String,
            descriptionGenerator: ((AITool) -> String)? = null,
            executor: (AITool) -> ToolResult
    ) {
        registerTool(
                name = name,
                descriptionGenerator = descriptionGenerator,
                executor =
                        object : ToolExecutor {
                            override fun invoke(tool: AITool): ToolResult {
                                return executor(tool)
                            }
                        }
        )
    }

    // Register all default tools
    fun registerDefaultTools() {
        if (defaultToolsRegistered.get()) return
        synchronized(registrationLock) {
            if (defaultToolsRegistered.get()) return
            registerAllTools(this, context)
            defaultToolsRegistered.set(true)
        }
    }

    // Package manager instance (lazy initialized)
    private var packageManagerInstance: PackageManager? = null

    /** Gets or creates the package manager instance */
    fun getOrCreatePackageManager(): PackageManager {
        return packageManagerInstance
                ?: run {
                    packageManagerInstance = PackageManager.getInstance(context, this)
                    packageManagerInstance!!
                }
    }

    /** Replace a tool invocation in the response with its result */
    private fun replaceToolInvocation(
            response: String,
            invocation: ToolInvocation,
            result: String
    ): String {
        val before = response.substring(0, invocation.responseLocation.first)
        val after = response.substring(invocation.responseLocation.last + 1)

        return "$before\n**Tool Result [${invocation.tool.name}]:** \n$result\n$after"
    }

    /**
     * Unescapes XML special characters
     * @param input The XML escaped string
     * @return Unescaped string
     */
    private fun unescapeXml(input: String): String {
        var result = input

        // 处理 CDATA 标记
        if (result.startsWith("<![CDATA[") && result.endsWith("]]>")) {
            result = result.substring(9, result.length - 3)
        }

        // 即使没有完整的 CDATA 标记，也尝试清理末尾的 ]]> 和开头的 <![CDATA[
        if (result.endsWith("]]>")) {
            result = result.substring(0, result.length - 3)
        }

        if (result.startsWith("<![CDATA[")) {
            result = result.substring(9)
        }

        return result.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
    }

    /** Reset the tool execution state */
    fun reset() {
        synchronized(registrationLock) {
            availableTools.clear()
            packageManagerInstance = null
            defaultToolsRegistered.set(false)
        }
    }

    /**
     * Get a registered tool executor by name
     * @param toolName The name of the tool
     * @return The tool executor or null if not found
     */
    fun getToolExecutor(toolName: String): ToolExecutor? {
        return availableTools[toolName]
    }

    private fun isMcpServiceActive(packageName: String): Boolean {
        val client = MCPManager.getInstance(context).getOrCreateClient(packageName) ?: return false
        val serviceInfo = runBlocking { client.getServiceInfo() } ?: return false
        return serviceInfo.active && serviceInfo.ready
    }


    /**
     * Returns a tool executor if available.
     * If missing, it will:
     * - Ensure default tools are registered (idempotent)
     * - Auto-activate a package when the name looks like 'packName:toolName'
     */
    fun getToolExecutorOrActivate(toolName: String): ToolExecutor? {
        var executor = availableTools[toolName]

        if (executor == null && !defaultToolsRegistered.get()) {
            try {
                registerDefaultTools()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to register default tools before executing tool $toolName", e)
            }
            executor = availableTools[toolName]
        }

        if (executor == null && toolName.contains(':')) {
            val packageName = toolName.substringBefore(':', missingDelimiterValue = "")
            if (packageName.isNotBlank()) {
                try {
                    val packageManager = getOrCreatePackageManager()
                    val isPackageAvailable = packageManager.getAvailablePackages().containsKey(packageName)
                    val isMcpAvailable = packageManager.getAvailableServerPackages().containsKey(packageName)
                    if (isPackageAvailable || isMcpAvailable) {
                        AppLogger.d(TAG, "Auto-activating package '$packageName' for tool $toolName")
                        packageManager.usePackage(packageName)
                        executor = availableTools[toolName]
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to auto-activate package '$packageName' for tool $toolName", e)
                }
            }
        }

        if (executor != null && toolName.contains(':')) {
            val packageName = toolName.substringBefore(':', missingDelimiterValue = "")
            if (packageName.isNotBlank()) {
                try {
                    val packageManager = getOrCreatePackageManager()
                    val isMcpAvailable =
                            packageManager.getAvailableServerPackages().containsKey(packageName)
                    if (isMcpAvailable && !isMcpServiceActive(packageName)) {
                        AppLogger.d(
                                TAG,
                                "MCP service '$packageName' is inactive while resolving $toolName, auto-reactivating package"
                        )
                        packageManager.usePackage(packageName)
                        executor = availableTools[toolName]
                    }
                } catch (e: Exception) {
                    AppLogger.e(
                            TAG,
                            "Failed to auto-reactivate MCP package '$packageName' for tool $toolName",
                            e
                    )
                }
            }
        }

        return executor
    }


    /** Executes a tool directly */
    fun executeTool(tool: AITool): ToolResult {
        notifyToolCallRequested(tool)
        when (val interception = checkToolInterception(tool)) {
            AIToolHookDecision.Allow -> Unit
            is AIToolHookDecision.Block -> {
                val interceptedResult = buildToolInterceptionResult(tool.name, interception)
                notifyToolExecutionResult(tool, interceptedResult)
                notifyToolExecutionFinished(tool)
                return interceptedResult
            }
        }

        val executor = getToolExecutorOrActivate(tool.name)

        if (executor == null) {
            val notFoundResult =
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = collapseToolError(tool, "Tool not found: ${tool.name}")
                    )
            notifyToolExecutionResult(tool, notFoundResult)
            notifyToolExecutionFinished(tool)
            return notFoundResult
        }

        // Validate parameters
        val validationResult = executor.validateParameters(tool)
        if (!validationResult.valid) {
            val validationFailedResult =
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = collapseToolError(tool, validationResult.errorMessage)
                    )
            notifyToolExecutionResult(tool, validationFailedResult)
            notifyToolExecutionFinished(tool)
            return validationFailedResult
        }

        notifyToolExecutionStarted(tool)
        return try {
            val result = executor.invoke(tool)
            if (!result.success && result.error != null) {
                result.copy(error = collapseToolError(tool, result.error))
            } else {
                if (result.success) {
                    toolErrorDeduplicator.clear(tool)
                }
                result
            }
        } catch (e: Exception) {
            notifyToolExecutionError(tool, e)
            val errorResult = ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = collapseToolError(tool, e.message ?: "Unknown tool execution error")
            )
            notifyToolExecutionResult(tool, errorResult)
            return errorResult
        } finally {
            notifyToolExecutionFinished(tool)
        }
    }

    /** Executes a tool and preserves intermediate streaming results when supported by the executor. */
    fun executeToolAndStream(tool: AITool): Flow<ToolResult> = flow {
        notifyToolCallRequested(tool)
        when (val interception = checkToolInterception(tool)) {
            AIToolHookDecision.Allow -> Unit
            is AIToolHookDecision.Block -> {
                val interceptedResult = buildToolInterceptionResult(tool.name, interception)
                notifyToolExecutionResult(tool, interceptedResult)
                notifyToolExecutionFinished(tool)
                emit(interceptedResult)
                return@flow
            }
        }

        val executor = getToolExecutorOrActivate(tool.name)

        if (executor == null) {
            val notFoundResult =
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Tool not found: ${tool.name}"
                )
            notifyToolExecutionResult(tool, notFoundResult)
            notifyToolExecutionFinished(tool)
            emit(notFoundResult)
            return@flow
        }

        val validationResult = executor.validateParameters(tool)
        if (!validationResult.valid) {
            val validationFailedResult =
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = validationResult.errorMessage
                )
            notifyToolExecutionResult(tool, validationFailedResult)
            notifyToolExecutionFinished(tool)
            emit(validationFailedResult)
            return@flow
        }

        notifyToolExecutionStarted(tool)
        try {
            val completed = withTimeoutOrNull(TOOL_EXECUTION_TIMEOUT_MS) {
                executor.invokeAndStream(tool).collect { result ->
                    notifyToolExecutionResult(tool, result)
                    emit(result)
                }
                true
            } == true
            if (!completed) {
                val timeoutResult =
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error =
                            "Tool execution timed out after " +
                                "${TOOL_EXECUTION_TIMEOUT_MS / 1000}s"
                    )
                notifyToolExecutionResult(tool, timeoutResult)
                emit(timeoutResult)
            }
        } catch (e: Exception) {
            notifyToolExecutionError(tool, e)
            val errorResult = ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = collapseToolError(tool, e.message ?: "Unknown streaming tool execution error")
            )
            notifyToolExecutionResult(tool, errorResult)
            throw e
        } finally {
            notifyToolExecutionFinished(tool)
        }
    }
}

/** Interface for tool executors */
interface ToolExecutor {
    fun invoke(tool: AITool): ToolResult

    fun invokeAndStream(tool: AITool): Flow<ToolResult> = flowOf(invoke(tool))

    /**
     * Validates the parameters of a tool before execution Default implementation always returns
     * valid
     */
    fun validateParameters(tool: AITool): ToolValidationResult {
        return ToolValidationResult(valid = true)
    }
}
