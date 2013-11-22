package org.andstatus.app.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;

public class DialogFactory {

    public static Dialog newNoActionAlertDialog(Activity activity, int titleId, int summaryId) {
        Dialog dlg;
        dlg = new AlertDialog.Builder(activity)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(titleId)
                .setMessage(summaryId)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface Dialog,
                                    int whichButton) {
                            }
                        }).create();
        return dlg;
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
}
