/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.util

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import org.andstatus.app.ActivityRequestCode
import java.util.function.Consumer

object DialogFactory {
    private val YES_CANCEL_DIALOG_TAG: String = "yes_cancel"
    private val DIALOG_TITLE_KEY: String = "title"
    private val DIALOG_MESSAGE_KEY: String = "message"
    fun showOkAlertDialog(method: Any, context: Context, @StringRes titleId: Int, @StringRes summaryId: Int): Dialog {
        return showOkAlertDialog(method, context, titleId, context.getText(summaryId))
    }

    fun showOkAlertDialog(method: Any, context: Context, @StringRes titleId: Int, summary: CharSequence?): Dialog {
        val dialog = AlertDialog.Builder(context)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(titleId)
                .setMessage(I18n.trimTextAt(summary, 1000))
                .setPositiveButton(android.R.string.ok) { dialog1: DialogInterface, whichButton: Int -> dialog1.dismiss() }
                .create()
        if (!Activity::class.java.isAssignableFrom(context.javaClass)) {
            // See http://stackoverflow.com/questions/32224452/android-unable-to-add-window-permission-denied-for-this-window-type
            // and maybe http://stackoverflow.com/questions/17059545/show-dialog-alert-from-a-non-activity-class-in-android
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_TOAST)
        }
        try {
            dialog.show()
        } catch (e: Exception) {
            try {
                MyLog.w(method, "Couldn't open alert dialog with the text: $summary", e)
                Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                MyLog.w(method, "Couldn't send toast with the text: $summary", e2)
            }
        }
        return dialog
    }

    fun dismissSafely(dlg: DialogInterface?) {
        if (dlg != null) {
            try {
                dlg.dismiss()
            } catch (e: Exception) {
                MyLog.v("Dialog dismiss", e)
            }
        }
    }

    fun showOkCancelDialog(fragment: Fragment?, titleId: Int, messageId: Int, requestCode: ActivityRequestCode) {
        if (fragment == null) return

        val dialog: DialogFragment = OkCancelDialogFragment()
        val args = Bundle()
        args.putCharSequence(DIALOG_TITLE_KEY, fragment.getText(titleId))
        args.putCharSequence(DIALOG_MESSAGE_KEY, fragment.getText(messageId))
        dialog.arguments = args
        dialog.setTargetFragment(fragment, requestCode.id)
        dialog.show(fragment.parentFragmentManager, YES_CANCEL_DIALOG_TAG)
    }

    fun showOkCancelDialog(context: Context, title: CharSequence, message: CharSequence,
                           okCancelConsumer: Consumer<Boolean>) {
        val theBox = AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(context.getText(android.R.string.ok)) { dialog: DialogInterface?, which: Int ->
                    okCancelConsumer.accept(true)
                    dismissSafely(dialog)
                }
                .setNegativeButton(context.getText(android.R.string.cancel)) { dialog: DialogInterface?, which: Int ->
                    okCancelConsumer.accept(false)
                    dismissSafely(dialog)
                }
                .create()
        theBox.show()
    }

    fun showTextInputBox(context: Context, title: String, message: String, textConsumer: Consumer<String>,
                         initialValue: String?) {
        val input = EditText(context)
        if (!initialValue.isNullOrEmpty()) {
            input.setText(initialValue)
        }
        val theBox = AlertDialog.Builder(context)
                .setTitle(title)
                .setView(input)
                .setMessage(message)
                .setPositiveButton(context.getText(android.R.string.ok)) { dialog: DialogInterface?, which: Int ->
                    textConsumer.accept(input.text.toString())
                    dismissSafely(dialog)
                }
                .setNegativeButton(context.getText(android.R.string.cancel)) { dialog: DialogInterface?, which: Int -> dismissSafely(dialog) }
                .create()
        theBox.show()
    }

    class OkCancelDialogFragment : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val args = arguments ?: Bundle()
            val title = args.getString(DIALOG_TITLE_KEY, "")
            val message = args.getString(DIALOG_MESSAGE_KEY, "")
            val builder = AlertDialog.Builder(activity)
            builder.setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(getText(android.R.string.ok)) { dialog: DialogInterface?, id: Int ->
                        targetFragment?.onActivityResult(
                                targetRequestCode, Activity.RESULT_OK, null)
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface?, id: Int ->
                        targetFragment?.onActivityResult(
                                targetRequestCode, Activity.RESULT_CANCELED, null)
                    }
            return builder.create()
        }
    }
}