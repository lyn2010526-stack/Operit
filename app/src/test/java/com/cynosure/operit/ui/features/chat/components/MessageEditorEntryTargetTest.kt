package com.cynosure.operit.ui.features.chat.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageEditorEntryTargetTest {
    @Test
    fun rawMode_alwaysTargetsRawEditor() {
        assertEquals(
            MessageEditorEntryTarget.RAW_TEXT,
            resolveMessageEditorEntryTarget(true, emptyList()),
        )
    }

    @Test
    fun visualMode_prefersTextEditor() {
        val parts = listOf(
            ParsedMessagePart(PartType.XML, "reasoning", "think"),
            ParsedMessagePart(PartType.TEXT, "answer"),
        )

        assertEquals(
            MessageEditorEntryTarget.VISUAL_TEXT,
            resolveMessageEditorEntryTarget(false, parts),
        )
    }

    @Test
    fun visualModeWithXmlOnly_opensFirstXmlEditor() {
        val parts = listOf(ParsedMessagePart(PartType.XML, "reasoning", "think"))

        assertEquals(
            MessageEditorEntryTarget.FIRST_XML,
            resolveMessageEditorEntryTarget(false, parts),
        )
        assertNull(resolveMessageEditorEntryTarget(false, emptyList()))
    }
}
