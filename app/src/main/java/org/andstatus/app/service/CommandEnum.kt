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
package org.andstatus.app.service

import android.os.Bundle
import org.andstatus.app.IntentExtra
import org.andstatus.app.R
import org.andstatus.app.context.MyContext
import org.andstatus.app.util.MyLog

/**
 * The command to the MyService or to MyAppWidgetProvider as a
 * enum We use 'code' for persistence
 *
 * @author yvolk@yurivolkov.com
 */
enum class CommandEnum @JvmOverloads constructor(
        /** code of the enum that is used in notes  */
        private val code: String?,
        /** The id of the string resource with the localized name of this enum to use in UI  */
        private val titleResId: Int = 0,
        /** less value of the  priority means higher priority  */
        private val priority: Int = 0, private val connectionRequired: ConnectionRequired? = ConnectionRequired.ANY) {
    /** The action is unknown  */
    UNKNOWN("unknown"),

    /** There is no action  */
    EMPTY("empty"), GET_TIMELINE("fetch-timeline", 0, 4, ConnectionRequired.SYNC), GET_OLDER_TIMELINE("get-older-timeline", 0, 4, ConnectionRequired.SYNC),

    /** Fetch avatar for the specified actor  */
    GET_AVATAR("fetch-avatar", R.string.title_command_fetch_avatar, 9, ConnectionRequired.SYNC), GET_ATTACHMENT("fetch-attachment", R.string.title_command_fetch_attachment, 11, ConnectionRequired.DOWNLOAD_ATTACHMENT), LIKE("create-favorite", R.string.menu_item_favorite, 0, ConnectionRequired.SYNC), UNDO_LIKE("destroy-favorite", R.string.menu_item_destroy_favorite, 0, ConnectionRequired.SYNC), GET_ACTOR("get-user", R.string.get_user, -5, ConnectionRequired.SYNC), SEARCH_ACTORS("search-users", R.string.search_users, -5, ConnectionRequired.SYNC), FOLLOW("follow-user", R.string.command_follow_user, 0, ConnectionRequired.SYNC), UNDO_FOLLOW("stop-following-user", R.string.command_stop_following_user, 0, ConnectionRequired.SYNC), GET_FOLLOWERS("get-followers", R.string.get_followers, -5, ConnectionRequired.SYNC), GET_FRIENDS("get-friends", R.string.get_friends, -5, ConnectionRequired.SYNC),

    /** This command is for sending both public and private notes  */
    UPDATE_NOTE("update-status", R.string.button_create_message, -10, ConnectionRequired.SYNC), DELETE_NOTE("destroy-status", R.string.menu_item_destroy_status, -3, ConnectionRequired.SYNC), GET_NOTE("get-status", R.string.title_command_get_status, -5, ConnectionRequired.SYNC), GET_CONVERSATION("get-conversation", R.string.get_conversation, -5, ConnectionRequired.SYNC),

    /** see http://gstools.org/api/doc/  */
    GET_OPEN_INSTANCES("get_open_instances", R.string.get_open_instances_title, -1, ConnectionRequired.SYNC), ANNOUNCE("reblog", R.string.menu_item_reblog, -9, ConnectionRequired.SYNC), UNDO_ANNOUNCE("destroy-reblog", R.string.menu_item_destroy_reblog, -3, ConnectionRequired.SYNC), RATE_LIMIT_STATUS("rate-limit-status", 0, 0, ConnectionRequired.SYNC),

    /** Stop the service after finishing all asynchronous treads (i.e. not immediately!)  */
    STOP_SERVICE("stop-service"),

    /** Broadcast back state of [MyService]  */
    BROADCAST_SERVICE_STATE("broadcast-service-state");

    /**
     * String code for the Command to be used in notes
     */
    fun save(): String? {
        return code
    }

    /** Localized title for UI
     * @param accountName
     */
    fun getTitle(myContext: MyContext?, accountName: String?): CharSequence? {
        if (titleResId == 0 || myContext == null || myContext.context() == null) {
            return this.code
        }
        var resId = titleResId
        val ma = myContext.accounts().fromAccountName(accountName)
        if (ma.isValid) {
            resId = ma.origin.alternativeTermForResourceId(titleResId)
        }
        return myContext.context().getText(resId)
    }

    fun getPriority(): Int {
        return priority
    }

    fun getConnectionRequired(): ConnectionRequired? {
        return connectionRequired
    }

    fun isGetTimeline(): Boolean {
        return when (this) {
            GET_TIMELINE, GET_OLDER_TIMELINE -> true
            else -> false
        }
    }

    companion object {
        fun fromBundle(bundle: Bundle?): CommandEnum? {
            var command: CommandEnum? = UNKNOWN
            if (bundle != null) {
                command = load(bundle.getString(IntentExtra.COMMAND.key))
                if (command == UNKNOWN) {
                    MyLog.w(CommandData::class.java, "Bundle has UNKNOWN command: $bundle")
                }
            }
            return command
        }

        /**
         * Returns the enum for a String action code or UNKNOWN
         */
        fun load(strCode: String?): CommandEnum? {
            if (!strCode.isNullOrEmpty()) {
                for (serviceCommand in values()) {
                    if (serviceCommand.code == strCode) {
                        return serviceCommand
                    }
                }
            }
            return UNKNOWN
        }
    }
}