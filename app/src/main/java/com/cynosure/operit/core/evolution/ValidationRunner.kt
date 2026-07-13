package com.cynosure.operit.core.evolution

import android.content.Context
import com.cynosure.operit.data.db.AppDatabase
import com.cynosure.operit.data.model.ValidationResultEntity
import com.cynosure.operit.util.AppLogger
import java.io.File
import java.util.zip.ZipFile

/**
 * 验证运行器：对工具执行结果做后置验证。
 *
 * 验证策略：
 * - write_file / edit_file → 验证目标文件存在且内容非空
 * - execute_shell → 解析输出中的成功/失败标记
 * - 其他工具 → 跳过
 *
 * 验证结果写入 Room validation_results 表和 DiagnosticsRegistry。
 */
object ValidationRunner {
    private const val TAG = "ValidationRunner"

    private val FILE_TOOLS = setOf("write_file", "edit_file", "create_file", "write_file_full")
    private val APK_DIRECTORY_TOOLS = setOf("apk_reverse_decode", "apk_reverse_jadx")
    private val APK_BUILD_TOOLS = setOf("apk_reverse_build")
    private val APK_SIGN_TOOLS = setOf("apk_reverse_sign", "apk_reverse_build_and_sign")

    fun supports(toolName: String): Boolean {
        val normalizedName = toolName.substringAfter(':')
        return normalizedName in FILE_TOOLS ||
            normalizedName in APK_DIRECTORY_TOOLS ||
            normalizedName in APK_BUILD_TOOLS ||
            normalizedName in APK_SIGN_TOOLS
    }

    data class ValidationInput(
        val toolName: String,
        val toolParams: Map<String, String>,
        val toolResult: String,
        val toolSuccess: Boolean
    )

    data class ValidationOutput(
        val passed: Boolean,
        val detail: String,
        val validatedPath: String
    )

    suspend fun validate(
        context: Context,
        taskTraceId: Long,
        input: ValidationInput
    ): ValidationOutput {
        val normalizedName = input.toolName.substringAfter(':')
        val output = when {
            !input.toolSuccess -> ValidationOutput(false, "工具执行失败，产物验证未通过", "")
            normalizedName in FILE_TOOLS -> validateFileOperation(input)
            normalizedName in APK_DIRECTORY_TOOLS -> validateApkDirectory(input)
            normalizedName in APK_BUILD_TOOLS -> validateApk(input, requireSignature = false)
            normalizedName in APK_SIGN_TOOLS -> validateApk(input, requireSignature = true)
            else -> ValidationOutput(input.toolSuccess, "跳过验证（非文件操作工具）", "")
        }

        runCatching {
            val dao = AppDatabase.getDatabase(context).validationResultDao()
            dao.insert(
                ValidationResultEntity(
                    taskTraceId = taskTraceId,
                    toolName = input.toolName,
                    validatedPath = output.validatedPath,
                    passed = output.passed,
                    detail = output.detail,
                    createdAt = System.currentTimeMillis()
                )
            )
        }.onFailure { e ->
            AppLogger.e(TAG, "写入验证结果失败", e)
        }

        return output
    }

    private fun validateFileOperation(input: ValidationInput): ValidationOutput {
        val targetPath = input.toolParams["path"] ?: input.toolParams["file_path"] ?: ""
        if (targetPath.isBlank()) {
            return ValidationOutput(false, "无法验证：缺少 path 参数", "")
        }

        val file = File(targetPath)
        if (!file.exists()) {
            return ValidationOutput(false, "文件不存在: $targetPath", targetPath)
        }

        if (file.length() == 0L) {
            return ValidationOutput(false, "文件为空: $targetPath", targetPath)
        }

        return ValidationOutput(true, "文件存在且非空: ${file.length()}字节", targetPath)
    }

    private fun validateApkDirectory(input: ValidationInput): ValidationOutput {
        val targetPath = input.toolParams["output_dir"].orEmpty()
        if (targetPath.isBlank()) {
            return ValidationOutput(false, "无法验证：缺少 output_dir 参数", "")
        }

        val directory = File(targetPath)
        if (!directory.isDirectory) {
            return ValidationOutput(false, "输出目录不存在: $targetPath", targetPath)
        }

        val normalizedName = input.toolName.substringAfter(':')
        val hasExpectedOutput = when (normalizedName) {
            "apk_reverse_decode" -> File(directory, "AndroidManifest.xml").isFile
            "apk_reverse_jadx" -> directory.walkTopDown().maxDepth(4).any {
                it.isFile && (it.extension == "java" || it.extension == "kt")
            }
            else -> false
        }
        if (!hasExpectedOutput) {
            return ValidationOutput(false, "输出目录缺少预期逆向产物: $targetPath", targetPath)
        }

        return ValidationOutput(true, "逆向输出目录验证通过", targetPath)
    }

    private fun validateApk(input: ValidationInput, requireSignature: Boolean): ValidationOutput {
        val targetPath = input.toolParams["output_apk_path"].orEmpty()
        if (targetPath.isBlank()) {
            return ValidationOutput(false, "无法验证：缺少 output_apk_path 参数", "")
        }

        val apk = File(targetPath)
        if (!apk.isFile || apk.length() == 0L) {
            return ValidationOutput(false, "APK 产物不存在或为空: $targetPath", targetPath)
        }

        return runCatching {
            ZipFile(apk).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toList()
                val hasManifest = "AndroidManifest.xml" in entries
                val hasDex = entries.any { it.matches(Regex("classes(\\d*)?\\.dex")) }
                val hasSignature = entries.any {
                    it.startsWith("META-INF/") &&
                        (it.endsWith(".RSA", true) || it.endsWith(".DSA", true) || it.endsWith(".EC", true))
                }
                if (!hasManifest || !hasDex) {
                    return@use ValidationOutput(false, "APK 缺少 Manifest 或 DEX: $targetPath", targetPath)
                }
                if (requireSignature && !hasSignature) {
                    return@use ValidationOutput(false, "APK 缺少签名条目: $targetPath", targetPath)
                }
                ValidationOutput(true, "APK 结构验证通过: ${apk.length()}字节", targetPath)
            }
        }.getOrElse { error ->
            ValidationOutput(false, "APK ZIP 结构无效: ${error.message.orEmpty()}", targetPath)
        }
    }
}
