package org.andstatus.app.notification

import android.provider.BaseColumns
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.TriState
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class NotifierTest {
    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initialize(this)
        NotificationMethodType.NOTIFICATION_AREA.setEnabled(true)
        NotificationEventType.ANNOUNCE.setEnabled(true)
        NotificationEventType.MENTION.setEnabled(true)
        NotificationEventType.PRIVATE.setEnabled(true)
        MyPreferences.onPreferencesChanged()
        TestSuite.initializeWithData(this)
    }

    @Test
    fun testCreateNotification() {
        val myContext: MyContext = TestSuite.getMyContextForTest()
        val notifier = myContext.getNotifier()
        notifier.clearAll()
        Assert.assertTrue("Events should be empty " + notifier.getEvents(), notifier.getEvents().isEmpty())
        addNotificationEvent(myContext, NotificationEventType.PRIVATE)
        assertCount(notifier, 0, NotificationEventType.ANNOUNCE)
        assertCount(notifier, 0, NotificationEventType.MENTION)
        assertCount(notifier, 1, NotificationEventType.PRIVATE)
        addNotificationEvent(myContext, NotificationEventType.MENTION)
        assertCount(notifier, 0, NotificationEventType.ANNOUNCE)
        assertCount(notifier, 1, NotificationEventType.MENTION)
        assertCount(notifier, 1, NotificationEventType.PRIVATE)
        addNotificationEvent(myContext, NotificationEventType.ANNOUNCE)
        assertCount(notifier, 1, NotificationEventType.ANNOUNCE)
        assertCount(notifier, 1, NotificationEventType.MENTION)
        assertCount(notifier, 1, NotificationEventType.PRIVATE)
    }

    private fun assertCount(notifier: Notifier, expectedCount: Long, eventType: NotificationEventType) {
        Assert.assertEquals("EventType: " + eventType + ", " + notifier.getEvents().map,
                expectedCount, notifier.getEvents().getCount(eventType))
    }

    companion object {
        fun addNotificationEvent(myContext: MyContext, eventType: NotificationEventType) {
            val where = "SELECT " + ActivityTable.TABLE_NAME + "." + BaseColumns._ID +
                    " FROM " + ActivityTable.TABLE_NAME +
                    " INNER JOIN " + NoteTable.TABLE_NAME +
                    " ON " + ActivityTable.TABLE_NAME + "." + ActivityTable.NOTE_ID + "=" +
                    NoteTable.TABLE_NAME + "." + BaseColumns._ID +
                    " WHERE " + ActivityTable.ACTIVITY_TYPE + "=" + eventTypeToActivityType(eventType).id +
                    " AND " + ActivityTable.UPDATED_DATE + ">" + RelativeTime.SOME_TIME_AGO +
                    " AND " + ActivityTable.NEW_NOTIFICATION_EVENT + "=0" +
                    if (eventType == NotificationEventType.PRIVATE) " AND " + NoteTable.VISIBILITY + "=" + Visibility.PRIVATE.id else ""
            val iterator = MyQuery.getLongs(myContext, where).iterator()
            Assert.assertTrue("No data for '$where'", iterator.hasNext())
            val activityId = iterator.next()
            Assert.assertNotEquals("No activity for '$where'", 0L, activityId)
            myContext.database?.execSQL("UPDATE " + ActivityTable.TABLE_NAME +
                    " SET " + ActivityTable.NEW_NOTIFICATION_EVENT + "=" + eventType.id +
                    ", " + ActivityTable.NOTIFIED + "=" + TriState.TRUE.id +
                    ", " + ActivityTable.NOTIFIED_ACTOR_ID + "=" + ActivityTable.ACTOR_ID +
                    " WHERE " + BaseColumns._ID + "=" + activityId)
            myContext.getNotifier().update()
        }

        private fun eventTypeToActivityType(eventType: NotificationEventType?): ActivityType {
            return when (eventType) {
                NotificationEventType.ANNOUNCE -> ActivityType.ANNOUNCE
                else -> ActivityType.UPDATE
            }
        }
    }
}
