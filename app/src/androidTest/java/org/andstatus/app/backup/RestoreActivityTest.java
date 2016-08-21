package org.andstatus.app.backup;

import android.test.ActivityInstrumentationTestCase2;

import org.andstatus.app.data.DbUtils;

public class RestoreActivityTest extends ActivityInstrumentationTestCase2<RestoreActivity> {
    public RestoreActivityTest() {
        super(RestoreActivity.class);
    }
    
    public void testOpenActivity() throws InterruptedException {
        final String method = "testOpenActivity";
        getActivity();
        getInstrumentation().waitForIdleSync();
        DbUtils.waitMs(method, 500);
    }
}
