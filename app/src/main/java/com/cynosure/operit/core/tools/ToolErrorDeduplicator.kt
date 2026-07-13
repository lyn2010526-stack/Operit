package com.cynosure.operit.core.tools

import com.cynosure.operit.data.model.AITool
import java.util.concurrent.ConcurrentHashMap

internal class ToolErrorDeduplicator(
    private val cooldownMs: Long,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val lastSeenBySignature = ConcurrentHashMap<String, Long>()

    fun shouldEmit(tool: AITool, error: String): Boolean {
        val now = nowMillis()
        val signature = signature(tool, error)
        val previous = lastSeenBySignature.put(signature, now)
        return previous == null || now - previous >= cooldownMs
    }

    fun clear(tool: AITool) {
        val invocationPrefix = invocationSignature(tool) + '\u0000'
        lastSeenBySignature.keys.removeIf { it.startsWith(invocationPrefix) }
    }

    internal fun signature(tool: AITool, error: String): String {
        return invocationSignature(tool) + '\u0000' + normalizeError(error)
    }

    private fun invocationSignature(tool: AITool): String {
        val parameters =
            tool.parameters
                .sortedWith(compareBy({ it.name }, { it.value }))
                .joinToString("\u0001") { "${it.name}\u0002${it.value}" }
        return "${tool.name}\u0000$parameters"
    }

    private fun normalizeError(error: String): String {
        return error.trim().replace(Regex("\\s+"), " ")
    }
}
