package com.cynosure.operit.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowSerializationTest {
    private val json = Json { classDiscriminator = "__type" }

    @Test
    fun workflowNodesUseStableTypeDiscriminator() {
        val workflow = Workflow(
            id = "workflow",
            nodes = listOf(GlobalRefNode(id = "ref", globalNodeId = "global"))
        )

        val encoded = json.encodeToString(workflow)
        val decoded = json.decodeFromString<Workflow>(encoded)

        assertTrue(encoded.contains("\"__type\""))
        assertTrue(encoded.contains("GlobalRefNode"))
        assertEquals("ref", decoded.nodes.single().id)
    }

    @Test
    fun parameterValuesUseStableTypeDiscriminator() {
        val node = ExecuteNode(actionConfig = mapOf("value" to ParameterValue.NodeReference("source")))

        val encoded = json.encodeToString<WorkflowNode>(node)

        assertTrue(encoded.contains("ExecuteNode"))
        assertTrue(encoded.contains("NodeReference"))
    }
}
