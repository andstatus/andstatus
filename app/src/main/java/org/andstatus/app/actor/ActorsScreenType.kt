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
package org.andstatus.app.actor

import android.content.Context
import androidx.annotation.StringRes
import org.andstatus.app.R
import org.andstatus.app.timeline.ListScope
import org.andstatus.app.util.StringUtil

/**
 * These values define different named filters for lists of Actors / Users / Groups
 */
enum class ActorsScreenType(
        /** code of the enum that is used in notes  */
        private val code: String?, @field:StringRes private val titleResId: Int, @field:StringRes private val titleResWithParamsId: Int, val scope: ListScope?) {
    UNKNOWN("unknown", R.string.unknown_userlist, 0, ListScope.ORIGIN),

    /** Actors, related to the selected note, including mentioned actors  */
    ACTORS_OF_NOTE("actors_of_note", R.string.users_of_message, 0, ListScope.ORIGIN), FOLLOWERS("followers", R.string.followers, R.string.followers_of, ListScope.USER), FRIENDS("friends", R.string.friends, R.string.friends_of, ListScope.USER), ACTORS_AT_ORIGIN("actors", R.string.user_list, 0, ListScope.ORIGIN), GROUPS_AT_ORIGIN("groups", R.string.groups, 0, ListScope.ORIGIN);

    /**
     * String to be used for persistence
     */
    fun save(): String? {
        return code
    }

    override fun toString(): String {
        return "ActorsScreen:$code"
    }

    /** Localized title for UI  */
    fun title(context: Context?): CharSequence? {
        return if (titleResId == 0 || context == null) {
            this.code
        } else {
            context.getText(titleResId)
        }
    }

    fun title(context: Context?, vararg params: Any?): CharSequence? {
        return StringUtil.format(context, titleResWithParamsId, *params)
    }

    companion object {
        /**
         * Returns the enum or UNKNOWN
         */
        fun load(strCode: String?): ActorsScreenType? {
            for (tt in values()) {
                if (tt.code == strCode) {
                    return tt
                }
            }
            return UNKNOWN
        }
    }
}