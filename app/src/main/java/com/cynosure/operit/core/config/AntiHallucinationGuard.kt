package com.cynosure.operit.core.config

/**
 * 防幻觉守卫引擎 — 提取自 zero-skill 零引擎精华。
 *
 * 三层防线：
 * Layer 1: 系统提示词 — 分别注入事实防幻觉与输出纪律规则
 * Layer 2: OutputFirewall — 对 AI 输出做后期检测和过滤（思考泄漏/废话/超长代码/乱码）
 * Layer 3: HallucinationCheck — 对关键声明做置信度、证据、绝对化语气验证
 *
 * zero-skill 五条防幻觉规则：
 * R1) 事实声明无证据 → 强制降级 GUESSED
 * R2) 编造工具引用 → 无对应工具调用记录则标违规
 * R3) 绝对化语气无 VERIFIED → 降级
 * R4) 过度自信推测 → 需要 ≥2 来源，否则 INFERRED
 * R5) 无来源技术断言 → 弃用/移除类断言需官方来源
 */
object AntiHallucinationGuard {

    // ==========================================================================
    // Layer 1: 系统提示词 — 从源头防止幻觉
    // ==========================================================================
    const val HALLUCINATION_SYSTEM_PROMPT = """
[ANTI-HALLUCINATION MODE ACTIVE · 零引擎规则]

你是首席软件工程师。职责是交付成果，不是回答问题。
每一条事实性声明必须带置信度标签：[VERIFIED] [INFERRED] [GUESSED] [UNKNOWN]

五条铁律：
R1 事实声明无证据 → 禁止使用"已读取/已修改/已完成/搞定/跑通/编译通过"等词，改用"正在…"过程描述
R2 工具引用必可追溯 → "日志显示/工具返回/命令输出" 等引用仅限真实工具执行记录，否则删除
R3 绝对化语气 → "一定/肯定/必然/绝对/百分之百" 仅限 [VERIFIED] 后使用，否则降 [GUESSED]
R4 推测声明 → "显然/很明显/众所周知" 需 ≥2 独立来源，否则改 [INFERRED]
R5 技术断言 → 声称某个 API/命令/库"已废弃/已移除/不再支持" 必须引用官方文档或版本发行说明
"""

    const val OUTPUT_DISCIPLINE_SYSTEM_PROMPT = """
[OUTPUT DISCIPLINE ACTIVE · 输出纪律]

输出纪律：
- 禁止思考过程泄漏：不说"我认为/我分析/让我想想"
- 禁止废话：不说"加油/好的/没问题/我这就"
- 禁止情感化：不说"我理解你的感受"
- 超 15 行代码必须写文件，不输出到对话框
- 禁止暴露工具参数中的 api_key/token/password/secret

违反任一规则 → 后续响应质量下降标记。全规则通过 → 最可靠输出。
"""

    // ==========================================================================
    // Layer 2: 输出防火墙 — 后期输出验证
    // ==========================================================================

    /** 思考泄漏词汇 */
    private val THOUGHT_LEAK = listOf(
        "我认为", "我推测", "我分析", "我想到", "我正在思考", "我准备",
        "我打算", "让我想想", "在我看来", "我个人觉得", "我的理解是"
    )

    /** 废话/填充词 */
    private val FILLER = listOf(
        "加油", "没问题的", "不用担心", "好的我这就来帮你", "我这就",
        "不好意思", "很抱歉", "你说得对", "完全理解", "没关系的"
    )

    /** 情感化表达 */
    private val EMOTIONAL = listOf(
        "我理解你的感受", "这种情况确实令人", "我感同身受", "我能体会"
    )

    /** 工具参数泄漏正则 */
    private val TOOL_LEAK_PATTERNS = listOf(
        Regex("""api_key\s*[:=]""", RegexOption.IGNORE_CASE),
        Regex("""secret\s*[:=]""", RegexOption.IGNORE_CASE),
        Regex("""password\s*[:=]""", RegexOption.IGNORE_CASE),
        Regex("""token\s*=\s*[A-Za-z0-9\-_]{20,}""", RegexOption.IGNORE_CASE),
        Regex("""Authorization:\s*Bearer""", RegexOption.IGNORE_CASE),
        Regex("""tool_name\s*[:=]""", RegexOption.IGNORE_CASE),
        Regex("""tool_args\s*[:=]""", RegexOption.IGNORE_CASE)
    )

    data class FirewallViolation(
        val type: FirewallViolationType,
        val hit: String,
        val fix: String
    )

    enum class FirewallViolationType {
        THOUGHT_LEAK,        // 思考过程泄漏
        TOOL_LEAK,           // 工具参数泄漏
        FILLER,              // 废话填充
        EMOTIONAL,           // 情感化表达
        OVERSIZED_CODE,      // 超长代码块
        MOJIBAKE             // 乱码/控制字符
    }

    data class FirewallResult(
        val clean: Boolean,
        val severity: String,       // CLEAN / MINOR / MAJOR / SEVERE
        val action: String,         // PASS / FILTER / BLOCK
        val violationCount: Int,
        val violations: List<FirewallViolation>
    )

    fun checkOutputFirewall(text: String): FirewallResult {
        val violations = mutableListOf<FirewallViolation>()

        // 1. 思考泄漏
        for (w in THOUGHT_LEAK) {
            if (w in text) violations.add(FirewallViolation(
                FirewallViolationType.THOUGHT_LEAK, w,
                "删除思考过程前缀，直接给结论"
            ))
        }

        // 2. 工具参数泄漏
        for (re in TOOL_LEAK_PATTERNS) {
            re.find(text)?.let { m ->
                violations.add(FirewallViolation(
                    FirewallViolationType.TOOL_LEAK, m.value.take(40),
                    "绝对禁止输出凭据，删除对应片段"
                ))
            }
        }

        // 3. 废话
        for (w in FILLER) {
            if (w in text) violations.add(FirewallViolation(
                FirewallViolationType.FILLER, w,
                "删除社交语，直接进入正题"
            ))
        }

        // 4. 情感化
        for (w in EMOTIONAL) {
            if (w in text) violations.add(FirewallViolation(
                FirewallViolationType.EMOTIONAL, w,
                "工程师不应情感化表达，改为客观陈述"
            ))
        }

        // 5. 超长代码块：``` fenced，>10 行或 >300 字符
        val codeBlockRegex = Regex("""```[\s\S]*?```""")
        for (block in codeBlockRegex.findAll(text)) {
            val lines = block.value.count { it == '\n' } - 1
            if (lines > 10 || block.value.length > 300) {
                violations.add(FirewallViolation(
                    FirewallViolationType.OVERSIZED_CODE, "$lines 行代码块",
                    "代码必须写文件，用文件工具输出"
                ))
            }
        }

        // 6. 乱码：替换字符 U+FFFD 或控制字符
        if (Regex("""[\uFFFD]""").containsMatchIn(text) ||
            Regex("""[\x00-\x08\x0B\x0C\x0E-\x1F]""").containsMatchIn(text)) {
            violations.add(FirewallViolation(
                FirewallViolationType.MOJIBAKE, "非可见控制字符/替换字符",
                "移除乱码后重新输出"
            ))
        }

        val sentences = text.split(Regex("""[。！？.!?\n]+""")).filter { it.isNotBlank() }
        val severity = when {
            violations.any { it.type == FirewallViolationType.TOOL_LEAK || it.type == FirewallViolationType.MOJIBAKE } -> "SEVERE"
            violations.size > maxOf(1, sentences.size / 2) -> "MAJOR"
            violations.isNotEmpty() -> "MINOR"
            else -> "CLEAN"
        }

        val action = when (severity) {
            "SEVERE", "MAJOR" -> "BLOCK: 需重写后输出"
            "MINOR" -> "FILTER: 过滤违规片段后输出"
            else -> "PASS"
        }

        return FirewallResult(
            clean = violations.isEmpty(),
            severity = severity,
            action = action,
            violationCount = violations.size,
            violations = violations
        )
    }

    // ==========================================================================
    // Layer 3: 幻觉检测 — 对已完成声明做置信度验证
    // ==========================================================================

    /** 事实性完成声明关键词 */
    private val FACT_CLAIMS = listOf(
        "已读取", "已修改", "已编译", "已安装", "已测试", "已修复",
        "已部署", "已删除", "已创建", "已验证", "完成", "搞定", "跑通",
        "编译通过", "测试通过", "构建成功"
    )

    /** 绝对化词语 */
    private val ABSOLUTE_WORDS = listOf(
        "一定", "肯定", "必然", "绝对", "百分之百", "毫无疑问", "毋庸置疑"
    )

    /** 过度自信词 */
    private val OVERCONFIDENT_WORDS = listOf(
        "显然", "很明显", "不用想", "众所周知"
    )

    /** 无来源技术断言模式 */
    private val TECH_ASSERTION_PATTERNS = listOf(
        Regex("""已(经)?(被)?(废弃|弃用|移除|删除|下线)"""),
        Regex("""在(新版本|最新版|[\u4e00-\u9fff]*版本).*(移除|删除|不支持|废弃)"""),
        Regex("""不再(支持|维护|推荐)""")
    )

    /** 合法置信度标签 */
    private val VALID_LABELS = listOf("VERIFIED", "INFERRED", "GUESSED", "UNKNOWN")

    data class HallucinationViolation(
        val rule: String,
        val hit: String,
        val fix: String
    )

    data class HallucinationResult(
        val allowed: Boolean,
        val currentLabel: String?,
        val suggestedLabel: String,
        val hasEvidence: Boolean,
        val isFactClaim: Boolean,
        val violations: List<HallucinationViolation>,
        val verdict: String
    )

    fun checkHallucination(text: String, evidence: String? = null): HallucinationResult {
        val hasEvidence = !evidence.isNullOrBlank()
        val violations = mutableListOf<HallucinationViolation>()
        val currentLabel = VALID_LABELS.firstOrNull { it in text }

        // R1: 事实声明无证据
        val factWord = FACT_CLAIMS.firstOrNull { it in text }
        if (factWord != null && !hasEvidence) {
            violations.add(HallucinationViolation(
                "fact_without_evidence", factWord,
                "补充工具执行证据，或改为'正在…'过程描述"
            ))
        }

        // R2: 编造工具引用
        val mentionsToolOutput = Regex(
            """(工具(返回|输出|结果)|命令(返回|输出)|日志显示|logcat)"""
        ).containsMatchIn(text)
        if (mentionsToolOutput && !hasEvidence) {
            violations.add(HallucinationViolation(
                "fabricated_citation", "",
                "引用必须可追溯到真实工具执行记录，否则删除该引用"
            ))
        }

        // R3: 绝对化语气无 VERIFIED
        val absWord = ABSOLUTE_WORDS.firstOrNull { it in text }
        if (absWord != null && currentLabel != "VERIFIED") {
            violations.add(HallucinationViolation(
                "absolute_without_verified", absWord,
                "绝对化断言需 VERIFIED 标签和工具结果引用，否则降 GUESSED"
            ))
        }

        // R4: 过度自信推测
        val ocWord = OVERCONFIDENT_WORDS.firstOrNull { it in text }
        if (ocWord != null) {
            violations.add(HallucinationViolation(
                "overconfident", ocWord,
                "需 ≥2 独立来源交叉验证，否则改为 INFERRED"
            ))
        }

        // R5: 无来源技术断言
        for (re in TECH_ASSERTION_PATTERNS) {
            re.find(text)?.let { m ->
                violations.add(HallucinationViolation(
                    "unsourced_tech_assertion", m.value,
                    "需引用官方文档/版本发行说明，否则加 UNKNOWN 并标'需查证'"
                ))
                break
            }
        }

        // 结论性声明缺标签
        val isConclusive = factWord != null ||
            absWord != null ||
            Regex("""结论|判定|确认为|证实""").containsMatchIn(text)
        val isProcess = Regex("""^(正在|准备|接下来|开始)""").containsMatchIn(text.trim())

        if (isConclusive && currentLabel == null && !isProcess && !hasEvidence) {
            violations.add(HallucinationViolation(
                "missing_confidence_label", "",
                "结论性声明必须带 VERIFIED/INFERRED/GUESSED/UNKNOWN 标签，或提供证据"
            ))
        }

        // 建议标签
        val suggestedLabel = when {
            hasEvidence -> "VERIFIED"
            factWord != null || absWord != null -> "GUESSED"
            ocWord != null -> "INFERRED"
            else -> "UNKNOWN"
        }

        return HallucinationResult(
            allowed = violations.isEmpty(),
            currentLabel = currentLabel,
            suggestedLabel = suggestedLabel,
            hasEvidence = hasEvidence,
            isFactClaim = factWord != null,
            violations = violations,
            verdict = if (violations.isEmpty()) {
                "PASS"
            } else {
                "BLOCK: 存在 ${violations.size} 处幻觉风险，需修正后输出"
            }
        )
    }

    // ==========================================================================
    // Convenience: 综合检查
    // ==========================================================================
    data class FullGuardResult(
        val outputFirewall: FirewallResult,
        val hallucination: HallucinationResult,
        val overallPass: Boolean,
        val summary: String
    )

    fun fullCheck(
        text: String,
        evidence: String? = null,
        checkOutputDiscipline: Boolean = true,
        checkFacts: Boolean = true
    ): FullGuardResult {
        val fw = if (checkOutputDiscipline) {
            checkOutputFirewall(text)
        } else {
            FirewallResult(true, "CLEAN", "PASS", 0, emptyList())
        }
        val hl = if (checkFacts) {
            checkHallucination(text, evidence)
        } else {
            HallucinationResult(true, null, "UNKNOWN", false, false, emptyList(), "PASS")
        }

        val overall = fw.action == "PASS" && hl.allowed
        val parts = mutableListOf<String>()
        if (fw.action != "PASS") parts.add("输出防火墙: ${fw.violationCount}处违规(${fw.severity})")
        if (!hl.allowed) parts.add("幻觉: ${hl.violations.size}处违规")

        return FullGuardResult(
            outputFirewall = fw,
            hallucination = hl,
            overallPass = overall,
            summary = if (parts.isEmpty()) "CLEAN" else parts.joinToString(" | ")
        )
    }

    // ==========================================================================
    // 防删代码层 (FileGuard) - 提取自 zero-skill
    // 14 种直接破坏性命令 + 9 种间接删除 + 7 类高风险路径
    // ==========================================================================

    private data class DeletePattern(val regex: Regex, val name: String, val desc: String, val soft: Boolean = false)

    private val DELETE_PATTERNS = listOf(
        DeletePattern(Regex("""\brm\s+(-[rfRvi]+\s+)*"""), "rm", "删除文件/目录"),
        DeletePattern(Regex("""\brmdir\b"""), "rmdir", "删除目录"),
        DeletePattern(Regex("""\bunlink\b"""), "unlink", "删除链接/文件"),
        DeletePattern(Regex("""\bshred\b"""), "shred", "粉碎文件（不可恢复）"),
        DeletePattern(Regex("""\bmkfs\.[a-z0-9]+\b"""), "mkfs", "格式化文件系统"),
        DeletePattern(Regex("""\bdd\s+if="""), "dd", "块级写入/擦除"),
        DeletePattern(Regex("""\btruncate\s+-s\s*0\b"""), "truncate", "清空文件内容"),
        DeletePattern(Regex(""">\s*/dev/sd[a-z]"""), "device-write", "写入原始磁盘设备"),
        DeletePattern(Regex("""\bgit\s+clean\s+-[a-z]*f"""), "git clean -f", "删除未跟踪文件"),
        DeletePattern(Regex("""\bgit\s+reset\s+--hard"""), "git reset --hard", "丢弃工作区改动"),
        DeletePattern(Regex("""\brsync\b[^\n]*--delete"""), "rsync --delete", "同步时删除目标多余文件"),
        DeletePattern(Regex("""\bfind\b[^\n]*-delete"""), "find -delete", "查找并删除"),
        DeletePattern(Regex("""\bxargs\b[^\n]*\brm\b"""), "xargs rm", "管道批量删除"),
        DeletePattern(Regex(""">\s*[^\s|&;]+"""), "overwrite", "重定向覆盖文件", soft = true)
    )

    private val INDIRECT_DELETE_PATTERNS = listOf(
        Regex("""os\.system\(\s*['"][^'"]*\brm\b"""),
        Regex("""subprocess\.[a-zA-Z_]+\(\s*\[?\s*['"]rm['"]"""),
        Regex("""shutil\.rmtree\s*\("""),
        Regex("""os\.remove\s*\("""),
        Regex("""os\.unlink\s*\("""),
        Regex("""Files\.deleteFile\s*\("""),
        Regex("""fs\.unlink(Sync)?\s*\("""),
        Regex("""fs\.rm(Sync)?\s*\("""),
        Regex("""\.delete\(\)""")
    )

    private data class RiskyPathPattern(val regex: Regex, val why: String)

    private val RISKY_PATH_PATTERNS = listOf(
        RiskyPathPattern(Regex("""^/(bin|boot|dev|etc|lib|proc|root|sbin|sys|usr|var)(/|$)"""), "系统目录"),
        RiskyPathPattern(Regex("""/sdcard/"""), "用户存储目录"),
        RiskyPathPattern(Regex("""/storage/emulated/"""), "用户存储目录"),
        RiskyPathPattern(Regex("""(^|/)\.env($|\.)"""), "环境变量/密钥文件"),
        RiskyPathPattern(Regex("""(^|/)(id_rsa|id_ed25519|\.pem|\.key|\.keystore|\.jks)($|/)"""), "私钥/签名文件"),
        RiskyPathPattern(Regex("""(^|/)credentials?(\.json)?($|/)"""), "凭据文件"),
        RiskyPathPattern(Regex("""(^|/)\.git($|/)"""), "版本库元数据")
    )

    data class FileGuardHit(val pattern: String, val desc: String, val soft: Boolean)
    data class PathRisk(val path: String, val why: String)

    data class FileGuardResult(
        val isDelete: Boolean,
        val requiresConfirmation: Boolean,
        val riskLevel: String,
        val hits: List<FileGuardHit>,
        val pathRisks: List<PathRisk>,
        val reasons: List<String>
    )

    fun analyzeCommandRisk(command: String): FileGuardResult {
        val text = command.ifBlank { return FileGuardResult(false, false, "LOW", emptyList(), emptyList(), emptyList()) }
        val hits = mutableListOf<FileGuardHit>()
        var requiresConfirmation = false
        var isDelete = false

        for (p in DELETE_PATTERNS) {
            if (p.regex.containsMatchIn(text)) {
                if (p.soft && text.contains("/dev/null")) continue
                hits.add(FileGuardHit(p.name, p.desc, p.soft))
                if (!p.soft) isDelete = true
                requiresConfirmation = true
            }
        }

        val pathRisks = mutableListOf<PathRisk>()
        val pathMatches = Regex("""(/[^\s"'|&;><]+)""").findAll(text).map { it.value }.toList()
        for (m in pathMatches) {
            for (rp in RISKY_PATH_PATTERNS) {
                if (rp.regex.containsMatchIn(m)) {
                    pathRisks.add(PathRisk(m, rp.why))
                    requiresConfirmation = true
                }
            }
        }

        return buildFileGuardResult(isDelete, requiresConfirmation, hits, pathRisks)
    }

    fun scanScriptRisk(script: String): FileGuardResult {
        val text = script.ifBlank { return FileGuardResult(false, false, "LOW", emptyList(), emptyList(), emptyList()) }
        val hits = mutableListOf<FileGuardHit>()
        var isDelete = false
        for (re in INDIRECT_DELETE_PATTERNS) {
            if (re.containsMatchIn(text)) {
                isDelete = true
                hits.add(FileGuardHit("indirect-delete", re.find(text)?.value ?: "", false))
            }
        }
        return buildFileGuardResult(isDelete, isDelete, hits, emptyList())
    }

    private fun buildFileGuardResult(
        isDelete: Boolean,
        requiresConfirmation: Boolean,
        hits: List<FileGuardHit>,
        pathRisks: List<PathRisk>
    ): FileGuardResult {
        val reasons = mutableListOf<String>()
        for (h in hits) {
            reasons.add("${if (h.soft) "覆盖风险" else "删除操作"}: ${h.desc.ifBlank { h.pattern }}")
        }
        for (pr in pathRisks) {
            reasons.add("高风险路径: ${pr.path}（${pr.why}）")
        }
        val level = when {
            isDelete && pathRisks.isNotEmpty() -> "CRITICAL"
            isDelete || pathRisks.isNotEmpty() -> "HIGH"
            hits.isNotEmpty() -> "MEDIUM"
            else -> "LOW"
        }
        return FileGuardResult(isDelete, requiresConfirmation, level, hits, pathRisks, reasons.distinct())
    }
}
