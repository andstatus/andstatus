package org.andstatus.app.notification;

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.service.CommandResult;
import org.andstatus.app.util.SharedPreferencesUtil;

public class AddedMessagesNotifierTest extends InstrumentationTestCase {
    
    @Override
    protected void setUp() throws Exception {
        TestSuite.initialize(this);
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_NOTIFICATIONS_ENABLED, true);
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_NOTIFY_OF_HOME_TIMELINE, true);
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_NOTIFY_OF_MENTIONS, true);
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_NOTIFY_OF_DIRECT_MESSAGES, true);
    }

    public void testCreateNotification() {
    	CommandResult result = new CommandResult();
    	result.incrementMessagesCount(TimelineType.DIRECT);
    	TestSuite.getMyContextForTest().getNotifications().clear();
        AddedMessagesNotifier.newInstance(MyContextHolder.get()).update(result);
        assertNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.HOME));
        assertNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.MENTIONS));
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.DIRECT));

    	result.incrementMentionsCount();
        AddedMessagesNotifier.newInstance(MyContextHolder.get()).update(result);
        assertNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.HOME));
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.MENTIONS));
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.DIRECT));

    	result.incrementMessagesCount(TimelineType.HOME);
        AddedMessagesNotifier.newInstance(MyContextHolder.get()).update(result);
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.HOME));
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.MENTIONS));
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.DIRECT));
    }
}
