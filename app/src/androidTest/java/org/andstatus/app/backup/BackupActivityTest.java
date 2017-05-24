package org.andstatus.app.backup;

import org.andstatus.app.context.ActivityTest;
import org.andstatus.app.data.DbUtils;
import org.junit.Test;

public class BackupActivityTest extends ActivityTest<BackupActivity> {

    @Override
    protected Class<BackupActivity> getActivityClass() {
        return BackupActivity.class;
    }

    @Test
    public void testOpenActivity() throws InterruptedException {
        final String method = "testOpenActivity";
        getActivity();
        getInstrumentation().waitForIdleSync();
        DbUtils.waitMs(method, 500);
    }
}
