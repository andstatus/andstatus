package org.andstatus.app.backup

import org.andstatus.app.context.ActivityTest
import org.andstatus.app.util.EspressoUtils.waitForIdleSync
import org.junit.Test

class BackupActivityTest : ActivityTest<BackupActivity>() {

    override fun getActivityClass(): Class<BackupActivity> {
        return BackupActivity::class.java
    }

    @Test
    fun testOpenActivity() {
        val method = "testOpenActivity"
        activity
        waitForIdleSync()
    }
}
