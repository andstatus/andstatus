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

package org.andstatus.app.context;

import android.app.backup.BackupManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import org.andstatus.app.R;
import org.andstatus.app.timeline.TapOnATimelineTitleBehaviour;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.UriUtils;

/**
 * This is a central point of accessing SharedPreferences
 * @author yvolk@yurivolkov.com
 */
public class MyPreferences {

    // ----------------------------------------------------------
    // Accounts
    // (no keys/settings here yet)

    // ----------------------------------------------------------
    // Appearance
    public static final String KEY_CUSTOM_LOCALE = "custom_locale";
    public static final String KEY_THEME_COLOR = "theme_color";
    public static final String KEY_ACTION_BAR_BACKGROUND_COLOR = "action_bar_background_color";
    public static final String KEY_ACTION_BAR_TEXT_COLOR = "action_bar_text_color";
    public static final String KEY_BACKGROUND_COLOR = "background_color";
    public static final String KEY_THEME_SIZE = "theme_size";
    public static final String KEY_ROUNDED_AVATARS = "rounded_avatars";

    // ----------------------------------------------------------
    // Timeline
    public static final String KEY_DEFAULT_TIMELINE = "default_timeline";
    public static final String KEY_TAP_ON_A_TIMELINE_TITLE_BEHAVIOUR = "tap_on_a_timeline_title";
    public static final String KEY_ACTOR_IN_TIMELINE = "user_in_timeline";
    public static final String KEY_SHOW_AVATARS = "show_avatars";
    public static final String KEY_SHOW_ORIGIN = "show_origin";
    public static final String KEY_SHOW_SENSITIVE_CONTENT = "show_sensitive";
    public static final String KEY_MARK_REPLIES_TO_ME_IN_TIMELINE = "mark_replies_in_timeline";
    public static final String KEY_SHOW_BUTTONS_BELOW_NOTE = "show_buttons_below_message";
    public static final String KEY_OLD_NOTES_FIRST_IN_CONVERSATION = "old_messages_first_in_conversation";
    public static final String KEY_REFRESH_TIMELINE_AUTOMATICALLY = "refresh_timeline_automatically";
    public static final String KEY_SHOW_THREADS_OF_CONVERSATION = "show_threads_of_conversation";
    public static final String KEY_MAX_DISTANCE_BETWEEN_DUPLICATES = "max_distance_between_duplicates";
    public static final int MAX_DISTANCE_BETWEEN_DUPLICATES_DEFAULT = 5;
    public static final String KEY_SHOW_MY_ACCOUNT_WHICH_DOWNLOADED = "show_my_account_which_downloaded";

    // ----------------------------------------------------------
    // Gestures
    public static final String KEY_LONG_PRESS_TO_OPEN_CONTEXT_MENU = "long_press_to_open_context_menu";
    public static final String KEY_ENTER_SENDS_NOTE = "enter_sends_message";

    // ----------------------------------------------------------
    // Attachments
    public static final String KEY_DOWNLOAD_AND_DISPLAY_ATTACHED_IMAGES = "show_attached_images";
    public static final String KEY_DOWNLOAD_ATTACHED_VIDEO = "download_attached_video";
    public static final String KEY_ATTACH_IMAGES_TO_MY_NOTES = "attach_images";
    public static final String KEY_DOWNLOAD_ATTACHMENTS_OVER_WIFI_ONLY = "download_attachments_over_wifi_only";
    public static final String KEY_MAXIMUM_SIZE_OF_ATTACHMENT_MB = "maximum_size_of_attachment_mb";
    public static final String KEY_MODERN_INTERFACE_TO_SELECT_AN_ATTACHMENT = "use_kitkat_media_chooser";

    // ----------------------------------------------------------
    // Syncing
    public static final String KEY_SYNC_FREQUENCY_SECONDS = "fetch_frequency";
    private static final long SYNC_FREQUENCY_DEFAULT_SECONDS = 180;
    public static final String KEY_SYNC_OVER_WIFI_ONLY = "sync_over_wifi_only";
    public static final String KEY_SYNC_WHILE_USING_APPLICATION = "sync_while_using_application";
    public static final String KEY_SYNC_INDICATOR_ON_TIMELINE = "sync_indicator_on_timeline";
    public static final String KEY_SYNC_AFTER_NOTE_WAS_SENT = "sync_after_message_was_sent";
    public static final String KEY_DONT_SYNCHRONIZE_OLD_NOTES = "dont_synchronize_old_messages";
    public static final String KEY_CONNECTION_TIMEOUT_SECONDS = "connection_timeout";
    private static final long CONNECTION_TIMEOUT_DEFAULT_SECONDS = 30;

    // ----------------------------------------------------------
    // Filters
    public static final String KEY_FILTER_HIDE_NOTES_BASED_ON_KEYWORDS = "hide_messages_based_on_keywords";
    public static final String KEY_FILTER_HIDE_REPLIES_NOT_TO_ME_OR_FRIENDS = "hide_replies_not_to_me_or_friends";

    // ----------------------------------------------------------
    // Notifications, How to notify
    public static final String KEY_NOTIFICATION_ICON_ALTERNATIVE = "notification_icon_alternative";
    public static final String KEY_NOTIFICATION_METHOD_SOUND = "notification_sound";

    // ----------------------------------------------------------
    // Storage
    public static final String KEY_USE_EXTERNAL_STORAGE = "use_external_storage";
    /** New value for #KEY_USE_EXTERNAL_STORAGE to be confirmed/processed */
    public static final String KEY_USE_EXTERNAL_STORAGE_NEW = "use_external_storage_new";
    public static final String KEY_HISTORY_SIZE = "history_size";
    public static final String KEY_HISTORY_TIME = "history_time";
    public static final String KEY_MAXIMUM_SIZE_OF_CACHED_MEDIA_MB = "maximum_size_of_cached_media_mb";
    public static final String KEY_ENABLE_ANDROID_BACKUP = "enable_android_backup";
    public static final String KEY_BACKUP_DOWNLOADS = "backup_downloads";
    public static final String KEY_LAST_BACKUP_URI = "last_backup_uri";
    public static final String KEY_APP_INSTANCE_NAME = "app_instance_name";

    // ----------------------------------------------------------
    // Information
    // (no keys/settings here yet)

    // ----------------------------------------------------------
    // Troubleshooting (logging and debugging)
    public static final String KEY_COMMANDS_QUEUE = "commands_queue";
    /** Minimum logging level for the whole application (i.e. for any tag) */
    public static final String KEY_MIN_LOG_LEVEL = "min_log_level";
    public static final String KEY_DEBUGGING_INFO_IN_UI = "debugging_info_in_ui";
    public static final String KEY_LOG_NETWORK_LEVEL_MESSAGES = "log_network_level_messages";
    public static final String KEY_LOG_EVERYTHING_TO_FILE = "log_everything_to_file";
    public static final String KEY_BACKUP_LOG_FILES = "backup_log_files";

    // ----------------------------------------------------------
    // Non-UI persistent items ("preferences")
    /** System time when shared preferences were changed */
    public static final String KEY_PREFERENCES_CHANGE_TIME = "preferences_change_time";
    public static final String KEY_DATA_PRUNED_DATE = "data_pruned_date";
    /** Version code of last opened application (int) */
    public static final String KEY_VERSION_CODE_LAST = "version_code_last";
    public static final String KEY_BEING_EDITED_NOTE_ID = "draft_message_id";

    private static final boolean COLLAPSE_DUPLICATES_DEFAULT_VALUE = true;

    public static final int BYTES_IN_MB = 1000 * 1000;

    private MyPreferences(){
        // Non instantiable
    }

    public static long getDontSynchronizeOldNotes() {
        return SharedPreferencesUtil.getLongStoredAsString(KEY_DONT_SYNCHRONIZE_OLD_NOTES, 0);
    }

    public static int getConnectionTimeoutMs() {
        return (int) java.util.concurrent.TimeUnit.SECONDS.toMillis(
                SharedPreferencesUtil.getLongStoredAsString(
                KEY_CONNECTION_TIMEOUT_SECONDS, CONNECTION_TIMEOUT_DEFAULT_SECONDS));
    }

    /**
     * @return the number of seconds between two sync ("fetch"...) actions.
     */
    public static long getSyncFrequencySeconds() {
        return SharedPreferencesUtil.getLongStoredAsString(KEY_SYNC_FREQUENCY_SECONDS,
                SYNC_FREQUENCY_DEFAULT_SECONDS);
    }

    public static boolean isSyncOverWiFiOnly() {
        return SharedPreferencesUtil.getBoolean(KEY_SYNC_OVER_WIFI_ONLY, false);
    }

    public static void setIsSyncOverWiFiOnly(boolean overWiFi) {
        SharedPreferencesUtil.putBoolean(KEY_SYNC_OVER_WIFI_ONLY, overWiFi);
    }

    public static boolean isSyncWhileUsingApplicationEnabled() {
        return SharedPreferencesUtil.getBoolean(KEY_SYNC_WHILE_USING_APPLICATION, true);
    }

    public static boolean isDownloadAttachmentsOverWiFiOnly() {
        return SharedPreferencesUtil.getBoolean(KEY_DOWNLOAD_ATTACHMENTS_OVER_WIFI_ONLY, true);
    }

    public static void setDownloadAttachmentsOverWiFiOnly(boolean overWiFi) {
        SharedPreferencesUtil.putBoolean(KEY_DOWNLOAD_ATTACHMENTS_OVER_WIFI_ONLY, overWiFi);
    }

    public static boolean isLongPressToOpenContextMenu() {
        return SharedPreferencesUtil.getBoolean(KEY_LONG_PRESS_TO_OPEN_CONTEXT_MENU, false);
    }

    public static boolean getShowAvatars() {
        return SharedPreferencesUtil.getBoolean(KEY_SHOW_AVATARS, true);
    }

    public static boolean getDownloadAndDisplayAttachedImages() {
        return SharedPreferencesUtil.getBoolean(KEY_DOWNLOAD_AND_DISPLAY_ATTACHED_IMAGES, true);
    }

    public static boolean getDownloadAttachedVideo() {
        return SharedPreferencesUtil.getBoolean(KEY_DOWNLOAD_ATTACHED_VIDEO, false);
    }

    public static boolean getShowOrigin() {
        return SharedPreferencesUtil.getBoolean(KEY_SHOW_ORIGIN, false);
    }

    public static boolean isShowSensitiveContent() {
        return SharedPreferencesUtil.getBoolean(KEY_SHOW_SENSITIVE_CONTENT, false);
    }

    public static void setShowSensitiveContent(boolean v) {
        SharedPreferencesUtil.putBoolean(KEY_SHOW_SENSITIVE_CONTENT, v);
    }

    public static TapOnATimelineTitleBehaviour getTapOnATimelineTitleBehaviour() {
        return TapOnATimelineTitleBehaviour.load(
                SharedPreferencesUtil.getString(KEY_TAP_ON_A_TIMELINE_TITLE_BEHAVIOUR, ""));
    }

    public static ActorInTimeline getActorInTimeline() {
        return ActorInTimeline.load(SharedPreferencesUtil.getString(KEY_ACTOR_IN_TIMELINE, ""));
    }

    public static boolean isShowDebuggingInfoInUi() {
        return SharedPreferencesUtil.getBoolean(KEY_DEBUGGING_INFO_IN_UI, false);
    }

    public static int getActionBarTextHomeIconResourceId() {
        return SharedPreferencesUtil.getString(KEY_ACTION_BAR_TEXT_COLOR, "")
                .equals("ActionBarTextBlack")
                ? R.drawable.icon_black_24dp : R.drawable.icon_white_24dp;
    }

    /**
     * @return System time when AndStatus preferences were last time changed.
     * We take into account here time when accounts were added/removed...
     */
    public static long getPreferencesChangeTime() {
        return SharedPreferencesUtil.getLong(KEY_PREFERENCES_CHANGE_TIME);
    }

    /**
     *  Event: Preferences have changed right now
     *  Remember when last changes to the preferences were made
     */
    public static void onPreferencesChanged() {
        SharedPreferencesUtil.forget();
        SharedPreferencesUtil.putLong(KEY_PREFERENCES_CHANGE_TIME, System.currentTimeMillis());
        Context context = MyContextHolder.get().context();
        if (context != null && SharedPreferencesUtil.getBoolean(KEY_ENABLE_ANDROID_BACKUP, false)) {
            new BackupManager(context).dataChanged();
        }
    }

    public static boolean isCollapseDuplicates() {
        return COLLAPSE_DUPLICATES_DEFAULT_VALUE;
    }

    public static boolean isShowThreadsOfConversation() {
        return SharedPreferencesUtil.getBoolean(KEY_SHOW_THREADS_OF_CONVERSATION, true);
    }

    public static void setShowThreadsOfConversation(boolean v) {
        SharedPreferencesUtil.putBoolean(KEY_SHOW_THREADS_OF_CONVERSATION, v);
    }

    public static void setOldNotesFirstInConversation(boolean v) {
        SharedPreferencesUtil.putBoolean(KEY_OLD_NOTES_FIRST_IN_CONVERSATION, v);
    }

    public static boolean areOldNotesFirstInConversation() {
        return SharedPreferencesUtil.getBoolean(KEY_OLD_NOTES_FIRST_IN_CONVERSATION, false);
    }

    public static boolean isLogEverythingToFile() {
        return SharedPreferencesUtil.getBoolean(MyPreferences.KEY_LOG_EVERYTHING_TO_FILE, MyContextHolder.get().isTestRun());
    }

    public static boolean isRefreshTimelineAutomatically() {
        return SharedPreferencesUtil.getBoolean(KEY_REFRESH_TIMELINE_AUTOMATICALLY, true);
    }

    public static int getMaxDistanceBetweenDuplicates() {
        return SharedPreferencesUtil.getIntStoredAsString(KEY_MAX_DISTANCE_BETWEEN_DUPLICATES,
                MAX_DISTANCE_BETWEEN_DUPLICATES_DEFAULT);
    }

    public static long getMaximumSizeOfAttachmentBytes() {
        return Math.max(SharedPreferencesUtil.getLongStoredAsString(KEY_MAXIMUM_SIZE_OF_ATTACHMENT_MB, 5), 1)
                * BYTES_IN_MB;
    }

    public static long getMaximumSizeOfCachedMediaBytes() {
        return Math.max(SharedPreferencesUtil.getLongStoredAsString(KEY_MAXIMUM_SIZE_OF_CACHED_MEDIA_MB, 1000), 1)
                * BYTES_IN_MB;
    }

    public static boolean isBackupDownloads() {
        return SharedPreferencesUtil.getBoolean(KEY_BACKUP_DOWNLOADS, false);
    }

    public static boolean isShowMyAccountWhichDownloadedActivity() {
        return SharedPreferencesUtil.getBoolean(KEY_SHOW_MY_ACCOUNT_WHICH_DOWNLOADED, false);
    }

    public static void setBeingEditedNoteId(long id) {
        SharedPreferencesUtil.putLong(KEY_BEING_EDITED_NOTE_ID, id);
    }

    public static long getBeingEditedNoteId() {
        return SharedPreferencesUtil.getLong(KEY_BEING_EDITED_NOTE_ID, 0);
    }

    public static boolean isBackupLogFiles() {
        return SharedPreferencesUtil.getBoolean(KEY_BACKUP_LOG_FILES, false);
    }

    public static boolean isLogNetworkLevelMessages() {
        return SharedPreferencesUtil.getBoolean(KEY_LOG_NETWORK_LEVEL_MESSAGES, false);
    }

    public static void setLastBackupUri(Uri backupUri) {
        SharedPreferencesUtil.putString(KEY_LAST_BACKUP_URI, UriUtils.isEmpty(backupUri) ? "" : backupUri.toString());
    }

    public static Uri getLastBackupUri() {
        return UriUtils.fromString(SharedPreferencesUtil.getString(KEY_LAST_BACKUP_URI,
                "content://com.android.externalstorage.documents/tree/primary%3Abackups%2FAndStatus"));
    }

    public static String getAppInstanceName() {
        return SharedPreferencesUtil.getString(KEY_APP_INSTANCE_NAME,
                (Build.BRAND + "-" + Build.MODEL).replaceAll(" ", "-"));
    }
}
