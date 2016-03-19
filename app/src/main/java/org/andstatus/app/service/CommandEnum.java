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

package org.andstatus.app.service;

import android.text.TextUtils;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;

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
    /** For testing purposes only */
    DROP_QUEUES("drop-queues"),
    DELETE_COMMAND("delete-command", R.string.button_delete, 100),
    /**
     * The action to fetch all usual timelines in the background.
     */
    AUTOMATIC_UPDATE("automatic-update", 0, 12, ConnectionRequired.ONLINE),
    /**
     * Fetch timeline(s) of the specified type for the specified MyAccount. 
     */
    FETCH_TIMELINE("fetch-timeline", 0, 4, ConnectionRequired.ONLINE),

    /**
     * Fetch avatar for the specified user 
     */
    FETCH_AVATAR("fetch-avatar", R.string.title_command_fetch_avatar, 9, ConnectionRequired.ONLINE),
    FETCH_ATTACHMENT("fetch-attachment", R.string.title_command_fetch_attachment, 11, ConnectionRequired.WIFI),
    
    CREATE_FAVORITE("create-favorite", R.string.menu_item_favorite, 0, ConnectionRequired.ONLINE), 
    DESTROY_FAVORITE("destroy-favorite", R.string.menu_item_destroy_favorite, 0, ConnectionRequired.ONLINE),

    GET_USER("get-user", R.string.get_user, -5, ConnectionRequired.ONLINE),
    FOLLOW_USER("follow-user", R.string.command_follow_user, 0, ConnectionRequired.ONLINE),
    STOP_FOLLOWING_USER("stop-following-user", R.string.command_stop_following_user, 0, ConnectionRequired.ONLINE),
    GET_FOLLOWERS("get-followers", R.string.get_followers, -5, ConnectionRequired.ONLINE),
    GET_FRIENDS("get-friends", R.string.get_friends, -5, ConnectionRequired.ONLINE),

    /**
     * This command is for sending both public and direct messages
     */
    UPDATE_STATUS("update-status", R.string.button_create_message, -10, ConnectionRequired.ONLINE), 
    DESTROY_STATUS("destroy-status", R.string.menu_item_destroy_status, -3, ConnectionRequired.ONLINE),
    GET_STATUS("get-status", R.string.title_command_get_status, -5, ConnectionRequired.ONLINE),
    /** see http://gstools.org/api/doc/ */
    GET_OPEN_INSTANCES("get_open_instances", R.string.get_open_instances_title, -1, ConnectionRequired.ONLINE),

    SEARCH_MESSAGE("search-message", R.string.options_menu_search, 4, ConnectionRequired.ONLINE),
    
    REBLOG("reblog", R.string.menu_item_reblog, -9, ConnectionRequired.ONLINE),
    DESTROY_REBLOG("destroy-reblog", R.string.menu_item_destroy_reblog, -3, ConnectionRequired.ONLINE),

    RATE_LIMIT_STATUS("rate-limit-status", 0, 0, ConnectionRequired.ONLINE),

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
    NOTIFY_CLEAR("notify-clear", 0, -20),

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
    /** less value of the  priority means higher priority */
    private final int priority;
    private final ConnectionRequired connectionRequired;

    private CommandEnum(String code) {
        this(code, 0);
    }

    private CommandEnum(String code, int titleResId) {
        this(code, titleResId, 0);
    }

    private CommandEnum(String code, int titleResId, int priority) {
        this(code, titleResId, priority, ConnectionRequired.ANY);
    }

    private CommandEnum(String code, int titleResId, int priority, ConnectionRequired connectionRequired) {
        this.code = code;
        this.titleResId = titleResId;
        this.priority = priority;
        this.connectionRequired = connectionRequired;
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

    /** Localized title for UI 
     * @param accountName */
    public CharSequence getTitle(MyContext myContext, String accountName) {
        if (titleResId == 0 || myContext == null) {
            return this.code;
        }
        int resId = titleResId;
        MyAccount ma = myContext.persistentAccounts().fromAccountName(accountName);
        if (ma.isValid()) {
            resId = ma.getOrigin().alternativeTermForResourceId(titleResId);
        }
        return myContext.context().getText(resId);
    }
    
    public int getPriority() {
        return priority;
    }

    public ConnectionRequired getConnetionRequired() {
        return connectionRequired;
    }
}
