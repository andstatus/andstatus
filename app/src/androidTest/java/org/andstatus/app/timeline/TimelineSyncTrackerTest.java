package org.andstatus.app.timeline;

import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.context.DemoData;
import org.andstatus.app.net.social.TimelinePosition;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

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
        testTimelineForAccount(DemoData.GNUSOCIAL_TEST_ACCOUNT_NAME);
    }

    @Test
    public void testTwitterTimeline() {
        testTimelineForAccount(DemoData.TWITTER_TEST_ACCOUNT_NAME);
    }

    private void testTimelineForAccount(String accountName) {
        oneTimelineType(TimelineType.PUBLIC, accountName);
        oneTimelineType(TimelineType.HOME, accountName);
        oneTimelineType(TimelineType.EVERYTHING, accountName);
    }

    private void oneTimelineType(TimelineType timelineType, String accountName) {
        MyContext myContext = MyContextHolder.get();
        MyAccount ma = DemoData.getMyAccount(accountName);
        assertTrue(ma.isValid());
        assertEquals("Account was found", ma.getAccountName(), accountName);
        Timeline timeline = getTimeline(myContext, timelineType, ma);
        if (timelineType.isAtOrigin()) {
            if (timeline.getOrigin().getOriginType().isTimelineTypeSyncable(timelineType)) {
                assertEquals("Timeline persistence " + timeline,
                        Arrays.asList(TimelineType.getDefaultOriginTimelineTypes()).contains(timelineType)
                        , timeline.getId() != 0);
            }
        } else {
            assertEquals("Timeline persistence " + timeline,
                    Arrays.asList(TimelineType.getDefaultMyAccountTimelineTypes()).contains(timelineType)
                    , timeline.getId() != 0);
        }
        long time1 = System.currentTimeMillis();
        TimelineSyncTracker syncTracker = new TimelineSyncTracker(timeline, true);
        syncTracker.onTimelineDownloaded();
        syncTracker.onNewMsg(
                new TimelinePosition("position_" + timelineType.save() + "_" + accountName),
                System.currentTimeMillis() - LATEST_ITEM_MILLIS_AGO);
        timeline.save(myContext);

        timeline = getTimeline(myContext, timelineType, ma);

        syncTracker = new TimelineSyncTracker(timeline, true);
        long time2 = System.currentTimeMillis();
        if (timeline.getId() == 0) {
            assertEquals("Remembered timeline dates for " + timeline, 0, syncTracker.getPreviousItemDate());
            assertEquals("Remembered timeline dates for " + timeline, syncTracker.getPreviousSyncedDate(), 0);
        } else {
            assertTrue(timeline.toString() + " was downloaded " + syncTracker.toString(),
                    syncTracker.getPreviousSyncedDate() >= time1);
            assertTrue(timeline.toString() + " was downloaded " + syncTracker.toString(),
                    syncTracker.getPreviousSyncedDate() <= time2);
            assertTrue(timeline.toString() + " latest item " + syncTracker.toString(),
                    syncTracker.getPreviousItemDate() >= time1 - LATEST_ITEM_MILLIS_AGO);
            assertTrue(timeline.toString() + " latest item " + syncTracker.toString(),
                    syncTracker.getPreviousItemDate() <= time2 - LATEST_ITEM_MILLIS_AGO);
        }
    }

    @NonNull
    private Timeline getTimeline(MyContext myContext, TimelineType timelineType, MyAccount ma) {
        return Timeline.getTimeline(myContext, 0, timelineType, ma, 0, null, "");
    }
}
