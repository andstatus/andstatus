package org.andstatus.app.account

import android.content.Intent
import android.view.View
import android.widget.TextView
import org.andstatus.app.IntentExtra
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.junit.Assert

class AccountSettingsActivityTestAdd() : AccountSettingsActivityTest() {
    override val accountAction: String = Intent.ACTION_INSERT
    override val accountNameString: String = "newUser/activityPubTest"
    val uniqueName = accountNameString.substring(0, accountNameString.indexOf("/"))

    override fun getActivityClass(): Class<AccountSettingsActivity> {
        return AccountSettingsActivity::class.java
    }

    override fun getActivityIntent(): Intent {
        TestSuite.initializeWithAccounts(this)
        val ma = MyContextHolder.myContextHolder.getNow().accounts().fromAccountName(accountNameString)
        if (ma.isValid) Assert.fail("Found persistent account '$accountNameString'")
        return Intent(accountAction).putExtra(IntentExtra.ACCOUNT_NAME.key, accountNameString)
    }

    override fun assertUniqueNameTextField(viewId: Int) {
        val uniqueNameText = activity.findViewById<View?>(viewId) as TextView?
        Assert.assertTrue(uniqueNameText != null)
        Assert.assertEquals("Unique name of the new account $accountNameString", uniqueName, uniqueNameText?.text.toString())
    }
}
