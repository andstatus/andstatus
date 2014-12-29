package org.andstatus.app.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;

import org.andstatus.app.ActivityRequestCode;

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
    public static void showOkDialog(Fragment activity, int titleId, int messageId, final int requestCode) {
        DialogFragment dialog = new DialogFragment() {
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
        };
        Bundle args = new Bundle();
        args.putCharSequence(DIALOG_TITLE_KEY, activity.getText(titleId));
        args.putCharSequence(DIALOG_MESSAGE_KEY, activity.getText(messageId));
        dialog.setArguments(args);
        dialog.setTargetFragment(activity, requestCode);
        dialog.show(activity.getFragmentManager(), OK_DIALOG_TAG);
    }

    public static void showYesCancelDialog(Fragment activity, int titleId, int messageId, final ActivityRequestCode requestCode) {
        DialogFragment dialog = new DialogFragment() {
            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState)
            {
                Bundle args = getArguments();
                String title = args.getString(DialogFactory.DIALOG_TITLE_KEY, "");
                String message = args.getString(DialogFactory.DIALOG_MESSAGE_KEY, "");

                return DialogFactory.newYesCancelDialog(this, title, message);
            }
        };
        Bundle args = new Bundle();
        args.putCharSequence(DialogFactory.DIALOG_TITLE_KEY, 
                activity.getText(titleId));
        args.putCharSequence(
                DialogFactory.DIALOG_MESSAGE_KEY,
                activity.getText(messageId));
        dialog.setArguments(args);
        dialog.setTargetFragment(activity, requestCode.id);
        dialog.show(activity.getFragmentManager(), DialogFactory.YES_CANCEL_DIALOG_TAG);
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
