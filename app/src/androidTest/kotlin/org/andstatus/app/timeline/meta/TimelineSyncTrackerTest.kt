package org.andstatus.app.timeline.meta

import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.service.TimelineSyncTracker
import org.andstatus.app.util.TriState
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class TimelineSyncTrackerTest {

    @Before
    fun setUp() {
        TestSuite.initializeWithData(this)
    }

    @Test
    fun testGnuSocialTimeline() {
        testTimelineForAccount(DemoData.demoData.gnusocialTestAccountName)
    }

    @Test
    fun testTwitterTimeline() {
        testTimelineForAccount(DemoData.demoData.twitterTestAccountName)
    }

    private fun testTimelineForAccount(accountName: String) {
        oneTimelineType(TimelineType.PUBLIC, accountName)
        oneTimelineType(TimelineType.HOME, accountName)
        oneTimelineType(TimelineType.EVERYTHING, accountName)
    }

    private fun oneTimelineType(timelineType: TimelineType, accountName: String) {
        val myContext: MyContext = myContextHolder.getNow()
        val ma: MyAccount = DemoData.demoData.getMyAccount(accountName)
        val message = timelineType.save() + " " + ma
        Assert.assertTrue(ma.isValid)
        Assert.assertEquals("Account was found", accountName, ma.accountName)
        val timeline = findTimeline(myContext, timelineType, ma)
        if (timeline === Timeline.EMPTY) return
        if (timelineType.isAtOrigin) {
            if (timeline.origin.originType.isTimelineTypeSyncable(timelineType)) {
                assertDefaultTimelinePersistence(
                    message, TimelineType.Companion.getDefaultOriginTimelineTypes().contains(timelineType), timeline
                )
            }
        } else {
            assertDefaultTimelinePersistence(message, ma.defaultTimelineTypes.contains(timelineType), timeline)
        }
        val time1 = System.currentTimeMillis()
        var syncTracker = TimelineSyncTracker(timeline, true)
        syncTracker.onTimelineDownloaded()
        syncTracker.onNewActivity(
            System.currentTimeMillis() - LATEST_ITEM_MILLIS_AGO,
            TimelinePosition.Companion.of("position_" + timelineType.save() + "_" + accountName),
            TimelinePosition.Companion.of("position_" + timelineType.save() + "_" + accountName)
        )
        timeline.save(myContext)
        val timeline2 = findTimeline(myContext, timelineType, ma)
        syncTracker = TimelineSyncTracker(timeline2, true)
        val time2 = System.currentTimeMillis()
        if (timeline2.isValid) {
            Assert.assertTrue(
                "$timeline2 was downloaded $syncTracker",
                syncTracker.getPreviousSyncedDate() >= time1
            )
            Assert.assertTrue(
                "$timeline2 was downloaded $syncTracker",
                syncTracker.getPreviousSyncedDate() <= time2
            )
            Assert.assertTrue(
                "$timeline2 latest item $syncTracker",
                syncTracker.getPreviousItemDate() >= time1 - LATEST_ITEM_MILLIS_AGO
            )
            Assert.assertTrue(
                "$timeline2 latest item $syncTracker",
                syncTracker.getPreviousItemDate() <= time2 - LATEST_ITEM_MILLIS_AGO
            )
        } else {
            Assert.assertEquals("Remembered timeline dates for $timeline", 0, syncTracker.getPreviousItemDate())
            Assert.assertEquals("Remembered timeline dates for $timeline", 0, syncTracker.getPreviousSyncedDate())
        }
    }

    private fun assertDefaultTimelinePersistence(message: String, isAddedByDefault: Boolean, timeline: Timeline) {
        Assert.assertEquals("$message; Is added by default\n$timeline\n", isAddedByDefault, timeline.isAddedByDefault())
        Assert.assertEquals(
            "$message; Timeline persistence\n$timeline\n", isAddedByDefault,
            timeline.isValid && timeline.isDisplayedInSelector != DisplayedInSelector.NEVER
        )
    }

    private fun findTimeline(myContext: MyContext, timelineType: TimelineType, ma: MyAccount): Timeline {
        return myContext.timelines.filter(
            false, TriState.UNKNOWN, timelineType, ma.actor,
            ma.origin
        ).findFirst().orElse(Timeline.EMPTY)
    }

    companion object {
        private const val LATEST_ITEM_MILLIS_AGO = 10000
    }
}
