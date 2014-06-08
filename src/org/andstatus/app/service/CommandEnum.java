package org.andstatus.app.service;

import android.content.Context;
import android.text.TextUtils;

import org.andstatus.app.R;

/**
 * The command to the MyService or to MyAppWidgetProvider as a
 * enum We use 'code' for persistence
 * 
 * @author yvolk@yurivolkov.com
 */
public enum CommandEnum {

    /**
     * The action is unknown
     */
    UNKNOWN("unknown"),
    /**
     * There is no action
     */
    EMPTY("empty"),
    /**
     * The action to fetch all usual timelines in the background.
     */
    AUTOMATIC_UPDATE("automatic-update", 0, -10, true),
    /**
     * Fetch timeline(s) of the specified type for the specified MyAccount. 
     */
    FETCH_TIMELINE("fetch-timeline", 0, -4, true),

    /**
     * Fetch avatar for the specified user and URL 
     */
    FETCH_AVATAR("fetch-avatar", R.string.title_command_fetch_avatar, -9, true),
    
    CREATE_FAVORITE("create-favorite", R.string.menu_item_favorite, 0, true), 
    DESTROY_FAVORITE("destroy-favorite", R.string.menu_item_destroy_favorite, 0, true),

    FOLLOW_USER("follow-user", R.string.menu_item_follow_user, 0, true), 
    STOP_FOLLOWING_USER("stop-following-user", R.string.menu_item_stop_following_user, 0, true),

    /**
     * This command is for sending both public and direct messages
     */
    UPDATE_STATUS("update-status", R.string.button_create_message, 10, true), 
    DESTROY_STATUS("destroy-status", R.string.menu_item_destroy_status, 3, true),
    GET_STATUS("get-status", R.string.title_command_get_status, 5, true),

    SEARCH_MESSAGE("search-message", R.string.options_menu_search, -4, true),
    
    REBLOG("reblog", R.string.menu_item_reblog, 9, true),
    DESTROY_REBLOG("destroy-reblog", R.string.menu_item_destroy_reblog, 3, true),

    RATE_LIMIT_STATUS("rate-limit-status", 0, 0, true),

    /**
     * Notify User about commands in the Queue
     */
    NOTIFY_QUEUE("notify-queue"),

    /**
     * Commands to the Widget New tweets|messages were successfully loaded
     * from the server
     */
    NOTIFY_DIRECT_MESSAGE("notify-direct-message"),
    /**
     * New messages in the Home timeline of Account
     */
    NOTIFY_HOME_TIMELINE("notify-home-timeline"),
    /**
     * Mentions and replies are currently shown in one timeline
     */
    NOTIFY_MENTIONS("notify-mentions"), 
            // TODO: Add NOTIFY_REPLIES("notify-replies"),
    /**
     * Clear previous notifications (because e.g. user opened a Timeline)
     */
    NOTIFY_CLEAR("notify-clear", 0, 20),

    /**
     * Stop the service after finishing all asynchronous treads (i.e. not immediately!)
     */
    STOP_SERVICE("stop-service"),

    /**
     * Broadcast back state of {@link MyService}
     */
    BROADCAST_SERVICE_STATE("broadcast-service-state");

    /**
     * code of the enum that is used in messages
     */
    private final String code;
    /**
     * The id of the string resource with the localized name of this enum to use in UI
     */
    private final int titleResId;
    private final int priority;
    private final boolean onlineOnly;

    private CommandEnum(String code) {
        this(code, 0);
    }

    private CommandEnum(String code, int titleResId) {
        this(code, titleResId, 0);
    }

    private CommandEnum(String code, int titleResId, int priority) {
        this(code, titleResId, priority, false);
    }

    private CommandEnum(String code, int titleResId, int priority, boolean onlineOnly) {
        this.code = code;
        this.titleResId = titleResId;
        this.priority = priority;
        this.onlineOnly = onlineOnly;
    }

    /**
     * String code for the Command to be used in messages
     */
    public String save() {
        return code;
    }

    /**
     * Returns the enum for a String action code or UNKNOWN
     */
    public static CommandEnum load(String strCode) {
        if (TextUtils.isEmpty(strCode)) {
            return EMPTY;
        }
        for (CommandEnum serviceCommand : CommandEnum.values()) {
            if (serviceCommand.code.equals(strCode)) {
                return serviceCommand;
            }
        }
        return UNKNOWN;
    }

    /** Localized title for UI */
    public CharSequence getTitle(Context context) {
        if (titleResId == 0 || context == null) {
            return this.code;
        } else {
            return context.getText(titleResId);        
        }
    }
    
    public int getPriority() {
        return priority;
    }

    public boolean isOnlineOnly() {
        return onlineOnly;
    }
}