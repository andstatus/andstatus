package org.andstatus.app.test

import android.content.Intent

/**
 * @author yvolk@yurivolkov.com
 */
interface SelectorActivityStub {
    open fun startActivityForResult(intent: Intent?, requestCode: Int)
}
