package org.andstatus.app.data;

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;

public class DataPrunerTest extends InstrumentationTestCase  {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
        assertTrue(TestSuite.setAndWaitForIsInForeground(false));
    }
    
    public void testPrune() throws MalformedURLException {
        final String method = "testPrune";
        MyLog.v(this, method + "; Started");
        MyLog.setLogToFile(true);
        String filename = MyLog.getLogFilename();
        File logFile1 = MyLog.getFileInLogDir(filename, true);
        MyLog.v(this, method);
        MyLog.setLogToFile(false);
        assertTrue(logFile1.exists());

        clearPrunedDate();
        DataPruner dp = new DataPruner(MyContextHolder.get());
        assertTrue("Pruned", dp.prune());
        
        assertTrue("File is fresh", logFile1.exists());
        long pruneDate1 = MyPreferences.getLong(MyPreferences.KEY_DATA_PRUNED_DATE);
        assertTrue(
                "Pruning date updated " + pruneDate1 + " - "
                        + RelativeTime.getDifference(MyContextHolder.get().context(),
                                pruneDate1),
                !RelativeTime.moreSecondsAgoThan(pruneDate1, 300));
        assertFalse("Second prune skipped", dp.prune());
        assertEquals("No more pruning", pruneDate1,
                MyPreferences.getLong(MyPreferences.KEY_DATA_PRUNED_DATE));
        
        // See http://stackoverflow.com/questions/6633748/file-lastmodified-is-never-what-was-set-with-file-setlastmodified
        long lastModifiedNew = (System.currentTimeMillis()
                - java.util.concurrent.TimeUnit.DAYS.toMillis(DataPruner.MAX_DAYS_LOGS_TO_KEEP + 1)) / 1000 * 1000;
        if (logFile1.setLastModified(lastModifiedNew)) {
            clearPrunedDate();
            File logFile2 = MyLog.getFileInLogDir(filename, true);
            assertEquals(lastModifiedNew, logFile2.lastModified());
            assertTrue("Pruned", dp.prune());
            assertFalse("File " + logFile2.getName() + " was old: " + millisToDateString(lastModifiedNew), 
                    logFile2.exists());
        } else {
            fail("Couldn't set modification date to " + millisToDateString(lastModifiedNew));
        }

        clearPrunedDate();
        assertTrue(TestSuite.setAndWaitForIsInForeground(true));
        assertFalse("Prune while in foreground skipped", dp.prune());
        
        MyLog.v(this, method + "; Ended");
    }

    public void testPruneAttachments() throws MalformedURLException {
        DataPruner dp = new DataPruner(MyContextHolder.get());
        dp.pruneAttachments();
        DownloadData dd = DownloadData.newForMessage(-555L, MyContentType.IMAGE, new URL("http://example.com/image.png"));
        dd.saveToDatabase();
        assertEquals(1, dp.pruneAttachments());
        assertEquals(0, dp.pruneAttachments());
    }

    private void clearPrunedDate() {
        MyPreferences.putLong(MyPreferences.KEY_DATA_PRUNED_DATE, 0);
    }

    private String millisToDateString(long dateTime) {
        return new Date(dateTime).toString();
    }
}
