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
import org.andstatus.app.database.UserTable;
import org.andstatus.app.net.social.Connection;

public enum TimelineType {
    /** The type is unknown */
    UNKNOWN("unknown", R.string.timeline_title_unknown, Connection.ApiRoutineEnum.DUMMY,
            false, false,
            UserTable.HOME_TIMELINE_POSITION, UserTable.HOME_TIMELINE_ITEM_DATE, UserTable.HOME_TIMELINE_DATE),
    /** The Home timeline and other information (replies...). */
    HOME("home", R.string.timeline_title_home, Connection.ApiRoutineEnum.STATUSES_HOME_TIMELINE,
            true, false,
            UserTable.HOME_TIMELINE_POSITION, UserTable.HOME_TIMELINE_ITEM_DATE, UserTable.HOME_TIMELINE_DATE),
    /** The Mentions timeline and other information (replies...). */
    MENTIONS("mentions", R.string.timeline_title_mentions, Connection.ApiRoutineEnum.STATUSES_MENTIONS_TIMELINE,
            true, false,
            UserTable.MENTIONS_TIMELINE_POSITION, UserTable.MENTIONS_TIMELINE_ITEM_DATE, UserTable.MENTIONS_TIMELINE_DATE),
    /** Direct messages (direct dents...) */
    DIRECT("direct", R.string.timeline_title_direct_messages, Connection.ApiRoutineEnum.DIRECT_MESSAGES,
            true, false,
            UserTable.DIRECT_TIMELINE_POSITION, UserTable.DIRECT_TIMELINE_ITEM_DATE, UserTable.DIRECT_TIMELINE_DATE),
    /** Favorites (favorited messages) */
    FAVORITES("favorites", R.string.timeline_title_favorites, Connection.ApiRoutineEnum.DUMMY,
            false, false,
            UserTable.FAVORITES_TIMELINE_POSITION, UserTable.FAVORITES_TIMELINE_ITEM_DATE, UserTable.FAVORITES_TIMELINE_DATE),
    /** Messages of the selected User (where he is an Author or a Sender only (e.g. for Reblog/Retweet).
     * This User may be not the same as a user of current account ( {@link PersistentAccounts#getCurrentAccountName()} ).
     * Moreover, the User may not be "AndStatus account" at all.
     * Hence this timeline type requires the User parameter. */
    USER("user", R.string.timeline_title_user, Connection.ApiRoutineEnum.STATUSES_USER_TIMELINE,
            false, true,
            UserTable.USER_TIMELINE_POSITION, UserTable.USER_TIMELINE_ITEM_DATE, UserTable.USER_TIMELINE_DATE),
    /** For the selected user, the timeline includes all messages of the same origin irrespectively existence
     * of the link between the message and the User. So the User may "Act" on this message. */
    MESSAGES_TO_ACT("messages_to_act", R.string.timeline_title_home, Connection.ApiRoutineEnum.STATUSES_HOME_TIMELINE,
            false, true,
            "", "", ""),
    /** Latest messages of every Friend of this user - AndStatus account
     * (i.e of every user, followed by this User).
     * So this is essentially a list of "Friends". See {@link FriendshipTable} */
    FRIENDS("friends", R.string.friends, Connection.ApiRoutineEnum.DUMMY,
            false, false,
            "", "", UserTable.FOLLOWING_USER_DATE),
    FOLLOWERS("followers", R.string.followers, Connection.ApiRoutineEnum.DUMMY, false, false,
            "", "", UserTable.FOLLOWERS_USER_DATE),

    REPLIES("replies", R.string.timeline_title_replies, Connection.ApiRoutineEnum.DUMMY,
            false, false,
            "", "", ""),
    PUBLIC("public", R.string.timeline_title_public, Connection.ApiRoutineEnum.PUBLIC_TIMELINE,
            false, true,
            "", "", ""),
    DRAFTS("drafts", R.string.timeline_title_drafts, Connection.ApiRoutineEnum.DUMMY,
            false, false,
            "", "", ""),
    OUTBOX("outbox", R.string.timeline_title_outbox, Connection.ApiRoutineEnum.DUMMY,
            false, false,
            "", "", ""),
    EVERYTHING("everything", R.string.timeline_title_everything, Connection.ApiRoutineEnum.DUMMY,
            false, true,
            "", "", ""),
    /** All timelines (e.g. for download of all timelines.
     * This is generally done after addition of the new MyAccount). */
    ALL("all", R.string.timeline_title_all, Connection.ApiRoutineEnum.DUMMY,
            false, false,
            UserTable.HOME_TIMELINE_POSITION, UserTable.HOME_TIMELINE_ITEM_DATE, UserTable.HOME_TIMELINE_DATE);
    
    /** Code - identifier of the type */
    private final String code;
    /** The id of the string resource with the localized name of this enum to use in UI */
    private final int titleResId;
    /** Position of the latest downloaded timeline item
     * E.g. the "timeline item" is a "message" for Twitter and an "Activity" for Pump.Io.
     * Name of the column in the {@link UserTable} table. */
    private final String columnNameLatestTimelinePosition;
    private final String columnNameLatestTimelineItemDate;
    /** Date when this timeline was last time fetched (downloaded).
     * Name of the column in the {@link UserTable} table. */
    private final String columnNameTimelineDownloadedDate;
    /** Api routine to download this timeline */
    private final Connection.ApiRoutineEnum connectionApiRoutine;
    private final boolean syncableAutomatically;
    private final boolean atOrigin;

    private TimelineType(String code, int resId, Connection.ApiRoutineEnum connectionApiRoutine,
                         boolean syncableAutomatically, boolean atOrigin,
            String columnNameLatestTimelinePosition, String columnNameLatestTimelineItemDate, 
            String columnNameTimelineDownloadedDate) {
        this.code = code;
        this.titleResId = resId;
        this.connectionApiRoutine = connectionApiRoutine;
        this.syncableAutomatically = syncableAutomatically;
        this.atOrigin = atOrigin;
        this.columnNameLatestTimelinePosition = columnNameLatestTimelinePosition;
        this.columnNameLatestTimelineItemDate = columnNameLatestTimelineItemDate;
        this.columnNameTimelineDownloadedDate = columnNameTimelineDownloadedDate;
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

    public boolean isSyncableAutomatically() {
        return syncableAutomatically;
    }

    public boolean isAtOrigin() {
        return atOrigin;
    }
    
    public Connection.ApiRoutineEnum getConnectionApiRoutine() {
        return connectionApiRoutine;
    }

    public String columnNameLatestTimelinePosition() {
        return columnNameLatestTimelinePosition;
    }

    public String columnNameLatestTimelineItemDate() {
        return columnNameLatestTimelineItemDate;
    }

    public String columnNameTimelineDownloadedDate() {
        return columnNameTimelineDownloadedDate;
    }
}