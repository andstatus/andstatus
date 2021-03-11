/*
 * Copyright (C) 2013-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.timeline.meta

import android.content.Context
import androidx.annotation.StringRes
import org.andstatus.app.R
import org.andstatus.app.lang.SelectableEnum
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.timeline.ListScope
import org.andstatus.app.util.StringUtil

enum class TimelineType(val scope: ListScope,
                        /** Code - identifier of the type  */
                        private val code: String, @field:StringRes @param:StringRes private val titleResId: Int,
                        @field:StringRes @param:StringRes val titleResWithParamsId: Int,
                        /** Api routine to download this timeline  */
                        val connectionApiRoutine: ApiRoutineEnum) : SelectableEnum {

    UNKNOWN(ListScope.ORIGIN, "unknown", R.string.timeline_title_unknown, 0, ApiRoutineEnum.DUMMY_API),

    /** The Home timeline and other information (replies...).  */
    HOME(ListScope.USER, "home", R.string.timeline_title_home, 0, ApiRoutineEnum.HOME_TIMELINE), UNREAD_NOTIFICATIONS(ListScope.USER, "unread_notifications", R.string.unread_notifications, 0, ApiRoutineEnum.NOTIFICATIONS_TIMELINE),

    /** The Mentions timeline and other information (replies...).  */
    INTERACTIONS(ListScope.USER, "interactions", R.string.timeline_title_interactions, 0, ApiRoutineEnum.NOTIFICATIONS_TIMELINE), FAVORITES(ListScope.USER, "favorites", R.string.timeline_title_favorites, 0, ApiRoutineEnum.LIKED_TIMELINE),

    /** Notes by the selected Actor (where he is an Author or an Actor only (e.g. for Reblog/Retweet).
     * This Actor is not necessarily one of our Accounts  */
    SENT(ListScope.USER, "sent", R.string.sent, R.string.menu_item_user_messages, ApiRoutineEnum.ACTOR_TIMELINE), SENT_AT_ORIGIN(ListScope.ACTOR_AT_ORIGIN, "sent_at_origin", R.string.sent, R.string.menu_item_user_messages, ApiRoutineEnum.ACTOR_TIMELINE),

    /** Latest notes of every Friend of this Actor
     * (i.e of every actor, followed by this Actor).
     * So this is essentially a list of "Friends". See [org.andstatus.app.database.table.GroupMembersTable]  */
    FRIENDS(ListScope.USER, "friends", R.string.friends, R.string.friends_of, ApiRoutineEnum.GET_FRIENDS), FOLLOWERS(ListScope.USER, "followers", R.string.followers, R.string.followers_of, ApiRoutineEnum.GET_FOLLOWERS), GROUP(ListScope.USER, "group", R.string.group, R.string.group_notes, ApiRoutineEnum.DUMMY_API), PUBLIC(ListScope.ORIGIN, "public", R.string.timeline_title_public, 0, ApiRoutineEnum.PUBLIC_TIMELINE), EVERYTHING(ListScope.ORIGIN, "everything", R.string.timeline_title_everything, 0, ApiRoutineEnum.DUMMY_API), SEARCH(ListScope.ORIGIN, "search", R.string.options_menu_search, 0, ApiRoutineEnum.SEARCH_NOTES), PRIVATE(ListScope.USER, "private", R.string.timeline_title_private, 0, ApiRoutineEnum.PRIVATE_NOTES), NOTIFICATIONS(ListScope.USER, "notifications", R.string.notifications_title, 0, ApiRoutineEnum.NOTIFICATIONS_TIMELINE), DRAFTS(ListScope.USER, "drafts", R.string.timeline_title_drafts, 0, ApiRoutineEnum.DUMMY_API), OUTBOX(ListScope.USER, "outbox", R.string.timeline_title_outbox, 0, ApiRoutineEnum.DUMMY_API), ACTORS(ListScope.ORIGIN, "users", R.string.user_list, 0, ApiRoutineEnum.DUMMY_API), CONVERSATION(ListScope.ORIGIN, "conversation", R.string.label_conversation, 0, ApiRoutineEnum.DUMMY_API), COMMANDS_QUEUE(ListScope.ORIGIN, "commands_queue", R.string.commands_in_a_queue, 0, ApiRoutineEnum.DUMMY_API), MANAGE_TIMELINES(ListScope.ORIGIN, "manages_timelines", R.string.manage_timelines, 0, ApiRoutineEnum.DUMMY_API);

    /** String to be used for persistence  */
    fun save(): String {
        return code
    }

    override fun toString(): String {
        return "timelineType:$code"
    }

    override fun getCode(): String {
        return code
    }

    /** Localized title for UI  */
    override fun title(context: Context?): CharSequence {
        return if (titleResId == 0 || context == null) {
            this.code
        } else {
            context.getText(titleResId)
        }
    }

    fun title(context: Context?, vararg params: Any?): CharSequence {
        return StringUtil.format(context, titleResWithParamsId, *params)
    }

    fun isSyncable(): Boolean {
        return connectionApiRoutine != ApiRoutineEnum.DUMMY_API
    }

    fun isSyncedAutomaticallyByDefault(): Boolean {
        return when (this) {
            PRIVATE, FAVORITES, HOME, UNREAD_NOTIFICATIONS, SENT -> true
            else -> false
        }
    }

    fun isCombinedRequired(): Boolean {
        return this != SEARCH && isSelectable()
    }

    override fun isSelectable(): Boolean {
        return when (this) {
            COMMANDS_QUEUE, CONVERSATION, FOLLOWERS, FRIENDS, MANAGE_TIMELINES, UNKNOWN, ACTORS, SENT_AT_ORIGIN -> false
            else -> true
        }
    }

    fun isAtOrigin(): Boolean {
        return scope == ListScope.ORIGIN || scope == ListScope.ACTOR_AT_ORIGIN
    }

    fun isForUser(): Boolean {
        return scope == ListScope.USER || scope == ListScope.ACTOR_AT_ORIGIN
    }

    fun canBeCombinedForOrigins(): Boolean {
        return when (this) {
            EVERYTHING, PUBLIC, SEARCH -> true
            else -> false
        }
    }

    fun canBeCombinedForMyAccounts(): Boolean {
        return when (this) {
            PRIVATE, DRAFTS, FAVORITES, FOLLOWERS, FRIENDS, HOME, INTERACTIONS, NOTIFICATIONS, OUTBOX, SENT, UNREAD_NOTIFICATIONS -> true
            else -> false
        }
    }

    fun isPersistable(): Boolean {
        return when (this) {
            COMMANDS_QUEUE, CONVERSATION, MANAGE_TIMELINES, UNKNOWN, ACTORS, SENT_AT_ORIGIN -> false
            else -> true
        }
    }

    fun showsActivities(): Boolean {
        return when (this) {
            DRAFTS, EVERYTHING, FOLLOWERS, FRIENDS, GROUP, HOME, INTERACTIONS, NOTIFICATIONS, OUTBOX, PRIVATE, PUBLIC, SEARCH, SENT, SENT_AT_ORIGIN, UNREAD_NOTIFICATIONS -> true
            FAVORITES -> false
            else -> false
        }
    }

    fun isSubscribedByMe(): Boolean {
        return when (this) {
            PRIVATE, FAVORITES, FRIENDS, HOME, INTERACTIONS, NOTIFICATIONS, SENT, UNREAD_NOTIFICATIONS -> true
            else -> false
        }
    }

    fun hasActorProfile(): Boolean {
        return when (this) {
            FAVORITES, FOLLOWERS, FRIENDS, SENT, GROUP -> true
            else -> false
        }
    }

    override fun getDialogTitleResId(): Int {
        return R.string.dialog_title_select_timeline
    }

    companion object {
        /** Returns the enum or UNKNOWN  */
        fun load(strCode: String?): TimelineType {
            for (value in values()) {
                if (value.code == strCode) {
                    return value
                }
            }
            return UNKNOWN
        }

        fun getDefaultMyAccountTimelineTypes(): List<TimelineType> {
            return defaultMyAccountTimelineTypes
        }

        fun getDefaultOriginTimelineTypes(): Set<TimelineType> {
            return defaultOriginTimelineTypes
        }

        fun from(event: NotificationEventType?): TimelineType {
            return when (event) {
                NotificationEventType.OUTBOX -> OUTBOX
                else -> UNREAD_NOTIFICATIONS
            }
        }

        private val defaultMyAccountTimelineTypes = listOf(
                DRAFTS,
                FAVORITES,
                HOME,
                INTERACTIONS,
                NOTIFICATIONS,
                OUTBOX,
                PRIVATE,
                SENT,
                UNREAD_NOTIFICATIONS)
        private val defaultOriginTimelineTypes = setOf(EVERYTHING, PUBLIC)
    }
}