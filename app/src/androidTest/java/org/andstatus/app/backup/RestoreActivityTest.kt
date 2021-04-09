package org.andstatus.app.backup

import org.andstatus.app.context.ActivityTest
import org.andstatus.app.data.DbUtils
import org.junit.Test

class RestoreActivityTest : ActivityTest<RestoreActivity>() {

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