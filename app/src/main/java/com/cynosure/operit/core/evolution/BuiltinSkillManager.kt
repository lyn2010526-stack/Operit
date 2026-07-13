package com.cynosure.operit.core.evolution

import android.content.Context
import com.cynosure.operit.core.tools.skill.SkillManager
import com.cynosure.operit.data.model.TaskTraceEntity
import com.cynosure.operit.util.AppLogger
import java.io.File

/**
 * 内置技能管理器：将通过验证的任务经验固化为 App 内部生成的 SKILL.md。
 *
 * 固化条件：
 * - 任务成功完成
 * - 至少使用了一个工具
 * - 验证通过
 *
 * 生成的 Skill 存放在 SkillManager 的扫描目录下，目录名以 auto_ 前缀区分。
 */
object BuiltinSkillManager {
    private const val TAG = "BuiltinSkillManager"
    private const val AUTO_PREFIX = "auto_"

    /**
     * 将通过验证的任务轨迹固化为 Skill。
     * 如果同类经验已存在，更新内容而非重复创建。
     */
    fun solidify(context: Context, trace: TaskTraceEntity, validationPassed: Boolean): Boolean {
        if (!trace.success || !validationPassed) {
            return false
        }

        val toolsUsed = trace.toolsUsed.split(",").filter { it.isNotBlank() }
        if (toolsUsed.isEmpty()) {
            return false
        }

        return try {
            val skillDir = getAutoSkillDir(context, trace)
            if (!skillDir.exists()) {
                skillDir.mkdirs()
            }
            val skillFile = File(skillDir, "SKILL.md")
            val content = buildSkillContent(trace, toolsUsed)
            skillFile.writeText(content)
            AppLogger.d(TAG, "已固化任务经验为 Skill: ${skillDir.name}")
            SkillManager.getInstance(context).refreshAvailableSkills()
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "固化 Skill 失败", e)
            false
        }
    }

    private fun getAutoSkillDir(context: Context, trace: TaskTraceEntity): File {
        val skillsRoot = SkillManager.getInstance(context).getSkillsDirectoryPath()
        val rootDir = File(skillsRoot)
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        val safeKey = deriveSkillKey(trace)
        return File(rootDir, "${AUTO_PREFIX}${safeKey}")
    }

    private fun deriveSkillKey(trace: TaskTraceEntity): String {
        val tools = trace.toolsUsed.split(",").filter { it.isNotBlank() }
        val userMsg = trace.userMessage.take(30).replace(Regex("""[^\w]"""), "_")
        return "${tools.firstOrNull() ?: "task"}_${userMsg}".lowercase().take(40)
    }

    private fun buildSkillContent(trace: TaskTraceEntity, toolsUsed: List<String>): String {
        val name = deriveSkillKey(trace)
        val description = "自动生成：${trace.userMessage.take(80)}"
        val sb = StringBuilder()
        sb.appendLine("---")
        sb.appendLine("name: \"$name\"")
        sb.appendLine("description: \"$description\"")
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("# 自动固化的任务经验")
        sb.appendLine()
        sb.appendLine("## 用户请求")
        sb.appendLine(trace.userMessage.take(500))
        sb.appendLine()
        sb.appendLine("## 使用工具")
        toolsUsed.forEach { sb.appendLine("- $it") }
        sb.appendLine()
        sb.appendLine("## AI 回复摘要")
        sb.appendLine(trace.assistantReply.take(1000))
        sb.appendLine()
        sb.appendLine("## 验证状态")
        sb.appendLine("已通过自动验证，可作为参考经验。")
        return sb.toString()
    }
}
