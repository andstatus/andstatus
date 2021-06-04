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
package org.andstatus.app.context

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.vavr.control.Try
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.HelpActivity
import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.account.AccountSettingsActivity
import org.andstatus.app.account.ManageAccountsActivity
import org.andstatus.app.backup.BackupActivity
import org.andstatus.app.backup.DefaultProgressListener
import org.andstatus.app.backup.ProgressLogger
import org.andstatus.app.backup.RestoreActivity
import org.andstatus.app.data.DataPruner
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.data.checker.DataChecker
import org.andstatus.app.graphics.ImageCaches
import org.andstatus.app.note.KeywordsFilter
import org.andstatus.app.notification.NotificationMethodType
import org.andstatus.app.origin.PersistentOriginList
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.service.QueueViewer
import org.andstatus.app.timeline.meta.ManageTimelines
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineTitle
import org.andstatus.app.util.DialogFactory
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.UriUtils
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class MySettingsFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {
    private val storageSwitch: StorageSwitch = StorageSwitch(this)
    private var onSharedPreferenceChangedIsBusy = false
    private var mIgnorePreferenceChange = false
    private var checkDataIncludeLong = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(
                MySettingsGroup.from(rootKey).getPreferencesXmlResId(),
                rootKey ?: KEY_ROOT
        )
    }

    override fun onResume() {
        super.onResume()
        val activity = getMyActivity()
        if (activity == null || activity.restartMeIfNeeded()) return
        activity.setTitle(MySettingsGroup.from(this).getTitleResId())
        showAllPreferences()
        SharedPreferencesUtil.getDefaultSharedPreferences()?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        SharedPreferencesUtil.getDefaultSharedPreferences()?.unregisterOnSharedPreferenceChangeListener(this)
    }

    /**
     * Show values of all preferences in the "summaries".
     * @see [
     * How do I display the current value of an Android Preference
     * in the Preference summary?](http://stackoverflow.com/questions/531427/how-do-i-display-the-current-value-of-an-android-preference-in-the-preference-sum)
     */
    protected fun showAllPreferences() {
        showImageAnimations()
        showManageAccounts()
        showFrequency()
        showDontSynchronizeOldNotes()
        showConnectionTimeout()
        showHistorySize()
        showHistoryTime()
        showRingtone()
        showMinLogLevel()
        showUseExternalStorage()
        showAppInstanceName()
        showBackupRestore()
        showAuthorInTimeline()
        showTapOnATimelineTitleBehaviour()
        showCustomLocale()
        showThemeColor()
        showActionBarBackgroundColor()
        showActionBarTextColor()
        showBackgroundColor()
        showThemeSize()
        showFilterHideNotesBasedOnKeywords()
        showManageTimelines()
        showMaxDistanceBetweenDuplicates()
        showMaximumSizeOfAttachment()
        showMaximumSizeOfCachedMedia()
    }

    private fun showManageAccounts() {
        val preference = findPreference<Preference>(KEY_MANAGE_ACCOUNTS)
        if (preference != null) {
            val summary: CharSequence
            summary = if ( MyContextHolder.myContextHolder.getNow().accounts().isEmpty) {
                getText(R.string.summary_preference_accounts_absent)
            } else {
                (getText(R.string.summary_preference_accounts_present).toString() + ": "
                        +  MyContextHolder.myContextHolder.getNow().accounts().size())
            }
            preference.summary = summary
        }
    }

    protected fun showFrequency() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_SYNC_FREQUENCY_SECONDS,
                R.array.fetch_frequency_values, R.array.fetch_frequency_entries,
                R.string.summary_preference_frequency)
    }

    private fun showConnectionTimeout() {
        val preference = findPreference<Preference?>(MyPreferences.KEY_CONNECTION_TIMEOUT_SECONDS)
        if (preference != null) {
            preference.summary = java.lang.Long.toString(TimeUnit.MILLISECONDS.toSeconds(MyPreferences.getConnectionTimeoutMs().toLong())) + "s"
        }
    }

    private fun showDontSynchronizeOldNotes() {
        val hours = MyPreferences.getDontSynchronizeOldNotes()
        val preference = findPreference<Preference?>(MyPreferences.KEY_DONT_SYNCHRONIZE_OLD_NOTES)
        if (preference != null) {
            preference.summary = if (hours > 0) StringUtil.format(this.context, R.string.dont_synchronize_old_messages_summary,
                    java.lang.Long.toString(hours)) else getString(R.string.this_option_is_turned_off)
        }
    }

    protected fun showHistorySize() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_HISTORY_SIZE,
                R.array.history_size_values, R.array.history_size_entries,
                R.string.summary_preference_history_size)
    }

    protected fun showHistoryTime() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_HISTORY_TIME,
                R.array.history_time_values, R.array.history_time_entries,
                R.string.summary_preference_history_time)
    }

    protected fun showMinLogLevel() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_MIN_LOG_LEVEL,
                R.array.log_level_values, R.array.log_level_entries,
                R.string.summary_preference_min_log_level)
    }

    protected fun showRingtone() {
        val preference = findPreference<Preference?>(KEY_NOTIFICATION_SELECT_RINGTONE)
        if (preference != null) {
            val uri = NotificationMethodType.SOUND.uri
            MyLog.v(this) { "Ringtone URI: $uri" }
            val ringtone = if (UriUtils.nonEmpty(uri)) RingtoneManager.getRingtone(activity, uri) else null
            if (ringtone != null) {
                preference.summary = ringtone.getTitle(activity)
            } else {
                preference.setSummary(R.string.summary_preference_no_ringtone)
            }
        }
    }

    fun showUseExternalStorage() {
        val preference = preferenceScreen.findPreference<Preference?>(
                MyPreferences.KEY_USE_EXTERNAL_STORAGE_NEW) as CheckBoxPreference? ?: return
        mIgnorePreferenceChange = true
        try {
            val use = MyStorage.isStorageExternal()
            if (use != preference.isChecked) {
                preference.isChecked = use
            }
            if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED
                    && !preference.isChecked) {
                preference.isEnabled = false
            }
            val summary = StringBuilder(getText(if (preference.isChecked) R.string.summary_preference_storage_external_on else R.string.summary_preference_storage_external_off))
            summary.append(":\n ")
            summary.append(MyStorage.getDataFilesDir(null))
            preference.summary = summary
        } catch (t: Throwable) {
            MyLog.d(this, "showUseExternalStorage", t)
        } finally {
            mIgnorePreferenceChange = false
        }
    }

    private fun showAppInstanceName() {
        val preference = findPreference<Preference?>(MyPreferences.KEY_APP_INSTANCE_NAME)
        if (preference != null) {
            var title: CharSequence? = MyPreferences.getAppInstanceName()
            if (title.isNullOrEmpty()) {
                title = getText(R.string.empty_in_parenthesis)
            }
            preference.summary = title
        }
    }

    private fun showBackupRestore() {
        val preference = findPreference<Preference?>(KEY_BACKUP_RESTORE)
        if (preference != null) {
            val title: CharSequence
            title = if ( MyContextHolder.myContextHolder.getNow().accounts().isEmpty) {
                getText(R.string.label_restore)
            } else {
                getText(R.string.label_backup)
            }
            preference.title = title
        }
    }

    private fun showFilterHideNotesBasedOnKeywords() {
        val preference = findPreference<Preference?>(MyPreferences.KEY_FILTER_HIDE_NOTES_BASED_ON_KEYWORDS) as EditTextPreference?
        if (preference != null) {
            val filter = KeywordsFilter(preference.text)
            if (filter.isEmpty) {
                preference.setSummary(R.string.this_option_is_turned_off)
            } else {
                preference.summary = filter.toString()
            }
        }
    }

    private fun showAuthorInTimeline() {
        SharedPreferencesUtil.showListPreference(this, MyPreferences.KEY_ACTOR_IN_TIMELINE,
                R.array.actor_in_timeline_values, R.array.actor_in_timeline_entries,
                R.string.summary_preference_user_in_timeline)
    }

    private fun showTapOnATimelineTitleBehaviour() {
        showListPreference(MyPreferences.KEY_TAP_ON_A_TIMELINE_TITLE_BEHAVIOUR)
    }

    private fun showCustomLocale() {
        showListPreference(MyPreferences.KEY_CUSTOM_LOCALE)
    }

    private fun showThemeColor() {
        showListPreference(MyPreferences.KEY_THEME_COLOR)
    }

    private fun showThemeSize() {
        showListPreference(MyPreferences.KEY_THEME_SIZE)
    }

    private fun showBackgroundColor() {
        showListPreference(MyPreferences.KEY_BACKGROUND_COLOR)
    }

    private fun showActionBarBackgroundColor() {
        showListPreference(MyPreferences.KEY_ACTION_BAR_BACKGROUND_COLOR)
    }

    private fun showActionBarTextColor() {
        showListPreference(MyPreferences.KEY_ACTION_BAR_TEXT_COLOR)
    }

    private fun showManageTimelines() {
        val timeline: Timeline =  MyContextHolder.myContextHolder.getNow().timelines().getDefault()
        val preference = findPreference<Preference?>(KEY_MANAGE_TIMELINES)
        if (preference != null) {
            preference.summary = StringUtil.format(context, R.string.default_timeline_summary,
                    TimelineTitle.from( MyContextHolder.myContextHolder.getNow(), timeline).toString())
        }
    }

    private fun showMaxDistanceBetweenDuplicates() {
        val preference = findPreference<Preference?>(MyPreferences.KEY_MAX_DISTANCE_BETWEEN_DUPLICATES)
        if (preference != null) {
            val value = MyPreferences.getMaxDistanceBetweenDuplicates()
            preference.summary = if (value > 0) value.toString() else getText(R.string.this_option_is_turned_off)
        }
    }

    private fun showMaximumSizeOfAttachment() {
        val preference = findPreference<Preference?>(MyPreferences.KEY_MAXIMUM_SIZE_OF_ATTACHMENT_MB)
        if (preference != null) {
            preference.summary = Formatter.formatShortFileSize(activity,
                    MyPreferences.getMaximumSizeOfAttachmentBytes())
        }
    }

    private fun showMaximumSizeOfCachedMedia() {
        showMaximumSizeOfCachedMedia(Optional.empty())
        val backgroundFunc: (MySettingsFragment?) -> Try<Optional<Long>> = { fragment: MySettingsFragment? ->
            Try.success(Optional.of(MyStorage.getMediaFilesSize()))
        }
        val uiConsumer: (MySettingsFragment?) -> Consumer<Try<Optional<Long>>> = { fragment: MySettingsFragment? ->
            Consumer<Try<Optional<Long>>>{ size: Try<Optional<Long>> ->
                size.onSuccess { optSize ->
                    fragment?.showMaximumSizeOfCachedMedia(optSize)
                }

            }
        }

        AsyncTaskLauncher.execute<MySettingsFragment, Optional<Long>>(this, backgroundFunc, uiConsumer)
    }

    private fun showMaximumSizeOfCachedMedia(size: Optional<Long>) {
        TryUtils.ofNullable(findPreference<Preference?>(MyPreferences.KEY_MAXIMUM_SIZE_OF_CACHED_MEDIA_MB))
                .map { preference: Preference ->
                    preference.setSummary(Formatter.formatShortFileSize(activity,
                            MyPreferences.getMaximumSizeOfCachedMediaBytes()) +
                            size.map { s: Long -> " (" + getText(R.string.reltime_just_now) + ": " + I18n.formatBytes(s) + ")" }
                                    .orElse(""))
                    true
                }
    }

    private fun showImageAnimations() {
        if (Build.VERSION.SDK_INT < 28) {
            val screen = preferenceScreen
            val preference = findPreference<Preference?>(MyPreferences.KEY_SHOW_IMAGE_ANIMATIONS)
            if (screen != null && preference != null) {
                screen.removePreference(preference)
            }
        }
    }

    private fun showListPreference(key: String) {
        val preference = findPreference<Preference?>(key) as ListPreference?
        if (preference != null) {
            preference.summary = preference.entry
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val activity = activity
        if (activity != null) when (preference.getKey()) {
            MyPreferences.KEY_USE_EXTERNAL_STORAGE_NEW -> if (preference is CheckBoxPreference) {
                storageSwitch.showSwitchStorageDialog(ActivityRequestCode.MOVE_DATA_BETWEEN_STORAGES,
                        preference.isChecked())
            }
            KEY_ADD_NEW_ACCOUNT -> AccountSettingsActivity.startAddingNewAccount(activity, null, false)
            KEY_DELETE_OLD_DATA -> DialogFactory.showOkCancelDialog(activity, getText(R.string.delete_old_data), "")
                                    { doLaunch: Boolean -> launchDataPruner(doLaunch) }
            KEY_MANAGE_ACCOUNTS -> startActivity(Intent(activity, ManageAccountsActivity::class.java))
            KEY_MANAGE_ACCOUNTS_ANDROID -> {
                /**
                 * Start system activity which allow to manage list of accounts
                 * See [
 * Android - How to Create Intent to open the activity that displays the “Accounts & Sync settings” screen](http://stackoverflow.com/questions/3010103/android-how-to-create-intent-to-open-the-activity-that-displays-the-accounts)
                 */
                val intent = Intent(Settings.ACTION_SYNC_SETTINGS)
                intent.putExtra(Settings.EXTRA_AUTHORITIES, arrayOf<String?>(MatchedUri.AUTHORITY))
                startActivity(intent)
            }
            KEY_BACKUP_RESTORE -> if ( MyContextHolder.myContextHolder.getNow().accounts().isEmpty) {
                startActivity(Intent(activity, RestoreActivity::class.java))
            } else {
                startActivity(Intent(activity, BackupActivity::class.java))
            }
            KEY_CHECK_DATA -> {
                preference.setEnabled(false)
                DialogFactory.showOkCancelDialog(this, R.string.check_and_fix_data,
                        R.string.full_check, ActivityRequestCode.CHECK_DATA_INCLUDE_LONG)
            }
            KEY_MANAGE_ORIGIN_SYSTEMS -> startActivity(Intent(activity, PersistentOriginList::class.java))
            KEY_MANAGE_TIMELINES -> startActivity(Intent(activity, ManageTimelines::class.java))
            KEY_ABOUT_APPLICATION -> HelpActivity.startMe(activity, false, HelpActivity.PAGE_LOGO)
            KEY_CHANGE_LOG -> HelpActivity.startMe(activity, false, HelpActivity.PAGE_CHANGELOG)
            KEY_USER_GUIDE -> HelpActivity.startMe(activity, false, HelpActivity.PAGE_USER_GUIDE)
            MyPreferences.KEY_COMMANDS_QUEUE -> startActivity(Intent(activity, QueueViewer::class.java))
            KEY_NOTIFICATION_SELECT_RINGTONE -> pickRingtone()
            else -> {
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    private fun launchDataPruner(doLaunch: Boolean) {
        if (!doLaunch) return
        val activity = activity as MyActivity? ?: return

        val progressListener = DefaultProgressListener(activity, R.string.delete_old_data, "DataPruner")
        progressListener.setCancelable(true)
        val pruner = DataPruner( MyContextHolder.myContextHolder.getNow())
                .setLogger(ProgressLogger(progressListener))
                .setPruneNow()
        AsyncTaskLauncher.execute { pruner.prune() }
    }

    private fun pickRingtone() {
        val activity = activity ?: return
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, activity.getText(R.string.notification_sound))
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        val currentRingtone = NotificationMethodType.SOUND.uri
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentRingtone)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
        startActivityForResult(intent, ActivityRequestCode.PICK_RINGTONE.id)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (mIgnorePreferenceChange || onSharedPreferenceChangedIsBusy ||
                !MyContextHolder.myContextHolder.getNow().initialized() ||
                storageSwitch.isDataBeingMoved()) {
            return
        }
        onSharedPreferenceChangedIsBusy = true
        try {
            MyLog.logSharedPreferencesValue(this, key)
            MyPreferences.onPreferencesChanged()
            when (key) {
                MyPreferences.KEY_CUSTOM_LOCALE -> {
                    activity?.let { MyLocale.setLocale(it)}
                    initializeThenRestartActivity()
                }
                MyPreferences.KEY_THEME_COLOR -> {
                    showThemeColor()
                    initializeThenRestartActivity()
                }
                MyPreferences.KEY_THEME_SIZE -> showThemeSize()
                MyPreferences.KEY_BACKGROUND_COLOR -> showBackgroundColor()
                MyPreferences.KEY_ACTION_BAR_BACKGROUND_COLOR -> {
                    showActionBarBackgroundColor()
                    initializeThenRestartActivity()
                }
                MyPreferences.KEY_ACTION_BAR_TEXT_COLOR -> {
                    showActionBarTextColor()
                    initializeThenRestartActivity()
                }
                MyPreferences.KEY_DONT_SYNCHRONIZE_OLD_NOTES -> showDontSynchronizeOldNotes()
                MyPreferences.KEY_SYNC_FREQUENCY_SECONDS -> {
                     MyContextHolder.myContextHolder.getNow().accounts().onDefaultSyncFrequencyChanged()
                    showFrequency()
                }
                MyPreferences.KEY_CONNECTION_TIMEOUT_SECONDS -> showConnectionTimeout()
                MyPreferences.KEY_NOTIFICATION_METHOD_SOUND -> showRingtone()
                MyPreferences.KEY_HISTORY_SIZE -> showHistorySize()
                MyPreferences.KEY_HISTORY_TIME -> showHistoryTime()
                MyPreferences.KEY_MIN_LOG_LEVEL -> showMinLogLevel()
                MyPreferences.KEY_ACTOR_IN_TIMELINE -> showAuthorInTimeline()
                MyPreferences.KEY_TAP_ON_A_TIMELINE_TITLE_BEHAVIOUR -> showTapOnATimelineTitleBehaviour()
                MyPreferences.KEY_FILTER_HIDE_NOTES_BASED_ON_KEYWORDS -> showFilterHideNotesBasedOnKeywords()
                MyPreferences.KEY_DEFAULT_TIMELINE -> showManageTimelines()
                MyPreferences.KEY_ROUNDED_AVATARS -> ImageCaches.setAvatarsRounded()
                MyPreferences.KEY_MAX_DISTANCE_BETWEEN_DUPLICATES -> showMaxDistanceBetweenDuplicates()
                MyPreferences.KEY_MAXIMUM_SIZE_OF_ATTACHMENT_MB -> showMaximumSizeOfAttachment()
                MyPreferences.KEY_MAXIMUM_SIZE_OF_CACHED_MEDIA_MB -> showMaximumSizeOfCachedMedia()
                MyPreferences.KEY_APP_INSTANCE_NAME -> showAppInstanceName()
                else -> {
                }
            }
        } finally {
            onSharedPreferenceChangedIsBusy = false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (ActivityRequestCode.fromId(requestCode)) {
            ActivityRequestCode.MOVE_DATA_BETWEEN_STORAGES -> {
                showUseExternalStorage()
                if (resultCode == Activity.RESULT_OK) {
                    storageSwitch.move()
                }
            }
            ActivityRequestCode.CHECK_DATA_INCLUDE_LONG -> {
                checkDataIncludeLong = resultCode == Activity.RESULT_OK
                DialogFactory.showOkCancelDialog(this, R.string.check_and_fix_data,
                        R.string.count_only, ActivityRequestCode.CHECK_DATA_COUNT_ONLY)
            }
            ActivityRequestCode.CHECK_DATA_COUNT_ONLY -> launchDataChecker(resultCode)
            ActivityRequestCode.PICK_RINGTONE -> if (resultCode == Activity.RESULT_OK) {
                val value = data?.getParcelableExtra<Uri?>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                val uri = UriUtils.notNull(value)
                MyLog.v(this, "Ringtone set to uri:$uri")
                SharedPreferencesUtil.putString(MyPreferences.KEY_NOTIFICATION_METHOD_SOUND,
                        if (UriUtils.isEmpty(uri)) "" else uri.toString())
                showRingtone()
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun launchDataChecker(resultCode: Int) {
        val activity = activity as MyActivity? ?: return

        val progressListener: ProgressLogger.ProgressListener = DefaultProgressListener(
                activity, R.string.app_name, "DataChecker")
        progressListener.setCancelable(true)
        DataChecker.fixDataAsync(ProgressLogger(progressListener),
                checkDataIncludeLong,
                resultCode == Activity.RESULT_OK)
    }

    fun getMyActivity(): MySettingsActivity? {
        return activity as MySettingsActivity?
    }

    /** @return true if we are restarting
     */
    private fun initializeThenRestartActivity(): Boolean {
        val activity = getMyActivity()
        return activity != null && activity.initializeThenRestartActivity()
    }

    companion object {
        val FRAGMENT_TAG: String = "settings_fragment"
        private val KEY_ROOT: String = "key_root"
        private val KEY_ABOUT_APPLICATION: String = "about_application"
        private val KEY_ADD_NEW_ACCOUNT: String = "add_new_account"
        private val KEY_BACKUP_RESTORE: String = "backup_restore"
        private val KEY_CHANGE_LOG: String = "change_log"
        private val KEY_CHECK_DATA: String = "check_data"
        private val KEY_DELETE_OLD_DATA: String = "delete_old_data"
        val KEY_MANAGE_ACCOUNTS: String = "manage_accounts_internally"
        private val KEY_MANAGE_ACCOUNTS_ANDROID: String = "manage_accounts_android"
        private val KEY_MANAGE_ORIGIN_SYSTEMS: String = "manage_origin_systems"
        private val KEY_MANAGE_TIMELINES: String = "manage_timelines"
        private val KEY_NOTIFICATION_SELECT_RINGTONE: String = "select_ringtone"
        private val KEY_USER_GUIDE: String = "user_guide"
    }
}
