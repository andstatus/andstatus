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
import org.andstatus.app.database.FriendshipTable;
import org.andstatus.app.lang.SelectableEnum;
import org.andstatus.app.net.social.Connection;

public enum TimelineType implements SelectableEnum {
    /** The type is unknown */
    UNKNOWN("unknown", R.string.timeline_title_unknown, Connection.ApiRoutineEnum.DUMMY),
    /** The Home timeline and other information (replies...). */
    HOME("home", R.string.timeline_title_home, Connection.ApiRoutineEnum.HOME_TIMELINE),
    /** Favorites (favorited messages) */
    PUBLIC("public", R.string.timeline_title_public, Connection.ApiRoutineEnum.PUBLIC_TIMELINE),
    EVERYTHING("everything", R.string.timeline_title_everything, Connection.ApiRoutineEnum.DUMMY),
    SEARCH("search", R.string.options_menu_search, Connection.ApiRoutineEnum.SEARCH_MESSAGES),
    FAVORITES("favorites", R.string.timeline_title_favorites, Connection.ApiRoutineEnum.FAVORITES_TIMELINE),
    /** The Mentions timeline and other information (replies...). */
    MENTIONS("mentions", R.string.timeline_title_mentions, Connection.ApiRoutineEnum.MENTIONS_TIMELINE),
    /** Direct messages (direct dents...) */
    DIRECT("direct", R.string.timeline_title_direct_messages, Connection.ApiRoutineEnum.DIRECT_MESSAGES),
    /** Messages of the selected User (where he is an Author or a Sender only (e.g. for Reblog/Retweet).
     * This User is NOT one of our Accounts.
     * Hence this timeline type requires the User parameter. */
    USER("user", R.string.timeline_title_user, Connection.ApiRoutineEnum.USER_TIMELINE),
    /** Almost like {@link #USER}, but for a User, who is one of my accounts. */
    SENT("sent", R.string.sent, Connection.ApiRoutineEnum.USER_TIMELINE),
    /** Latest messages of every Friend of this user - AndStatus account
     * (i.e of every user, followed by this User).
     * So this is essentially a list of "Friends". See {@link FriendshipTable} */
    FRIENDS("friends", R.string.friends, Connection.ApiRoutineEnum.GET_FRIENDS),
    /** Same as {@link #FRIENDS} but for my accounts only */
    MY_FRIENDS("my_friends", R.string.friends, Connection.ApiRoutineEnum.GET_FRIENDS),
    FOLLOWERS("followers", R.string.followers, Connection.ApiRoutineEnum.GET_FOLLOWERS),
    /** Same as {@link #FOLLOWERS} but for my accounts only */
    MY_FOLLOWERS("my_followers", R.string.followers, Connection.ApiRoutineEnum.GET_FOLLOWERS),
    DRAFTS("drafts", R.string.timeline_title_drafts, Connection.ApiRoutineEnum.DUMMY),
    OUTBOX("outbox", R.string.timeline_title_outbox, Connection.ApiRoutineEnum.DUMMY),
    /** For the selected my account (a user), the timeline includes all messages of the same origin irrespectively existence
     * of the link between the message and the User. So the User may "Act" on this message. */
    MESSAGES_TO_ACT("messages_to_act", R.string.timeline_title_home, Connection.ApiRoutineEnum.HOME_TIMELINE),
    REPLIES("replies", R.string.timeline_title_replies, Connection.ApiRoutineEnum.DUMMY),
    NOTIFICATIONS("notifications", R.string.category_title_preference_notifications, Connection.ApiRoutineEnum.MENTIONS_TIMELINE);

    /** Code - identifier of the type */
    private final String code;
    /** The id of the string resource with the localized name of this enum to use in UI */
    private final int titleResId;
    /** Api routine to download this timeline */
    private final Connection.ApiRoutineEnum connectionApiRoutine;

    TimelineType(String code, int resId, Connection.ApiRoutineEnum connectionApiRoutine) {
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

    public static TimelineType[] getDefaultMyAccountTimelineTypes() {
        return defaultMyAccountTimelineTypes;
    }

    public static TimelineType[] getDefaultOriginTimelineTypes() {
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
    
    public CharSequence getPrepositionForNotCombinedTimeline(Context context) {
        if (context == null) {
            return "";
        } else if (isAtOrigin()) {
            return context.getText(R.string.combined_timeline_off_origin);
        } else {
            return context.getText(R.string.combined_timeline_off_account);
        }
    }

    public boolean isSyncable() {
        return getConnectionApiRoutine() != Connection.ApiRoutineEnum.DUMMY;
    }

    public boolean isSyncedAutomaticallyByDefault() {
        switch (this) {
            case HOME:
            case MENTIONS:
            case DIRECT:
            case SENT:
            case FAVORITES:
                return true;
            default:
                return false;
        }
    }

    public boolean isSelectable() {
        switch (this) {
            case UNKNOWN:
            case MESSAGES_TO_ACT:
            case USER:
            case FRIENDS:
            case FOLLOWERS:
            case REPLIES:
                return false;
            default:
                return true;
        }
    }

    private static final TimelineType[] defaultMyAccountTimelineTypes = {
            HOME,
            FAVORITES,
            MENTIONS,
            DIRECT,
            SENT,
            MY_FRIENDS,
            MY_FOLLOWERS,
            DRAFTS,
            OUTBOX
    };

    private static final TimelineType[] defaultOriginTimelineTypes = {
            PUBLIC,
            EVERYTHING
    };

    public boolean isAtOrigin() {
        switch (this) {
            case USER:
            case FRIENDS:
            case FOLLOWERS:
            case PUBLIC:
            case SEARCH:
            case EVERYTHING:
            case MESSAGES_TO_ACT:
                return true;
            default:
                return false;
        }
    }

    public boolean isForUser() {
        switch (this) {
            case UNKNOWN:
            case PUBLIC:
            case EVERYTHING:
            case SEARCH:
                return false;
            default:
                return true;
        }
    }

    public boolean isForSearchQuery() {
        switch (this) {
            case SEARCH:
                return true;
            default:
                return false;
        }
    }

    public boolean canBeCombinedForOrigins() {
        switch (this) {
            case PUBLIC:
            case SEARCH:
            case EVERYTHING:
                return true;
            default:
                return false;
        }
    }

    public boolean canBeCombinedForMyAccounts() {
        switch (this) {
            case HOME:
            case FAVORITES:
            case MENTIONS:
            case DIRECT:
            case SENT:
            case MY_FRIENDS:
            case MY_FOLLOWERS:
            case DRAFTS:
            case OUTBOX:
            case REPLIES:
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