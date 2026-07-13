package com.cynosure.operit.ui.features.chat.components

internal sealed interface ExportUiState {
    data object Idle : ExportUiState

    data class Running(
        val progress: Float,
        val status: String,
    ) : ExportUiState

    data class Completed(
        val success: Boolean,
        val filePath: String?,
        val errorMessage: String?,
    ) : ExportUiState
}
