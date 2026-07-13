package com.cynosure.operit.core.tools

import com.cynosure.operit.data.model.AITool
import com.cynosure.operit.data.model.ToolParameter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolErrorDeduplicatorTest {
    @Test
    fun identicalToolParametersAndError_areCollapsedDuringCooldown() {
        var now = 1_000L
        val deduplicator = ToolErrorDeduplicator(cooldownMs = 5_000L) { now }
        val tool = AITool("read_file", listOf(ToolParameter("path", "/tmp/a")))

        assertTrue(deduplicator.shouldEmit(tool, "Permission denied"))
        now += 100
        assertFalse(deduplicator.shouldEmit(tool, " Permission   denied "))
    }

    @Test
    fun parameterOrErrorChange_isASeparateSignature() {
        val deduplicator = ToolErrorDeduplicator(cooldownMs = 5_000L) { 1_000L }
        val first = AITool("read_file", listOf(ToolParameter("path", "/tmp/a")))
        val second = AITool("read_file", listOf(ToolParameter("path", "/tmp/b")))

        assertTrue(deduplicator.shouldEmit(first, "Permission denied"))
        assertTrue(deduplicator.shouldEmit(second, "Permission denied"))
        assertTrue(deduplicator.shouldEmit(first, "File missing"))
    }

    @Test
    fun parameterOrder_doesNotChangeSignature() {
        val deduplicator = ToolErrorDeduplicator(cooldownMs = 5_000L) { 1_000L }
        val first = AITool(
            "http_request",
            listOf(ToolParameter("url", "https://example.com"), ToolParameter("method", "GET")),
        )
        val reordered = AITool(
            "http_request",
            listOf(ToolParameter("method", "GET"), ToolParameter("url", "https://example.com")),
        )

        assertTrue(deduplicator.shouldEmit(first, "timeout"))
        assertFalse(deduplicator.shouldEmit(reordered, "timeout"))
    }
}
