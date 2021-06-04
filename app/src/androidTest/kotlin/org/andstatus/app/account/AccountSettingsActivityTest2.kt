package org.andstatus.app.account

import android.content.Intent
import org.andstatus.app.context.DemoData

class AccountSettingsActivityTest2() : AccountSettingsActivityTest() {
    override val accountAction: String = Intent.ACTION_EDIT
    override val accountNameString: String =  DemoData.demoData.activityPubTestAccountName
}
