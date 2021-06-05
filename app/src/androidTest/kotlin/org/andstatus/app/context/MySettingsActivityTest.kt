package org.andstatus.app.contex

import android.preference.PreferenceFragment
import org.andstatus.app.context.ActivityTest
import org.andstatus.app.context.MySettingsActivity
import org.andstatus.app.context.MySettingsFragment
import org.andstatus.app.data.DbUtils
import org.andstatus.app.service.MyServiceManager
import org.junit.Assert
import org.junit.Test

class MySettingsActivityTest : ActivityTest<MySettingsActivity>() {

    override fun getActivityClass(): Class<MySettingsActivity> {
        return MySettingsActivity::class.java
    }

    @Test
    fun test() {
        val method = "test"
        val fragment = activity.getFragmentManager().findFragmentByTag(MySettingsFragment::class.java.simpleName) as PreferenceFragment?
        if (fragment != null) {
            val preference = fragment.findPreference(
                    MySettingsFragment.Companion.KEY_MANAGE_ACCOUNTS)
            Assert.assertTrue(MySettingsFragment.Companion.KEY_MANAGE_ACCOUNTS, preference != null)
        }
        DbUtils.waitMs(method, 500)
        Assert.assertFalse("MyService is not available", MyServiceManager.Companion.isServiceAvailable())
    }
}
