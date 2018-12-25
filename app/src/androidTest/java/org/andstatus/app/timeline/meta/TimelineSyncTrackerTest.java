package org.andstatus.app.timeline.meta;

import androidx.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.service.TimelineSyncTracker;
import org.andstatus.app.util.TriState;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TimelineSyncTrackerTest {
    private static final int LATEST_ITEM_MILLIS_AGO = 10000;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void testGnuSocialTimeline() {
        testTimelineForAccount(demoData.gnusocialTestAccountName);
    }

    @Test
    public void testTwitterTimeline() {
        testTimelineForAccount(demoData.twitterTestAccountName);
    }

    private void testTimelineForAccount(String accountName) {
        oneTimelineType(TimelineType.PUBLIC, accountName);
        oneTimelineType(TimelineType.HOME, accountName);
        oneTimelineType(TimelineType.EVERYTHING, accountName);
    }

    private void oneTimelineType(TimelineType timelineType, String accountName) {
        MyContext myContext = MyContextHolder.get();
        MyAccount ma = demoData.getMyAccount(accountName);
        String message = timelineType.save() + " " + ma;
        assertTrue(ma.isValid());
        assertEquals("Account was found", ma.getAccountName(), accountName);
        Timeline timeline = findTimeline(myContext, timelineType, ma);
        if (timeline == Timeline.EMPTY) return;

        if (timelineType.isAtOrigin()) {
            if (timeline.getOrigin().getOriginType().isTimelineTypeSyncable(timelineType)) {
                assertDefaultTimelinePersistence(
                        message, TimelineType.getDefaultOriginTimelineTypes().contains(timelineType), timeline);
            }
        } else {
            assertDefaultTimelinePersistence(
                    message, TimelineType.getDefaultMyAccountTimelineTypes().contains(timelineType), timeline);
        }
        long time1 = System.currentTimeMillis();
        TimelineSyncTracker syncTracker = new TimelineSyncTracker(timeline, true);
        syncTracker.onTimelineDownloaded();
        syncTracker.onNewMsg(
                new TimelinePosition("position_" + timelineType.save() + "_" + accountName),
                System.currentTimeMillis() - LATEST_ITEM_MILLIS_AGO);
        timeline.save(myContext);

        Timeline timeline2 = findTimeline(myContext, timelineType, ma);

        syncTracker = new TimelineSyncTracker(timeline2, true);
        long time2 = System.currentTimeMillis();
        if (timeline2.isValid()) {
            assertTrue(timeline2.toString() + " was downloaded " + syncTracker.toString(),
                    syncTracker.getPreviousSyncedDate() >= time1);
            assertTrue(timeline2.toString() + " was downloaded " + syncTracker.toString(),
                    syncTracker.getPreviousSyncedDate() <= time2);
            assertTrue(timeline2.toString() + " latest item " + syncTracker.toString(),
                    syncTracker.getPreviousItemDate() >= time1 - LATEST_ITEM_MILLIS_AGO);
            assertTrue(timeline2.toString() + " latest item " + syncTracker.toString(),
                    syncTracker.getPreviousItemDate() <= time2 - LATEST_ITEM_MILLIS_AGO);
        } else {
            assertEquals("Remembered timeline dates for " + timeline, 0, syncTracker.getPreviousItemDate());
            assertEquals("Remembered timeline dates for " + timeline, 0, syncTracker.getPreviousSyncedDate());
        }
    }

    private void assertDefaultTimelinePersistence(String message, boolean isAddedByDefault, Timeline timeline) {
        assertEquals(message + "; Is added by default\n" + timeline + "\n", isAddedByDefault, timeline.isAddedByDefault());
        assertEquals(message + "; Timeline persistence\n" + timeline + "\n", isAddedByDefault,
                timeline.isValid() && timeline.isDisplayedInSelector() != DisplayedInSelector.NEVER);
    }

    @NonNull
    private Timeline findTimeline(MyContext myContext, TimelineType timelineType, MyAccount ma) {
        return myContext.timelines().filter(false, TriState.UNKNOWN, timelineType, ma.getActor(),
                ma.getOrigin()).findFirst().orElse(Timeline.EMPTY);
    }
}
