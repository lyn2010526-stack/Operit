package com.cynosure.operit.core.workflow

import android.content.Context
import android.util.AtomicFile
import com.cynosure.operit.data.model.ConditionNode
import com.cynosure.operit.data.model.ExecuteNode
import com.cynosure.operit.data.model.ExtractNode
import com.cynosure.operit.data.model.GlobalRefNode
import com.cynosure.operit.data.model.LogicNode
import com.cynosure.operit.data.model.ParameterValue
import com.cynosure.operit.data.model.WorkflowGlobalNode
import com.cynosure.operit.data.model.WorkflowNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStreamWriter

class WorkflowGlobalNodeManager private constructor(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        classDiscriminator = "__type"
    }
    private val mutex = Mutex()
    private val atomicFile by lazy {
        AtomicFile(File(context.filesDir, "workflow_global_nodes.json"))
    }
    private val _globalNodes = MutableStateFlow<List<WorkflowGlobalNode>>(emptyList())
    val globalNodes: StateFlow<List<WorkflowGlobalNode>> = _globalNodes.asStateFlow()
    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error.asStateFlow()

    companion object {
        @Volatile
        private var instance: WorkflowGlobalNodeManager? = null

        fun getInstance(context: Context): WorkflowGlobalNodeManager {
            return instance ?: synchronized(this) {
                instance ?: WorkflowGlobalNodeManager(context.applicationContext).also { instance = it }
            }
        }
    }

    suspend fun refresh(): Result<List<WorkflowGlobalNode>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { readLocked() }
                .onSuccess {
                    _globalNodes.value = it
                    _error.value = null
                }
                .onFailure { _error.value = it }
        }
    }

    suspend fun getAllGlobalNodes(): Result<List<WorkflowGlobalNode>> = refresh()

    suspend fun getGlobalNode(id: String): Result<WorkflowGlobalNode?> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val nodes = readLocked()
                _globalNodes.value = nodes
                _error.value = null
                nodes.find { it.id == id }
            }.onFailure { _error.value = it }
        }
    }

    suspend fun saveGlobalNode(node: WorkflowGlobalNode): Result<WorkflowGlobalNode> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                validateTemplate(node.nodeData)
                val nodes = readLocked().toMutableList()
                val existing = nodes.indexOfFirst { it.id == node.id }
                val updated = node.copy(updatedAt = System.currentTimeMillis())
                if (existing >= 0) nodes[existing] = updated else nodes.add(updated)
                writeLocked(nodes)
                _globalNodes.value = nodes.toList()
                _error.value = null
                updated
            }.onFailure { _error.value = it }
        }
    }

    suspend fun deleteGlobalNode(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val current = readLocked()
                val nodes = current.filter { it.id != id }
                if (nodes.size == current.size) return@runCatching false
                writeLocked(nodes)
                _globalNodes.value = nodes
                _error.value = null
                true
            }.onFailure { _error.value = it }
        }
    }

    suspend fun resolveReference(reference: GlobalRefNode): Result<WorkflowNode> = withContext(Dispatchers.IO) {
        require(reference.globalNodeId.isNotBlank()) {
            "Global reference ${reference.id} has an empty template id"
        }
        getGlobalNode(reference.globalNodeId).mapCatching { global ->
            global ?: throw IllegalStateException("Global template not found: ${reference.globalNodeId}")
            validateTemplate(global.nodeData)
            instantiate(global.nodeData, reference)
        }
    }

    fun validateTemplate(node: WorkflowNode) {
        require(node is ExecuteNode || node is ConditionNode || node is LogicNode || node is ExtractNode) {
            "Global templates only support execute, condition, logic, and extract nodes"
        }
        require(node !is GlobalRefNode) { "Global templates cannot reference another global template" }
        require(parameterReferences(node).isEmpty()) {
            "Global templates cannot contain node parameter references"
        }
    }

    private fun parameterReferences(node: WorkflowNode): List<ParameterValue.NodeReference> = when (node) {
        is ExecuteNode -> node.actionConfig.values.filterIsInstance<ParameterValue.NodeReference>()
        is ConditionNode -> listOf(node.left, node.right).filterIsInstance<ParameterValue.NodeReference>()
        is ExtractNode -> (listOf(node.source) + node.others).filterIsInstance<ParameterValue.NodeReference>()
        else -> emptyList()
    }

    private fun instantiate(template: WorkflowNode, reference: GlobalRefNode): WorkflowNode {
        val name = reference.name.ifBlank { template.name }
        val description = reference.description.ifBlank { template.description }
        return when (template) {
            is ExecuteNode -> template.copy(id = reference.id, name = name, description = description, position = reference.position.copy())
            is ConditionNode -> template.copy(id = reference.id, name = name, description = description, position = reference.position.copy())
            is LogicNode -> template.copy(id = reference.id, name = name, description = description, position = reference.position.copy())
            is ExtractNode -> template.copy(id = reference.id, name = name, description = description, position = reference.position.copy())
            else -> throw IllegalArgumentException("Unsupported global template node type: ${template.type}")
        }
    }

    private fun readLocked(): List<WorkflowGlobalNode> {
        if (!atomicFile.baseFile.exists()) return emptyList()
        val content = atomicFile.openRead().bufferedReader().use { it.readText() }
        return if (content.isBlank()) emptyList() else json.decodeFromString(content)
    }

    private fun writeLocked(nodes: List<WorkflowGlobalNode>) {
        val stream = atomicFile.startWrite()
        try {
            val writer = OutputStreamWriter(stream)
            writer.write(json.encodeToString(nodes))
            writer.flush()
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            atomicFile.failWrite(stream)
            throw e
        }
    }
}
