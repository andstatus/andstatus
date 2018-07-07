/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.support.annotation.NonNull;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.database.table.FriendshipTable;
import org.andstatus.app.lang.SelectableEnum;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.timeline.ListScope;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum TimelineType implements SelectableEnum {
    UNKNOWN(ListScope.ORIGIN, "unknown", R.string.timeline_title_unknown, Connection.ApiRoutineEnum.DUMMY),
    /** The Home timeline and other information (replies...). */
    HOME(ListScope.USER, "home", R.string.timeline_title_home, Connection.ApiRoutineEnum.HOME_TIMELINE),
    NOTIFICATIONS(ListScope.USER, "notifications", R.string.notifications_title, Connection.ApiRoutineEnum.NOTIFICATIONS_TIMELINE),
    PUBLIC(ListScope.ORIGIN, "public", R.string.timeline_title_public, Connection.ApiRoutineEnum.PUBLIC_TIMELINE),
    EVERYTHING(ListScope.ORIGIN, "everything", R.string.timeline_title_everything, Connection.ApiRoutineEnum.DUMMY),
    SEARCH(ListScope.ORIGIN, "search", R.string.options_menu_search, Connection.ApiRoutineEnum.SEARCH_NOTES),
    FAVORITES(ListScope.USER, "favorites", R.string.timeline_title_favorites, Connection.ApiRoutineEnum.FAVORITES_TIMELINE),
    /** The Mentions timeline and other information (replies...). */
    INTERACTIONS(ListScope.USER, "interactions", R.string.timeline_title_interactions,
            Connection.ApiRoutineEnum.NOTIFICATIONS_TIMELINE),
    /** Private notes (direct tweets, dents...) */
    PRIVATE(ListScope.USER, "private", R.string.timeline_title_private, Connection.ApiRoutineEnum.PRIVATE_NOTES),
    /** Notes by the selected Actor (where he is an Author or an Actor only (e.g. for Reblog/Retweet).
     * This Actor is not necessarily one of our Accounts */
    SENT(ListScope.USER, "sent", R.string.sent, Connection.ApiRoutineEnum.ACTOR_TIMELINE),
    /** Latest notes of every Friend of this Actor
     * (i.e of every actor, followed by this Actor).
     * So this is essentially a list of "Friends". See {@link FriendshipTable} */
    FRIENDS(ListScope.USER, "friends", R.string.friends, Connection.ApiRoutineEnum.GET_FRIENDS),
    FOLLOWERS(ListScope.USER, "followers", R.string.followers, Connection.ApiRoutineEnum.GET_FOLLOWERS),
    DRAFTS(ListScope.USER, "drafts", R.string.timeline_title_drafts, Connection.ApiRoutineEnum.DUMMY),
    OUTBOX(ListScope.USER, "outbox", R.string.timeline_title_outbox, Connection.ApiRoutineEnum.DUMMY),
    ACTORS(ListScope.ORIGIN, "users", R.string.user_list, Connection.ApiRoutineEnum.DUMMY),
    CONVERSATION(ListScope.ORIGIN, "conversation", R.string.label_conversation, Connection.ApiRoutineEnum.DUMMY),
    COMMANDS_QUEUE(ListScope.ORIGIN, "commands_queue", R.string.commands_in_a_queue, Connection.ApiRoutineEnum.DUMMY),
    MANAGE_TIMELINES(ListScope.ORIGIN, "manages_timelines", R.string.manage_timelines, Connection.ApiRoutineEnum.DUMMY),
    ;

    /** Code - identifier of the type */
    private final String code;
    /** The id of the string resource with the localized name of this enum to use in UI */
    private final int titleResId;
    /** Api routine to download this timeline */
    private final Connection.ApiRoutineEnum connectionApiRoutine;
    public final ListScope scope;

    TimelineType(ListScope scope, String code, int resId, Connection.ApiRoutineEnum connectionApiRoutine) {
        this.scope = scope;
        this.code = code;
        this.titleResId = resId;
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

    public static Set<TimelineType> getDefaultMyAccountTimelineTypes() {
        return defaultMyAccountTimelineTypes;
    }

    public static Set<TimelineType> getDefaultOriginTimelineTypes() {
        return defaultOriginTimelineTypes;
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
    public CharSequence getTitle(Context context) {
        if (titleResId == 0 || context == null) {
            return this.code;
        } else {
            return context.getText(titleResId);        
        }
    }
    
    public CharSequence timelinePreposition(MyContext myContext) {
        return scope.timelinePreposition(myContext);
    }

    public boolean isSyncable() {
        return getConnectionApiRoutine() != Connection.ApiRoutineEnum.DUMMY;
    }

    public boolean isSyncedAutomaticallyByDefault() {
        switch (this) {
            case PRIVATE:
            case FAVORITES:
            case HOME:
            case NOTIFICATIONS:
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
                return false;
            default:
                return true;
        }
    }

    private static final Set<TimelineType> defaultMyAccountTimelineTypes = Stream.of(
            PRIVATE,
            DRAFTS,
            FAVORITES,
            HOME,
            INTERACTIONS,
            FOLLOWERS,
            FRIENDS,
            NOTIFICATIONS,
            OUTBOX,
            SENT
    ).collect(Collectors.toSet());

    private static final Set<TimelineType> defaultOriginTimelineTypes = Stream.of(
            EVERYTHING,
            PUBLIC
    ).collect(Collectors.toSet());

    public boolean isAtOrigin() {
        return scope == ListScope.ORIGIN;
    }

    public boolean isForUser() {
        return scope == ListScope.USER;
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
            case HOME:
            case INTERACTIONS:
            case NOTIFICATIONS:
            case OUTBOX:
            case PRIVATE:
            case PUBLIC:
            case SEARCH:
            case SENT:
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
                return true;
            default:
                return false;
        }
    }

    public boolean withActorProfile() {
        switch (this) {
            case FAVORITES:
            case FOLLOWERS:
            case FRIENDS:
            case SENT:
                return true;
            default:
                return false;
        }
    }

    @Override
    public int getDialogTitleResId() {
        return R.string.dialog_title_select_timeline;
    }

    public Connection.ApiRoutineEnum getConnectionApiRoutine() {
        return connectionApiRoutine;
    }
}