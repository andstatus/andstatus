/* 
 * Copyright (C) 2010-2013 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2008 Torgny Bjers
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

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.RingtonePreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.widget.Toast;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.HelpActivity;
import org.andstatus.app.MyActionBar;
import org.andstatus.app.MyActionBarContainer;
import org.andstatus.app.R;
import org.andstatus.app.TimelineActivity;
import org.andstatus.app.account.AccountSettingsActivity;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.origin.OriginList;
import org.andstatus.app.service.MyService;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.DialogFactory;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Application settings
 * 
 */
public class MyPreferenceActivity extends PreferenceActivity implements
        OnSharedPreferenceChangeListener, OnPreferenceChangeListener, MyActionBarContainer {

    private static final String KEY_ADD_NEW_ACCOUNT = "add_new_account";
    private static final String KEY_MANAGE_EXISTING_ACCOUNTS = "manage_existing_accounts";

    private static final String TAG = MyPreferenceActivity.class.getSimpleName();

    /**
     * This is single list of (in fact, enums...) of Message/Dialog IDs
     */
    public static final int MSG_NONE = 1;

    public static final int MSG_SERVICE_UNAVAILABLE_ERROR = 4;

    public static final int MSG_CONNECTION_EXCEPTION = 5;

    public static final int MSG_SOCKET_TIMEOUT_EXCEPTION = 6;

    public static final int DLG_MOVE_DATA_BETWEEN_STORAGES = 8;
    
    
    // End Of the list ----------------------------------------

    private CheckBoxPreference mUseExternalStorage;
    private boolean useExternalStorageIsBusy = false;

    private RingtonePreference mNotificationRingtone;

    private boolean onSharedPreferenceChangedIsBusy = false;

    private boolean startTimelineActivity = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MyActionBar actionBar = new MyActionBar(this);
        super.onCreate(savedInstanceState);

        MyContextHolder.initialize(this, this);
        addPreferencesFromResource(R.xml.preferences);
        
        mNotificationRingtone = (RingtonePreference) findPreference(MyPreferences.KEY_RINGTONE_PREFERENCE);
        mUseExternalStorage = (CheckBoxPreference) getPreferenceScreen().findPreference(
                MyPreferences.KEY_USE_EXTERNAL_STORAGE_NEW);
        
        Preference myPref = findPreference(KEY_MANAGE_EXISTING_ACCOUNTS);
        myPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                AccountSettingsActivity.startManageExistingAccounts(MyPreferenceActivity.this);
                return false;
            }
        });
        
        myPref = findPreference(KEY_ADD_NEW_ACCOUNT);
        myPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                AccountSettingsActivity.startAddNewAccount(MyPreferenceActivity.this);
                return false;
            }
        });
        
        myPref = findPreference("manage_origin_systems");
        myPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(MyPreferenceActivity.this, OriginList.class);
                startActivity(intent);
                return false;
            }
        });
        
        myPref = findPreference("about_application");
        myPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(MyPreferenceActivity.this, HelpActivity.class);
                startActivity(intent);
                return false;
            }
        });
        
        myPref = findPreference("change_log");
        myPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(MyPreferenceActivity.this, HelpActivity.class);
                intent.putExtra(HelpActivity.EXTRA_HELP_PAGE_INDEX, HelpActivity.PAGE_INDEX_CHANGELOG);
                startActivity(intent);
                return false;
            }
        });
        
        actionBar.attach();
        actionBar.setTitle(R.string.settings_activity_title);
    }

    @Override
    protected void onResume() {
        super.onResume();

        MyContextHolder.initialize(this, this);
        
        MyServiceManager.setServiceUnavailable();
        MyServiceManager.stopService();

        showAllPreferences();
        MyPreferences.getDefaultSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MyPreferences.getDefaultSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

        if (startTimelineActivity) {
            MyContextHolder.release();
            // On modifying activity back stack see http://stackoverflow.com/questions/11366700/modification-of-the-back-stack-in-android
            Intent i = new Intent(this, TimelineActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        }
    }

    /**
     * Show values of all preferences in the "summaries".
     * @see <a href="http://stackoverflow.com/questions/531427/how-do-i-display-the-current-value-of-an-android-preference-in-the-preference-sum"> 
       How do I display the current value of an Android Preference 
       in the Preference summary?</a>
     */
    protected void showAllPreferences() {
        showFrequency();
        showHistorySize();
        showHistoryTime();
        showRingtone();
        showMinLogLevel();
        showUseExternalStorage();
        
        Preference myPref = findPreference(KEY_MANAGE_EXISTING_ACCOUNTS);
        CharSequence summary;
        if (MyContextHolder.get().persistentAccounts().isEmpty()) {
            summary = getText(R.string.summary_preference_accounts_absent);
        } else {
            summary = getText(R.string.summary_preference_accounts_present) + ": " + MyContextHolder.get().persistentAccounts().size();
        }
        myPref.setSummary(summary);
        
    }
    
    protected void showHistorySize() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_HISTORY_SIZE, R.array.history_size_values, R.array.history_size_display, R.string.summary_preference_history_size);
    }

    protected void showHistoryTime() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_HISTORY_TIME, R.array.history_time_values, R.array.history_time_display, R.string.summary_preference_history_time);
    }

    protected void showFrequency() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, R.array.fetch_frequency_values, R.array.fetch_frequency_display, R.string.summary_preference_frequency);
    }

    protected void showMinLogLevel() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_MIN_LOG_LEVEL, R.array.log_level_value, R.array.log_level_display, R.string.summary_preference_min_log_level);
    }
    
    protected void showRingtone() {
        String ringtoneString = MyPreferences.getDefaultSharedPreferences().getString(
                MyPreferences.KEY_RINGTONE_PREFERENCE, null);
        Uri uri = Uri.EMPTY;
        Ringtone rt = null;
        if (ringtoneString == null) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        } else if (!TextUtils.isEmpty(ringtoneString)) {
            uri = Uri.parse(ringtoneString);
        }
        MyLog.v(this, "Ringtone URI: " + uri);
        if (uri != null && uri != Uri.EMPTY) {
            rt = RingtoneManager.getRingtone(this, uri);
        }
        if (rt != null) {
            mNotificationRingtone.setSummary(rt.getTitle(this));
        } else {
            mNotificationRingtone.setSummary(R.string.summary_preference_no_ringtone);
        }
    }
    
    protected void showUseExternalStorage() {
        boolean use = MyPreferences.isStorageExternal(null);
        if (use != mUseExternalStorage.isChecked()) {
            MyPreferences.getDefaultSharedPreferences().edit().putBoolean(MyPreferences.KEY_USE_EXTERNAL_STORAGE_NEW, use).commit();
            mUseExternalStorage.setChecked(use);
        }
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) && !mUseExternalStorage.isChecked()) {
            mUseExternalStorage.setEnabled(false);
        }
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (dataIsBeingMoved) {
            return;
        }
        if (onSharedPreferenceChangedIsBusy || !MyContextHolder.get().initialized()) {
            return;
        }
        onSharedPreferenceChangedIsBusy = true;

        try {
            MyLog.logSharedPreferencesValue(this, sharedPreferences, key);
            MyPreferences.onPreferencesChanged();
            
            if (key.equals(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS)) {
                MyContextHolder.get().persistentAccounts().onMyPreferencesChanged(MyContextHolder.get());
                showFrequency();
            }
            if (key.equals(MyPreferences.KEY_RINGTONE_PREFERENCE)) {
                showRingtone();
            }
            if (key.equals(MyPreferences.KEY_HISTORY_SIZE)) {
                showHistorySize();
            }
            if (key.equals(MyPreferences.KEY_HISTORY_TIME)) {
                showHistoryTime();
            }
            if (key.equals(MyPreferences.KEY_MIN_LOG_LEVEL)) {
                showMinLogLevel();
            }
            if (key.equals(MyPreferences.KEY_USE_EXTERNAL_STORAGE_NEW)
                    && !useExternalStorageIsBusy) {
                useExternalStorageIsBusy = true;
                showDialog(DLG_MOVE_DATA_BETWEEN_STORAGES);
            }
        } finally {
            onSharedPreferenceChangedIsBusy = false;
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        int titleId = 0;
        int summaryId = 0;
        Dialog dlg = null;

        switch (id) {
            case DLG_MOVE_DATA_BETWEEN_STORAGES:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getText(R.string.dialog_title_external_storage))
                    .setMessage("")
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            MyPreferenceActivity.this.showUseExternalStorage();
                            MyPreferenceActivity.this.useExternalStorageIsBusy = false;
                        }
                    })
                    .setPositiveButton(getText(android.R.string.yes), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            MyServiceManager.setServiceUnavailable();
                            if (MyServiceManager.getServiceState() == MyService.ServiceState.STOPPED) {
                                new MoveDataBetweenStoragesTask().execute();
                            } else {
                                MyServiceManager.stopService();
                                dialog.cancel();
                                Toast.makeText(MyPreferenceActivity.this, getText(R.string.system_is_busy_try_later), Toast.LENGTH_LONG).show();
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                        }
                    });
                dlg = builder.create();
                break;
            default:
                switch (id) {
                    case MSG_SERVICE_UNAVAILABLE_ERROR:
                        if (titleId == 0) {
                            titleId = R.string.dialog_title_service_unavailable;
                            summaryId = R.string.dialog_summary_service_unavailable;
                        }
                        dlg = DialogFactory.newNoActionAlertDialog(this, titleId, summaryId);
                        break;
                    case MSG_SOCKET_TIMEOUT_EXCEPTION:
                        if (titleId == 0) {
                            titleId = R.string.dialog_title_connection_timeout;
                            summaryId = R.string.dialog_summary_connection_timeout;
                        }
                        dlg = DialogFactory.newNoActionAlertDialog(this, titleId, summaryId);
                        break;
                    default:
                        dlg = super.onCreateDialog(id);
                        break;
                }
                break;
        }
        return dlg;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
        switch (id) {
            case DLG_MOVE_DATA_BETWEEN_STORAGES:
                ((AlertDialog) dialog)
                        .setMessage(getText(mUseExternalStorage.isChecked() ? R.string.summary_preference_storage_external_on
                                : R.string.summary_preference_storage_external_off));
                break;
            default:
                break;
        }
    }

    private final Object moveLock = new Object();
    /**
     * This semaphore helps to avoid ripple effect: changes in MyAccount cause
     * changes in this activity ...
     */
    @GuardedBy("moveLock")
    private volatile boolean dataIsBeingMoved = false;
     
    /**
     * Move Data to/from External Storage
     *  
     * @author yvolk@yurivolkov.com
     */
    private class MoveDataBetweenStoragesTask extends AsyncTask<Uri, Void, JSONObject> {
        private ProgressDialog dlg;

        @Override
        protected void onPreExecute() {
            // indeterminate duration not cancelable
            dlg = ProgressDialog.show(MyPreferenceActivity.this,
                    getText(R.string.dialog_title_external_storage),
                    getText(R.string.dialog_summary_external_storage), 
                    true, 
                    false);
        }

        @Override
        protected JSONObject doInBackground(Uri... uris) {
            JSONObject jso = null;
            boolean done = false;
            boolean succeeded = false;
            StringBuilder messageToAppend = new StringBuilder();

            /**
             * Did we acquired the lock?
             */
            boolean locked = false;

            boolean useExternalStorageOld = MyPreferences.isStorageExternal(null);
            boolean useExternalStorageNew = MyPreferenceActivity.this.mUseExternalStorage
                    .isChecked();

            MyLog.d(TAG, "About to move data from " + useExternalStorageOld + " to "
                    + useExternalStorageNew);

            if (useExternalStorageNew == useExternalStorageOld) {
                messageToAppend.append(" Nothing to do.");
                done = true;
                succeeded = true;
            }
            if (!done) {
                try {
                    synchronized (moveLock) {
                        if (dataIsBeingMoved) {
                            done = true;
                            messageToAppend.append(" skipped");
                        } else {
                            dataIsBeingMoved = true;
                            locked = true;
                        }
                    }
                    if (!done) {
                        succeeded = moveDatabase(useExternalStorageNew, messageToAppend);
                        if (succeeded) {
                            moveAvatars(useExternalStorageNew, messageToAppend);
                        }
                    }
                } finally {
                    if (succeeded && ( useExternalStorageOld != useExternalStorageNew)) {
                        saveNewSettings(useExternalStorageNew, messageToAppend);
                    }
                    
                    if (locked) {
                        synchronized (moveLock) {
                            dataIsBeingMoved = false;
                        }
                    }
                }
            }
            messageToAppend.insert(0, " Move " + (succeeded ? "succeeded" : "failed"));
            MyLog.v(this, messageToAppend.toString());

            try {
                jso = new JSONObject();
                jso.put("succeeded", succeeded);
                jso.put("message", messageToAppend.toString());
            } catch (JSONException e) {
                MyLog.e(this, e);
            }
            return jso;
        }

        private void moveAvatars(boolean useExternalStorageNew, StringBuilder messageToAppend) {
            String method = "moveAvatars";
            boolean succeeded = false;
            boolean done = false;
            /**
             * Did we actually copied anything?
             */
            boolean copied = false;
            File dirOld = null;
            File dirNew = null;
            try {

                if (!done) {
                    dirOld = MyPreferences.getDataFilesDir(MyPreferences.DIRECTORY_AVATARS, null);
                    dirNew = MyPreferences.getDataFilesDir(MyPreferences.DIRECTORY_AVATARS, useExternalStorageNew);
                    if (dirOld == null || !dirOld.exists()) {
                        messageToAppend.append("No old avatars. ");
                        done = true;
                        succeeded = true;
                    }
                    if (dirNew == null) {
                        messageToAppend.append("No directory for new avatars?! ");
                        done = true;
                    }                    
                }
                if (!done) {
                    if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                        MyLog.v(this, method + " from: " + dirOld.getPath());
                        MyLog.v(this, method + " to: " + dirNew.getPath());
                    }
                    String fileName = "";
                    try {
                        for (File fileOld : dirOld.listFiles()) {
                            if (fileOld.isFile()) {
                                fileName = fileOld.getName();
                                File fileNew = new File(dirNew, fileName);
                                if (copyFile(fileOld, fileNew)) {
                                    copied = true;
                                }
                            }
                        }
                        succeeded = true;
                    } catch (Exception e) {
                        String logMsg = method + " couldn't copy'" + fileName + "'";
                        MyLog.v(this, logMsg, e);
                        messageToAppend.insert(0, logMsg + ": " + e.getMessage());
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
                        if (copied) {
                            for (File fileOld : dirOld.listFiles()) {
                                if (fileOld.isFile() && !fileOld.delete()) {
                                    messageToAppend.append(method + " couldn't delete old file " + fileOld.getName());
                                }
                            }
                        }
                    } else {
                        if (dirNew != null && dirNew.exists()) {
                            for (File fileNew : dirNew.listFiles()) {
                                if (fileNew.isFile() && !fileNew.delete()) {
                                    messageToAppend.append(method + " couldn't delete new file " + fileNew.getName());
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
            MyLog.d(this, method  + " " + (succeeded ? "succeeded" : "failed"));
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

        private boolean moveDatabase(boolean useExternalStorageNew, StringBuilder messageToAppend) {
            String method = "moveDatabase";
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
                            MyDatabase.DATABASE_NAME);
                    dbFileNew = MyPreferences.getDatabasePath(
                            MyDatabase.DATABASE_NAME, useExternalStorageNew);
                    if (dbFileOld == null) {
                        messageToAppend.append("No old database. ");
                        done = true;
                    }
                }
                if (!done) {
                    if (dbFileNew == null) {
                        messageToAppend.append("No new database. ");
                        done = true;
                    } else {
                        if (!dbFileOld.exists()) {
                            messageToAppend.append("No old database. ");
                            done = true;
                            succeeded = true;
                        } else if (dbFileNew.exists()) {
                            messageToAppend.insert(0, "Database already exists. ");
                            if (!dbFileNew.delete()) {
                                messageToAppend.insert(0, "Couldn't delete already existed files. ");
                                done = true;
                            }
                        }
                    }
                }
                if (!done) {
                    if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                        MyLog.v(this, method + " from: " + dbFileOld.getPath());
                        MyLog.v(this, method + " to: " + dbFileNew.getPath());
                    }
                    try {
                        if (copyFile(dbFileOld, dbFileNew)) {
                            copied = true;
                            succeeded = true;
                        }
                    } catch (Exception e) {
                        MyLog.v(this, "Copy database", e);
                        messageToAppend.insert(0, "Couldn't copy database: " + e.getMessage() + ". ");
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
                    if (succeeded
                            && (copied && dbFileOld != null)
                            && dbFileOld.exists()
                            && !dbFileOld.delete()) {
                        messageToAppend.append(method + " couldn't delete old files. ");
                    } else if (dbFileNew != null
                            && dbFileNew.exists()
                            && !dbFileNew.delete()) {
                        messageToAppend.append(method + " couldn't delete new files. ");

                    }
                } catch (Exception e) {
                    MyLog.v(this, method + " Delete old file", e);
                    messageToAppend.append(method + " couldn't delete old files. " + e.getMessage() + ". ");
                }
            }
            MyLog.d(this, method  + " " + (succeeded ? "succeeded" : "failed"));
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
                    MyLog.e(TAG, "New file was not created: '" + dst.getCanonicalPath() + "'");
                } else if (src.getCanonicalPath().compareTo(dst.getCanonicalPath()) == 0) {
                    MyLog.d(TAG, "Cannot copy to itself: '" + src.getCanonicalPath() + "'");
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
            MyLog.d(TAG, "Copied " + sizeCopied + " bytes of " + sizeIn);
            return ok;
        }

        // This is in the UI thread, so we can mess with the UI
        @Override
        protected void onPostExecute(JSONObject jso) {
            DialogFactory.dismissSafely(dlg);
            if (jso != null) {
                try {
                    boolean succeeded = jso.getBoolean("succeeded");
                    String message = jso.getString("message");

                    MyLog.d(TAG, this.getClass().getSimpleName() + " ended, "
                            + (succeeded ? "moved" : "move failed"));
                    
                    if (!succeeded) {
                        String message2 = MyPreferenceActivity.this
                        .getString(R.string.error);
                         message = message2 + ": " + message;
                    }
                    Toast.makeText(MyPreferenceActivity.this, message, Toast.LENGTH_LONG).show();
                    MyPreferenceActivity.this.showUseExternalStorage();
                    
                } catch (JSONException e) {
                    MyLog.e(this, e);
                }
            }
            useExternalStorageIsBusy = false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK
                && event.getRepeatCount() == 0
                && !MyContextHolder.get().persistentAccounts().isEmpty()) {
            closeAndGoBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public void closeAndGoBack() {
        MyLog.v(this, "Going back to the Timeline");
        finish();
        startTimelineActivity = true;
    }
}
