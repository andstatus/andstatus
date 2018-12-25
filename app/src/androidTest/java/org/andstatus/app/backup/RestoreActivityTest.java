package org.andstatus.app.backup;

import android.Manifest;
import androidx.test.rule.GrantPermissionRule;

import org.andstatus.app.context.ActivityTest;
import org.andstatus.app.data.DbUtils;
import org.junit.Rule;
import org.junit.Test;

public class RestoreActivityTest extends ActivityTest<RestoreActivity> {

    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule.grant(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE);

    @Override
    protected Class<RestoreActivity> getActivityClass() {
        return RestoreActivity.class;
    }

    @Test
    public void testOpenActivity() throws InterruptedException {
        final String method = "testOpenActivity";
        getActivity();
        getInstrumentation().waitForIdleSync();
        DbUtils.waitMs(method, 500);
    }
}
