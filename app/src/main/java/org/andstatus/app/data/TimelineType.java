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

package org.andstatus.app.data;

import android.content.Context;
import android.support.annotation.NonNull;

import org.andstatus.app.R;
import org.andstatus.app.account.PersistentAccounts;
import org.andstatus.app.database.FriendshipTable;
import org.andstatus.app.net.social.Connection;

public enum TimelineType {
    /** The type is unknown */
    UNKNOWN("unknown", R.string.timeline_title_unknown, Connection.ApiRoutineEnum.DUMMY,
            false, false),
    /** The Home timeline and other information (replies...). */
    HOME("home", R.string.timeline_title_home, Connection.ApiRoutineEnum.STATUSES_HOME_TIMELINE,
            true, false),
    /** Favorites (favorited messages) */
    FAVORITES("favorites", R.string.timeline_title_favorites, Connection.ApiRoutineEnum.DUMMY,
            false, false),
    /** The Mentions timeline and other information (replies...). */
    MENTIONS("mentions", R.string.timeline_title_mentions, Connection.ApiRoutineEnum.STATUSES_MENTIONS_TIMELINE,
            true, false),
    /** Direct messages (direct dents...) */
    DIRECT("direct", R.string.timeline_title_direct_messages, Connection.ApiRoutineEnum.DIRECT_MESSAGES,
            true, false),
    /** Messages of the selected User (where he is an Author or a Sender only (e.g. for Reblog/Retweet).
     * This User may be not the same as a user of current account ( {@link PersistentAccounts#getCurrentAccountName()} ).
     * Moreover, the User may not be "AndStatus account" at all.
     * Hence this timeline type requires the User parameter. */
    USER("user", R.string.timeline_title_user, Connection.ApiRoutineEnum.STATUSES_USER_TIMELINE,
            false, true),
    /** Latest messages of every Friend of this user - AndStatus account
     * (i.e of every user, followed by this User).
     * So this is essentially a list of "Friends". See {@link FriendshipTable} */
    FRIENDS("friends", R.string.friends, Connection.ApiRoutineEnum.DUMMY,
            false, false),
    FOLLOWERS("followers", R.string.followers, Connection.ApiRoutineEnum.DUMMY,
            false, false),
    PUBLIC("public", R.string.timeline_title_public, Connection.ApiRoutineEnum.PUBLIC_TIMELINE,
            false, true),
    EVERYTHING("everything", R.string.timeline_title_everything, Connection.ApiRoutineEnum.DUMMY,
            false, true),
    DRAFTS("drafts", R.string.timeline_title_drafts, Connection.ApiRoutineEnum.DUMMY,
            false, false),
    OUTBOX("outbox", R.string.timeline_title_outbox, Connection.ApiRoutineEnum.DUMMY,
            false, false),
    /** For the selected user, the timeline includes all messages of the same origin irrespectively existence
     * of the link between the message and the User. So the User may "Act" on this message. */
    MESSAGES_TO_ACT("messages_to_act", R.string.timeline_title_home, Connection.ApiRoutineEnum.STATUSES_HOME_TIMELINE,
            false, true),
    REPLIES("replies", R.string.timeline_title_replies, Connection.ApiRoutineEnum.DUMMY,
            false, false),
    /** All timelines (e.g. for download of all timelines.
     * This is generally done after addition of the new MyAccount). */
    ALL("all", R.string.timeline_title_all, Connection.ApiRoutineEnum.DUMMY,
            false, false);

    public static final TimelineType[] defaultTimelineTypes = {
            HOME,
            FAVORITES,
            MENTIONS,
            DIRECT,
            USER,
            FRIENDS,
            FOLLOWERS,
            PUBLIC,
            EVERYTHING,
            DRAFTS,
            OUTBOX
            };

    /** Code - identifier of the type */
    private final String code;
    /** The id of the string resource with the localized name of this enum to use in UI */
    private final int titleResId;
    /** Api routine to download this timeline */
    private final Connection.ApiRoutineEnum connectionApiRoutine;
    private final boolean syncableByDefault;
    private final boolean atOrigin;

    private TimelineType(String code, int resId, Connection.ApiRoutineEnum connectionApiRoutine,
                         boolean syncableByDefault, boolean atOrigin) {
        this.code = code;
        this.titleResId = resId;
        this.connectionApiRoutine = connectionApiRoutine;
        this.syncableByDefault = syncableByDefault;
        this.atOrigin = atOrigin;
    }

    /** Returns the enum or UNKNOWN */
    @NonNull
    public static TimelineType load(String strCode) {
        for (TimelineType tt : TimelineType.values()) {
            if (tt.code.equals(strCode)) {
                return tt;
            }
        }
        return UNKNOWN;
    }

    /** String to be used for persistence */
    public String save() {
        return code;
    }
    
    @Override
    public String toString() {
        return "timelineType:" + code;
    }

    /** Localized title for UI */
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

    public boolean isSyncableByDefault() {
        return syncableByDefault;
    }

    public boolean isAtOrigin() {
        return atOrigin;
    }
    
    public Connection.ApiRoutineEnum getConnectionApiRoutine() {
        return connectionApiRoutine;
    }
}