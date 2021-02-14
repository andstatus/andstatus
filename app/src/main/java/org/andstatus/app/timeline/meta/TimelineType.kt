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

package org.andstatus.app.timeline.meta;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.andstatus.app.R;
import org.andstatus.app.lang.SelectableEnum;
import org.andstatus.app.net.social.ApiRoutineEnum;
import org.andstatus.app.notification.NotificationEventType;
import org.andstatus.app.timeline.ListScope;
import org.andstatus.app.util.StringUtil;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.andstatus.app.net.social.ApiRoutineEnum.ACTOR_TIMELINE;
import static org.andstatus.app.net.social.ApiRoutineEnum.DUMMY_API;
import static org.andstatus.app.net.social.ApiRoutineEnum.GET_FOLLOWERS;
import static org.andstatus.app.net.social.ApiRoutineEnum.GET_FRIENDS;
import static org.andstatus.app.net.social.ApiRoutineEnum.HOME_TIMELINE;
import static org.andstatus.app.net.social.ApiRoutineEnum.LIKED_TIMELINE;
import static org.andstatus.app.net.social.ApiRoutineEnum.NOTIFICATIONS_TIMELINE;
import static org.andstatus.app.net.social.ApiRoutineEnum.PRIVATE_NOTES;
import static org.andstatus.app.net.social.ApiRoutineEnum.PUBLIC_TIMELINE;
import static org.andstatus.app.net.social.ApiRoutineEnum.SEARCH_NOTES;

public enum TimelineType implements SelectableEnum {
    UNKNOWN(ListScope.ORIGIN, "unknown", R.string.timeline_title_unknown, 0, DUMMY_API),
    /** The Home timeline and other information (replies...). */
    HOME(ListScope.USER, "home", R.string.timeline_title_home, 0, HOME_TIMELINE),
    UNREAD_NOTIFICATIONS(ListScope.USER, "unread_notifications", R.string.unread_notifications, 0, NOTIFICATIONS_TIMELINE),
    /** The Mentions timeline and other information (replies...). */
    INTERACTIONS(ListScope.USER, "interactions", R.string.timeline_title_interactions, 0, NOTIFICATIONS_TIMELINE),
    FAVORITES(ListScope.USER, "favorites", R.string.timeline_title_favorites, 0, LIKED_TIMELINE),
    /** Notes by the selected Actor (where he is an Author or an Actor only (e.g. for Reblog/Retweet).
     * This Actor is not necessarily one of our Accounts */
    SENT(ListScope.USER, "sent", R.string.sent, R.string.menu_item_user_messages, ACTOR_TIMELINE),
    SENT_AT_ORIGIN(ListScope.ACTOR_AT_ORIGIN, "sent_at_origin", R.string.sent, R.string.menu_item_user_messages, ACTOR_TIMELINE),
    /** Latest notes of every Friend of this Actor
     * (i.e of every actor, followed by this Actor).
     * So this is essentially a list of "Friends". See {@link org.andstatus.app.database.table.GroupMembersTable} */
    FRIENDS(ListScope.USER, "friends", R.string.friends, R.string.friends_of, GET_FRIENDS),
    FOLLOWERS(ListScope.USER, "followers", R.string.followers, R.string.followers_of, GET_FOLLOWERS),
    GROUP(ListScope.USER, "group", R.string.group, R.string.group_notes, DUMMY_API),
    PUBLIC(ListScope.ORIGIN, "public", R.string.timeline_title_public, 0, PUBLIC_TIMELINE),
    EVERYTHING(ListScope.ORIGIN, "everything", R.string.timeline_title_everything, 0, DUMMY_API),
    SEARCH(ListScope.ORIGIN, "search", R.string.options_menu_search, 0, SEARCH_NOTES),
    PRIVATE(ListScope.USER, "private", R.string.timeline_title_private, 0, PRIVATE_NOTES),
    NOTIFICATIONS(ListScope.USER, "notifications", R.string.notifications_title, 0, NOTIFICATIONS_TIMELINE),
    DRAFTS(ListScope.USER, "drafts", R.string.timeline_title_drafts, 0, DUMMY_API),
    OUTBOX(ListScope.USER, "outbox", R.string.timeline_title_outbox, 0, DUMMY_API),
    ACTORS(ListScope.ORIGIN, "users", R.string.user_list, 0, DUMMY_API),
    CONVERSATION(ListScope.ORIGIN, "conversation", R.string.label_conversation, 0, DUMMY_API),
    COMMANDS_QUEUE(ListScope.ORIGIN, "commands_queue", R.string.commands_in_a_queue, 0, DUMMY_API),
    MANAGE_TIMELINES(ListScope.ORIGIN, "manages_timelines", R.string.manage_timelines, 0, DUMMY_API);

    /** Code - identifier of the type */
    private final String code;
    @StringRes
    private final int titleResId;
    @StringRes
    public final int titleResWithParamsId;
    /** Api routine to download this timeline */
    private final ApiRoutineEnum connectionApiRoutine;
    public final ListScope scope;

    TimelineType(ListScope scope, String code, @StringRes int resId, @StringRes int resWithParamsId,
                 ApiRoutineEnum connectionApiRoutine) {
        this.scope = scope;
        this.code = code;
        this.titleResId = resId;
        this.titleResWithParamsId = resWithParamsId;
        this.connectionApiRoutine = connectionApiRoutine;
    }

    /** Returns the enum or UNKNOWN */
    @NonNull
    public static TimelineType load(String strCode) {
        for (TimelineType value : TimelineType.values()) {
            if (value.code.equals(strCode)) {
                return value;
            }
        }
        return UNKNOWN;
    }

    public static List<TimelineType> getDefaultMyAccountTimelineTypes() {
        return defaultMyAccountTimelineTypes;
    }

    public static Set<TimelineType> getDefaultOriginTimelineTypes() {
        return defaultOriginTimelineTypes;
    }

    @NonNull
    public static TimelineType from(NotificationEventType event) {
        switch (event) {
            case OUTBOX:
                return OUTBOX;
            default:
                return UNREAD_NOTIFICATIONS;
        }
    }

    /** String to be used for persistence */
    public String save() {
        return code;
    }
    
    @Override
    public String toString() {
        return "timelineType:" + code;
    }

    @Override
    public String getCode() {
        return code;
    }

    /** Localized title for UI */
    @Override
    public CharSequence title(Context context) {
        if (titleResId == 0 || context == null) {
            return this.code;
        } else {
            return context.getText(titleResId);        
        }
    }

    public CharSequence title(Context context, Object ... params) {
        return StringUtil.format(context, titleResWithParamsId, params);
    }

    public boolean isSyncable() {
        return getConnectionApiRoutine() != DUMMY_API;
    }

    public boolean isSyncedAutomaticallyByDefault() {
        switch (this) {
            case PRIVATE:
            case FAVORITES:
            case HOME:
            case UNREAD_NOTIFICATIONS:
            case SENT:
                return true;
            default:
                return false;
        }
    }

    public boolean isCombinedRequired() {
        return this != SEARCH && isSelectable();
    }

    public boolean isSelectable() {
        switch (this) {
            case COMMANDS_QUEUE:
            case CONVERSATION:
            case FOLLOWERS:
            case FRIENDS:
            case MANAGE_TIMELINES:
            case UNKNOWN:
            case ACTORS:
            case SENT_AT_ORIGIN:
                return false;
            default:
                return true;
        }
    }

    private static final List<TimelineType> defaultMyAccountTimelineTypes = Stream.of(
            DRAFTS,
            FAVORITES,
            HOME,
            INTERACTIONS,
            NOTIFICATIONS,
            OUTBOX,
            PRIVATE,
            SENT,
            UNREAD_NOTIFICATIONS
    ).collect(Collectors.toList());

    private static final Set<TimelineType> defaultOriginTimelineTypes = Stream.of(
            EVERYTHING,
            PUBLIC
    ).collect(Collectors.toSet());

    public boolean isAtOrigin() {
        return scope == ListScope.ORIGIN || scope == ListScope.ACTOR_AT_ORIGIN;
    }

    public boolean isForUser() {
        return scope == ListScope.USER || scope == ListScope.ACTOR_AT_ORIGIN;
    }

    public boolean canBeCombinedForOrigins() {
        switch (this) {
            case EVERYTHING:
            case PUBLIC:
            case SEARCH:
                return true;
            default:
                return false;
        }
    }

    public boolean canBeCombinedForMyAccounts() {
        switch (this) {
            case PRIVATE:
            case DRAFTS:
            case FAVORITES:
            case FOLLOWERS:
            case FRIENDS:
            case HOME:
            case INTERACTIONS:
            case NOTIFICATIONS:
            case OUTBOX:
            case SENT:
            case UNREAD_NOTIFICATIONS:
                return true;
            default:
                return false;
        }
    }

    public boolean isPersistable() {
        switch (this) {
            case COMMANDS_QUEUE:
            case CONVERSATION:
            case MANAGE_TIMELINES:
            case UNKNOWN:
            case ACTORS:
            case SENT_AT_ORIGIN:
                return false;
            default:
                return true;
        }
    }

    public boolean showsActivities() {
        switch (this) {
            case DRAFTS:
            case EVERYTHING:
            case FOLLOWERS:
            case FRIENDS:
            case GROUP:
            case HOME:
            case INTERACTIONS:
            case NOTIFICATIONS:
            case OUTBOX:
            case PRIVATE:
            case PUBLIC:
            case SEARCH:
            case SENT:
            case SENT_AT_ORIGIN:
            case UNREAD_NOTIFICATIONS:
                return true;
            case FAVORITES:
            default:
                return false;
        }
    }

    public boolean isSubscribedByMe() {
        switch (this) {
            case PRIVATE:
            case FAVORITES:
            case FRIENDS:
            case HOME:
            case INTERACTIONS:
            case NOTIFICATIONS:
            case SENT:
            case UNREAD_NOTIFICATIONS:
                return true;
            default:
                return false;
        }
    }

    public boolean hasActorProfile() {
        switch (this) {
            case FAVORITES:
            case FOLLOWERS:
            case FRIENDS:
            case SENT:
            case GROUP:
                return true;
            default:
                return false;
        }
    }

    @Override
    public int getDialogTitleResId() {
        return R.string.dialog_title_select_timeline;
    }

    public ApiRoutineEnum getConnectionApiRoutine() {
        return connectionApiRoutine;
    }
}