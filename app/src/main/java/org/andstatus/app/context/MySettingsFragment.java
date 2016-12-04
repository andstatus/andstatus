/* 
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.RingtonePreference;
import android.text.TextUtils;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.HelpActivity;
import org.andstatus.app.R;
import org.andstatus.app.account.AccountSettingsActivity;
import org.andstatus.app.account.ManageAccountsActivity;
import org.andstatus.app.backup.BackupActivity;
import org.andstatus.app.backup.RestoreActivity;
import org.andstatus.app.graphics.MyImageCache;
import org.andstatus.app.msg.KeywordsFilter;
import org.andstatus.app.origin.PersistentOriginList;
import org.andstatus.app.service.QueueViewer;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.timeline.TimelineList;
import org.andstatus.app.timeline.TimelineTitle;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

public class MySettingsFragment extends PreferenceFragment implements
        OnSharedPreferenceChangeListener {

    private static final String KEY_ABOUT_APPLICATION = "about_application";
    private static final String KEY_ADD_NEW_ACCOUNT = "add_new_account";
    private static final String KEY_BACKUP_RESTORE = "backup_restore";
    private static final String KEY_CHANGE_LOG = "change_log";
    public static final String KEY_CHECK_DATA = "check_data";
    static final String KEY_MANAGE_ACCOUNTS = "manage_accounts";
    private static final String KEY_MANAGE_ORIGIN_SYSTEMS = "manage_origin_systems";
    private static final String KEY_MANAGE_TIMELINES = "manage_timelines";

    private StorageSwitch storageSwitch = null;
    
    private boolean onSharedPreferenceChangedIsBusy = false;
    private boolean mIgnorePreferenceChange = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(MyPreferencesGroupsEnum.load(getArguments()
                .getString(MySettingsActivity.PREFERENCES_GROUPS_KEY)).getPreferencesXmlResId());
        storageSwitch = new StorageSwitch(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        showAllPreferences();
        SharedPreferencesUtil.getDefaultSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        SharedPreferencesUtil.getDefaultSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
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
        showDontSynchronizeOldMessages();
        showConnectionTimeout();
        showHistorySize();
        showHistoryTime();
        showRingtone();
        showMinLogLevel();
        showUseExternalStorage();
        showBackupRestore();
        showAuthorInTimeline();
        showTapOnATimelineTitleBehaviour();
        showCustomLocale();
        showThemeColor();
        showActionBarBackgroundColor();
        showActionBarTextColor();
        showBackgroundColor();
        showThemeSize();
        showFilterHideMessagesBasedOnKeywords();
        showManageTimelines();
    }

    private void showManageAccounts() {
        Preference preference = findPreference(KEY_MANAGE_ACCOUNTS);
        if (preference != null) {
            CharSequence summary;
            if (MyContextHolder.get().persistentAccounts().isEmpty()) {
                summary = getText(R.string.summary_preference_accounts_absent);
            } else {
                summary = getText(R.string.summary_preference_accounts_present) + ": "
                        + MyContextHolder.get().persistentAccounts().size();
            }
            preference.setSummary(summary);
        }
    }
    
    protected void showFrequency() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_SYNC_FREQUENCY_SECONDS,
                R.array.fetch_frequency_values, R.array.fetch_frequency_entries,
                R.string.summary_preference_frequency);
    }

    private void showConnectionTimeout() {
        Preference preference = findPreference(MyPreferences.KEY_CONNECTION_TIMEOUT_SECONDS);
        if (preference != null) {
            preference.setSummary(
                    Long.toString(java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(MyPreferences
                            .getConnectionTimeoutMs())) + "s");
        }
    }

    private void showDontSynchronizeOldMessages() {
        long hours = MyPreferences.getDontSynchronizeOldMessages();
        Preference preference = findPreference(MyPreferences.KEY_DONT_SYNCHRONIZE_OLD_MESSAGES);
        if (preference != null) {
            preference.setSummary( hours > 0 ?
                    String.format(getText(R.string.dont_synchronize_old_messages_summary).toString(),
                            Long.toString(hours)) : getString(R.string.this_option_is_turned_off));
        }
    }

    protected void showHistorySize() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_HISTORY_SIZE,
                R.array.history_size_values, R.array.history_size_entries,
                R.string.summary_preference_history_size);
    }

    protected void showHistoryTime() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_HISTORY_TIME,
                R.array.history_time_values, R.array.history_time_entries,
                R.string.summary_preference_history_time);
    }
    
    protected void showMinLogLevel() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_MIN_LOG_LEVEL,
                R.array.log_level_values, R.array.log_level_entries,
                R.string.summary_preference_min_log_level);
    }
    
    protected void showRingtone() {
        RingtonePreference ringtonePreference = (RingtonePreference) findPreference(MyPreferences.KEY_NOTIFICATION_RINGTONE);
        if (ringtonePreference != null) {
            String ringtoneString = SharedPreferencesUtil.getString(MyPreferences.KEY_NOTIFICATION_RINGTONE, null);
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
            if (rt != null) {
                ringtonePreference.setSummary(rt.getTitle(getActivity()));
            } else {
                ringtonePreference.setSummary(R.string.summary_preference_no_ringtone);
            }
        }
    }
    
    protected void showUseExternalStorage() {
        CheckBoxPreference preference = (CheckBoxPreference) getPreferenceScreen().findPreference(
                MyPreferences.KEY_USE_EXTERNAL_STORAGE_NEW);
        if (preference != null) {
            mIgnorePreferenceChange = true;
            boolean use = MyStorage.isStorageExternal();
            if (use != preference.isChecked()) {
                preference.setChecked(use);
            }
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)
                    && !preference.isChecked()) {
                preference.setEnabled(false);
            }
            StringBuilder summary = new StringBuilder( getText(preference.isChecked()
                    ? R.string.summary_preference_storage_external_on : R.string.summary_preference_storage_external_off));
            summary.append(":\n ");
            summary.append(MyStorage.getDataFilesDir(null));
            preference.setSummary(summary);
            mIgnorePreferenceChange = false;
        }
    }

    private void showBackupRestore() {
        Preference preference = findPreference(KEY_BACKUP_RESTORE);
        if (preference != null) {
            CharSequence title;
            if (MyContextHolder.get().persistentAccounts().isEmpty()) {
                title = getText(R.string.label_restore);
            } else {
                title = getText(R.string.label_backup);
            }
            preference.setTitle(title);
        }
    }

    private void showFilterHideMessagesBasedOnKeywords() {
        EditTextPreference preference = (EditTextPreference) findPreference(MyPreferences.KEY_FILTER_HIDE_MESSAGES_BASED_ON_KEYWORDS);
        if (preference != null) {
            KeywordsFilter filter = new KeywordsFilter(preference.getText());
            if (filter.isEmpty()) {
                preference.setSummary(R.string.this_option_is_turned_off);
            } else {
                preference.setSummary(filter.toString());
            }
        }
    }

    private void showAuthorInTimeline() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_USER_IN_TIMELINE,
                R.array.user_in_timeline_values, R.array.user_in_timeline_entries,
                R.string.summary_preference_user_in_timeline);
    }

    private void showTapOnATimelineTitleBehaviour() {
        showListPreference(MyPreferences.KEY_TAP_ON_A_TIMELINE_TITLE_BEHAVIOUR);
    }

    private void showCustomLocale() {
        showListPreference(MyPreferences.KEY_CUSTOM_LOCALE);
    }

    private void showThemeColor() {
        showListPreference(MyPreferences.KEY_THEME_COLOR);
    }

    private void showThemeSize() {
        showListPreference(MyPreferences.KEY_THEME_SIZE);
    }

    private void showBackgroundColor() {
        showListPreference(MyPreferences.KEY_BACKGROUND_COLOR);
    }

    private void showActionBarBackgroundColor() {
        showListPreference(MyPreferences.KEY_ACTION_BAR_BACKGROUND_COLOR);
    }

    private void showActionBarTextColor() {
        showListPreference(MyPreferences.KEY_ACTION_BAR_TEXT_COLOR);
    }

    private void showManageTimelines() {
        Timeline timeline = MyContextHolder.get().persistentTimelines().getDefault();
        Preference preference = findPreference(KEY_MANAGE_TIMELINES);
        if (preference != null) {
            preference.setSummary(String.format(getText(R.string.default_timeline_summary).toString(),
                    TimelineTitle.load(MyContextHolder.get(), timeline, null).toString()));
        }
    }

    private void showListPreference(String key) {
        ListPreference preference = (ListPreference) findPreference(key);
        if (preference != null) {
            preference.setSummary(preference.getEntry());
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
            case KEY_MANAGE_ACCOUNTS:
                startActivity(new Intent(getActivity(), ManageAccountsActivity.class));
                break;
            case KEY_BACKUP_RESTORE:
                if (MyContextHolder.get().persistentAccounts().isEmpty()) {
                    startActivity(new Intent(getActivity(), RestoreActivity.class));
                } else {
                    startActivity(new Intent(getActivity(), BackupActivity.class));
                }
                break;
            case KEY_CHECK_DATA:
                preference.setEnabled(false);
                startActivity(new Intent(getActivity(), HelpActivity.class).putExtra(HelpActivity.EXTRA_CHECK_DATA, "1"));
                break;
            case KEY_MANAGE_ORIGIN_SYSTEMS:
                startActivity(new Intent(getActivity(), PersistentOriginList.class));
                break;
            case KEY_MANAGE_TIMELINES:
                startActivity(new Intent(getActivity(), TimelineList.class));
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
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (mIgnorePreferenceChange || onSharedPreferenceChangedIsBusy
                || !MyContextHolder.get().initialized() || storageSwitch.isDataBeingMoved()) {
            return;
        }
        onSharedPreferenceChangedIsBusy = true;
        try {
            MyLog.logSharedPreferencesValue(this, key);
            MyPreferences.onPreferencesChanged();

            switch (key) {
                case MyPreferences.KEY_CUSTOM_LOCALE:
                    MyLocale.setLocale(getActivity());
                    MySettingsActivity.restartMe(getActivity());
                    break;
                case MyPreferences.KEY_THEME_COLOR:
                    showThemeColor();
                    MySettingsActivity.restartMe(getActivity());
                    break;
                case MyPreferences.KEY_THEME_SIZE:
                    showThemeSize();
                    break;
                case MyPreferences.KEY_BACKGROUND_COLOR:
                    showBackgroundColor();
                    break;
                case MyPreferences.KEY_ACTION_BAR_BACKGROUND_COLOR:
                    showActionBarBackgroundColor();
                    MySettingsActivity.restartMe(getActivity());
                    break;
                case MyPreferences.KEY_ACTION_BAR_TEXT_COLOR:
                    showActionBarTextColor();
                    MySettingsActivity.restartMe(getActivity());
                    break;
                case MyPreferences.KEY_DONT_SYNCHRONIZE_OLD_MESSAGES:
                    showDontSynchronizeOldMessages();
                    break;
                case MyPreferences.KEY_SYNC_FREQUENCY_SECONDS:
                    MyContextHolder.get().persistentAccounts().onDefaultSyncFrequencyChanged();
                    showFrequency();
                    break;
                case MyPreferences.KEY_CONNECTION_TIMEOUT_SECONDS:
                    showConnectionTimeout();
                    break;
                case MyPreferences.KEY_NOTIFICATION_RINGTONE:
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
                case MyPreferences.KEY_TAP_ON_A_TIMELINE_TITLE_BEHAVIOUR:
                    showTapOnATimelineTitleBehaviour();
                    break;
                case MyPreferences.KEY_FILTER_HIDE_MESSAGES_BASED_ON_KEYWORDS:
                    showFilterHideMessagesBasedOnKeywords();
                    break;
                case MyPreferences.KEY_DEFAULT_TIMELINE:
                    showManageTimelines();
                    break;
                case MyPreferences.KEY_ROUNDED_AVATARS:
                    MyImageCache.setAvatarsRounded();
                    break;
                default:
                    break;
            }
        } finally {
            onSharedPreferenceChangedIsBusy = false;
        }
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
