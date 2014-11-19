package org.andstatus.app.service;

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;

public class CommandsQueueNotifierTest extends InstrumentationTestCase {
    
    @Override
    protected void setUp() throws Exception {
        TestSuite.initialize(this);
        MyPreferences.putBoolean(MyPreferences.KEY_NOTIFICATIONS_ENABLED, true);
        MyPreferences.putBoolean(MyPreferences.KEY_NOTIFY_OF_COMMANDS_IN_THE_QUEUE, true);
    }

    public void testCreateNotification() {
        CommandsQueueNotifier.newInstance(MyContextHolder.get()).update(3, 7);
    }
}
