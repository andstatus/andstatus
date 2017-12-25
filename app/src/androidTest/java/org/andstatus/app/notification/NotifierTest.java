package org.andstatus.app.notification;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.net.social.MbActivityType;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NotifierTest {
    
    @Before
    public void setUp() throws Exception {
        TestSuite.initialize(this);
        NotificationMethodType.NOTIFICATION_AREA.setEnabled(true);
        NotificationEventType.ANNOUNCE.setEnabled(true);
        NotificationEventType.MENTION.setEnabled(true);
        NotificationEventType.PRIVATE.setEnabled(true);
        MyContextHolder.setExpiredIfConfigChanged();
        TestSuite.initializeWithData(this);
    }

    @Test
    public void testCreateNotification() {
        final MyContext myContext = TestSuite.getMyContextForTest();
        Notifier notifier = myContext.getNotifier();
        NotificationEvents events = notifier.events;
        notifier.clearAll();
        assertEquals("Events should be empty " + events, true, events.isEmpty());

        onNotificationEvent(notifier, NotificationEventType.PRIVATE);

        assertEquals(0, events.getCount(NotificationEventType.ANNOUNCE));
        assertEquals(0, events.getCount(NotificationEventType.MENTION));
        assertEquals(1, events.getCount(NotificationEventType.PRIVATE));

    	onNotificationEvent(notifier, NotificationEventType.MENTION);
        notifier.update();
        assertEquals(0, events.getCount(NotificationEventType.ANNOUNCE));
        assertEquals(1, events.getCount(NotificationEventType.MENTION));
        assertEquals(1, events.getCount(NotificationEventType.PRIVATE));

        onNotificationEvent(notifier, NotificationEventType.ANNOUNCE);
        notifier.update();
        assertEquals(1, events.getCount(NotificationEventType.ANNOUNCE));
        assertEquals(1, events.getCount(NotificationEventType.MENTION));
        assertEquals(1, events.getCount(NotificationEventType.PRIVATE));
    }

    private void onNotificationEvent(Notifier notifier, NotificationEventType eventType) {
        String where = "SELECT " + ActivityTable._ID + " FROM " + ActivityTable.TABLE_NAME +
                " WHERE " + ActivityTable.ACTIVITY_TYPE + "=" + eventTypeToActivityType(eventType).id  +
                " AND " + ActivityTable.UPDATED_DATE + ">1" +
                " AND " + ActivityTable.NEW_NOTIFICATION_EVENT + "=0";
        final Iterator<Long> iterator = MyQuery.getLongs(where).iterator();
        assertTrue("No data for '" + where + "'", iterator.hasNext());

        Long activityId = iterator.next();
        MyContextHolder.get().getDatabase().execSQL("UPDATE " + ActivityTable.TABLE_NAME +
        " SET " + ActivityTable.NEW_NOTIFICATION_EVENT + "=" + eventType.id +
        " WHERE " + ActivityTable._ID + "=" + activityId);
        notifier.update();
    }

    private MbActivityType eventTypeToActivityType(NotificationEventType eventType) {
        switch (eventType) {
            case ANNOUNCE:
                return MbActivityType.ANNOUNCE;
            default:
                return MbActivityType.UPDATE;
        }
    }

}
