package com.cynosure.operit.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsReadableContentTest {
    @Test
    fun plainAiReply_isReadable() {
        assertTrue(TtsReadableContent.isReadable("这是可朗读回复。", emptyList()))
    }

    @Test
    fun contentRemovedByCleaner_isNotReadable() {
        assertFalse(TtsReadableContent.isReadable("[silent]", listOf("\\[silent]")))
    }
}
