package org.andstatus.app.msg;

import android.support.annotation.NonNull;
import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.LatestTimelineItem;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.net.social.TimelinePosition;

import java.util.Arrays;

public class LatestTimelineItemTest extends InstrumentationTestCase {

    public static final int LATEST_ITEM_MILLIS_AGO = 10000;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
    }

    public void testGnuSocialTimeline() {
        testTimelineForAccount(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME);
    }

    public void testTwitterTimeline() {
        testTimelineForAccount(TestSuite.TWITTER_TEST_ACCOUNT_NAME);
    }

    private void testTimelineForAccount(String accountName) {
        oneTimelineType(TimelineType.PUBLIC, accountName);
        oneTimelineType(TimelineType.HOME, accountName);
        oneTimelineType(TimelineType.ALL, accountName);
    }

    private void oneTimelineType(TimelineType timelineType, String accountName) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(accountName);
        assertTrue(ma.isValid());
        assertEquals("Account was found", ma.getAccountName(), accountName);
        Timeline timeline = getTimeline(timelineType, ma);
        assertEquals("Timeline persistence " + timeline,
                Arrays.asList(TimelineType.defaultTimelineTypes).contains(timelineType), timeline.getId() != 0);
        long time1 = System.currentTimeMillis();
        LatestTimelineItem latest = new LatestTimelineItem(timeline);
        latest.onTimelineDownloaded();
        latest.onNewMsg(
                new TimelinePosition("position_" + timelineType.save() + "_" + accountName),
                System.currentTimeMillis() - LATEST_ITEM_MILLIS_AGO);
        latest.save();

        MyContextHolder.get().persistentTimelines().initialize();
        timeline = getTimeline(timelineType, ma);

        latest = new LatestTimelineItem(timeline);
        long time2 = System.currentTimeMillis();
        if (timeline.getId() == 0) {
            assertEquals("Remembered timeline dates for " + timeline, 0, latest.getTimelineItemDate());
            assertEquals("Remembered timeline dates for " + timeline, latest.getTimelineDownloadedDate(), 0);
        } else {
            assertTrue(timeline.toString() + " was downloaded " + latest.toString(),
                    latest.getTimelineDownloadedDate() >= time1);
            assertTrue(timeline.toString() + " was downloaded " + latest.toString(),
                    latest.getTimelineDownloadedDate() <= time2);
            assertTrue(timeline.toString() + " latest item " + latest.toString(),
                    latest.getTimelineItemDate() >= time1 - LATEST_ITEM_MILLIS_AGO);
            assertTrue(timeline.toString() + " latest item " + latest.toString(),
                    latest.getTimelineItemDate() <= time2 - LATEST_ITEM_MILLIS_AGO);
        }
    }

    @NonNull
    private Timeline getTimeline(TimelineType timelineType, MyAccount ma) {
        return MyContextHolder.get().persistentTimelines()
                .fromNewTimeLine(new Timeline(timelineType, ma, 0, null));
    }
}
