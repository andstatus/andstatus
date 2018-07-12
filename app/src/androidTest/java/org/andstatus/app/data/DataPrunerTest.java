package org.andstatus.app.data;

import android.net.Uri;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Date;

import static org.andstatus.app.context.MyPreferences.BYTES_IN_MB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class DataPrunerTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initialize(this);
        assertTrue(TestSuite.setAndWaitForIsInForeground(false));
    }

    @Test
    public void testPrune() throws MalformedURLException {
        final String method = "testPrune";
        MyLog.v(this, method + "; Started");

        boolean isLogEnabled = MyLog.isLogToFileEnabled();
        MyLog.setLogToFile(false);
        MyLog.setLogToFile(true);
        String filename = MyLog.getLogFilename();
        File logFile1 = MyLog.getFileInLogDir(filename, true);
        MyLog.v(this, method);
        MyLog.setLogToFile(false);
        assertTrue(logFile1.exists());
        clearPrunedDate();
        DataPruner dp = new DataPruner(MyContextHolder.get(), MyContextHolder.get().getDatabase());
        assertTrue("Pruned", dp.prune());
        
        assertTrue("File is fresh", logFile1.exists());
        long pruneDate1 = SharedPreferencesUtil.getLong(MyPreferences.KEY_DATA_PRUNED_DATE);
        assertTrue(
                "Pruning date updated " + pruneDate1 + " - "
                        + RelativeTime.getDifference(MyContextHolder.get().context(),
                                pruneDate1),
                !RelativeTime.moreSecondsAgoThan(pruneDate1, 300));
        assertFalse("Second prune skipped", dp.prune());
        assertEquals("No more pruning", pruneDate1,
                SharedPreferencesUtil.getLong(MyPreferences.KEY_DATA_PRUNED_DATE));
        
        // See http://stackoverflow.com/questions/6633748/file-lastmodified-is-never-what-was-set-with-file-setlastmodified
        long lastModifiedNew = ((System.currentTimeMillis()
                - java.util.concurrent.TimeUnit.DAYS.toMillis(DataPruner.MAX_DAYS_LOGS_TO_KEEP + 1)) / 1000) * 1000;
        if (logFile1.setLastModified(lastModifiedNew)) {
            MyLog.v(this, method + "; Last modified date set for " + filename);
            clearPrunedDate();
            File logFile2 = MyLog.getFileInLogDir(filename, true);
            assertEquals(lastModifiedNew, logFile2.lastModified());
            assertTrue("Pruned", dp.prune());
            assertFalse("File " + logFile2.getName() + " was old: " + millisToDateString(lastModifiedNew), 
                    logFile2.exists());
        } else {
            String msg = method + "; Couldn't set modification date of '" + logFile1.getAbsolutePath()
                    + "' to " + millisToDateString(lastModifiedNew)
                    + " actual: " + millisToDateString(logFile1.lastModified());
            // TODO: Is this really a bug in Android?!
            MyLog.e(this, msg);
        }

        clearPrunedDate();
        assertTrue(TestSuite.setAndWaitForIsInForeground(true));
        assertFalse("Prune while in foreground skipped", dp.prune());

        if (isLogEnabled) {
            MyLog.setLogToFile(true);
        }
        MyLog.v(this, method + "; Ended");
    }

    @Test
    public void testPruneParentlessAttachments() {
        DataPruner dp = new DataPruner(MyContextHolder.get(), MyContextHolder.get().getDatabase());
        dp.pruneParentlessAttachments();
        DownloadData dd = DownloadData.getThisForNote(-555L, "", DownloadType.ATTACHMENT,
                Uri.parse("http://example.com/image.png"));
        dd.saveToDatabase();
        assertEquals(1, dp.pruneParentlessAttachments());
        assertEquals(0, dp.pruneParentlessAttachments());
    }


    @Test
    public void testPruneMedia() {
        long dirSize1 = DownloadFile.getDirSize();
        long newSizeOfAttachmentMb = 1;
        long newSizeOfAttachment = newSizeOfAttachmentMb * BYTES_IN_MB;
        long attachmentsStoredMin = DataPruner.ATTACHMENTS_TO_STORE_MIN + 2;
        if (dirSize1 < attachmentsStoredMin * newSizeOfAttachment) {
            MyLog.i(this, "Too few media files to prune, size " + dirSize1);
            return;
        }
        long maximumSizeOfStoredMediaMb = Math.round(
                (dirSize1 - DataPruner.ATTACHMENTS_TO_STORE_MIN * newSizeOfAttachment) / BYTES_IN_MB - 1
        );
        SharedPreferencesUtil.forget();
        SharedPreferencesUtil.putLong(MyPreferences.KEY_MAXIMUM_SIZE_OF_ATTACHMENT_MB, newSizeOfAttachmentMb);
        SharedPreferencesUtil.putLong(MyPreferences.KEY_MAXIMUM_SIZE_OF_CACHED_MEDIA_MB, maximumSizeOfStoredMediaMb);

        DataPruner dp = new DataPruner(MyContextHolder.get(), MyContextHolder.get().getDatabase());
        long prunedCount1 = dp.pruneMedia();
        long dirSize2 = DownloadFile.getDirSize();
        long prunedCount2 = dp.pruneMedia();
        long dirSize3 = DownloadFile.getDirSize();

        SharedPreferencesUtil.removeKey(MyPreferences.KEY_MAXIMUM_SIZE_OF_ATTACHMENT_MB);
        SharedPreferencesUtil.removeKey(MyPreferences.KEY_MAXIMUM_SIZE_OF_CACHED_MEDIA_MB);

        assertNotEquals("Something should be pruned, dir size: " + dirSize1
                + " max: " + maximumSizeOfStoredMediaMb + " MB", 0, prunedCount1);
        assertTrue("Dir size should decrease " + dirSize1 + " -> " + dirSize2, dirSize1 > dirSize2);
        assertEquals("Nothing should be pruned, " + dirSize2 + " -> " + dirSize3, 0, prunedCount2);
    }

    private void clearPrunedDate() {
        SharedPreferencesUtil.putLong(MyPreferences.KEY_DATA_PRUNED_DATE, 0);
    }

    private String millisToDateString(long dateTime) {
        return new Date(dateTime).toString();
    }
}
