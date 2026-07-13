package com.cynosure.operit.core.workflow

import android.content.Context
import com.cynosure.operit.data.model.WorkflowGlobalNode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class WorkflowGlobalNodeManager private constructor(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val storageFile: File
        get() = File(context.filesDir, "workflow_global_nodes.json")

    companion object {
        @Volatile
        private var instance: WorkflowGlobalNodeManager? = null

        fun getInstance(context: Context): WorkflowGlobalNodeManager {
            return instance ?: synchronized(this) {
                instance ?: WorkflowGlobalNodeManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun getAllGlobalNodes(): List<WorkflowGlobalNode> {
        if (!storageFile.exists()) return emptyList()
        return try {
            val content = storageFile.readText()
            if (content.isBlank()) emptyList()
            else json.decodeFromString<List<WorkflowGlobalNode>>(content)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getGlobalNode(id: String): WorkflowGlobalNode? {
        return getAllGlobalNodes().find { it.id == id }
    }

    fun saveGlobalNode(node: WorkflowGlobalNode) {
        val nodes = getAllGlobalNodes().toMutableList()
        val existing = nodes.indexOfFirst { it.id == node.id }
        val updated = node.copy(updatedAt = System.currentTimeMillis())
        if (existing >= 0) {
            nodes[existing] = updated
        } else {
            nodes.add(updated)
        }
        storageFile.writeText(json.encodeToString(nodes))
    }

    fun deleteGlobalNode(id: String) {
        val nodes = getAllGlobalNodes().filter { it.id != id }
        storageFile.writeText(json.encodeToString(nodes))
    }
}
