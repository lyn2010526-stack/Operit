package com.cynosure.operit.data.model

data class UserInputQuestion(
    val key: String,
    val label: String,
    val type: QuestionType,
    val options: List<String> = emptyList(),
    val required: Boolean = true,
    val placeholder: String = ""
)

enum class QuestionType {
    TEXT,
    SELECT,
    MULTI_SELECT,
    NUMBER,
    BOOLEAN
}

data class UserInputRequest(
    val id: String,
    val title: String,
    val description: String = "",
    val questions: List<UserInputQuestion>,
    val createdAt: Long = System.currentTimeMillis()
)

data class UserInputResponse(
    val requestId: String,
    val answers: Map<String, String>
)