package org.andstatus.app.notification;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.service.CommandResult;

import android.test.InstrumentationTestCase;

public class AddedMessagesNotifierTest extends InstrumentationTestCase {
    
    @Override
    protected void setUp() throws Exception {
        TestSuite.initialize(this);
        MyPreferences.putBoolean(MyPreferences.KEY_NOTIFICATIONS_ENABLED, true);
        MyPreferences.putBoolean(MyPreferences.KEY_NOTIFY_OF_HOME_TIMELINE, true);
        MyPreferences.putBoolean(MyPreferences.KEY_NOTIFY_OF_MENTIONS, true);
        MyPreferences.putBoolean(MyPreferences.KEY_NOTIFY_OF_DIRECT_MESSAGES, true);
    }

    public void testCreateNotification() {
    	CommandResult result = new CommandResult();
    	result.incrementMessagesCount(TimelineTypeEnum.DIRECT);
    	TestSuite.getMyContextForTest().getNotifications().clear();
        AddedMessagesNotifier.newInstance(MyContextHolder.get()).update(result);
        assertNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineTypeEnum.HOME));
        assertNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineTypeEnum.MENTIONS));
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineTypeEnum.DIRECT));

    	result.incrementMentionsCount();
        AddedMessagesNotifier.newInstance(MyContextHolder.get()).update(result);
        assertNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineTypeEnum.HOME));
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineTypeEnum.MENTIONS));
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineTypeEnum.DIRECT));

    	result.incrementMessagesCount(TimelineTypeEnum.HOME);
        AddedMessagesNotifier.newInstance(MyContextHolder.get()).update(result);
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineTypeEnum.HOME));
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineTypeEnum.MENTIONS));
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineTypeEnum.DIRECT));
    }
}
