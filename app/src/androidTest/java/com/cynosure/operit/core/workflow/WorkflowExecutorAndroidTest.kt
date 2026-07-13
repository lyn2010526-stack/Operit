package com.cynosure.operit.core.workflow

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cynosure.operit.data.model.ConditionNode
import com.cynosure.operit.data.model.ConditionOperator
import com.cynosure.operit.data.model.ExecuteNode
import com.cynosure.operit.data.model.NodePosition
import com.cynosure.operit.data.model.ParameterValue
import com.cynosure.operit.data.model.TriggerNode
import com.cynosure.operit.data.model.Workflow
import com.cynosure.operit.data.model.WorkflowNodeConnection
import com.cynosure.operit.data.model.GlobalRefNode
import com.cynosure.operit.data.model.WorkflowGlobalNode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkflowExecutorAndroidTest {

    @Test
    fun repeatedGlobalReferencesKeepInstanceIdentity() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WorkflowGlobalNodeManager.getInstance(context)
        val template = WorkflowGlobalNode(
            id = "global-condition",
            name = "Template",
            nodeData = ConditionNode(
                id = "template-id",
                name = "Template condition",
                left = ParameterValue.StaticValue("1"),
                operator = ConditionOperator.EQ,
                right = ParameterValue.StaticValue("1")
            )
        )
        manager.saveGlobalNode(template).getOrThrow()
        val workflow = Workflow(
            id = "global-workflow",
            nodes = listOf(
                GlobalRefNode(id = "first", name = "First", globalNodeId = template.id),
                GlobalRefNode(id = "second", name = "Second", globalNodeId = template.id)
            )
        )

        val resolved = WorkflowExecutor(context).resolveGlobalNodes(workflow)

        assertEquals(listOf("first", "second"), resolved.map { it.id })
        assertEquals(listOf("First", "Second"), resolved.map { it.name })
    }

    @Test(expected = IllegalStateException::class)
    fun missingGlobalReferenceFailsExplicitly() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WorkflowExecutor(context).resolveGlobalNodes(
            Workflow(id = "missing", nodes = listOf(GlobalRefNode(globalNodeId = "missing-template")))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyGlobalReferenceFailsExplicitly() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WorkflowExecutor(context).resolveGlobalNodes(
            Workflow(id = "empty", nodes = listOf(GlobalRefNode(globalNodeId = "")))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun globalTemplateRejectsTriggerNode() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WorkflowGlobalNodeManager.getInstance(context).saveGlobalNode(
            WorkflowGlobalNode(id = "invalid-trigger", nodeData = TriggerNode())
        ).getOrThrow()
    }

    @Test
    fun nodeFailureShouldNotStopWorkflowExecution() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val trigger = TriggerNode(
            id = "trigger",
            name = "Trigger",
            triggerType = "manual",
            position = NodePosition(0f, 0f)
        )

        val failingNode = ExecuteNode(
            id = "fail",
            name = "Failing Node",
            actionType = "",
            position = NodePosition(1f, 0f)
        )

        val succeedingNode = ConditionNode(
            id = "ok",
            name = "Succeeding Node",
            left = ParameterValue.StaticValue("1"),
            operator = ConditionOperator.EQ,
            right = ParameterValue.StaticValue("1"),
            position = NodePosition(1f, 1f)
        )

        val workflow = Workflow(
            id = "workflow",
            name = "Test Workflow",
            enabled = true,
            nodes = listOf(trigger, failingNode, succeedingNode),
            connections = listOf(
                WorkflowNodeConnection(sourceNodeId = trigger.id, targetNodeId = failingNode.id),
                WorkflowNodeConnection(sourceNodeId = trigger.id, targetNodeId = succeedingNode.id)
            )
        )

        val executor = WorkflowExecutor(context)

        val result = runBlocking {
            executor.executeWorkflow(workflow) { _, _ -> }
        }

        assertFalse(result.success)
        assertTrue(result.nodeResults[failingNode.id] is NodeExecutionState.Failed)
        assertTrue(result.nodeResults[succeedingNode.id] is NodeExecutionState.Success)
    }

    @Test
    fun onErrorBranchShouldExecuteWhenNodeFails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val trigger = TriggerNode(
            id = "trigger",
            name = "Trigger",
            triggerType = "manual",
            position = NodePosition(0f, 0f)
        )

        val mainNode = ExecuteNode(
            id = "main",
            name = "Main (Fail)",
            actionType = "",
            position = NodePosition(1f, 0f)
        )

        val successBranch = ConditionNode(
            id = "success",
            name = "Success Branch",
            left = ParameterValue.StaticValue("1"),
            operator = ConditionOperator.EQ,
            right = ParameterValue.StaticValue("1"),
            position = NodePosition(2f, 0f)
        )

        val errorBranch = ConditionNode(
            id = "error",
            name = "Error Branch",
            left = ParameterValue.StaticValue("1"),
            operator = ConditionOperator.EQ,
            right = ParameterValue.StaticValue("1"),
            position = NodePosition(2f, 1f)
        )

        val workflow = Workflow(
            id = "workflow",
            name = "Error Branch Test Workflow",
            enabled = true,
            nodes = listOf(trigger, mainNode, successBranch, errorBranch),
            connections = listOf(
                WorkflowNodeConnection(sourceNodeId = trigger.id, targetNodeId = mainNode.id),
                WorkflowNodeConnection(sourceNodeId = mainNode.id, targetNodeId = successBranch.id, condition = "on_success"),
                WorkflowNodeConnection(sourceNodeId = mainNode.id, targetNodeId = errorBranch.id, condition = "on_error")
            )
        )

        val executor = WorkflowExecutor(context)

        val result = runBlocking {
            executor.executeWorkflow(workflow) { _, _ -> }
        }

        assertTrue(result.nodeResults[mainNode.id] is NodeExecutionState.Failed)
        assertTrue(result.nodeResults[successBranch.id] is NodeExecutionState.Skipped)
        assertTrue(result.nodeResults[errorBranch.id] is NodeExecutionState.Success)
    }
}
