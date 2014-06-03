package org.andstatus.app.service;

import android.text.TextUtils;

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
    AUTOMATIC_UPDATE("automatic-update", -10, true),
    /**
     * Fetch timeline(s) of the specified type for the specified MyAccount. 
     */
    FETCH_TIMELINE("fetch-timeline", -4, true),

    /**
     * Fetch avatar for the specified user and URL 
     */
    FETCH_AVATAR("fetch-avatar", -9, true),
    
    CREATE_FAVORITE("create-favorite", 0, true), 
    DESTROY_FAVORITE("destroy-favorite", 0, true),

    FOLLOW_USER("follow-user", 0, true), 
    STOP_FOLLOWING_USER("stop-following-user", 0, true),

    /**
     * This command is for sending both public and direct messages
     */
    UPDATE_STATUS("update-status", 10, true), 
    DESTROY_STATUS("destroy-status", 3, true),
    GET_STATUS("get-status", 5, true),

    SEARCH_MESSAGE("search-message", -4, true),
    
    REBLOG("reblog", 9, true),
    DESTROY_REBLOG("destroy-reblog", 3, true),

    RATE_LIMIT_STATUS("rate-limit-status", 0, true),

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
    NOTIFY_CLEAR("notify-clear", 20),

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
    private final int priority;
    private final boolean onlineOnly;

    private CommandEnum(String code) {
        this(code, 0);
    }

    private CommandEnum(String code, int priority) {
        this(code, priority, false);
    }

    private CommandEnum(String code, int priority, boolean onlineOnly) {
        this.code = code;
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

    public int getPriority() {
        return priority;
    }

    public boolean isOnlineOnly() {
        return onlineOnly;
    }
}