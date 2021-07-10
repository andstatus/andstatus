/*
 * Copyright (C) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.backup.BackupManager
import android.content.Context
import android.net.Uri
import android.os.Build
import org.andstatus.app.R
import org.andstatus.app.timeline.TapOnATimelineTitleBehaviour
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.UriUtils
import java.util.concurrent.TimeUnit

/**
 * This is a central point of accessing SharedPreferences
 * @author yvolk@yurivolkov.com
 */
object MyPreferences {
    // ----------------------------------------------------------
    // Accounts
    // (no keys/settings here yet)
    // ----------------------------------------------------------
    // Appearance
    val KEY_CUSTOM_LOCALE: String = "custom_locale"
    val KEY_THEME_COLOR: String = "theme_color"
    val KEY_ACTION_BAR_BACKGROUND_COLOR: String = "action_bar_background_color"
    val KEY_ACTION_BAR_TEXT_COLOR: String = "action_bar_text_color"
    val KEY_BACKGROUND_COLOR: String = "background_color"
    val KEY_THEME_SIZE: String = "theme_size"
    val KEY_ROUNDED_AVATARS: String = "rounded_avatars"
    val KEY_SHOW_IMAGE_ANIMATIONS: String = "show_image_animations"

    // ----------------------------------------------------------
    // Timeline
    val KEY_DEFAULT_TIMELINE: String = "default_timeline"
    val KEY_TAP_ON_A_TIMELINE_TITLE_BEHAVIOUR: String = "tap_on_a_timeline_title"
    val KEY_ACTOR_IN_TIMELINE: String = "user_in_timeline"
    val KEY_SHOW_AVATARS: String = "show_avatars"
    val KEY_SHOW_ORIGIN: String = "show_origin"
    val KEY_SHOW_SENSITIVE_CONTENT: String = "show_sensitive"
    val KEY_MARK_REPLIES_TO_ME_IN_TIMELINE: String = "mark_replies_in_timeline"
    val KEY_SHOW_BUTTONS_BELOW_NOTE: String = "show_buttons_below_message"
    val KEY_OLD_NOTES_FIRST_IN_CONVERSATION: String = "old_messages_first_in_conversation"
    val KEY_REFRESH_TIMELINE_AUTOMATICALLY: String = "refresh_timeline_automatically"
    val KEY_SHOW_THREADS_OF_CONVERSATION: String = "show_threads_of_conversation"
    val KEY_MAX_DISTANCE_BETWEEN_DUPLICATES: String = "max_distance_between_duplicates"
    const val MAX_DISTANCE_BETWEEN_DUPLICATES_DEFAULT = 5
    val KEY_SHOW_MY_ACCOUNT_WHICH_DOWNLOADED: String = "show_my_account_which_downloaded"

    // ----------------------------------------------------------
    // Gestures
    val KEY_LONG_PRESS_TO_OPEN_CONTEXT_MENU: String = "long_press_to_open_context_menu"
    val KEY_SEND_IN_EDITOR_BUTTON: String = "send_in_editor_button"

    // ----------------------------------------------------------
    // Attachments
    val KEY_DOWNLOAD_AND_DISPLAY_ATTACHED_IMAGES: String = "show_attached_images"
    val KEY_DOWNLOAD_ATTACHED_VIDEO: String = "download_attached_video"
    val KEY_ATTACH_IMAGES_TO_MY_NOTES: String = "attach_images"
    val KEY_DOWNLOAD_ATTACHMENTS_OVER_WIFI_ONLY: String = "download_attachments_over_wifi_only"
    val KEY_MAXIMUM_SIZE_OF_ATTACHMENT_MB: String = "maximum_size_of_attachment_mb"
    val KEY_MODERN_INTERFACE_TO_SELECT_AN_ATTACHMENT: String = "use_kitkat_media_chooser"

    // ----------------------------------------------------------
    // Syncing
    val KEY_SYNC_FREQUENCY_SECONDS: String = "fetch_frequency"
    private const val SYNC_FREQUENCY_DEFAULT_SECONDS: Long = 180
    val KEY_SYNC_OVER_WIFI_ONLY: String = "sync_over_wifi_only"
    val KEY_SYNC_WHILE_USING_APPLICATION: String = "sync_while_using_application"
    val KEY_SYNC_INDICATOR_ON_TIMELINE: String = "sync_indicator_on_timeline"
    val KEY_SYNC_AFTER_NOTE_WAS_SENT: String = "sync_after_message_was_sent"
    val KEY_DONT_SYNCHRONIZE_OLD_NOTES: String = "dont_synchronize_old_messages"
    val KEY_CONNECTION_TIMEOUT_SECONDS: String = "connection_timeout"
    private const val CONNECTION_TIMEOUT_DEFAULT_SECONDS: Long = 30

    // ----------------------------------------------------------
    // Filters
    val KEY_FILTER_HIDE_NOTES_BASED_ON_KEYWORDS: String = "hide_messages_based_on_keywords"
    val KEY_FILTER_HIDE_REPLIES_NOT_TO_ME_OR_FRIENDS: String = "hide_replies_not_to_me_or_friends"

    // ----------------------------------------------------------
    // Notifications, How to notify
    val KEY_NOTIFICATION_ICON_ALTERNATIVE: String = "notification_icon_alternative"
    val KEY_NOTIFICATION_METHOD_SOUND: String = "notification_sound"

    // ----------------------------------------------------------
    // Storage
    val KEY_USE_EXTERNAL_STORAGE: String = "use_external_storage"

    /** New value for #KEY_USE_EXTERNAL_STORAGE to be confirmed/processed  */
    val KEY_USE_EXTERNAL_STORAGE_NEW: String = "use_external_storage_new"
    val KEY_HISTORY_SIZE: String = "history_size"
    val KEY_HISTORY_TIME: String = "history_time"
    val KEY_MAXIMUM_SIZE_OF_CACHED_MEDIA_MB: String = "maximum_size_of_cached_media_mb"
    val KEY_ENABLE_ANDROID_BACKUP: String = "enable_android_backup"
    val KEY_BACKUP_DOWNLOADS: String = "backup_downloads"
    val KEY_LAST_BACKUP_URI: String = "last_backup_uri"
    val KEY_APP_INSTANCE_NAME: String = "app_instance_name"

    // ----------------------------------------------------------
    // Information
    // (no keys/settings here yet)
    // ----------------------------------------------------------
    // Troubleshooting (logging and debugging)
    val KEY_COMMANDS_QUEUE: String = "commands_queue"

    /** Minimum logging level for the whole application (i.e. for any tag)  */
    val KEY_MIN_LOG_LEVEL: String = "min_log_level"
    val KEY_DEBUGGING_INFO_IN_UI: String = "debugging_info_in_ui"
    val KEY_LOG_NETWORK_LEVEL_MESSAGES: String = "log_network_level_messages"
    val KEY_LOG_EVERYTHING_TO_FILE: String = "log_everything_to_file"
    val KEY_BACKUP_LOG_FILES: String = "backup_log_files"
    // ----------------------------------------------------------
    // Non-UI persistent items ("preferences")
    /** System time when shared preferences were changed  */
    val KEY_PREFERENCES_CHANGE_TIME: String = "preferences_change_time"
    val KEY_DATA_PRUNED_DATE: String = "data_pruned_date"

    /** Version code of last opened application (int)  */
    val KEY_VERSION_CODE_LAST: String = "version_code_last"
    val KEY_BEING_EDITED_NOTE_ID: String = "draft_message_id"
    private const val COLLAPSE_DUPLICATES_DEFAULT_VALUE = true
    const val BYTES_IN_MB = 1000 * 1000

    fun getDontSynchronizeOldNotes(): Long {
        return SharedPreferencesUtil.getLongStoredAsString(KEY_DONT_SYNCHRONIZE_OLD_NOTES, 0)
    }

    fun getConnectionTimeoutMs(): Int {
        return TimeUnit.SECONDS.toMillis(
                SharedPreferencesUtil.getLongStoredAsString(
                        KEY_CONNECTION_TIMEOUT_SECONDS, CONNECTION_TIMEOUT_DEFAULT_SECONDS)).toInt()
    }

    fun setConnectionTimeoutMs(value: Int) {
        val stringValue = TimeUnit.MILLISECONDS.toSeconds(value.toLong()).coerceAtLeast(1).toString()
        SharedPreferencesUtil.putString(KEY_CONNECTION_TIMEOUT_SECONDS, stringValue)
    }

    /**
     * @return the number of seconds between two sync ("fetch"...) actions.
     */
    fun getSyncFrequencySeconds(): Long {
        return SharedPreferencesUtil.getLongStoredAsString(KEY_SYNC_FREQUENCY_SECONDS,
                SYNC_FREQUENCY_DEFAULT_SECONDS)
    }

    fun isSyncOverWiFiOnly(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_SYNC_OVER_WIFI_ONLY, false)
    }

    fun setIsSyncOverWiFiOnly(overWiFi: Boolean) {
        SharedPreferencesUtil.putBoolean(KEY_SYNC_OVER_WIFI_ONLY, overWiFi)
    }

    fun isSyncWhileUsingApplicationEnabled(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_SYNC_WHILE_USING_APPLICATION, true)
    }

    fun isDownloadAttachmentsOverWiFiOnly(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_DOWNLOAD_ATTACHMENTS_OVER_WIFI_ONLY, true)
    }

    fun setDownloadAttachmentsOverWiFiOnly(overWiFi: Boolean) {
        SharedPreferencesUtil.putBoolean(KEY_DOWNLOAD_ATTACHMENTS_OVER_WIFI_ONLY, overWiFi)
    }

    fun isLongPressToOpenContextMenu(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_LONG_PRESS_TO_OPEN_CONTEXT_MENU, false)
    }

    fun getShowAvatars(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_SHOW_AVATARS, true)
    }

    fun getDownloadAndDisplayAttachedImages(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_DOWNLOAD_AND_DISPLAY_ATTACHED_IMAGES, true)
    }

    fun getDownloadAttachedVideo(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_DOWNLOAD_ATTACHED_VIDEO, false)
    }

    fun getShowOrigin(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_SHOW_ORIGIN, false)
    }

    fun isShowSensitiveContent(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_SHOW_SENSITIVE_CONTENT, false)
    }

    fun setShowSensitiveContent(v: Boolean) {
        SharedPreferencesUtil.putBoolean(KEY_SHOW_SENSITIVE_CONTENT, v)
    }

    fun getTapOnATimelineTitleBehaviour(): TapOnATimelineTitleBehaviour? {
        return TapOnATimelineTitleBehaviour.load(
                SharedPreferencesUtil.getString(KEY_TAP_ON_A_TIMELINE_TITLE_BEHAVIOUR, ""))
    }

    fun getActorInTimeline(): ActorInTimeline {
        return ActorInTimeline.load(SharedPreferencesUtil.getString(KEY_ACTOR_IN_TIMELINE, ""))
    }

    fun isShowDebuggingInfoInUi(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_DEBUGGING_INFO_IN_UI, false)
    }

    fun getActionBarTextHomeIconResourceId(): Int {
        return if (SharedPreferencesUtil.getString(KEY_ACTION_BAR_TEXT_COLOR, "")
                == "ActionBarTextBlack") R.drawable.icon_black_24dp else R.drawable.icon_white_24dp
    }

    fun isShowImageAnimations(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_SHOW_IMAGE_ANIMATIONS, true)
    }

    /**
     * @return System time when AndStatus preferences were last time changed.
     * We take into account here time when accounts were added/removed...
     */
    fun getPreferencesChangeTime(): Long {
        return SharedPreferencesUtil.getLong(KEY_PREFERENCES_CHANGE_TIME)
    }

    /**
     * Event: Preferences have changed right now
     * Remember when last changes to the preferences were made
     */
    fun onPreferencesChanged() {
        SharedPreferencesUtil.forget()
        SharedPreferencesUtil.putLong(KEY_PREFERENCES_CHANGE_TIME, System.currentTimeMillis())
        val context: Context? =  MyContextHolder.myContextHolder.getNow().context
        if (context != null && SharedPreferencesUtil.getBoolean(KEY_ENABLE_ANDROID_BACKUP, false)) {
            BackupManager(context).dataChanged()
        }
    }

    fun isCollapseDuplicates(): Boolean {
        return COLLAPSE_DUPLICATES_DEFAULT_VALUE
    }

    fun isShowThreadsOfConversation(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_SHOW_THREADS_OF_CONVERSATION, true)
    }

    fun setShowThreadsOfConversation(v: Boolean) {
        SharedPreferencesUtil.putBoolean(KEY_SHOW_THREADS_OF_CONVERSATION, v)
    }

    fun setOldNotesFirstInConversation(v: Boolean) {
        SharedPreferencesUtil.putBoolean(KEY_OLD_NOTES_FIRST_IN_CONVERSATION, v)
    }

    fun areOldNotesFirstInConversation(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_OLD_NOTES_FIRST_IN_CONVERSATION, false)
    }

    fun isLogEverythingToFile(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_LOG_EVERYTHING_TO_FILE,  MyContextHolder.myContextHolder.getNow().isTestRun)
    }

    fun isRefreshTimelineAutomatically(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_REFRESH_TIMELINE_AUTOMATICALLY, true)
    }

    fun getMaxDistanceBetweenDuplicates(): Int {
        return SharedPreferencesUtil.getIntStoredAsString(KEY_MAX_DISTANCE_BETWEEN_DUPLICATES,
                MAX_DISTANCE_BETWEEN_DUPLICATES_DEFAULT)
    }

    fun getMaximumSizeOfAttachmentBytes(): Long {
        return (Math.max(SharedPreferencesUtil.getLongStoredAsString(KEY_MAXIMUM_SIZE_OF_ATTACHMENT_MB, 5), 1)
                * BYTES_IN_MB)
    }

    val maximumSizeOfCachedMediaBytes: Long get() = SharedPreferencesUtil.getLongStoredAsString(
        KEY_MAXIMUM_SIZE_OF_CACHED_MEDIA_MB, 1000)
        .coerceAtLeast(1) * BYTES_IN_MB

    fun isBackupDownloads(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_BACKUP_DOWNLOADS, false)
    }

    fun isShowMyAccountWhichDownloadedActivity(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_SHOW_MY_ACCOUNT_WHICH_DOWNLOADED, false)
    }

    fun setBeingEditedNoteId(id: Long) {
        SharedPreferencesUtil.putLong(KEY_BEING_EDITED_NOTE_ID, id)
    }

    fun getBeingEditedNoteId(): Long {
        return SharedPreferencesUtil.getLong(KEY_BEING_EDITED_NOTE_ID, 0)
    }

    fun isBackupLogFiles(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_BACKUP_LOG_FILES, false)
    }

    fun isLogNetworkLevelMessages(): Boolean {
        return SharedPreferencesUtil.getBoolean(KEY_LOG_NETWORK_LEVEL_MESSAGES, false)
    }

    fun setLastBackupUri(backupUri: Uri?) {
        SharedPreferencesUtil.putString(KEY_LAST_BACKUP_URI, if (UriUtils.isEmpty(backupUri)) "" else backupUri.toString())
    }

    fun getLastBackupUri(): Uri {
        return UriUtils.fromString(SharedPreferencesUtil.getString(KEY_LAST_BACKUP_URI,
                "content://com.android.externalstorage.documents/tree/primary%3Abackups%2FAndStatus"))
    }

    fun getDeviceBrandModelString(): String {
        return (Build.BRAND + "-" + Build.MODEL).replace(" ".toRegex(), "-")
    }

    fun getAppInstanceName(): String {
        return SharedPreferencesUtil.getString(KEY_APP_INSTANCE_NAME, "")
    }
}
