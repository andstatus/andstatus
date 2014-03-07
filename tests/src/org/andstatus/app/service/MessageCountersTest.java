package org.andstatus.app.service;

import junit.framework.TestCase;

import org.andstatus.app.data.TimelineTypeEnum;

public class MessageCountersTest extends TestCase {
    public void testHomeAccumulation() {
        CommandExecutionData counters = new CommandExecutionData(null, null).setTimelineType(TimelineTypeEnum.HOME);
        final int MESSAGES = 4;
        final int MENTIONS = 2;
        for (int ind=0; ind < MESSAGES; ind++) {
            counters.incrementMessagesCount();
        }
        for (int ind=0; ind < MENTIONS; ind++) {
            counters.incrementMentionsCount();
        }
        counters.accumulate();
        assertEquals(4, counters.getMessagesAdded());
        assertEquals(2, counters.getMentionsAdded());
        assertEquals(0, counters.getDirectedAdded());
    }

    public void testDirectAccumulation() {
        CommandExecutionData counters = new CommandExecutionData(null, null).setTimelineType(TimelineTypeEnum.DIRECT);
        final int MESSAGES = 4;
        for (int ind=0; ind < MESSAGES; ind++) {
            counters.incrementMessagesCount();
        }
        counters.accumulate();
        assertEquals(0, counters.getMessagesAdded());
        assertEquals(0, counters.getMentionsAdded());
        assertEquals(4, counters.getDirectedAdded());
    }
}
