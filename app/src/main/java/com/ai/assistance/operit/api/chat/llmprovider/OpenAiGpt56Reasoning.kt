package com.ai.assistance.operit.api.chat.llmprovider

internal object OpenAiGpt56Reasoning {
    private val efforts = listOf("low", "medium", "high", "xhigh", "max")

    private val supportedModelIds = setOf(
        "gpt-5.6",
        "gpt-5.6-sol",
        "gpt-5.6-terra",
        "gpt-5.6-luna"
    )

    fun supports(modelName: String): Boolean =
        modelName.trim().lowercase() in supportedModelIds

    fun effortForQualityLevel(qualityLevel: Int): String =
        efforts[qualityLevel.coerceIn(1, efforts.size) - 1]
}
