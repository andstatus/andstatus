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

package org.andstatus.app.nosupport.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.util.MyLog;

/**
 * The class doesn't use android support libraries.
 * The need for it is due to {@link org.andstatus.app.context.MySettingsActivity}
 * @author yvolk@yurivolkov.com
 */
public class DialogFactory {
    public static final String OK_DIALOG_TAG = "ok";
    public static final String YES_CANCEL_DIALOG_TAG = "yes_cancel";
    public static final String YES_NO_DIALOG_TAG = "yes_no";

    public static final String DIALOG_TITLE_KEY = "title";
    public static final String DIALOG_MESSAGE_KEY = "message";

    private DialogFactory() {
    }

    public static Dialog newNoActionAlertDialog(Activity activity, int titleId, int summaryId) {
        return new AlertDialog.Builder(activity)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(titleId)
                .setMessage(summaryId)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int whichButton) {
                                dialog.dismiss();
                            }
                        }).create();
    }

    public static void dismissSafely (Dialog dlg) {
        if (dlg != null) {
            try {
                dlg.dismiss();
            } catch (Exception e) {
                MyLog.v("Dialog dismiss", e);
            }
        }
    }

    /** See http://stackoverflow.com/questions/10285047/showdialog-deprecated-whats-the-alternative */
    public static void showOkDialog(Fragment fragment, int titleId, int messageId, final int requestCode) {
        DialogFragment dialog = new OkDialogFragment();
        Bundle args = new Bundle();
        args.putCharSequence(DIALOG_TITLE_KEY, fragment.getText(titleId));
        args.putCharSequence(DIALOG_MESSAGE_KEY, fragment.getText(messageId));
        dialog.setArguments(args);
        dialog.setTargetFragment(fragment, requestCode);
        dialog.show(fragment.getFragmentManager(), OK_DIALOG_TAG);
    }

    public static class OkDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle args = getArguments();
            String title = args.getString(DIALOG_TITLE_KEY, "");
            String message = args.getString(DIALOG_MESSAGE_KEY, "");

            return new AlertDialog.Builder(getActivity())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, null);
                        }
                    })
                    .create();
        }
    }

    public static void showYesCancelDialog(Fragment fragment, int titleId, int messageId, final ActivityRequestCode requestCode) {
        DialogFragment dialog = new YesCancelDialog();
        Bundle args = new Bundle();
        args.putCharSequence(DIALOG_TITLE_KEY,
                fragment.getText(titleId));
        args.putCharSequence(
                DIALOG_MESSAGE_KEY,
                fragment.getText(messageId));
        dialog.setArguments(args);
        dialog.setTargetFragment(fragment, requestCode.id);
        dialog.show(fragment.getFragmentManager(), YES_CANCEL_DIALOG_TAG);
    }

    public static class YesCancelDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle args = getArguments();
            String title = args.getString(DIALOG_TITLE_KEY, "");
            String message = args.getString(DIALOG_MESSAGE_KEY, "");

            return newYesCancelDialog(this, title, message);
        }
    }

    public static Dialog newYesCancelDialog(final DialogFragment dialogFragment, String title, String message) {
        Dialog dlg;
        AlertDialog.Builder builder = new AlertDialog.Builder(dialogFragment.getActivity());
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton(dialogFragment.getText(android.R.string.yes),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                dialogFragment.getTargetFragment().onActivityResult(dialogFragment.getTargetRequestCode(), Activity.RESULT_OK, null);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialogFragment.getTargetFragment().onActivityResult(dialogFragment.getTargetRequestCode(), Activity.RESULT_CANCELED, null);
                    }
                });
        dlg = builder.create();
        return dlg;
    }
}