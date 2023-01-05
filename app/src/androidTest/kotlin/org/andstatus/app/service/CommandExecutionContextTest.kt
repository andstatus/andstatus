package org.andstatus.app.service

import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.timeline.meta.TimelineType
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class CommandExecutionContextTest {
    private var ma: MyAccount = MyAccount.EMPTY

    @Before
    fun setUp() {
        TestSuite.initializeWithData(this)
        ma = myContextHolder.getNow().accounts.getFirstSucceeded()
    }

    @Test
    fun testMentionsAccumulation() {
        val execContext = CommandExecutionContext(
            myContextHolder.getNow(),
            CommandData.Companion.newTimelineCommand(CommandEnum.GET_TIMELINE, ma, TimelineType.INTERACTIONS)
        )
        Assert.assertEquals(TimelineType.INTERACTIONS, execContext.getTimeline().timelineType)
        val noteCount = 4
        val mentionCount = 2
        for (ind in 0 until noteCount) {
            execContext.getResult().incrementNewCount()
        }
        for (ind in 0 until mentionCount) {
            execContext.getResult().onNotificationEvent(NotificationEventType.MENTION)
        }
        Assert.assertEquals(noteCount.toLong(), execContext.getResult().getNewCount())
        Assert.assertEquals(
            mentionCount.toLong(),
            execContext.getResult().notificationEventCounts[NotificationEventType.MENTION]?.get()
        )
        Assert.assertEquals(
            0, execContext.getResult().notificationEventCounts.getOrDefault(
                NotificationEventType.PRIVATE, AtomicLong(0)
            ).get()
        )
    }

    @Test
    fun testPrivateAccumulation() {
        val execContext = CommandExecutionContext(
            myContextHolder.getNow(),
            CommandData.Companion.newTimelineCommand(CommandEnum.GET_TIMELINE, ma, TimelineType.PRIVATE)
        )
        val privateCount = 4
        for (ind in 0 until privateCount) {
            execContext.getResult().onNotificationEvent(NotificationEventType.PRIVATE)
        }
        Assert.assertEquals(0, execContext.getResult().getNewCount())
        Assert.assertEquals(
            0, execContext.getResult().notificationEventCounts.getOrDefault(
                NotificationEventType.MENTION, AtomicLong(0)
            ).get()
        )
        Assert.assertEquals(
            privateCount.toLong(),
            execContext.getResult().notificationEventCounts[NotificationEventType.PRIVATE]?.get()
        )
    }
}
