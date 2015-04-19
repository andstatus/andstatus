/* 
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.context;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.R;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.TimelineSearchSuggestionsProvider;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceState;
import org.andstatus.app.util.DialogFactory;
import org.andstatus.app.util.MyLog;

import java.io.File;
import java.io.IOException;

public class StorageSwitch {

    static final Object MOVE_LOCK = new Object();
    /**
     * This semaphore helps to avoid ripple effect: changes in MyAccount cause
     * changes in this activity ...
     */
    @GuardedBy("moveLock")
    private static volatile boolean mDataBeingMoved = false;

    private final MySettingsFragment parentActivity;
    private final Context mContext;
    private boolean mUseExternalStorageNew = false;

    public StorageSwitch(MySettingsFragment myPreferenceFragment) {
        this.parentActivity = myPreferenceFragment;
        this.mContext = myPreferenceFragment.getActivity();
    }

    public void showSwitchStorageDialog(final ActivityRequestCode requestCode, boolean useExternalStorageNew) {
        this.mUseExternalStorageNew = useExternalStorageNew;
        DialogFactory.showYesCancelDialog(parentActivity, R.string.dialog_title_external_storage, 
                useExternalStorageNew ? R.string.summary_preference_storage_external_on
                        : R.string.summary_preference_storage_external_off, 
                        requestCode);
    }
    
    void move() {
        MyServiceManager.setServiceUnavailable();
        if (MyServiceManager.getServiceState() == MyServiceState.STOPPED) {
            new MoveDataBetweenStoragesTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            MyServiceManager.stopService();
            Toast.makeText(parentActivity.getActivity(),
                    mContext.getText(R.string.system_is_busy_try_later),
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean checkAndSetDataBeingMoved() {
        synchronized (MOVE_LOCK) {
            if (mDataBeingMoved) {
                return false;
            }
            mDataBeingMoved = true;
            return true;
        }
    }

    boolean isDataBeingMoved() {
        synchronized (MOVE_LOCK) {
            return mDataBeingMoved;
        }
    }

    private static class TaskResult {
        boolean success = false;
        boolean moved = false;
        StringBuilder messageBuilder = new StringBuilder();
        
        String getMessage() {
            return messageBuilder.toString();
        }
    }

    /**
     * Move Data to/from External Storage
     * 
     * @author yvolk@yurivolkov.com
     */
    private class MoveDataBetweenStoragesTask extends AsyncTask<Void, Void, TaskResult> {
        private ProgressDialog dlg;

        @Override
        protected void onPreExecute() {
            // indeterminate duration, not cancelable
            dlg = ProgressDialog.show(mContext,
                    mContext.getText(R.string.dialog_title_external_storage),
                    mContext.getText(R.string.dialog_summary_external_storage),
                    true,
                    false);
        }

        @Override
        protected TaskResult doInBackground(Void... params) {
            TaskResult result = new TaskResult();
            if (!checkAndSetDataBeingMoved()) {
                return result;
            }
            try {
                moveAll(result);
            } finally {
                synchronized (MOVE_LOCK) {
                    mDataBeingMoved = false;
                }
            }
            result.messageBuilder.insert(0, " Move " + (result.success ? "succeeded" : "failed"));
            MyLog.v(this, result.getMessage());
            return result;
        }

        private void moveAll(TaskResult result) {
            boolean useExternalStorageOld = MyPreferences.isStorageExternal(null);
            if (mUseExternalStorageNew
                    && !MyPreferences.isWritableExternalStorageAvailable(result.messageBuilder)) {
                mUseExternalStorageNew = false;
            }

            MyLog.d(this, "About to move data from " + useExternalStorageOld + " to "
                    + mUseExternalStorageNew);

            if (mUseExternalStorageNew == useExternalStorageOld) {
                result.messageBuilder.append(" Nothing to do.");
                result.success = true;
                return;
            }
            try {
                result.success = moveDatabase(mUseExternalStorageNew, result.messageBuilder, MyDatabase.DATABASE_NAME);
                if (result.success) {
                    result.moved = true;
                    moveDatabase(mUseExternalStorageNew, result.messageBuilder, 
                            TimelineSearchSuggestionsProvider.DATABASE_NAME);
                    moveDownloads(mUseExternalStorageNew, result.messageBuilder);
                }
            } finally {
                if (result.success) {
                    saveNewSettings(mUseExternalStorageNew, result.messageBuilder);
                }
            }
        }

        private boolean moveDatabase(boolean useExternalStorageNew, StringBuilder messageToAppend, String databaseName) {
            final String method = "moveDatabase";
            boolean succeeded = false;
            boolean done = false;
            /**
             * Did we actually copied database?
             */
            boolean copied = false;
            File dbFileOld = null;
            File dbFileNew = null;
            try {

                if (!done) {
                    dbFileOld = MyContextHolder.get().context().getDatabasePath(
                            databaseName);
                    dbFileNew = MyPreferences.getDatabasePath(
                            databaseName, useExternalStorageNew);
                    if (dbFileOld == null) {
                        messageToAppend.append(" No old database " + databaseName);
                        done = true;
                    }
                }
                if (!done) {
                    if (dbFileNew == null) {
                        messageToAppend.append(" No new database " + databaseName);
                        done = true;
                    } else {
                        if (!dbFileOld.exists()) {
                            messageToAppend.append(" No old database " + databaseName);
                            done = true;
                            succeeded = true;
                        } else if (dbFileNew.exists()) {
                            messageToAppend.insert(0, " Database already exists " + databaseName);
                            if (!dbFileNew.delete()) {
                                messageToAppend
                                        .insert(0, " Couldn't delete already existed files. ");
                                done = true;
                            }
                        }
                    }
                }
                if (!done) {
                    if (MyLog.isVerboseEnabled()) {
                        MyLog.v(this, method + " from: " + dbFileOld.getPath());
                        MyLog.v(this, method + " to: " + dbFileNew.getPath());
                    }
                    try {
                        if (copyFile(dbFileOld, dbFileNew)) {
                            copied = true;
                            succeeded = true;
                        }
                    } catch (Exception e) {
                        MyLog.v(this, "Copy database " + databaseName, e);
                        messageToAppend.insert(0, " Couldn't copy database " 
                                + databaseName + ": " + e.getMessage()  + ". ");
                    }
                    done = true;
                }
            } catch (Exception e) {
                MyLog.v(this, e);
                messageToAppend.append(method + " error: " + e.getMessage() + ". ");
                succeeded = false;
            } finally {
                // Delete unnecessary files
                try {
                    if (succeeded) {
                        if ( copied && dbFileOld != null
                                && dbFileOld.exists()
                                && !dbFileOld.delete()) {
                            messageToAppend.append(method + " couldn't delete old files. ");
                        }
                    } else {
                        if (dbFileNew != null
                                && dbFileNew.exists()
                                && !dbFileNew.delete()) {
                            messageToAppend.append(method + " couldn't delete new files. ");
                        }
                    }
                } catch (Exception e) {
                    MyLog.v(this, method + " Delete old file", e);
                    messageToAppend.append(method + " couldn't delete old files. " + e.getMessage()
                            + ". ");
                }
            }
            MyLog.d(this, method + "; " + databaseName + " " + (succeeded ? "succeeded" : "failed"));
            return succeeded;
        }

        /**
         * Based on <a href="http://www.screaming-penguin.com/node/7749">Backing
         * up your Android SQLite database to the SD card</a>
         * 
         * @param src
         * @param dst
         * @return true if success
         * @throws IOException
         */
        boolean copyFile(File src, File dst) throws IOException {
            long sizeIn = -1;
            long sizeCopied = 0;
            boolean ok = false;
            if (src != null && src.exists()) {
                sizeIn = src.length();
                if (!dst.createNewFile()) {
                    MyLog.e(this, "New file was not created: '" + dst.getCanonicalPath() + "'");
                } else if (src.getCanonicalPath().compareTo(dst.getCanonicalPath()) == 0) {
                    MyLog.d(this, "Cannot copy to itself: '" + src.getCanonicalPath() + "'");
                } else {
                    java.nio.channels.FileChannel inChannel = null;
                    java.nio.channels.FileChannel outChannel = null;
                    try {
                        inChannel = new java.io.FileInputStream(src).getChannel();
                        outChannel = new java.io.FileOutputStream(dst)
                                .getChannel();
                        sizeCopied = inChannel.transferTo(0, inChannel.size(), outChannel);
                        ok = (sizeIn == sizeCopied);
                    } finally {
                        DbUtils.closeSilently(inChannel);
                        DbUtils.closeSilently(outChannel);
                    }

                }
            }
            MyLog.d(this, "Copied " + sizeCopied + " bytes of " + sizeIn);
            return ok;
        }


        private void moveDownloads(boolean useExternalStorageNew, StringBuilder messageToAppend) {
            String method = "moveDownloads";
            boolean succeeded = false;
            boolean done = false;
            boolean didWeCopyAnything = false;
            File dirOld = null;
            File dirNew = null;
            try {

                if (!done) {
                    dirOld = MyPreferences.getDataFilesDir(MyPreferences.DIRECTORY_DOWNLOADS, null);
                    dirNew = MyPreferences.getDataFilesDir(MyPreferences.DIRECTORY_DOWNLOADS,
                            useExternalStorageNew);
                    if (dirOld == null || !dirOld.exists()) {
                        messageToAppend.append(" No old avatars. ");
                        done = true;
                        succeeded = true;
                    }
                    if (dirNew == null) {
                        messageToAppend.append(" No directory for new avatars?! ");
                        done = true;
                    }
                }
                if (!done) {
                    if (MyLog.isVerboseEnabled()) {
                        MyLog.v(this, method + " from: " + dirOld.getPath());
                        MyLog.v(this, method + " to: " + dirNew.getPath());
                    }
                    String filename = "";
                    try {
                        for (File fileOld : dirOld.listFiles()) {
                            if (fileOld.isFile()) {
                                filename = fileOld.getName();
                                File fileNew = new File(dirNew, filename);
                                if (copyFile(fileOld, fileNew)) {
                                    didWeCopyAnything = true;
                                }
                            }
                        }
                        succeeded = true;
                    } catch (Exception e) {
                        String logMsg = method + " couldn't copy'" + filename + "'";
                        MyLog.v(this, logMsg, e);
                        messageToAppend.insert(0, " " + logMsg + ": " + e.getMessage());
                    }
                    done = true;
                }
            } catch (Exception e) {
                MyLog.v(this, e);
                messageToAppend.append(method + " error: " + e.getMessage() + ". ");
                succeeded = false;
            } finally {
                // Delete unnecessary files
                try {
                    if (succeeded) {
                        if (didWeCopyAnything) {
                            for (File fileOld : dirOld.listFiles()) {
                                if (fileOld.isFile() && !fileOld.delete()) {
                                    messageToAppend.append(method + " couldn't delete old file "
                                            + fileOld.getName());
                                }
                            }
                        }
                    } else {
                        if (dirNew != null && dirNew.exists()) {
                            for (File fileNew : dirNew.listFiles()) {
                                if (fileNew.isFile() && !fileNew.delete()) {
                                    messageToAppend.append(method + " couldn't delete new file "
                                            + fileNew.getName());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    String logMsg = method + " deleting unnecessary files";
                    MyLog.v(this, logMsg, e);
                    messageToAppend.append(logMsg + ": " + e.getMessage());
                }
            }
            MyLog.d(this, method + " " + (succeeded ? "succeeded" : "failed"));
        }

        private void saveNewSettings(boolean useExternalStorageNew, StringBuilder messageToAppend) {
            try {
                MyPreferences
                        .getDefaultSharedPreferences()
                        .edit()
                        .putBoolean(MyPreferences.KEY_USE_EXTERNAL_STORAGE,
                                useExternalStorageNew).commit();
                MyPreferences.onPreferencesChanged();
            } catch (Exception e) {
                MyLog.v(this, "Save new settings", e);
                messageToAppend.append("Couldn't save new settings. " + e.getMessage());
            }
        }
        
        // This is in the UI thread, so we can mess with the UI
        @Override
        protected void onPostExecute(TaskResult result) {
            DialogFactory.dismissSafely(dlg);
            if (result == null) {
                MyLog.e(this, "Result is Null");
                return;
            }
            MyLog.d(this, this.getClass().getSimpleName() + " ended, "
                    + (result.success ? (result.moved ? "moved" : "didn't move") : "failed"));

            if (!result.success) {
                result.messageBuilder.insert(0, mContext.getString(R.string.error) + ": ");
            }
            Toast.makeText(mContext, result.getMessage(), Toast.LENGTH_LONG).show();
            parentActivity.showUseExternalStorage();
        }

        @Override
        protected void onCancelled(TaskResult result) {
            DialogFactory.dismissSafely(dlg);
        }
    }
}
