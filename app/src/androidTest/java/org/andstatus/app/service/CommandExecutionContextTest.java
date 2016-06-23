package org.andstatus.app.service;

import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.TriState;

public class CommandExecutionContextTest extends InstrumentationTestCase {
    MyAccount ma;

    @Override
    protected void setUp() throws Exception {
        TestSuite.initializeWithData(this);
        ma = MyContextHolder.get().persistentAccounts().getFirstSucceededForOriginId(0);
    }

    public void testHomeAccumulation() {
        CommandExecutionContext execContext = new CommandExecutionContext(
                CommandData.newAccountCommand(CommandEnum.EMPTY, ma));
        assertEquals(execContext.getTimeline().getTimelineType(), TimelineType.HOME);
        
        final int MESSAGES = 4;
        final int MENTIONS = 2;
        for (int ind=0; ind < MESSAGES; ind++) {
            execContext.getResult().incrementMessagesCount(execContext.getTimeline());
        }
        for (int ind=0; ind < MENTIONS; ind++) {
            execContext.getResult().incrementMentionsCount();
        }
        assertEquals(MESSAGES, execContext.getResult().getMessagesAdded());
        assertEquals(MENTIONS, execContext.getResult().getMentionsAdded());
        assertEquals(0, execContext.getResult().getDirectedAdded());
    }

    public void testDirectAccumulation() {
        CommandExecutionContext execContext = new CommandExecutionContext(
                CommandData.newTimelineCommand(CommandEnum.EMPTY, ma, TimelineType.DIRECT));
        final int MESSAGES = 4;
        for (int ind=0; ind < MESSAGES; ind++) {
            execContext.getResult().incrementMessagesCount(execContext.getTimeline());
        }
        assertEquals(0, execContext.getResult().getMessagesAdded());
        assertEquals(0, execContext.getResult().getMentionsAdded());
        assertEquals(MESSAGES, execContext.getResult().getDirectedAdded());
    }
}
