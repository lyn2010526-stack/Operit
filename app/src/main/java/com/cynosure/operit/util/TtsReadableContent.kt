package com.cynosure.operit.util

object TtsReadableContent {
    fun prepare(content: String, cleanerRegexs: List<String>): String {
        return WaifuMessageProcessor.cleanContentForWaifu(TtsCleaner.clean(content, cleanerRegexs))
    }

    fun isReadable(content: String, cleanerRegexs: List<String>): Boolean {
        return prepare(content, cleanerRegexs).isNotBlank()
    }
}
