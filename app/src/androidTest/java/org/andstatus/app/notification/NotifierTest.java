package org.andstatus.app.notification;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.TriState;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
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
        MyContext myContext = TestSuite.getMyContextForTest();
        Notifier notifier = myContext.getNotifier();
        notifier.clearAll();
        assertTrue("Events should be empty " + notifier.getEvents(), notifier.getEvents().isEmpty());

        addNotificationEvent(myContext, NotificationEventType.PRIVATE);
        assertCount(notifier, 0, NotificationEventType.ANNOUNCE);
        assertCount(notifier, 0, NotificationEventType.MENTION);
        assertCount(notifier, 1, NotificationEventType.PRIVATE);

    	addNotificationEvent(myContext, NotificationEventType.MENTION);
        assertCount(notifier, 0, NotificationEventType.ANNOUNCE);
        assertCount(notifier, 1, NotificationEventType.MENTION);
        assertCount(notifier, 1, NotificationEventType.PRIVATE);

        addNotificationEvent(myContext, NotificationEventType.ANNOUNCE);
        assertCount(notifier, 1, NotificationEventType.ANNOUNCE);
        assertCount(notifier, 1, NotificationEventType.MENTION);
        assertCount(notifier, 1, NotificationEventType.PRIVATE);
    }

    public static void addNotificationEvent(MyContext myContext, NotificationEventType eventType) {
        String where = "SELECT " + ActivityTable.TABLE_NAME + "." + ActivityTable._ID +
                " FROM " + ActivityTable.TABLE_NAME +
                " INNER JOIN " + NoteTable.TABLE_NAME +
                " ON " + ActivityTable.TABLE_NAME + "." + ActivityTable.NOTE_ID + "=" +
                    NoteTable.TABLE_NAME + "." + NoteTable._ID +
                " WHERE " + ActivityTable.ACTIVITY_TYPE + "=" + eventTypeToActivityType(eventType).id  +
                " AND " + ActivityTable.UPDATED_DATE + ">" + RelativeTime.SOME_TIME_AGO +
                " AND " + ActivityTable.NEW_NOTIFICATION_EVENT + "=0" +
                (eventType == NotificationEventType.PRIVATE
                        ? " AND " + NoteTable.VISIBILITY + "=" + TriState.FALSE.id
                        : "");
        final Iterator<Long> iterator = MyQuery.getLongs(myContext, where).iterator();
        assertTrue("No data for '" + where + "'", iterator.hasNext());

        long activityId = iterator.next();
        assertNotEquals("No activity for '" + where + "'", 0L, activityId);
        myContext.getDatabase().execSQL("UPDATE " + ActivityTable.TABLE_NAME +
        " SET " + ActivityTable.NEW_NOTIFICATION_EVENT + "=" + eventType.id +
        ", " + ActivityTable.NOTIFIED + "=" + TriState.TRUE.id +
        ", " + ActivityTable.NOTIFIED_ACTOR_ID + "=" + ActivityTable.ACTOR_ID +
        " WHERE " + ActivityTable._ID + "=" + activityId);
        myContext.getNotifier().update();
    }

    private void assertCount(Notifier notifier, long expectedCount, NotificationEventType eventType) {
        assertEquals("EventType: " + eventType + ", " + notifier.getEvents().map,
                expectedCount, notifier.getEvents().getCount(eventType));
    }

    private static ActivityType eventTypeToActivityType(NotificationEventType eventType) {
        switch (eventType) {
            case ANNOUNCE:
                return ActivityType.ANNOUNCE;
            default:
                return ActivityType.UPDATE;
        }
    }

}
