package org.andstatus.app.data;

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.MyLog;

import java.io.File;
import java.util.Date;

public class DataPrunerTest extends InstrumentationTestCase  {
    
    public void testPruneLogs() {
        final String method = "testPruneLogs";
        MyLog.setLogToFile(true);
        String fileName = MyLog.getLogFileName();
        File file1 = MyLog.getLogFile(fileName, true);
        MyLog.v(this, method);
        MyLog.setLogToFile(false);

        assertTrue(file1.exists());

        DataPruner dp = new DataPruner(MyContextHolder.get().context());
        dp.prune();
        assertTrue("File is fresh", file1.exists());

        // See http://stackoverflow.com/questions/6633748/file-lastmodified-is-never-what-was-set-with-file-setlastmodified
        long lastModifiedNew = (System.currentTimeMillis()
                - MyLog.daysToMillis(DataPruner.MAX_DAYS_LOGS_TO_KEEP + 1)) / 1000 * 1000;
        if (file1.setLastModified(lastModifiedNew)) {
            File file2 = MyLog.getLogFile(fileName, true);
            assertEquals(lastModifiedNew, file2.lastModified());
            dp.prune();
            assertFalse("File " + file2.getName() + " was old: " + millisToDateString(lastModifiedNew), 
                    file2.exists());
        } else {
            fail("Couldn't set modification date to " + millisToDateString(lastModifiedNew));
        }
        
    }

    private String millisToDateString(long dateTime) {
        return new Date(dateTime).toString();
    }
}
