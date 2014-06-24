package org.andstatus.app.data;

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;

import java.io.File;
import java.util.Date;

public class DataPrunerTest extends InstrumentationTestCase  {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
    }
    
    public void testPruneLogs() {
        final String method = "testPruneLogs";
        MyLog.setLogToFile(true);
        String fileName = MyLog.getLogFileName();
        File logFile1 = MyLog.getLogFile(fileName, true);
        MyLog.v(this, method);
        MyLog.setLogToFile(false);

        assertTrue(logFile1.exists());

        MyPreferences.putLong(MyPreferences.KEY_DATA_PRUNED_DATE, 0);
        DataPruner dp = new DataPruner(MyContextHolder.get());
        dp.prune();
        assertTrue("File is fresh", logFile1.exists());
        long pruneDate1 = MyPreferences.getLong(MyPreferences.KEY_DATA_PRUNED_DATE);
        assertTrue(
                "Pruning date updated "
                        + RelativeTime.getDifference(MyContextHolder.get().context(),
                                System.currentTimeMillis()),
                !RelativeTime.moreSecondsAgoThan(pruneDate1, 300));
        dp.prune();
        assertEquals("No more pruning", pruneDate1,
                MyPreferences.getLong(MyPreferences.KEY_DATA_PRUNED_DATE));

        // See http://stackoverflow.com/questions/6633748/file-lastmodified-is-never-what-was-set-with-file-setlastmodified
        long lastModifiedNew = (System.currentTimeMillis()
                - MyLog.daysToMillis(DataPruner.MAX_DAYS_LOGS_TO_KEEP + 1)) / 1000 * 1000;
        if (logFile1.setLastModified(lastModifiedNew)) {
            MyPreferences.putLong(MyPreferences.KEY_DATA_PRUNED_DATE, 0);
            File logFile2 = MyLog.getLogFile(fileName, true);
            assertEquals(lastModifiedNew, logFile2.lastModified());
            dp.prune();
            assertFalse("File " + logFile2.getName() + " was old: " + millisToDateString(lastModifiedNew), 
                    logFile2.exists());
        } else {
            fail("Couldn't set modification date to " + millisToDateString(lastModifiedNew));
        }
        
    }

    private String millisToDateString(long dateTime) {
        return new Date(dateTime).toString();
    }
}
