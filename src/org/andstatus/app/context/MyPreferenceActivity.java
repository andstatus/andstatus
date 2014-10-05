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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
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

import org.andstatus.app.HelpActivity;
import org.andstatus.app.MyActionBar;
import org.andstatus.app.MyActionBarContainer;
import org.andstatus.app.R;
import org.andstatus.app.TimelineActivity;
import org.andstatus.app.account.AccountSettingsActivity;
import org.andstatus.app.backup.BackupActivity;
import org.andstatus.app.backup.RestoreActivity;
import org.andstatus.app.origin.OriginList;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.QueueViewer;
import org.andstatus.app.util.DialogFactory;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

/** Application settings */
public class MyPreferenceActivity extends PreferenceActivity implements
        OnSharedPreferenceChangeListener, OnPreferenceChangeListener, MyActionBarContainer {

    private static final String KEY_ADD_NEW_ACCOUNT = "add_new_account";
    private static final String KEY_BACKUP_RESTORE = "backup_restore";
    private static final String KEY_MANAGE_EXISTING_ACCOUNTS = "manage_existing_accounts";

    /**
     * This is single list of (in fact, enums...) of Message/Dialog IDs
     */
    public static final int MSG_NONE = 1;

    public static final int MSG_SERVICE_UNAVAILABLE_ERROR = 4;

    public static final int MSG_CONNECTION_EXCEPTION = 5;

    public static final int MSG_SOCKET_TIMEOUT_EXCEPTION = 6;

    public static final int DLG_MOVE_DATA_BETWEEN_STORAGES = 8;
    
    
    // End Of the list ----------------------------------------

    CheckBoxPreference mUseExternalStorage;
    private StorageSwitch storageSwitch = null;
    
    private RingtonePreference mNotificationRingtone;
    private Preference mBackupRestore;
    
    private boolean onSharedPreferenceChangedIsBusy = false;
    volatile boolean dialogIsOpened = false;

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

        mBackupRestore = findPreference(KEY_BACKUP_RESTORE);
        mBackupRestore.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (MyContextHolder.get().persistentAccounts().isEmpty()) {
                    startActivity(new Intent(MyPreferenceActivity.this, RestoreActivity.class));
                } else {
                    startActivity(new Intent(MyPreferenceActivity.this, BackupActivity.class));
                }
                return false;
            }
        });
        
        storageSwitch = new StorageSwitch(this);
        
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

        myPref = findPreference(MyPreferences.KEY_COMMANDS_QUEUE);
        myPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(MyPreferenceActivity.this, QueueViewer.class);
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
        MyContextHolder.get().setInForeground(true);
        
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
        MyContextHolder.get().setInForeground(false);
    }

    /**
     * Show values of all preferences in the "summaries".
     * @see <a href="http://stackoverflow.com/questions/531427/how-do-i-display-the-current-value-of-an-android-preference-in-the-preference-sum"> 
       How do I display the current value of an Android Preference 
       in the Preference summary?</a>
     */
    protected void showAllPreferences() {
        showFrequency();
        showConnectionTimeout();
        showHistorySize();
        showHistoryTime();
        showRingtone();
        showMinLogLevel();
        showUseExternalStorage();
        showBackupRestore();
        showAuthorInTimeline();
        
        Preference myPref = findPreference(KEY_MANAGE_EXISTING_ACCOUNTS);
        CharSequence summary;
        if (MyContextHolder.get().persistentAccounts().isEmpty()) {
            summary = getText(R.string.summary_preference_accounts_absent);
        } else {
            summary = getText(R.string.summary_preference_accounts_present) + ": " + MyContextHolder.get().persistentAccounts().size();
        }
        myPref.setSummary(summary);
    }

    protected void showFrequency() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, R.array.fetch_frequency_values, R.array.fetch_frequency_entries, R.string.summary_preference_frequency);
    }

    private void showConnectionTimeout() {
        findPreference(MyPreferences.KEY_CONNNECTION_TIMEOUT_SECONDS).setSummary("" + MyPreferences.getConnectionTimeoutMs()/1000 + "s");
    }

    protected void showHistorySize() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_HISTORY_SIZE, R.array.history_size_values, R.array.history_size_entries, R.string.summary_preference_history_size);
    }

    protected void showHistoryTime() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_HISTORY_TIME, R.array.history_time_values, R.array.history_time_entries, R.string.summary_preference_history_time);
    }
    
    protected void showMinLogLevel() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_MIN_LOG_LEVEL, R.array.log_level_values, R.array.log_level_entries, R.string.summary_preference_min_log_level);
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

    private void showBackupRestore() {
        CharSequence title;
        if (MyContextHolder.get().persistentAccounts().isEmpty()) {
            title = getText(R.string.label_restore);
        } else {
            title = getText(R.string.label_backup);
        }
        mBackupRestore.setTitle(title);
    }

    private void showAuthorInTimeline() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_USER_IN_TIMELINE, R.array.user_in_timeline_values, R.array.user_in_timeline_entries, R.string.summary_preference_user_in_timeline);
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (dialogIsOpened || onSharedPreferenceChangedIsBusy
                || !MyContextHolder.get().initialized() || storageSwitch.isDataBeingMoved()) {
            return;
        }
        onSharedPreferenceChangedIsBusy = true;
        try {
            MyLog.logSharedPreferencesValue(this, sharedPreferences, key);
            MyPreferences.onPreferencesChanged();
            
            if (MyPreferences.KEY_SYNC_FREQUENCY_SECONDS.equals(key)) {
                MyContextHolder.get().persistentAccounts().onMyPreferencesChanged(MyContextHolder.get());
                showFrequency();
            }
            if (MyPreferences.KEY_CONNNECTION_TIMEOUT_SECONDS.equals(key)) {
                showConnectionTimeout();
            }
            if (MyPreferences.KEY_RINGTONE_PREFERENCE.equals(key)) {
                showRingtone();
            }
            if (MyPreferences.KEY_HISTORY_SIZE.equals(key)) {
                showHistorySize();
            }
            if (MyPreferences.KEY_HISTORY_TIME.equals(key)) {
                showHistoryTime();
            }
            if (MyPreferences.KEY_MIN_LOG_LEVEL.equals(key)) {
                showMinLogLevel();
            }
            if (MyPreferences.KEY_USE_EXTERNAL_STORAGE_NEW.equals(key)) {
                dialogIsOpened = true;
                showDialog(DLG_MOVE_DATA_BETWEEN_STORAGES);
            }
            if (MyPreferences.KEY_USER_IN_TIMELINE.equals(key)) {
                showAuthorInTimeline();
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
                dlg = storageSwitch.newSwichStorageDialog();
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

    @Override
    public boolean hasOptionsMenu() {
        return false;
    }
}
