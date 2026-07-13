package com.cynosure.operit.core.tools

import com.cynosure.operit.core.events.BusinessEvent
import com.cynosure.operit.core.events.BusinessEventBus
import com.cynosure.operit.core.events.BusinessEventType
import com.cynosure.operit.core.agent.AgentExecutionSource
import com.cynosure.operit.core.agent.AgentExecutionState
import com.cynosure.operit.core.agent.AgentExecutionStore
import com.cynosure.operit.data.model.AITool
import com.cynosure.operit.data.model.QuestionType
import com.cynosure.operit.data.model.ToolResult
import com.cynosure.operit.data.model.UserInputQuestion
import com.cynosure.operit.data.model.UserInputRequest
import com.cynosure.operit.services.core.UserInputRequestRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class CollectUserInputExecutor : ToolExecutor {

    private val repository = UserInputRequestRepository

    override fun invoke(tool: AITool): ToolResult {
        return runBlocking(Dispatchers.IO) {
            val request = try {
                buildRequest(tool)
            } catch (e: IllegalArgumentException) {
                return@runBlocking failureResult(e.message ?: "Invalid user input request")
            }
            val requestId = try {
                repository.createRequest(request)
            } catch (e: IllegalStateException) {
                return@runBlocking failureResult(e.message ?: "Unable to create user input request")
            }

            try {
                AgentExecutionStore.start(
                    taskId = requestId,
                    source = AgentExecutionSource.TOOL,
                    title = request.title,
                    owner = "collect_user_input",
                    initialState = AgentExecutionState.RUNNING
                )
                AgentExecutionStore.transition(
                    requestId,
                    AgentExecutionState.WAITING_USER,
                    detail = request.description.ifBlank { request.title }
                )
                ToolProgressBus.update("collect_user_input", 0.5f, request.title)
                BusinessEventBus.publish(
                    BusinessEvent(
                        id = requestId,
                        type = BusinessEventType.TOOL_REQUESTED,
                        source = "collect_user_input",
                        entityId = requestId,
                        attributes = mapOf(
                            "title" to request.title,
                            "questionCount" to request.questions.size.toString()
                        )
                    )
                )
                val response = repository.awaitResponse(requestId)
                AgentExecutionStore.transition(
                    requestId,
                    AgentExecutionState.RUNNING,
                    detail = "用户输入已提交"
                )
                val answersJson = JSONObject(response.answers).toString()
                AgentExecutionStore.transition(
                    requestId,
                    AgentExecutionState.COMPLETED,
                    detail = "用户输入收集完成"
                )
                ToolResult(
                    toolName = "collect_user_input",
                    success = true,
                    result = StringResultData(answersJson)
                )
            } catch (e: Exception) {
                repository.cancelRequest(requestId)
                if (e is CancellationException) {
                    AgentExecutionStore.get(requestId)?.let {
                        AgentExecutionStore.cancel(requestId, "用户输入收集已取消")
                    }
                    throw e
                }
                AgentExecutionStore.get(requestId)?.let {
                    AgentExecutionStore.transition(
                        requestId,
                        AgentExecutionState.FAILED,
                        detail = e.message ?: "用户输入收集失败"
                    )
                }
                failureResult("User input collection cancelled or timed out")
            } finally {
                ToolProgressBus.clear()
            }
        }
    }

    override fun invokeAndStream(tool: AITool): Flow<ToolResult> = flow {
        val result = invoke(tool)
        emit(result)
    }

    private fun buildRequest(tool: AITool): UserInputRequest {
        val title = tool.parameterValue("title").orEmpty().trim()
        require(title.isNotEmpty()) { "Parameter 'title' is required" }
        val description = tool.parameterValue("description").orEmpty().trim()
        val questionsValue = tool.parameterValue("questions")
            ?: throw IllegalArgumentException("Parameter 'questions' is required")
        val questionsArray = try {
            JSONArray(questionsValue)
        } catch (e: Exception) {
            throw IllegalArgumentException("Parameter 'questions' must be a JSON array", e)
        }
        require(questionsArray.length() > 0) { "Parameter 'questions' must contain at least one question" }

        val questions = (0 until questionsArray.length()).map { i ->
            val q = try {
                questionsArray.getJSONObject(i)
            } catch (e: Exception) {
                throw IllegalArgumentException("Question ${i + 1} must be a JSON object", e)
            }
            val key = q.optString("key").trim()
            val label = q.optString("label").trim()
            require(key.isNotEmpty()) { "Question ${i + 1} requires a non-empty key" }
            require(label.isNotEmpty()) { "Question ${i + 1} requires a non-empty label" }
            val type = parseType(q.optString("type", "text"))
            val options = parseJsonArray(q.optJSONArray("options"))
            require(type !in setOf(QuestionType.SELECT, QuestionType.MULTI_SELECT) || options.isNotEmpty()) {
                "Question '$key' requires at least one option"
            }
            UserInputQuestion(
                key = key,
                label = label,
                type = type,
                options = options,
                required = q.optBoolean("required", true),
                placeholder = q.optString("placeholder", "")
            )
        }
        require(questions.map { it.key }.distinct().size == questions.size) {
            "Question keys must be unique"
        }

        return UserInputRequest(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            questions = questions
        )
    }

    private fun parseType(type: String): QuestionType {
        return when (type.lowercase()) {
            "select", "choice", "dropdown" -> QuestionType.SELECT
            "multi_select", "multi_choice", "checkbox" -> QuestionType.MULTI_SELECT
            "number", "int", "integer" -> QuestionType.NUMBER
            "boolean", "bool", "switch", "toggle" -> QuestionType.BOOLEAN
            "text" -> QuestionType.TEXT
            else -> throw IllegalArgumentException("Unsupported question type: $type")
        }
    }

    private fun parseJsonArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
    }

    private fun AITool.parameterValue(name: String): String? =
        parameters.find { it.name == name }?.value

    private fun failureResult(error: String) = ToolResult(
        toolName = "collect_user_input",
        success = false,
        result = StringResultData(""),
        error = error
    )
}
