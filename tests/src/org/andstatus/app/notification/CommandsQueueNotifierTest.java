package org.andstatus.app.notification;

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.notification.CommandsQueueNotifier;

public class CommandsQueueNotifierTest extends InstrumentationTestCase {
    
    @Override
    protected void setUp() throws Exception {
        TestSuite.initialize(this);
        MyPreferences.putBoolean(MyPreferences.KEY_NOTIFICATIONS_ENABLED, true);
        MyPreferences.putBoolean(MyPreferences.KEY_NOTIFY_OF_COMMANDS_IN_THE_QUEUE, true);
    }

    public void testCreateNotification() {
    	TestSuite.getMyContextForTest().getNotifications().clear();
        CommandsQueueNotifier.newInstance(MyContextHolder.get()).update(3, 7);
        assertTrue(TestSuite.getMyContextForTest().getNotifications().get(TimelineTypeEnum.ALL) != null);
    }
}
