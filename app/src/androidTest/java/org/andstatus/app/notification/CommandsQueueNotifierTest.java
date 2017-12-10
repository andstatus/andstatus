package org.andstatus.app.notification;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CommandsQueueNotifierTest {
    
    @Before
    public void setUp() throws Exception {
        TestSuite.initialize(this);
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_NOTIFICATIONS_ENABLED, true);
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_NOTIFY_OF_COMMANDS_IN_THE_QUEUE, true);
    }

    @Test
    public void testCreateNotification() {
    	TestSuite.getMyContextForTest().getNotifications().clear();
        CommandsQueueNotifier.newInstance(MyContextHolder.get()).update(3, 7);
        assertTrue(TestSuite.getMyContextForTest().getNotifications().get(NotificationEvent.OUTBOX) != null);
    }
}
