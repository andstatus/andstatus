package org.andstatus.app.notification;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.service.CommandResult;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class AddedMessagesNotifierTest {
    
    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_NOTIFICATIONS_ENABLED, true);
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_NOTIFY_OF_HOME_TIMELINE, true);
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_NOTIFY_OF_MENTIONS, true);
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_NOTIFY_OF_DIRECT_MESSAGES, true);
    }

    @Test
    public void testCreateNotification() {
    	CommandResult result = new CommandResult();
    	result.incrementDirectCount();
    	TestSuite.getMyContextForTest().getNotifications().clear();
        AddedMessagesNotifier.notify(MyContextHolder.get(), result);
        assertNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.HOME));
        assertNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.MENTIONS));
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.DIRECT));

    	result.incrementMentionsCount();
        AddedMessagesNotifier.notify(MyContextHolder.get(), result);
        assertNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.HOME));
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.MENTIONS));
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.DIRECT));

    	result.incrementMessagesCount();
        AddedMessagesNotifier.notify(MyContextHolder.get(), result);
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.HOME));
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.MENTIONS));
        assertNotNull(TestSuite.getMyContextForTest().getNotifications().get(TimelineType.DIRECT));
    }
}
