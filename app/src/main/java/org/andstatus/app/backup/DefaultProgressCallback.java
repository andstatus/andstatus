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

package org.andstatus.app.backup;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.widget.Toast;

import androidx.annotation.StringRes;

import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyContextState;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.util.DialogFactory;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class DefaultProgressCallback implements ProgressLogger.ProgressCallback, DialogInterface.OnDismissListener {
    private final MyActivity activity;
    @StringRes
    private final int defaultTitleId;
    private volatile ProgressDialog progressDialog = null;

    public DefaultProgressCallback(MyActivity activity, int defaultTitleId) {
        this.activity = activity;
        this.defaultTitleId = defaultTitleId;
    }

    @Override
    public void onProgressMessage(CharSequence message) {
        final String method = "onProgressMessage";
        try {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    boolean shown = false;
                    if (activity.isMyResumed()) {
                        try {
                            if (progressDialog == null) {
                                progressDialog = new ProgressDialog(activity, ProgressDialog.STYLE_SPINNER);
                                progressDialog.setOnDismissListener(DefaultProgressCallback.this);
                                progressDialog.setTitle(MyContextHolder.get().state() == MyContextState.UPGRADING ?
                                        R.string.label_upgrading : defaultTitleId);
                                progressDialog.setMessage(message);
                                progressDialog.show();
                            } else {
                                progressDialog.setMessage(message);
                            }
                            shown = true;
                        } catch (Exception e) {
                            MyLog.d(this, method + " '" + message + "'", e);
                        }
                    }
                    if (!shown) {
                        try {
                            Toast.makeText(MyContextHolder.get().context(),
                                    activity.getText(defaultTitleId) + "\n"
                                            + MyContextHolder.getVersionText(activity.getBaseContext())
                                            + (MyContextHolder.get().state() == MyContextState.UPGRADING ?
                                            "\n" + activity.getText(R.string.label_upgrading) : "")
                                            + "\n\n" + message,
                                    Toast.LENGTH_LONG).show();
                        } catch (Exception e2) {
                            MyLog.e(method, "Couldn't send toast with the text: " + method, e2);
                        }
                    }
                }
            });
        } catch (Exception e) {
            MyLog.d(activity, method + " '" + message + "'", e);
        }
    }

    @Override
    public void onComplete(final boolean success) {
        try {
            activity.runOnUiThread(() -> {
                DialogFactory.dismissSafely(progressDialog);
                TimelineActivity.goHome(activity);
                activity.finish();
            });
        } catch (Exception e) {
            MyLog.d(this, "onComplete " + success, e);
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (MyAsyncTask.isUiThread()) {
            DialogFactory.dismissSafely(progressDialog);
        } else {
            try {
                activity.runOnUiThread(() ->
                    DialogFactory.dismissSafely(progressDialog)
                );
            } catch (Exception e) {
                MyLog.d(this, "cleanOnFinish", e);
            }
        }
        progressDialog = null;
        MyLog.v(activity, "Progress dialog dismissed");
    }
}