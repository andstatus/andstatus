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
import android.provider.Settings;
import android.text.format.Formatter;

import androidx.fragment.app.FragmentActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.HelpActivity;
import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.account.AccountSettingsActivity;
import org.andstatus.app.account.ManageAccountsActivity;
import org.andstatus.app.backup.BackupActivity;
import org.andstatus.app.backup.DefaultProgressListener;
import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.backup.RestoreActivity;
import org.andstatus.app.data.DataPruner;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.checker.DataChecker;
import org.andstatus.app.graphics.ImageCaches;
import org.andstatus.app.note.KeywordsFilter;
import org.andstatus.app.notification.NotificationMethodType;
import org.andstatus.app.origin.PersistentOriginList;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.service.QueueViewer;
import org.andstatus.app.timeline.meta.ManageTimelines;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineTitle;
import org.andstatus.app.util.DialogFactory;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TryUtils;
import org.andstatus.app.util.UriUtils;

import java.util.Optional;

import io.vavr.control.Try;

import static org.andstatus.app.util.I18n.formatBytes;

public class MySettingsFragment extends PreferenceFragmentCompat implements
        OnSharedPreferenceChangeListener {
    static final String FRAGMENT_TAG = "settings_fragment";

    private static final String KEY_ROOT = "key_root";
    private static final String KEY_ABOUT_APPLICATION = "about_application";
    private static final String KEY_ADD_NEW_ACCOUNT = "add_new_account";
    private static final String KEY_BACKUP_RESTORE = "backup_restore";
    private static final String KEY_CHANGE_LOG = "change_log";
    private static final String KEY_CHECK_DATA = "check_data";
    private static final String KEY_DELETE_OLD_DATA = "delete_old_data";
    static final String KEY_MANAGE_ACCOUNTS = "manage_accounts_internally";
    private static final String KEY_MANAGE_ACCOUNTS_ANDROID = "manage_accounts_android";
    private static final String KEY_MANAGE_ORIGIN_SYSTEMS = "manage_origin_systems";
    private static final String KEY_MANAGE_TIMELINES = "manage_timelines";
    private static final String KEY_NOTIFICATION_SELECT_RINGTONE = "select_ringtone";
    private static final String KEY_USER_GUIDE = "user_guide";

    private StorageSwitch storageSwitch = null;
    
    private boolean onSharedPreferenceChangedIsBusy = false;
    private boolean mIgnorePreferenceChange = false;
    private boolean checkDataIncludeLong = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        storageSwitch = new StorageSwitch(this);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(
                MySettingsGroup.from(rootKey).getPreferencesXmlResId(),
                rootKey == null ? KEY_ROOT : rootKey
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!MyContextHolder.get().isReady()) {
            MySettingsActivity.restartMe(getActivity());
            return;
        }
        getActivity().setTitle(MySettingsGroup.from(this).getTitleResId());
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
        showDontSynchronizeOldNotes();
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
        showFilterHideNotesBasedOnKeywords();
        showManageTimelines();
        showMaxDistanceBetweenDuplicates();
        showMaximumSizeOfAttachment();
        showMaximumSizeOfCachedMedia();
    }

    private void showManageAccounts() {
        Preference preference = findPreference(KEY_MANAGE_ACCOUNTS);
        if (preference != null) {
            CharSequence summary;
            if (MyContextHolder.get().accounts().isEmpty()) {
                summary = getText(R.string.summary_preference_accounts_absent);
            } else {
                summary = getText(R.string.summary_preference_accounts_present) + ": "
                        + MyContextHolder.get().accounts().size();
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

    private void showDontSynchronizeOldNotes() {
        long hours = MyPreferences.getDontSynchronizeOldNotes();
        Preference preference = findPreference(MyPreferences.KEY_DONT_SYNCHRONIZE_OLD_NOTES);
        if (preference != null) {
            preference.setSummary( hours > 0 ?
                    StringUtils.format(this.getContext(), R.string.dont_synchronize_old_messages_summary,
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
        final Preference preference = findPreference(KEY_NOTIFICATION_SELECT_RINGTONE);
        if (preference != null) {
            Uri uri = NotificationMethodType.SOUND.getUri();
            MyLog.v(this, () -> "Ringtone URI: " + uri);

            Ringtone ringtone = UriUtils.nonEmpty(uri)
                    ? RingtoneManager.getRingtone(getActivity(), uri)
                    : null;
            if (ringtone != null) {
                preference.setSummary(ringtone.getTitle(getActivity()));
            } else {
                preference.setSummary(R.string.summary_preference_no_ringtone);
            }
        }
    }
    
    protected void showUseExternalStorage() {
        CheckBoxPreference preference = (CheckBoxPreference) getPreferenceScreen().findPreference(
                MyPreferences.KEY_USE_EXTERNAL_STORAGE_NEW);
        if (preference == null) return;

        mIgnorePreferenceChange = true;
        try {
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
        } catch (Throwable t) {
            MyLog.d(this, "showUseExternalStorage", t);
        } finally {
            mIgnorePreferenceChange = false;
        }
    }

    private void showBackupRestore() {
        Preference preference = findPreference(KEY_BACKUP_RESTORE);
        if (preference != null) {
            CharSequence title;
            if (MyContextHolder.get().accounts().isEmpty()) {
                title = getText(R.string.label_restore);
            } else {
                title = getText(R.string.label_backup);
            }
            preference.setTitle(title);
        }
    }

    private void showFilterHideNotesBasedOnKeywords() {
        EditTextPreference preference = (EditTextPreference) findPreference(MyPreferences.KEY_FILTER_HIDE_NOTES_BASED_ON_KEYWORDS);
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
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_ACTOR_IN_TIMELINE,
                R.array.actor_in_timeline_values, R.array.actor_in_timeline_entries,
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
        Timeline timeline = MyContextHolder.get().timelines().getDefault();
        Preference preference = findPreference(KEY_MANAGE_TIMELINES);
        if (preference != null) {
            preference.setSummary(StringUtils.format(getContext(), R.string.default_timeline_summary,
                    TimelineTitle.from(MyContextHolder.get(), timeline).toString()));
        }
    }

    private void showMaxDistanceBetweenDuplicates() {
        Preference preference = findPreference(MyPreferences.KEY_MAX_DISTANCE_BETWEEN_DUPLICATES);
        if (preference != null) {
            Integer value = MyPreferences.getMaxDistanceBetweenDuplicates();
            preference.setSummary( value > 0 ? value.toString() : getText(R.string.this_option_is_turned_off));;
        }
    }

    private void showMaximumSizeOfAttachment() {
        Preference preference = findPreference(MyPreferences.KEY_MAXIMUM_SIZE_OF_ATTACHMENT_MB);
        if (preference != null) {
            preference.setSummary(Formatter.formatShortFileSize(getActivity(),
                    MyPreferences.getMaximumSizeOfAttachmentBytes()));
        }
    }

    private void showMaximumSizeOfCachedMedia() {
        showMaximumSizeOfCachedMedia(Optional.empty());
        AsyncTaskLauncher.execute(this,
                fragment -> Try.success(Optional.of(MyStorage.getMediaFilesSize())),
                fragment -> size -> size.onSuccess(fragment::showMaximumSizeOfCachedMedia));
    }

    private void showMaximumSizeOfCachedMedia(Optional<Long> size) {
        TryUtils.<Preference>ofNullable(findPreference(MyPreferences.KEY_MAXIMUM_SIZE_OF_CACHED_MEDIA_MB))
            .map(preference -> {
                preference.setSummary(Formatter.formatShortFileSize(getActivity(),
                    MyPreferences.getMaximumSizeOfCachedMediaBytes()) +
                    size.map(s -> " (" + getText(R.string.reltime_just_now) + ": " + formatBytes(s) + ")")
                        .orElse(""));
                return true;
            });
    }

    private void showListPreference(String key) {
        ListPreference preference = (ListPreference) findPreference(key);
        if (preference != null) {
            preference.setSummary(preference.getEntry());
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
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
            case KEY_DELETE_OLD_DATA:
                DialogFactory.showOkCancelDialog(getActivity(), this.getText(R.string.delete_old_data), "", this::launchDataPruner);
                break;
            case KEY_MANAGE_ACCOUNTS:
                startActivity(new Intent(getActivity(), ManageAccountsActivity.class));
                break;
            case KEY_MANAGE_ACCOUNTS_ANDROID:
                /**
                 * Start system activity which allow to manage list of accounts
                 * See <a href="http://stackoverflow.com/questions/3010103/android-how-to-create-intent-to-open-the-activity-that-displays-the-accounts">
                 *  Android - How to Create Intent to open the activity that displays the “Accounts & Sync settings” screen</a>
                 */
                Intent  intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
                intent.putExtra(Settings.EXTRA_AUTHORITIES, new String[]{MatchedUri.AUTHORITY});
                startActivity(intent);
                break;
            case KEY_BACKUP_RESTORE:
                if (MyContextHolder.get().accounts().isEmpty()) {
                    startActivity(new Intent(getActivity(), RestoreActivity.class));
                } else {
                    startActivity(new Intent(getActivity(), BackupActivity.class));
                }
                break;
            case KEY_CHECK_DATA:
                preference.setEnabled(false);
                DialogFactory.showOkCancelDialog(this, R.string.check_and_fix_data,
                        R.string.full_check, ActivityRequestCode.CHECK_DATA_INCLUDE_LONG);
                break;
            case KEY_MANAGE_ORIGIN_SYSTEMS:
                startActivity(new Intent(getActivity(), PersistentOriginList.class));
                break;
            case KEY_MANAGE_TIMELINES:
                startActivity(new Intent(getActivity(), ManageTimelines.class));
                break;
            case KEY_ABOUT_APPLICATION:
                HelpActivity.startMe(getActivity(), false, HelpActivity.PAGE_LOGO);
                break;
            case KEY_CHANGE_LOG:
                HelpActivity.startMe(getActivity(), false, HelpActivity.PAGE_CHANGELOG);
                break;
            case KEY_USER_GUIDE:
                HelpActivity.startMe(getActivity(), false, HelpActivity.PAGE_USER_GUIDE);
                break;
            case MyPreferences.KEY_COMMANDS_QUEUE:
                startActivity(new Intent(getActivity(), QueueViewer.class));
                break;
            case KEY_NOTIFICATION_SELECT_RINGTONE:
                pickRingtone();
                break;
            default:
                break;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void launchDataPruner(boolean doLaunch) {
        if (!doLaunch) return;

        DefaultProgressListener progressListener = new DefaultProgressListener(
                (MyActivity) getActivity(), R.string.delete_old_data, "DataPruner");
        progressListener.setCancelable(true);
        DataPruner pruner = new DataPruner(MyContextHolder.get())
            .setLogger(new ProgressLogger(progressListener))
            .setPruneNow();

        AsyncTaskLauncher.execute(pruner::prune);
    }

    private void pickRingtone() {
        FragmentActivity activity = getActivity();
        if (activity == null) return;

        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, activity.getText(R.string.notification_sound));
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);

        Uri currentRingtone = NotificationMethodType.SOUND.getUri();
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentRingtone);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        startActivityForResult(intent, ActivityRequestCode.PICK_RINGTONE.id);
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
                case MyPreferences.KEY_DONT_SYNCHRONIZE_OLD_NOTES:
                    showDontSynchronizeOldNotes();
                    break;
                case MyPreferences.KEY_SYNC_FREQUENCY_SECONDS:
                    MyContextHolder.get().accounts().onDefaultSyncFrequencyChanged();
                    showFrequency();
                    break;
                case MyPreferences.KEY_CONNECTION_TIMEOUT_SECONDS:
                    showConnectionTimeout();
                    break;
                case MyPreferences.KEY_NOTIFICATION_METHOD_SOUND:
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
                case MyPreferences.KEY_ACTOR_IN_TIMELINE:
                    showAuthorInTimeline();
                    break;
                case MyPreferences.KEY_TAP_ON_A_TIMELINE_TITLE_BEHAVIOUR:
                    showTapOnATimelineTitleBehaviour();
                    break;
                case MyPreferences.KEY_FILTER_HIDE_NOTES_BASED_ON_KEYWORDS:
                    showFilterHideNotesBasedOnKeywords();
                    break;
                case MyPreferences.KEY_DEFAULT_TIMELINE:
                    showManageTimelines();
                    break;
                case MyPreferences.KEY_ROUNDED_AVATARS:
                    ImageCaches.setAvatarsRounded();
                    break;
                case MyPreferences.KEY_MAX_DISTANCE_BETWEEN_DUPLICATES:
                    showMaxDistanceBetweenDuplicates();
                    break;
                case MyPreferences.KEY_MAXIMUM_SIZE_OF_ATTACHMENT_MB:
                    showMaximumSizeOfAttachment();
                    break;
                case MyPreferences.KEY_MAXIMUM_SIZE_OF_CACHED_MEDIA_MB:
                    showMaximumSizeOfCachedMedia();
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
            case CHECK_DATA_INCLUDE_LONG:
                checkDataIncludeLong = resultCode == Activity.RESULT_OK;
                DialogFactory.showOkCancelDialog(this, R.string.check_and_fix_data,
                        R.string.count_only, ActivityRequestCode.CHECK_DATA_COUNT_ONLY);
                break;
            case CHECK_DATA_COUNT_ONLY:
                launchDataChecker(resultCode);
                break;
            case PICK_RINGTONE:
                if (resultCode == Activity.RESULT_OK) {
                    final Uri value = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    Uri uri = UriUtils.notNull(value);
                    MyLog.v(this, "Ringtone set to uri:" + uri);
                    SharedPreferencesUtil.putString(MyPreferences.KEY_NOTIFICATION_METHOD_SOUND,
                            UriUtils.isEmpty(uri) ? "" : uri.toString());
                    showRingtone();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    private void launchDataChecker(int resultCode) {
        ProgressLogger.ProgressListener progressListener = new DefaultProgressListener(
                (MyActivity) getActivity(), R.string.app_name, "DataChecker");
        progressListener.setCancelable(true);
        DataChecker.fixDataAsync(new ProgressLogger(progressListener),
                checkDataIncludeLong,
                resultCode == Activity.RESULT_OK);
    }
}
