package org.andstatus.app.service;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.timeline.TimelineType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CommandExecutionContextTest {
    private MyAccount ma;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
        ma = MyContextHolder.get().persistentAccounts().getFirstSucceeded();
    }

    @Test
    public void testHomeAccumulation() {
        CommandExecutionContext execContext = new CommandExecutionContext(
                CommandData.newTimelineCommand( CommandEnum.GET_TIMELINE, ma, TimelineType.MENTIONS));
        assertEquals(TimelineType.MENTIONS, execContext.getTimeline().getTimelineType());
        
        final int MESSAGES = 4;
        final int MENTIONS = 2;
        for (int ind=0; ind < MESSAGES; ind++) {
            execContext.getResult().incrementMessagesCount();
        }
        for (int ind=0; ind < MENTIONS; ind++) {
            execContext.getResult().incrementMentionsCount();
        }
        assertEquals(MESSAGES, execContext.getResult().getMessagesAdded());
        assertEquals(MENTIONS, execContext.getResult().getMentionsAdded());
        assertEquals(0, execContext.getResult().getDirectedAdded());
    }

    @Test
    public void testDirectAccumulation() {
        CommandExecutionContext execContext = new CommandExecutionContext(
                CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE, ma, TimelineType.DIRECT));
        final int MESSAGES = 4;
        for (int ind=0; ind < MESSAGES; ind++) {
            execContext.getResult().incrementDirectCount();
        }
        assertEquals(0, execContext.getResult().getMessagesAdded());
        assertEquals(0, execContext.getResult().getMentionsAdded());
        assertEquals(MESSAGES, execContext.getResult().getDirectedAdded());
    }
}
