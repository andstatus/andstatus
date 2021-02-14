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

import org.andstatus.app.FirstActivity;
import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextState;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.DialogFactory;
import org.andstatus.app.util.MyLog;

import java.util.Optional;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * @author yvolk@yurivolkov.com
 * Only one "progressing" process is allowed. all previous are being marked as cancelled
 */
public class DefaultProgressListener implements ProgressLogger.ProgressListener, DialogInterface.OnDismissListener {
    private volatile Optional<MyActivity> activity;
    private final CharSequence defaultTitle;
    private final String logTag;
    private final long iStartedAt;
    private final CharSequence upgradingText;
    private final CharSequence cancelText;
    private final CharSequence versionText;
    private volatile boolean isCancelable = false;
    private volatile boolean isCancelled = false;
    private volatile boolean isCompleted = false;
    private volatile ProgressDialog progressDialog = null;

    public DefaultProgressListener(MyActivity activity, int defaultTitleId, String logTag) {
        this.activity = Optional.ofNullable(activity);
        this.logTag = logTag;
        iStartedAt = ProgressLogger.newStartingTime();
        this.defaultTitle = activity.getText(defaultTitleId);
        this.upgradingText = activity.getText(R.string.label_upgrading);
        this.cancelText = activity.getText(android.R.string.cancel);
        this.versionText = myContextHolder.getVersionText(activity.getBaseContext());
    }

    @Override
    public void setCancelable(boolean isCancelable) {
        this.isCancelable = isCancelable;
    }

    @Override
    public void onProgressMessage(CharSequence messageIn) {
        final String message = formatMessage(messageIn);
        showMessage(message);
        if (!isCancelled() && ProgressLogger.startedAt.get() != iStartedAt) {
            showMessage("New progress started, cancelling this...");
            cancel();
        }
    }

    private void showMessage(String message) {
        if (!isCancelled() && activity.isPresent()) {
            activity.ifPresent(activity -> showMessage(activity, message));
        } else {
            MyLog.i(logTag, message);
        }
    }

    private void showMessage(MyActivity activity, String message) {
        final String method = "showMessage";
        try {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    boolean shown = false;
                    if (activity.isMyResumed()) {
                        try {
                            if (progressDialog == null) {
                                progressDialog = new ProgressDialog(activity, ProgressDialog.STYLE_SPINNER);
                                progressDialog.setOnDismissListener(DefaultProgressListener.this);
                                progressDialog.setTitle(myContextHolder.getNow().state() == MyContextState.UPGRADING
                                        ? upgradingText
                                        : defaultTitle);
                                progressDialog.setMessage(message);
                                if (isCancelable && !isCancelled()) {
                                    progressDialog.setCancelable(false);
                                    progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                                            cancelText,
                                            (dialog, which) -> DefaultProgressListener.this.cancel());
                                }
                                progressDialog.show();
                            } else {
                                progressDialog.setMessage(message);
                            }
                            shown = true;
                        } catch (Exception e) {
                            MyLog.d(logTag, method + " '" + message + "'", e);
                        }
                    }
                    if (!shown) {
                        showToast(message);
                    }
                }
            });
        } catch (Exception e) {
            MyLog.d(logTag, method + " '" + message + "'", e);
        }
    }

    private String formatMessage(CharSequence message) {
        return (isCancelled() ? cancelText + ": " : "") + message;
    }

    private void showToast(CharSequence message) {
        try {
            Toast.makeText(myContextHolder.getNow().context(),
                defaultTitle + "\n" +
                    versionText +
                    (myContextHolder.getNow().state() == MyContextState.UPGRADING
                        ? "\n" + upgradingText
                        : "") +
                    "\n\n" + message,
                Toast.LENGTH_LONG)
            .show();
        } catch (Exception e2) {
            MyLog.w(logTag, "Couldn't send toast with the text: " + message, e2);
        }
    }

    @Override
    public void onComplete(final boolean success) {
        isCompleted = true;
        activity.ifPresent(activity -> {
            try {
                activity.runOnUiThread(() -> {
                    freeResources();
                    FirstActivity.goHome(activity);
                    activity.finish();
                });
            } catch (Exception e) {
                MyLog.d(logTag, "onComplete " + success, e);
            }
        });
    }

    @Override
    public void cancel() {
        isCancelled = true;
    }

    @Override
    public void onActivityFinish() {
        activity = Optional.empty();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        freeResources();
        MyLog.v(logTag, "Progress dialog dismissed");
    }

    private void freeResources() {
        if (!activity.isPresent() || MyAsyncTask.isUiThread()) {
            DialogFactory.dismissSafely(progressDialog);
        } else {
            try {
                activity.get().runOnUiThread(() ->
                    DialogFactory.dismissSafely(progressDialog)
                );
            } catch (Exception e) {
                MyLog.d(logTag, "cleanOnFinish", e);
            }
        }
        progressDialog = null;
        activity = Optional.empty();
    }

    @Override
    public boolean isCancelled() {
        return isCancelled || isCompleted;
    }

    @Override
    public String getLogTag() {
        return logTag;
    }
}