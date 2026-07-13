package com.cynosure.operit.data.model

data class GroupMemberConfig(
    val characterCardId: String,
    val orderIndex: Int = 0,
    val role: String = "",
    val modelConfigIdOverride: String? = null
)

data class CharacterGroupCard(
    val id: String,
    val name: String,
    val description: String = "",
    val members: List<GroupMemberConfig> = emptyList(),
    val systemPrompt: String = "",
    val rules: String = "",
    val maxRounds: Int = 5,
    val enableSelfCorrection: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)