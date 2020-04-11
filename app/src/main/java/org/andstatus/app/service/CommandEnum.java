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

import android.os.Bundle;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;

/**
 * The command to the MyService or to MyAppWidgetProvider as a
 * enum We use 'code' for persistence
 * 
 * @author yvolk@yurivolkov.com
 */
public enum CommandEnum {

    /** The action is unknown */
    UNKNOWN("unknown"),
    /** There is no action */
    EMPTY("empty"),
    DELETE_COMMAND("delete-command", R.string.button_delete, -100, ConnectionRequired.ANY),
    GET_TIMELINE("fetch-timeline", 0, 4, ConnectionRequired.SYNC),
    GET_OLDER_TIMELINE("get-older-timeline", 0, 4, ConnectionRequired.SYNC),

    /** Fetch avatar for the specified actor */
    GET_AVATAR("fetch-avatar", R.string.title_command_fetch_avatar, 9, ConnectionRequired.SYNC),
    GET_ATTACHMENT("fetch-attachment", R.string.title_command_fetch_attachment, 11, ConnectionRequired.DOWNLOAD_ATTACHMENT),
    
    LIKE("create-favorite", R.string.menu_item_favorite, 0, ConnectionRequired.SYNC),
    UNDO_LIKE("destroy-favorite", R.string.menu_item_destroy_favorite, 0, ConnectionRequired.SYNC),

    GET_ACTOR("get-user", R.string.get_user, -5, ConnectionRequired.SYNC),
    SEARCH_ACTORS("search-users", R.string.search_users, -5, ConnectionRequired.SYNC),
    FOLLOW("follow-user", R.string.command_follow_user, 0, ConnectionRequired.SYNC),
    UNDO_FOLLOW("stop-following-user", R.string.command_stop_following_user, 0, ConnectionRequired.SYNC),
    GET_FOLLOWERS("get-followers", R.string.get_followers, -5, ConnectionRequired.SYNC),
    GET_FRIENDS("get-friends", R.string.get_friends, -5, ConnectionRequired.SYNC),

    /** This command is for sending both public and private notes */
    UPDATE_NOTE("update-status", R.string.button_create_message, -10, ConnectionRequired.SYNC),
    DELETE_NOTE("destroy-status", R.string.menu_item_destroy_status, -3, ConnectionRequired.SYNC),
    GET_NOTE("get-status", R.string.title_command_get_status, -5, ConnectionRequired.SYNC),
    GET_CONVERSATION("get-conversation", R.string.get_conversation, -5, ConnectionRequired.SYNC),
    /** see http://gstools.org/api/doc/ */
    GET_OPEN_INSTANCES("get_open_instances", R.string.get_open_instances_title, -1, ConnectionRequired.SYNC),

    ANNOUNCE("reblog", R.string.menu_item_reblog, -9, ConnectionRequired.SYNC),
    UNDO_ANNOUNCE("destroy-reblog", R.string.menu_item_destroy_reblog, -3, ConnectionRequired.SYNC),

    RATE_LIMIT_STATUS("rate-limit-status", 0, 0, ConnectionRequired.SYNC),

    /** Stop the service after finishing all asynchronous treads (i.e. not immediately!) */
    STOP_SERVICE("stop-service"),

    /** Broadcast back state of {@link MyService} */
    BROADCAST_SERVICE_STATE("broadcast-service-state");

    /** code of the enum that is used in notes */
    private final String code;
    /** The id of the string resource with the localized name of this enum to use in UI */
    private final int titleResId;
    /** less value of the  priority means higher priority */
    private final int priority;
    private final ConnectionRequired connectionRequired;

    CommandEnum(String code) {
        this(code, 0, 0, ConnectionRequired.ANY);
    }

    CommandEnum(String code, int titleResId, int priority, ConnectionRequired connectionRequired) {
        this.code = code;
        this.titleResId = titleResId;
        this.priority = priority;
        this.connectionRequired = connectionRequired;
    }

    /**
     * String code for the Command to be used in notes
     */
    public String save() {
        return code;
    }

    public static CommandEnum fromBundle(Bundle bundle) {
        CommandEnum command = UNKNOWN;
        if (bundle != null) {
            command = CommandEnum.load(bundle.getString(IntentExtra.COMMAND.key));
            if (command == UNKNOWN) {
                MyLog.w(CommandData.class, "Bundle has UNKNOWN command: " + bundle);
            }
        }
        return command;
    }

    /**
     * Returns the enum for a String action code or UNKNOWN
     */
    public static CommandEnum load(String strCode) {
        if (!StringUtil.isEmpty(strCode)) {
            for (CommandEnum serviceCommand : CommandEnum.values()) {
                if (serviceCommand.code.equals(strCode)) {
                    return serviceCommand;
                }
            }
        }
        return UNKNOWN;
    }

    /** Localized title for UI 
     * @param accountName */
    public CharSequence getTitle(MyContext myContext, String accountName) {
        if (titleResId == 0 || myContext == null || myContext.context() == null) {
            return this.code;
        }
        int resId = titleResId;
        MyAccount ma = myContext.accounts().fromAccountName(accountName);
        if (ma.isValid()) {
            resId = ma.getOrigin().alternativeTermForResourceId(titleResId);
        }
        return myContext.context().getText(resId);
    }
    
    public int getPriority() {
        return priority;
    }

    public ConnectionRequired getConnectionRequired() {
        return connectionRequired;
    }
}
