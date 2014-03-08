package org.andstatus.app.service;

import junit.framework.TestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.util.TriState;

public class CommandExecutionContextTest extends TestCase {
    MyAccount ma = MyAccount.Builder.newOrExistingFromAccountName("temp/", TriState.UNKNOWN).getAccount();

    public void testHomeAccumulation() {
        CommandExecutionContext execContext = new CommandExecutionContext(CommandData.getEmpty(), ma).setTimelineType(TimelineTypeEnum.HOME);
        assertEquals(execContext.getTimelineType(), TimelineTypeEnum.HOME);
        
        final int MESSAGES = 4;
        final int MENTIONS = 2;
        for (int ind=0; ind < MESSAGES; ind++) {
            execContext.getResult().incrementMessagesCount(execContext.getTimelineType());
        }
        for (int ind=0; ind < MENTIONS; ind++) {
            execContext.getResult().incrementMentionsCount();
        }
        assertEquals(4, execContext.getResult().getMessagesAdded());
        assertEquals(2, execContext.getResult().getMentionsAdded());
        assertEquals(0, execContext.getResult().getDirectedAdded());
    }

    public void testDirectAccumulation() {
        CommandExecutionContext execContext = new CommandExecutionContext(CommandData.getEmpty(), ma).setTimelineType(TimelineTypeEnum.DIRECT);
        final int MESSAGES = 4;
        for (int ind=0; ind < MESSAGES; ind++) {
            execContext.getResult().incrementMessagesCount(execContext.getTimelineType());
        }
        assertEquals(0, execContext.getResult().getMessagesAdded());
        assertEquals(0, execContext.getResult().getMentionsAdded());
        assertEquals(4, execContext.getResult().getDirectedAdded());
    }
}
