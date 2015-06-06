package org.andstatus.app;

import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.LatestTimelineItem;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.net.social.TimelinePosition;

public class LatestTimelineItemTest extends InstrumentationTestCase {
    
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
    }

    private void oneTimelineType(TimelineType timelineType, String accountName) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(accountName);
        assertTrue(ma.isValid());
        assertEquals("Account was found", ma.getAccountName(), accountName);

        LatestTimelineItem latest = new LatestTimelineItem(timelineType, ma.getUserId());
        latest.onTimelineDownloaded();
        latest.onNewMsg(
                new TimelinePosition("position_" + timelineType.save() + "_" + accountName),
                System.currentTimeMillis() - 10000);
        latest.save();

        latest = new LatestTimelineItem(timelineType, ma.getUserId());
        switch (timelineType) {
            case PUBLIC:
                assertEquals("Don't remember public timeline dates", latest.getTimelineItemDate(), 0);
                assertEquals("Don't remember public timeline dates", latest.getTimelineDownloadedDate(), 0);
                break;
            default:
                assertTrue("Timeline for " + accountName + " was downloaded " + latest.toString(),
                        latest.getTimelineItemDate() > 0);
                assertTrue("Timeline for " + accountName + " was downloaded " + latest.toString(),
                        latest.getTimelineDownloadedDate() > 0);
                break;
        }
    }
}
