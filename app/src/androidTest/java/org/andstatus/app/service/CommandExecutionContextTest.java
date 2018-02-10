package org.andstatus.app.service;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.notification.NotificationEventType;
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
        ma = MyContextHolder.get().accounts().getFirstSucceeded();
    }

    @Test
    public void testMentionsAccumulation() {
        CommandExecutionContext execContext = new CommandExecutionContext(
                CommandData.newTimelineCommand( CommandEnum.GET_TIMELINE, ma, TimelineType.MENTIONS));
        assertEquals(TimelineType.MENTIONS, execContext.getTimeline().getTimelineType());
        
        final int noteCount = 4;
        final int mentionCount = 2;
        for (int ind=0; ind < noteCount; ind++) {
            execContext.getResult().incrementNewCount();
        }
        for (int ind=0; ind < mentionCount; ind++) {
            execContext.getResult().onNotificationEvent(NotificationEventType.MENTION);
        }
        assertEquals(noteCount, execContext.getResult().getNewCount());
        assertEquals(mentionCount, execContext.getResult().notificationEventCounts.get(NotificationEventType.MENTION).get());
        assertEquals(0, execContext.getResult().notificationEventCounts.getOrDefault(
                NotificationEventType.PRIVATE, new AtomicLong(0)).get());
    }

    @Test
    public void testPrivateAccumulation() {
        CommandExecutionContext execContext = new CommandExecutionContext(
                CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE, ma, TimelineType.PRIVATE));
        final int privateCount = 4;
        for (int ind=0; ind < privateCount; ind++) {
            execContext.getResult().onNotificationEvent(NotificationEventType.PRIVATE);
        }
        assertEquals(0, execContext.getResult().getNewCount());
        assertEquals(0, execContext.getResult().notificationEventCounts.getOrDefault(
                NotificationEventType.MENTION, new AtomicLong(0)).get());
        assertEquals(privateCount, execContext.getResult().notificationEventCounts.get(NotificationEventType.PRIVATE).get());
    }
}
