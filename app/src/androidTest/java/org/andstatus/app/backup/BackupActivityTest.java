package org.andstatus.app.backup;

import android.Manifest;
import android.support.test.rule.GrantPermissionRule;

import org.andstatus.app.context.ActivityTest;
import org.andstatus.app.data.DbUtils;
import org.junit.Rule;
import org.junit.Test;

public class BackupActivityTest extends ActivityTest<BackupActivity> {

    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule.grant(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE);

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
