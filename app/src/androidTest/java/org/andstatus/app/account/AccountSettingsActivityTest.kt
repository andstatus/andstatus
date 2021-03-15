/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.account

import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.TextView
import org.andstatus.app.FirstActivity
import org.andstatus.app.IntentExtra
import org.andstatus.app.R
import org.andstatus.app.context.ActivityTest
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.origin.OriginType
import org.andstatus.app.origin.PersistentOriginList
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.MyLog
import org.junit.Assert
import org.junit.Test

/**
 * @author yvolk@yurivolkov.com
 */
class AccountSettingsActivityTest : ActivityTest<AccountSettingsActivity>() {
    private var ma: MyAccount = MyAccount.EMPTY
    override fun getActivityClass(): Class<AccountSettingsActivity> {
        return AccountSettingsActivity::class.java
    }

    override fun getActivityIntent(): Intent {
        TestSuite.initializeWithAccounts(this)
        val currentAccount = MyContextHolder.myContextHolder.getNow().accounts().currentAccount
        ma = currentAccount
        if (currentAccount.nonValid) Assert.fail("No persistent accounts yet")
        return Intent().putExtra(IntentExtra.ACCOUNT_NAME.key, currentAccount.getAccountName())
    }

    @Test
    @Throws(InterruptedException::class)
    fun test() {
        val method = "test"
        val addAccountOrVerifyCredentials = activity.findViewById<View?>(R.id.add_account) as Button
        Assert.assertTrue(addAccountOrVerifyCredentials != null)
        assertUniqueNameTextField(R.id.uniqueName)
        assertUniqueNameTextField(R.id.uniqueName_readonly)
        DbUtils.waitMs(method, 500)
        Assert.assertEquals("MyService is available", false, MyServiceManager.Companion.isServiceAvailable())
        openingOriginList()
        DbUtils.waitMs(method, 500)
        activity.finish()
        DbUtils.waitMs(method, 500)
        FirstActivity.Companion.closeAllActivities(getInstrumentation().targetContext)
        DbUtils.waitMs(method, 500)
    }

    private fun assertUniqueNameTextField(viewId: Int) {
        val uniqueNameText = activity.findViewById<View?>(viewId) as TextView?
        Assert.assertTrue(uniqueNameText != null)
        Assert.assertEquals("Unique name of selected account $ma", ma?.getOAccountName()?.getUniqueName(), uniqueNameText?.text.toString())
    }

    @Throws(InterruptedException::class)
    private fun openingOriginList() {
        val method = "testOpeningOriginList"
        val activityMonitor = getInstrumentation().addMonitor(PersistentOriginList::class.java.name, null, false)
        val clicker: Runnable = object : Runnable {
            override fun run() {
                MyLog.v(this, "$method-Log before click")
                activity.selectOrigin(OriginType.GNUSOCIAL)
            }
        }
        MyLog.v(this, "$method-Log before run clicker 1")
        activity.runOnUiThread(clicker)
        val nextActivity = getInstrumentation().waitForMonitorWithTimeout(activityMonitor, 15000)
        MyLog.v(this, method + "-Log after waitForMonitor: "
                + nextActivity)
        Assert.assertNotNull("Next activity is opened and captured", nextActivity)
        TestSuite.waitForListLoaded(nextActivity, 6)
        DbUtils.waitMs(method, 500)
        nextActivity.finish()
    }
}