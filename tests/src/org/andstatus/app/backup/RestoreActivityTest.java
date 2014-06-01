package org.andstatus.app.backup;

import android.test.ActivityInstrumentationTestCase2;

public class RestoreActivityTest extends ActivityInstrumentationTestCase2<RestoreActivity> {
    public RestoreActivityTest() {
        super(RestoreActivity.class);
    }
    
    public void testOpenActivity() throws InterruptedException {
        getActivity();
        getInstrumentation().waitForIdleSync();
        Thread.sleep(500);
    }
}
