package org.andstatus.app.backup

import android.Manifest
import androidx.test.rule.GrantPermissionRule
import org.andstatus.app.context.ActivityTest
import org.andstatus.app.data.DbUtils
import org.junit.Rule
import org.junit.Test

class RestoreActivityTest : ActivityTest<RestoreActivity>() {
    @Rule
    @JvmField
    var mRuntimePermissionRule = GrantPermissionRule.grant(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE)

    override fun getActivityClass(): Class<RestoreActivity> {
        return RestoreActivity::class.java
    }

    @Test
    @Throws(InterruptedException::class)
    fun testOpenActivity() {
        val method = "testOpenActivity"
        activity
        getInstrumentation().waitForIdleSync()
        DbUtils.waitMs(method, 500)
    }
}