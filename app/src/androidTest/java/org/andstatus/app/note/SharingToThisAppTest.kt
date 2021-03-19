package org.andstatus.app.note

import org.andstatus.app.note.NoteEditor
import org.junit.Assert
import org.junit.Test
import java.util.*

class SharingToThisAppTest {
    @Test
    fun testInputSharedContent() {
        val part1 = "This is a long a long post"
        val text = "$part1 that doesn't fit into subject"
        val prefix = "Note - "
        val ellipsis = "…"
        oneInputSharedContent(prefix, part1, text, ellipsis, false)
        oneInputSharedContent(prefix, "Another text for a subject", text, ellipsis, true)
        oneInputSharedContent("", "This is a long but not exact subject", text, "", true)
        oneInputSharedContent("Tweet:", part1, text, ellipsis, false)
        oneInputSharedContent(prefix, part1, text, "", false)
        oneInputSharedContent(prefix, "", text, ellipsis, false)
        oneInputSharedContent("", part1, text, ellipsis, false)
        oneInputSharedContent(prefix, part1, "", ellipsis, true)
    }

    private fun oneInputSharedContent(prefix: String?, part1: String?, text: String?, ellipsis: String?, hasAdditionalContent: Boolean) {
        val subject = prefix + part1 + ellipsis
        Assert.assertEquals(part1 + ellipsis, NoteEditor.Companion.stripBeginning(subject))
        Assert.assertEquals((prefix + part1).trim { it <= ' ' }, NoteEditor.Companion.stripEllipsis(subject))
        Assert.assertEquals(part1, NoteEditor.Companion.stripEllipsis(NoteEditor.Companion.stripBeginning(subject)))
        Assert.assertEquals(hasAdditionalContent, NoteEditor.Companion.subjectHasAdditionalContent(Optional.of(subject),
                Optional.ofNullable(text)))
    }
}