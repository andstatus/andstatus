package org.andstatus.app.backup;

import android.test.ActivityInstrumentationTestCase2;

import org.andstatus.app.data.DbUtils;

public class BackupActivityTest extends ActivityInstrumentationTestCase2<BackupActivity> {
    public BackupActivityTest() {
        super(BackupActivity.class);
    }
    
    public void testOpenActivity() throws InterruptedException {
        final String method = "testOpenActivity";
        getActivity();
        getInstrumentation().waitForIdleSync();
        DbUtils.waitMs(method, 500);
    }
}
