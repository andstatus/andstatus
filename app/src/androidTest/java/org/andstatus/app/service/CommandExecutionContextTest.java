package org.andstatus.app.service;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.notification.NotificationEvent;
import org.andstatus.app.timeline.meta.TimelineType;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

public class CommandExecutionContextTest {
    private MyAccount ma;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
        ma = MyContextHolder.get().persistentAccounts().getFirstSucceeded();
    }

    @Test
    public void testMentionsAccumulation() {
        CommandExecutionContext execContext = new CommandExecutionContext(
                CommandData.newTimelineCommand( CommandEnum.GET_TIMELINE, ma, TimelineType.MENTIONS));
        assertEquals(TimelineType.MENTIONS, execContext.getTimeline().getTimelineType());
        
        final int messageCount = 4;
        final int mentionCount = 2;
        for (int ind=0; ind < messageCount; ind++) {
            execContext.getResult().incrementNewCount();
        }
        for (int ind=0; ind < mentionCount; ind++) {
            execContext.getResult().onNotificationEvent(NotificationEvent.MENTION);
        }
        assertEquals(messageCount, execContext.getResult().getNewCount());
        assertEquals(mentionCount, execContext.getResult().notificationEventCounts.get(NotificationEvent.MENTION).get());
        assertEquals(0, execContext.getResult().notificationEventCounts.getOrDefault(
                NotificationEvent.PRIVATE, new AtomicLong(0)).get());
    }

    @Test
    public void testPrivateAccumulation() {
        CommandExecutionContext execContext = new CommandExecutionContext(
                CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE, ma, TimelineType.PRIVATE));
        final int privateCount = 4;
        for (int ind=0; ind < privateCount; ind++) {
            execContext.getResult().onNotificationEvent(NotificationEvent.PRIVATE);
        }
        assertEquals(0, execContext.getResult().getNewCount());
        assertEquals(0, execContext.getResult().notificationEventCounts.getOrDefault(
                NotificationEvent.MENTION, new AtomicLong(0)).get());
        assertEquals(privateCount, execContext.getResult().notificationEventCounts.get(NotificationEvent.PRIVATE).get());
    }
}
