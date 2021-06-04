/*
 * Copyright (c) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.backup

import android.app.ProgressDialog
import android.content.DialogInterface
import android.widget.Toast
import org.andstatus.app.FirstActivity
import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyContextState
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.util.DialogFactory
import org.andstatus.app.util.MyLog
import java.util.*

/**
 * @author yvolk@yurivolkov.com
 * Only one "progressing" process is allowed. all previous are being marked as cancelled
 */
class DefaultProgressListener(activity: MyActivity, defaultTitleId: Int, logTag: String) : ProgressLogger.ProgressListener, DialogInterface.OnDismissListener {
    @Volatile
    private var activity: Optional<MyActivity>
    private val defaultTitle: CharSequence?
    private val logTag: String
    private val iStartedAt: Long
    private val upgradingText: CharSequence?
    private val cancelText: CharSequence?
    private val versionText: CharSequence?

    @Volatile
    private var isCancelable = false

    @Volatile
    private var isCancelled = false

    @Volatile
    private var isCompleted = false

    @Volatile
    private var progressDialog: ProgressDialog? = null
    override fun setCancelable(isCancelable: Boolean) {
        this.isCancelable = isCancelable
    }

    override fun onProgressMessage(messageIn: CharSequence) {
        val message = formatMessage(messageIn)
        showMessage(message)
        if (!isCancelled() && ProgressLogger.startedAt.get() != iStartedAt) {
            showMessage("New progress started, cancelling this...")
            cancel()
        }
    }

    private fun showMessage(message: String) {
        if (!isCancelled() && activity.isPresent()) {
            activity.ifPresent { activity: MyActivity -> showMessage(activity, message) }
        } else {
            MyLog.i(logTag, message)
        }
    }

    private fun showMessage(activity: MyActivity, message: String) {
        val method = "showMessage"
        try {
            activity.runOnUiThread {
                var shown = false
                if (activity.isMyResumed()) {
                    try {
                        if (progressDialog == null) {
                            progressDialog = ProgressDialog(activity, ProgressDialog.STYLE_SPINNER).also { dialog ->
                                dialog.setOnDismissListener(this@DefaultProgressListener)
                                dialog.setTitle(if (MyContextHolder.myContextHolder.getNow().state() == MyContextState.UPGRADING) upgradingText else defaultTitle)
                                dialog.setMessage(message)
                                if (isCancelable && !isCancelled()) {
                                    dialog.setCancelable(false)
                                    dialog.setButton(DialogInterface.BUTTON_NEGATIVE, cancelText)
                                        { dialog1: DialogInterface, which: Int -> cancel() }
                                }
                                dialog.show()
                            }
                        } else {
                            progressDialog?.setMessage(message)
                        }
                        shown = true
                    } catch (e: Exception) {
                        MyLog.d(logTag, "$method '$message'", e)
                    }
                }
                if (!shown) {
                    showToast(message)
                }
            }
        } catch (e: Exception) {
            MyLog.d(logTag, "$method '$message'", e)
        }
    }

    private fun formatMessage(message: CharSequence?): String {
        return (if (isCancelled()) cancelText.toString() + ": " else "") + message
    }

    private fun showToast(message: CharSequence?) {
        try {
            Toast.makeText( MyContextHolder.myContextHolder.getNow().context,
                "${defaultTitle.toString()}\n$versionText" +
                        (if ( MyContextHolder.myContextHolder.getNow().state() == MyContextState.UPGRADING) "\n$upgradingText" else "") +
                        "\n\n" + message,
                Toast.LENGTH_LONG)
                .show()
        } catch (e2: Exception) {
            MyLog.w(logTag, "Couldn't send toast with the text: $message", e2)
        }
    }

    override fun onComplete(success: Boolean) {
        isCompleted = true
        activity.ifPresent { activity: MyActivity ->
            try {
                activity.runOnUiThread {
                    freeResources()
                    FirstActivity.goHome(activity)
                    activity.finish()
                }
            } catch (e: Exception) {
                MyLog.d(logTag, "onComplete $success", e)
            }
        }
    }

    override fun cancel() {
        isCancelled = true
    }

    override fun onActivityFinish() {
        activity = Optional.empty()
    }

    override fun onDismiss(dialog: DialogInterface?) {
        freeResources()
        MyLog.v(logTag, "Progress dialog dismissed")
    }

    private fun freeResources() {
        if (!activity.isPresent() || MyAsyncTask.isUiThread()) {
            DialogFactory.dismissSafely(progressDialog)
        } else {
            try {
                activity.get().runOnUiThread { DialogFactory.dismissSafely(progressDialog) }
            } catch (e: Exception) {
                MyLog.d(logTag, "cleanOnFinish", e)
            }
        }
        progressDialog = null
        activity = Optional.empty()
    }

    override fun isCancelled(): Boolean {
        return isCancelled || isCompleted
    }

    override fun getLogTag(): String {
        return logTag
    }

    init {
        this.activity = Optional.ofNullable(activity)
        this.logTag = logTag
        iStartedAt = ProgressLogger.newStartingTime()
        defaultTitle = activity.getText(defaultTitleId)
        upgradingText = activity.getText(R.string.label_upgrading)
        cancelText = activity.getText(android.R.string.cancel)
        versionText =  MyContextHolder.myContextHolder.getVersionText(activity.getBaseContext())
    }
}
