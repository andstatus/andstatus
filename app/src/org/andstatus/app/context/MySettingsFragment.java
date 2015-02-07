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

import android.app.Activity;
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
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.RingtonePreference;
import android.text.TextUtils;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.HelpActivity;
import org.andstatus.app.R;
import org.andstatus.app.TimelineActivity;
import org.andstatus.app.account.AccountSettingsActivity;
import org.andstatus.app.backup.BackupActivity;
import org.andstatus.app.backup.RestoreActivity;
import org.andstatus.app.origin.PersistentOriginList;
import org.andstatus.app.service.QueueViewer;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

public class MySettingsFragment extends PreferenceFragment implements
        OnSharedPreferenceChangeListener {

    private static final String KEY_ABOUT_APPLICATION = "about_application";
    private static final String KEY_ADD_NEW_ACCOUNT = "add_new_account";
    private static final String KEY_BACKUP_RESTORE = "backup_restore";
    private static final String KEY_CHANGE_LOG = "change_log";
    private static final String KEY_MANAGE_EXISTING_ACCOUNTS = "manage_existing_accounts";
    private static final String KEY_MANAGE_ORIGIN_SYSTEMS = "manage_origin_systems";

    private StorageSwitch storageSwitch = null;
    
    private boolean onSharedPreferenceChangedIsBusy = false;
    private boolean mIgnorePreferenceChange = false;

    private boolean startTimelineActivity = false;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        storageSwitch = new StorageSwitch(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        showAllPreferences();
        MyPreferences.getDefaultSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        MyPreferences.getDefaultSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

        if (startTimelineActivity) {
            MyContextHolder.release();
            // On modifying activity back stack see http://stackoverflow.com/questions/11366700/modification-of-the-back-stack-in-android
            Intent i = new Intent(getActivity(), TimelineActivity.class);
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
        showManageAccounts();
        showFrequency();
        showConnectionTimeout();
        showHistorySize();
        showHistoryTime();
        showRingtone();
        showMinLogLevel();
        showUseExternalStorage();
        showBackupRestore();
        showAuthorInTimeline();
    }

    private void showManageAccounts() {
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
        findPreference(MyPreferences.KEY_CONNNECTION_TIMEOUT_SECONDS).setSummary(
                Long.toString(java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(MyPreferences
                        .getConnectionTimeoutMs())) + "s");
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
            rt = RingtoneManager.getRingtone(getActivity(), uri);
        }
        RingtonePreference ringtonePreference = (RingtonePreference) findPreference(MyPreferences.KEY_RINGTONE_PREFERENCE);
        if (rt != null) {
            ringtonePreference.setSummary(rt.getTitle(getActivity()));
        } else {
            ringtonePreference.setSummary(R.string.summary_preference_no_ringtone);
        }
    }
    
    protected void showUseExternalStorage() {
        mIgnorePreferenceChange = true;
        boolean use = MyPreferences.isStorageExternal(null);
        CheckBoxPreference preference = (CheckBoxPreference) getPreferenceScreen().findPreference(
                MyPreferences.KEY_USE_EXTERNAL_STORAGE_NEW);
        if (use != preference.isChecked()) {
            preference.setChecked(use);
        }
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) 
                && !preference.isChecked()) {
            preference.setEnabled(false);
        }
        mIgnorePreferenceChange = false;
    }

    private void showBackupRestore() {
        CharSequence title;
        if (MyContextHolder.get().persistentAccounts().isEmpty()) {
            title = getText(R.string.label_restore);
        } else {
            title = getText(R.string.label_backup);
        }
        findPreference(KEY_BACKUP_RESTORE).setTitle(title);
    }

    private void showAuthorInTimeline() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_USER_IN_TIMELINE, R.array.user_in_timeline_values, R.array.user_in_timeline_entries, R.string.summary_preference_user_in_timeline);
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (mIgnorePreferenceChange || onSharedPreferenceChangedIsBusy
                || !MyContextHolder.get().initialized() || storageSwitch.isDataBeingMoved()) {
            return;
        }
        onSharedPreferenceChangedIsBusy = true;
        try {
            MyLog.logSharedPreferencesValue(this, sharedPreferences, key);
            MyPreferences.onPreferencesChanged();
            
            switch (key) {
                case MyPreferences.KEY_SYNC_FREQUENCY_SECONDS:
                    MyContextHolder.get().persistentAccounts().onMyPreferencesChanged(MyContextHolder.get());
                    showFrequency();
                    break;
                case MyPreferences.KEY_CONNNECTION_TIMEOUT_SECONDS:
                    showConnectionTimeout();
                    break;
                case MyPreferences.KEY_RINGTONE_PREFERENCE:
                    showRingtone();
                    break;
                case MyPreferences.KEY_HISTORY_SIZE:
                    showHistorySize();
                    break;
                case MyPreferences.KEY_HISTORY_TIME:
                    showHistoryTime();
                    break;
                case MyPreferences.KEY_MIN_LOG_LEVEL:
                    showMinLogLevel();
                    break;
                case MyPreferences.KEY_USER_IN_TIMELINE:
                    showAuthorInTimeline();
                    break;
                default:
                    break;
            }
        } finally {
            onSharedPreferenceChangedIsBusy = false;
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        switch (preference.getKey()) {
            case MyPreferences.KEY_USE_EXTERNAL_STORAGE_NEW:
                if (CheckBoxPreference.class.isInstance(preference)) {
                    storageSwitch.showSwitchStorageDialog(ActivityRequestCode.MOVE_DATA_BETWEEN_STORAGES, 
                            ((CheckBoxPreference) preference).isChecked());
                }
                break;
            case KEY_ADD_NEW_ACCOUNT:
                AccountSettingsActivity.startAddNewAccount(getActivity());
                break;
            case KEY_MANAGE_EXISTING_ACCOUNTS:
                AccountSettingsActivity.startManageExistingAccounts(getActivity());
                break;
            case KEY_BACKUP_RESTORE:
                if (MyContextHolder.get().persistentAccounts().isEmpty()) {
                    startActivity(new Intent(getActivity(), RestoreActivity.class));
                } else {
                    startActivity(new Intent(getActivity(), BackupActivity.class));
                }
                break;
            case KEY_MANAGE_ORIGIN_SYSTEMS:
                startActivity(new Intent(getActivity(), PersistentOriginList.class));
                break;
            case KEY_ABOUT_APPLICATION:
                startActivity(new Intent(getActivity(), HelpActivity.class));
                break;
            case KEY_CHANGE_LOG:
                Intent intent = new Intent(getActivity(), HelpActivity.class);
                intent.putExtra(HelpActivity.EXTRA_HELP_PAGE_INDEX, HelpActivity.PAGE_INDEX_CHANGELOG);
                startActivity(intent);
                break;
            case MyPreferences.KEY_COMMANDS_QUEUE:
                startActivity(new Intent(getActivity(), QueueViewer.class));
                break;
            default:
                break;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (ActivityRequestCode.fromId(requestCode)) {
            case MOVE_DATA_BETWEEN_STORAGES:
                showUseExternalStorage();
                if (resultCode == Activity.RESULT_OK) {
                    storageSwitch.move();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }
}
