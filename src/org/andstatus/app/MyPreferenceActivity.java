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

package org.andstatus.app;

import java.io.File;
import java.io.IOException;

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
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import org.andstatus.app.account.AccountSettingsActivity;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Application settings
 * 
 */
public class MyPreferenceActivity extends PreferenceActivity implements
        OnSharedPreferenceChangeListener, OnPreferenceChangeListener {

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
    private boolean mUseExternalStorage_busy = false;

    private RingtonePreference mNotificationRingtone;

    private boolean onSharedPreferenceChanged_busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);
        // Default values for the preferences will be set only once
        // and in one place: here
        MyPreferences.setDefaultValues(R.xml.preferences, false);
        if (!MyPreferences.getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, MODE_PRIVATE).getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false)) {
          Log.e(TAG, "Default values were not set?!");   
        }
        
        mNotificationRingtone = (RingtonePreference) findPreference(MyPreferences.KEY_RINGTONE_PREFERENCE);
        mUseExternalStorage = (CheckBoxPreference) getPreferenceScreen().findPreference(
                MyPreferences.KEY_USE_EXTERNAL_STORAGE_NEW);

        mNotificationRingtone.setOnPreferenceChangeListener(this);
        
        Preference myPref = (Preference) findPreference("manage_accounts");
        myPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                AccountSettingsActivity.startManageAccountsActivity(MyPreferenceActivity.this);
                return false;
            }
        });
        
        myPref = (Preference) findPreference("about_application");
        myPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(MyPreferenceActivity.this, HelpActivity.class);
                startActivity(intent);
                return false;
            }
        });
        
        myPref = (Preference) findPreference("change_log");
        myPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(MyPreferenceActivity.this, HelpActivity.class);
                intent.putExtra(HelpActivity.EXTRA_HELP_PAGE_ID, HelpActivity.HELP_PAGE_CHANGELOG);
                startActivity(intent);
                return false;
            }
        });
        
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Stop service to force preferences reload on the next start
        // Plus disable repeating alarms for awhile (till next start service...)
        MyServiceManager.stopMyService(true);

        // Ensure MyPreferences are initialized
        MyPreferences.initialize(this, this);
        
        showAllPreferences();
        MyPreferences.getDefaultSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MyPreferences.getDefaultSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
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
        showRingtone(MyPreferences.getDefaultSharedPreferences().getString(
                MyPreferences.KEY_RINGTONE_PREFERENCE, null));
        showMinLogLevel();
        showUseExternalStorage();
        
        Preference myPref = (Preference) findPreference("manage_accounts");
        CharSequence summary;
        if (MyAccount.numberOfPersistentAccounts() > 0) {
            summary = getText(R.string.summary_preference_accounts_present) + ": " + MyAccount.numberOfPersistentAccounts();
        } else {
            summary = getText(R.string.summary_preference_accounts_absent);
        }
        myPref.setSummary(summary);
        
    }
    
    protected void showHistorySize() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_HISTORY_SIZE, R.array.history_size_keys, R.array.history_size_display, R.string.summary_preference_history_size);
    }

    protected void showHistoryTime() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_HISTORY_TIME, R.array.history_time_keys, R.array.history_time_display, R.string.summary_preference_history_time);
    }

    protected void showFrequency() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_FETCH_FREQUENCY, R.array.fetch_frequency_keys, R.array.fetch_frequency_display, R.string.summary_preference_frequency);
    }

    protected void showMinLogLevel() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_MIN_LOG_LEVEL, R.array.log_level_keys, R.array.log_level_display, R.string.summary_preference_min_log_level);
    }
    
    protected void showRingtone(Object newValue) {
        String ringtone = (String) newValue;
        Uri uri;
        Ringtone rt;
        if (ringtone == null) {
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        } else if ("".equals(ringtone)) {
            mNotificationRingtone.setSummary(R.string.summary_preference_no_ringtone);
        } else {
            uri = Uri.parse(ringtone);
            rt = RingtoneManager.getRingtone(this, uri);
            mNotificationRingtone.setSummary(rt.getTitle(this));
        }
    }
    
    protected void showUseExternalStorage() {
        boolean use = MyPreferences.getDefaultSharedPreferences().getBoolean(MyPreferences.KEY_USE_EXTERNAL_STORAGE, false);
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
        if (mSomethingIsBeingProcessed) {
            return;
        }
        if (onSharedPreferenceChanged_busy || !MyPreferences.isInitialized()) {
            return;
        }
        onSharedPreferenceChanged_busy = true;

        try {
            String value = "(not set)";
            if (sharedPreferences.contains(key)) {
                try {
                    value = sharedPreferences.getString(key, "");
                } catch (ClassCastException e) {
                    try {
                        value = Boolean.toString(sharedPreferences.getBoolean(key, false));
                    } catch (ClassCastException e2) {
                        value = "??";
                    }
                }
            }
            MyLog.d(TAG, "onSharedPreferenceChanged: " + key + "='" + value + "'");

            MyPreferences.onPreferencesChanged();
            
            if (key.equals(MyPreferences.KEY_FETCH_FREQUENCY)) {
                MyAccount.updateFetchFrequency();
                showFrequency();
            }
            if (key.equals(MyPreferences.KEY_RINGTONE_PREFERENCE)) {
                // TODO: Try to move it here from onPreferenceChange...
                // updateRingtone();
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
            if (key.equals(MyPreferences.KEY_USE_EXTERNAL_STORAGE_NEW)) {
                if (!mUseExternalStorage_busy) {
                    mUseExternalStorage_busy = true;
                    showDialog(DLG_MOVE_DATA_BETWEEN_STORAGES);
                }
            }
        } finally {
            onSharedPreferenceChanged_busy = false;
        }
    };

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference.getKey().equals(MyPreferences.KEY_RINGTONE_PREFERENCE)) {
            showRingtone(newValue);
            return true;
        }
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
                            MyPreferenceActivity.this.mUseExternalStorage_busy = false;
                        }
                    })
                    .setPositiveButton(getText(android.R.string.yes), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            if (MyServiceManager.getServiceState() == MyService.ServiceState.STOPPED) {
                                new MoveDataBetweenStoragesTask().execute();
                            } else {
                                MyServiceManager.stopMyService(true);
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
                    case MSG_SOCKET_TIMEOUT_EXCEPTION:
                        if (titleId == 0) {
                            titleId = R.string.dialog_title_connection_timeout;
                            summaryId = R.string.dialog_summary_connection_timeout;
                        }
                        dlg = new AlertDialog.Builder(this)
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
                    default:
                        dlg = super.onCreateDialog(id);
                }
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
        }
    }

    /**
     * This semaphore helps to avoid ripple effect: changes in MyAccount cause
     * changes in this activity ...
     */
    private volatile boolean mSomethingIsBeingProcessed = false;
     
    /**
     * Move Data to/from External Storage
     *  
     * @author yvolk
     */
    private class MoveDataBetweenStoragesTask extends AsyncTask<Uri, Void, JSONObject> {
        private ProgressDialog dlg;

        @Override
        protected void onPreExecute() {
            dlg = ProgressDialog.show(MyPreferenceActivity.this,
                    getText(R.string.dialog_title_external_storage),
                    getText(R.string.dialog_summary_external_storage), true, // indeterminate
                    // duration
                    false); // not cancel-able
        }

        @Override
        protected JSONObject doInBackground(Uri... uris) {
            JSONObject jso = null;
            boolean done = false;
            boolean succeeded = false;
            String message = "";

            File dbFileOld = null;
            File dbFileNew = null;
            /**
             * Did we actually copied database?
             */
            boolean copied = false;
            /**
             * Did we lock mSomethingIsBeingProcessed ?
             */
            boolean locked = false;

            boolean useExternalStorageOld = MyPreferences.getDefaultSharedPreferences()
                    .getBoolean(MyPreferences.KEY_USE_EXTERNAL_STORAGE, false);
            boolean useExternalStorageNew = MyPreferenceActivity.this.mUseExternalStorage
                    .isChecked();

            try {
                MyLog.d(TAG, "About to move data from " + useExternalStorageOld + " to "
                        + useExternalStorageNew);

                if (useExternalStorageNew == useExternalStorageOld) {
                    message = message + "Nothing to do. ";
                    done = true;
                    succeeded = true;
                }

                if (!done) {
                    synchronized (MyPreferenceActivity.this) {
                        if (mSomethingIsBeingProcessed) {
                            done = true;
                            message = "skipped";
                        } else {
                            mSomethingIsBeingProcessed = true;
                            locked = true;
                        }
                    }
                }

                if (!done) {
                    dbFileOld = MyPreferences.getContext().getDatabasePath(
                            MyDatabase.DATABASE_NAME);
                    dbFileNew = MyPreferences.getDatabasePath(
                            MyDatabase.DATABASE_NAME, useExternalStorageNew);
                    if (dbFileOld == null) {
                        message = message + "No old database. ";
                        done = true;
                    }
                }
                if (!done) {
                    if (dbFileNew == null) {
                        message = message + "No new database. ";
                        done = true;
                    } else {
                        if (!dbFileOld.exists()) {
                            message = message + "No old database. ";
                            done = true;
                            succeeded = true;
                        } else if (dbFileNew.exists()) {
                            message = "Database already exists. " + message;
                            if (!dbFileNew.delete()) {
                                message = "Couldn't delete already existed files. "
                                        + message;
                                done = true;
                            }
                        }
                    }
                }
                if (!done) {
                    if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                        MyLog.v(TAG, "from: " + dbFileOld.getPath());
                        MyLog.v(TAG, "to: " + dbFileNew.getPath());
                    }
                    try {
                        dbFileNew.createNewFile();
                        if (this.copyFile(dbFileOld, dbFileNew)) {
                            copied = true;
                            succeeded = true;
                        }
                    } catch (Exception e) {
                        message = "Couldn't copy database: " + e.getMessage() + ". "
                                + message;
                    }
                    done = true;
                }
            } catch (Exception e) {
                message = "Error: " + e.getMessage() + ". " + message;
                succeeded = false;
            } finally {
                // Save new settings
                if (succeeded && ( useExternalStorageOld != useExternalStorageNew)) {
                    try {
                        MyPreferences
                        .getDefaultSharedPreferences()
                        .edit()
                        .putBoolean(MyPreferences.KEY_USE_EXTERNAL_STORAGE,
                                useExternalStorageNew).commit();
                        MyPreferences.forget();
                        MyPreferences.initialize(MyPreferenceActivity.this, this);
                    } catch (Exception e) {
                        message = "Couldn't save new settings. " + e.getMessage()
                                + message;
                    }
                }
    
                // Delete unnecessary files
                try {
                    if (succeeded) {
                        if (copied && dbFileOld != null) {
                            if (dbFileOld.exists()) {
                                if (!dbFileOld.delete()) {
                                    message = "Couldn't delete old files. " + message;
                                }
                            }
                        }
                    } else {
                        if (dbFileNew != null) {
                            if (dbFileNew.exists()) {
                                if (!dbFileNew.delete()) {
                                    message = "Couldn't delete new files. " + message;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    message = "Couldn't delete old files. " + e.getMessage() + ". "
                            + message;
                }
                
                if (locked) {
                    mSomethingIsBeingProcessed = false;
                }
            }
            MyLog.v(TAG, "Move: " + message);

            try {
                jso = new JSONObject();
                jso.put("succeeded", succeeded);
                jso.put("message", message);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return jso;
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
            boolean Ok = false;
            if (src != null) {
                if (src.exists()) {
                    sizeIn = src.length();
                    if (src.getCanonicalPath().compareTo(dst.getCanonicalPath()) == 0) {
                        MyLog.d(TAG, "Cannot copy to itself: '" + src.getCanonicalPath() + "'");
                    } else {
                        java.nio.channels.FileChannel inChannel = new java.io.FileInputStream(src)
                                .getChannel();
                        java.nio.channels.FileChannel outChannel = new java.io.FileOutputStream(dst)
                                .getChannel();
                        try {
                            sizeCopied = inChannel.transferTo(0, inChannel.size(), outChannel);
                            Ok = (sizeIn == sizeCopied);
                        } finally {
                            if (inChannel != null)
                                inChannel.close();
                            if (outChannel != null)
                                outChannel.close();
                        }

                    }
                }
            }
            MyLog.d(TAG, "Copied " + sizeCopied + " bytes of " + sizeIn);
            return (Ok);
        }

        // This is in the UI thread, so we can mess with the UI
        @Override
        protected void onPostExecute(JSONObject jso) {
            try {
                dlg.dismiss();
            } catch (Exception e1) { 
                // Ignore this error  
            }
            if (jso != null) {
                try {
                    boolean succeeded = jso.getBoolean("succeeded");
                    String message = jso.getString("message");

                    MyLog.d(TAG, message);
                    MyLog.d(TAG, this.getClass().getSimpleName() + " ended, "
                            + (succeeded ? "moved" : "move failed"));
                    
                    if (succeeded) {

                    } else {

                        String message2 = MyPreferenceActivity.this
                        .getString(R.string.error);
                        if (message != null && message.length() > 0) {
                            message2 = message2 + ": " + message;
                            Log.d(TAG, message);
                        }
                        Toast.makeText(MyPreferenceActivity.this, message2, Toast.LENGTH_LONG).show();
                        
                    }
                    MyPreferenceActivity.this.showUseExternalStorage();
                    
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            mUseExternalStorage_busy = false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            if (MyAccount.numberOfPersistentAccounts() > 0) {
                MyLog.v(TAG, "Going back to the Timeline");
                finish();
                // On modifying activity back stack see http://stackoverflow.com/questions/11366700/modification-of-the-back-stack-in-android
                Intent i = new Intent(this, TimelineActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
                return true;    
            }
        }        
        return super.onKeyDown(keyCode, event);
    }
}
