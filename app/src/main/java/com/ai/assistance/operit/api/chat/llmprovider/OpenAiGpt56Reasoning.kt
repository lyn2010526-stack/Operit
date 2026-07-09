package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProviderType

internal object OpenAiGpt56Reasoning {
    const val LEGACY_MAX_QUALITY_LEVEL = 4
    const val GPT56_MAX_QUALITY_LEVEL = 5

    private val efforts = listOf("low", "medium", "high", "xhigh", "max")

    private val chatProviderTypes = setOf(
        ApiProviderType.OPENAI,
        ApiProviderType.OPENAI_GENERIC
    )

    private val responsesProviderTypes = setOf(
        ApiProviderType.OPENAI_RESPONSES,
        ApiProviderType.OPENAI_RESPONSES_GENERIC
    )

    private val supportedProviderTypes = chatProviderTypes + responsesProviderTypes

    private val supportedModelIds = setOf(
        "gpt-5.6",
        "gpt-5.6-sol",
        "gpt-5.6-terra",
        "gpt-5.6-luna"
    )

    fun supports(modelName: String): Boolean =
        modelName.trim().lowercase() in supportedModelIds

    fun supports(providerType: ApiProviderType?, modelName: String): Boolean =
        providerType in supportedProviderTypes && supports(modelName)

    fun supportsChat(providerType: ApiProviderType, modelName: String): Boolean =
        providerType in chatProviderTypes && supports(modelName)

    fun supportsResponses(providerType: ApiProviderType, modelName: String): Boolean =
        providerType in responsesProviderTypes && supports(modelName)

    fun maxQualityLevel(providerType: ApiProviderType?, modelName: String): Int =
        if (supports(providerType, modelName)) {
            GPT56_MAX_QUALITY_LEVEL
        } else {
            LEGACY_MAX_QUALITY_LEVEL
        }

    fun effortForQualityLevel(qualityLevel: Int): String =
        efforts[qualityLevel.coerceIn(1, efforts.size) - 1]
}
