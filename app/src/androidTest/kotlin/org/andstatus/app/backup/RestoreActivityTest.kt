package org.andstatus.app.backup

import org.andstatus.app.context.ActivityTest
import org.andstatus.app.util.EspressoUtils.waitForIdleSync
import org.junit.Test

class RestoreActivityTest : ActivityTest<RestoreActivity>() {

    override fun getActivityClass(): Class<RestoreActivity> {
        return RestoreActivity::class.java
    }

    @Test
    fun testOpenActivity() {
        val method = "testOpenActivity"
        activity
        waitForIdleSync()
    }
}
