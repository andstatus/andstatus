package org.andstatus.app.note;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SharingToThisAppTest {
    @Test
    public void testInputSharedContent() {
        String part1 = "This is a long a long post";
        String text = part1 + " that doesn't fit into subject";
        String prefix = "Note - ";
        String ellipsis = "…";
        oneInputSharedContent(prefix, part1, text, ellipsis, false);
        oneInputSharedContent(prefix, "Another text for a subject", text, ellipsis, true);
        oneInputSharedContent("", "This is a long but not exact subject", text, "", true);
        oneInputSharedContent("Tweet:", part1, text, ellipsis, false);
        oneInputSharedContent(prefix, part1, text, "", false);
        oneInputSharedContent(prefix, "", text, ellipsis, false);
        oneInputSharedContent("", part1, text, ellipsis, false);
        oneInputSharedContent(prefix, part1, "", ellipsis, true);
    }

    private void oneInputSharedContent(String prefix, String part1, String text, String ellipsis, boolean hasAdditionalContent) {
        String subject = prefix + part1 + ellipsis;
        assertEquals(part1 + ellipsis, NoteEditor.stripBeginning(subject));
        assertEquals(String.valueOf(prefix + part1).trim(), NoteEditor.stripEllipsis(subject));
        assertEquals(part1, NoteEditor.stripEllipsis(NoteEditor.stripBeginning(subject)));
        assertEquals(hasAdditionalContent, NoteEditor.subjectHasAdditionalContent(subject, text));
    }
}
