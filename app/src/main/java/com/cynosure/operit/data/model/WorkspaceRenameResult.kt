package com.cynosure.operit.data.model

data class WorkspaceRenameResult(
    val workspacePath: String,
    val workspaceEnv: String?,
    val workspaceName: String
)
