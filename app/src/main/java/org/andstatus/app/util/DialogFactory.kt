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

package org.andstatus.app.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import org.andstatus.app.ActivityRequestCode;

import java.util.function.Consumer;

public class DialogFactory {
    private static final String YES_CANCEL_DIALOG_TAG = "yes_cancel";

    private static final String DIALOG_TITLE_KEY = "title";
    private static final String DIALOG_MESSAGE_KEY = "message";

    private DialogFactory() {
    }

    public static Dialog showOkAlertDialog(Object method, Context context, @StringRes int titleId, @StringRes int summaryId) {
        return showOkAlertDialog(method, context, titleId, context.getText(summaryId));
    }

    public static Dialog showOkAlertDialog(Object method, Context context, @StringRes int titleId, CharSequence summary) {
        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(titleId)
                .setMessage(I18n.trimTextAt(summary, 1000))
                .setPositiveButton(android.R.string.ok, (dialog1, whichButton) -> dialog1.dismiss())
                .create();
        if (!Activity.class.isAssignableFrom(context.getClass())) {
            // See http://stackoverflow.com/questions/32224452/android-unable-to-add-window-permission-denied-for-this-window-type
            // and maybe http://stackoverflow.com/questions/17059545/show-dialog-alert-from-a-non-activity-class-in-android
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_TOAST);
        }
        try {
            dialog.show();
        } catch (Exception e) {
            try {
                MyLog.w(method, "Couldn't open alert dialog with the text: " + summary, e);
                Toast.makeText(context, summary, Toast.LENGTH_LONG).show();
            } catch (Exception e2) {
                MyLog.w(method, "Couldn't send toast with the text: " + summary, e2);
            }
        }
        return dialog;
    }

    public static void dismissSafely (DialogInterface dlg) {
        if (dlg != null) {
            try {
                dlg.dismiss();
            } catch (Exception e) { 
                MyLog.v("Dialog dismiss", e);  
            }
        }
    }

    public static void showOkCancelDialog(Fragment fragment, int titleId, int messageId, final ActivityRequestCode requestCode) {
        DialogFragment dialog = new OkCancelDialogFragment();
        Bundle args = new Bundle();
        args.putCharSequence(DIALOG_TITLE_KEY, fragment.getText(titleId));
        args.putCharSequence(DIALOG_MESSAGE_KEY, fragment.getText(messageId));
        dialog.setArguments(args);
        dialog.setTargetFragment(fragment, requestCode.id);
        dialog.show(fragment.getFragmentManager(), YES_CANCEL_DIALOG_TAG);
    }

    public static class OkCancelDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle args = getArguments();
            String title = args.getString(DIALOG_TITLE_KEY, "");
            String message = args.getString(DIALOG_MESSAGE_KEY, "");

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton(getText(android.R.string.ok), (dialog, id) ->
                        getTargetFragment().onActivityResult(
                            getTargetRequestCode(), Activity.RESULT_OK, null))
                .setNegativeButton(android.R.string.cancel, (dialog, id) ->
                        getTargetFragment().onActivityResult(
                            getTargetRequestCode(), Activity.RESULT_CANCELED, null));
            return builder.create();
        }
    }

    public static void showOkCancelDialog(Context context, CharSequence title, CharSequence message,
                                          Consumer<Boolean> okCancelConsumer) {
        AlertDialog theBox = new AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(context.getText(android.R.string.ok), ( dialog, which) -> {
                okCancelConsumer.accept(true);
                dismissSafely(dialog);
            })
            .setNegativeButton(context.getText(android.R.string.cancel), (dialog, which) -> {
                okCancelConsumer.accept(false);
                dismissSafely(dialog);
            })
            .create();

        theBox.show();
    }

    public static void showTextInputBox(Context context, String title, String message, Consumer<String> textConsumer,
                                        String initialValue) {
        EditText input = new EditText(context);
        if (StringUtil.nonEmpty(initialValue)) {
            input.setText(initialValue);
        }

        AlertDialog theBox = new AlertDialog.Builder(context)
            .setTitle(title)
            .setView(input)
            .setMessage(message)
            .setPositiveButton(context.getText(android.R.string.ok), ( dialog, which) -> {
                    textConsumer.accept(input.getText().toString());
                    dismissSafely(dialog);
                })
            .setNegativeButton(context.getText(android.R.string.cancel), (dialog, which) -> dismissSafely(dialog))
            .create();

        theBox.show();
    }
}
