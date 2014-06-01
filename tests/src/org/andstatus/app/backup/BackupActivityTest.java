package org.andstatus.app.backup;

import android.test.ActivityInstrumentationTestCase2;

public class BackupActivityTest extends ActivityInstrumentationTestCase2<BackupActivity> {
    public BackupActivityTest() {
        super(BackupActivity.class);
    }
    
    public void testOpenActivity() throws InterruptedException {
        getActivity();
        getInstrumentation().waitForIdleSync();
        Thread.sleep(500);
    }
}
