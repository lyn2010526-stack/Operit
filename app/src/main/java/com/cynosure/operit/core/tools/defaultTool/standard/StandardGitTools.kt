package com.cynosure.operit.core.tools.defaultTool.standard

import com.cynosure.operit.core.tools.GitDiffData
import com.cynosure.operit.core.tools.GitLogData
import com.cynosure.operit.core.tools.GitLogEntry
import com.cynosure.operit.core.tools.GitStatusData
import com.cynosure.operit.core.tools.GitStatusEntry
import com.cynosure.operit.core.tools.GitBlameData
import com.cynosure.operit.core.tools.GitBlameEntry
import com.cynosure.operit.core.tools.StringResultData
import com.cynosure.operit.core.tools.system.AndroidShellExecutor
import com.cynosure.operit.data.model.AITool
import com.cynosure.operit.data.model.ToolResult
import com.cynosure.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StandardGitTools {

    companion object {
        private const val TAG = "StandardGitTools"
        private const val MAX_LOG_COUNT = 50
        private const val MAX_DIFF_SIZE = 512 * 1024
    }

    private fun resolveRepoPath(path: String, environment: String?): String {
        val effectivePath = if (environment == "linux") {
            path
        } else {
            path.ifBlank { "/sdcard/" }
        }
        return File(effectivePath).absolutePath
    }

    private suspend fun executeGitCommand(
        repoPath: String,
        command: String
    ): AndroidShellExecutor.CommandResult {
        return try {
            val fullCommand = "cd ${escapeShellArg(repoPath)} 2>/dev/null || exit 1; $command"
            withContext(Dispatchers.IO) {
                AndroidShellExecutor.executeShellCommand(fullCommand)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Git command failed: $command in $repoPath", e)
            AndroidShellExecutor.CommandResult(false, "", "Git not available: ${e.message}", -1)
        }
    }

    private fun escapeShellArg(arg: String): String = "'${arg.replace("'", "'\\''")}'"

    suspend fun gitStatus(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val repoPath = resolveRepoPath(path, environment)

        val result = executeGitCommand(repoPath, "git status --porcelain -b 2>&1")

        if (!result.success && result.exitCode != 0) {
            return@withContext ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Git status failed: ${result.stderr.ifBlank { result.stdout }}"
            )
        }

        val lines = result.stdout.lines().filter { it.isNotBlank() }
        val branchLine = lines.firstOrNull { it.startsWith("## ") }
        val branch = branchLine?.removePrefix("## ")?.split("...")?.firstOrNull()?.trim() ?: "unknown"
        val entries = lines.filter { !it.startsWith("## ") }.mapNotNull { parseStatusLine(it) }
        val isRepo = result.stdout.contains("## ") || result.exitCode == 0

        ToolResult(
            toolName = tool.name,
            success = isRepo,
            result = GitStatusData(
                branch = branch,
                entries = entries,
                repoPath = repoPath,
                isGitRepo = isRepo
            )
        )
    }

    private fun parseStatusLine(line: String): GitStatusEntry? {
        if (line.length < 3) return null
        val indexStatus = line[0]
        val workTreeStatus = line[1]
        val filePath = line.substring(3).trim().let { raw ->
            if (raw.startsWith("\"") && raw.endsWith("\"")) {
                raw.substring(1, raw.length - 1)
            } else {
                raw
            }
        }
        if (filePath.isBlank()) return null
        return GitStatusEntry(
            indexStatus = indexStatus,
            workTreeStatus = workTreeStatus,
            file = filePath
        )
    }

    suspend fun gitDiff(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val staged = tool.parameters.find { it.name == "staged" }?.value?.lowercase() == "true"
        val fileFilter = tool.parameters.find { it.name == "file" }?.value
        val repoPath = resolveRepoPath(path, environment)

        val diffCmd = buildString {
            append("git diff --no-color")
            if (staged) append(" --cached")
            if (!fileFilter.isNullOrBlank()) append(" -- ${escapeShellArg(fileFilter)}")
        }

        val result = executeGitCommand(repoPath, diffCmd)

        val diffContent = result.stdout
        if (diffContent.length > MAX_DIFF_SIZE) {
            val truncated = diffContent.take(MAX_DIFF_SIZE)
            return@withContext ToolResult(
                toolName = tool.name,
                success = true,
                result = GitDiffData(
                    diff = truncated,
                    truncated = true,
                    totalSize = diffContent.length,
                    repoPath = repoPath
                ),
                error = "Diff truncated to ${MAX_DIFF_SIZE / 1024}KB"
            )
        }

        if (!result.success && result.exitCode != 0) {
            return@withContext ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Git diff failed: ${result.stderr.ifBlank { result.stdout }}"
            )
        }

        ToolResult(
            toolName = tool.name,
            success = true,
            result = GitDiffData(
                diff = diffContent.ifBlank { "(no changes)" },
                truncated = false,
                totalSize = diffContent.length,
                repoPath = repoPath
            )
        )
    }

    suspend fun gitLog(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val countParam = tool.parameters.find { it.name == "count" }?.value
        val fileFilter = tool.parameters.find { it.name == "file" }?.value
        val repoPath = resolveRepoPath(path, environment)
        val count = countParam?.toIntOrNull()?.coerceIn(1, MAX_LOG_COUNT) ?: 20

        val fileArg = if (!fileFilter.isNullOrBlank()) " -- ${escapeShellArg(fileFilter)}" else ""
        val result = executeGitCommand(
            repoPath,
            "git log --oneline --decorate -n $count --format='%h%x1f%s%x1f%an%x1f%ar'$fileArg 2>&1"
        )

        if (!result.success && result.exitCode != 0) {
            return@withContext ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Git log failed: ${result.stderr.ifBlank { result.stdout }}"
            )
        }

        val entries = result.stdout.lines().filter { it.isNotBlank() }.mapNotNull { parseLogLine(it) }

        ToolResult(
            toolName = tool.name,
            success = true,
            result = GitLogData(
                entries = entries,
                repoPath = repoPath
            )
        )
    }

    private fun parseLogLine(line: String): GitLogEntry? {
        val parts = line.split("\u001f", limit = 4)
        if (parts.size < 4) return null
        return GitLogEntry(
            hash = parts[0].trim(),
            message = parts[1],
            author = parts[2],
            relativeDate = parts[3]
        )
    }

    suspend fun gitBlame(tool: AITool): ToolResult = withContext(Dispatchers.IO) {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val environment = tool.parameters.find { it.name == "environment" }?.value
        val filePath = tool.parameters.find { it.name == "file" }?.value ?: ""
        val startLine = tool.parameters.find { it.name == "start_line" }?.value
        val endLine = tool.parameters.find { it.name == "end_line" }?.value
        val repoPath = resolveRepoPath(path, environment)

        if (filePath.isBlank()) {
            return@withContext ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "file parameter is required"
            )
        }

        val lineRange = buildString {
            val start = startLine?.toIntOrNull()
            val end = endLine?.toIntOrNull()
            if (start != null && end != null) {
                append(" -L $start,$end")
            } else if (start != null) {
                append(" -L $start,$start")
            }
        }

        val result = executeGitCommand(
            repoPath,
            "git blame --line-porcelain$lineRange -- ${escapeShellArg(filePath)} 2>&1"
        )

        if (!result.success && result.exitCode != 0) {
            return@withContext ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Git blame failed: ${result.stderr.ifBlank { result.stdout }}"
            )
        }

        val entries = parseBlameOutput(result.stdout)

        ToolResult(
            toolName = tool.name,
            success = true,
            result = GitBlameData(
                entries = entries,
                file = filePath,
                repoPath = repoPath
            )
        )
    }

    private fun parseBlameOutput(output: String): List<GitBlameEntry> {
        if (output.isBlank()) return emptyList()
        val entries = mutableListOf<GitBlameEntry>()
        val lines = output.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("\t")) {
                i++
                continue
            }
            val headerParts = line.split(" ")
            if (headerParts.size < 3) {
                i++
                continue
            }
            val hash = headerParts[0].trimStart('^')
            val originalLine = headerParts[1].toIntOrNull() ?: 0
            val finalLine = headerParts[2].toIntOrNull() ?: 0

            var author = ""
            var authorMail = ""
            var authorTime = ""
            var summary = ""
            var previousHash = ""
            var filename = ""

            i++
            while (i < lines.size && !lines[i].startsWith("\t")) {
                val porcelainLine = lines[i]
                when {
                    porcelainLine.startsWith("author ") -> author = porcelainLine.removePrefix("author ")
                    porcelainLine.startsWith("author-mail ") -> authorMail = porcelainLine.removePrefix("author-mail ")
                    porcelainLine.startsWith("author-time ") -> authorTime = porcelainLine.removePrefix("author-time ")
                    porcelainLine.startsWith("summary ") -> summary = porcelainLine.removePrefix("summary ")
                    porcelainLine.startsWith("previous ") -> previousHash = porcelainLine.removePrefix("previous ")
                    porcelainLine.startsWith("filename ") -> filename = porcelainLine.removePrefix("filename ")
                }
                i++
            }

            val codeLine = if (i < lines.size && lines[i].startsWith("\t")) {
                lines[i].removePrefix("\t")
            } else {
                i++
                continue
            }

            entries.add(
                GitBlameEntry(
                    hash = hash.take(8),
                    author = author,
                    authorMail = authorMail,
                    authorTime = authorTime,
                    summary = summary,
                    lineNumber = finalLine,
                    originalLineNumber = originalLine,
                    codeLine = codeLine,
                    previousHash = previousHash.takeIf { it.isNotBlank() },
                    filename = filename.takeIf { it.isNotBlank() }
                )
            )
            i++
        }
        return entries
    }
}
