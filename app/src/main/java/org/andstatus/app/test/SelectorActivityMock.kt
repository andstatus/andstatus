package org.andstatus.app.test

import android.content.Intent

/**
 * @author yvolk@yurivolkov.com
 */
interface SelectorActivityMock {
    open fun startActivityForResult(intent: Intent?, requestCode: Int)
}