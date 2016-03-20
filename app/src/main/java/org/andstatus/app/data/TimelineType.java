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

import org.andstatus.app.R;
import org.andstatus.app.account.PersistentAccounts;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.net.social.Connection;

/**
 * These values help set Timeline filters
 */
public enum TimelineType {
    /**
     * The type is unknown
     */
    UNKNOWN("unknown", R.string.timeline_title_unknown, 
            User.HOME_TIMELINE_POSITION, User.HOME_TIMELINE_ITEM_DATE, User.HOME_TIMELINE_DATE, Connection.ApiRoutineEnum.DUMMY),
    /**
     * The Home timeline and other information (replies...).
     */
    HOME("home", R.string.timeline_title_home, 
            User.HOME_TIMELINE_POSITION, User.HOME_TIMELINE_ITEM_DATE, User.HOME_TIMELINE_DATE, Connection.ApiRoutineEnum.STATUSES_HOME_TIMELINE),
    /**
     * The Mentions timeline and other information (replies...).
     */
    MENTIONS("mentions", R.string.timeline_title_mentions, 
            User.MENTIONS_TIMELINE_POSITION, User.MENTIONS_TIMELINE_ITEM_DATE, User.MENTIONS_TIMELINE_DATE, Connection.ApiRoutineEnum.STATUSES_MENTIONS_TIMELINE),
    /**
     * Direct messages (direct dents...)
     */
    DIRECT("direct", R.string.timeline_title_direct_messages, 
            User.DIRECT_TIMELINE_POSITION, User.DIRECT_TIMELINE_ITEM_DATE, User.DIRECT_TIMELINE_DATE, Connection.ApiRoutineEnum.DIRECT_MESSAGES),
    /**
     * Favorites (favorited messages)
     */
    FAVORITES("favorites", R.string.timeline_title_favorites, 
            User.FAVORITES_TIMELINE_POSITION, User.FAVORITES_TIMELINE_ITEM_DATE, User.FAVORITES_TIMELINE_DATE, Connection.ApiRoutineEnum.DUMMY),
    /**
     * Messages of the selected User (where he is an Author or a Sender only (e.g. for Reblog/Retweet). 
     * This User may be not the same as a user of current account ( {@link PersistentAccounts#getCurrentAccountName()} ).
     * Moreover, the User may not be "AndStatus account" at all.
     * Hence this timeline type requires the User parameter.
     */
    USER("user", R.string.timeline_title_user, 
            User.USER_TIMELINE_POSITION, User.USER_TIMELINE_ITEM_DATE, User.USER_TIMELINE_DATE, Connection.ApiRoutineEnum.STATUSES_USER_TIMELINE,
            true),
    /**
     * For the selected user, the timeline includes all messages of the same origin irrespectively existence
     * of the link between the message and the User. So the User may "Act" on this message.
     */
    MESSAGES_TO_ACT("messages_to_act", R.string.timeline_title_home,
            "", "", "", Connection.ApiRoutineEnum.STATUSES_HOME_TIMELINE,
            true),
    /**
     * Latest messages of every user, followed by this User - AndStatus account. 
     * So this is essentially a list of "Following users". 
     * The timeline doesn't have Message ID because we download User IDs only 
     * See {@link MyDatabase.Friendship}
     */
    FOLLOWING_USER("following_user", R.string.timeline_title_following_user, 
            "", "", User.FOLLOWING_USER_DATE, Connection.ApiRoutineEnum.GET_FRIENDS_IDS),
    /**
     * Replies
     */
    REPLIES("replies", R.string.timeline_title_replies, 
            "", "", "", Connection.ApiRoutineEnum.DUMMY),
    PUBLIC("public", R.string.timeline_title_public, 
            "", "", "", Connection.ApiRoutineEnum.PUBLIC_TIMELINE,
            true),
    DRAFTS("drafts", R.string.timeline_title_drafts,
            "", "", "", Connection.ApiRoutineEnum.DUMMY),
    OUTBOX("outbox", R.string.timeline_title_outbox,
            "", "", "", Connection.ApiRoutineEnum.DUMMY),
    EVERYTHING("everything", R.string.timeline_title_everything,
            "", "", "", Connection.ApiRoutineEnum.DUMMY,
            true),
    /**
     * All timelines (e.g. for download of all timelines. 
     * This is generally done after addition of the new MyAccount).
     */
    ALL("all", R.string.timeline_title_all, 
            User.HOME_TIMELINE_POSITION, User.HOME_TIMELINE_ITEM_DATE, User.HOME_TIMELINE_DATE, Connection.ApiRoutineEnum.DUMMY);
    
    /**
     * code of the enum that is used in messages
     */
    private final String code;
    /**
     * The id of the string resource with the localized name of this enum to use in UI
     */
    private final int titleResId;
    /**
     * Position of the latest downloaded timeline item 
     * E.g. the "timeline item" is a "message" for Twitter and an "Activity" for Pump.Io.
     * Name of the column in the {@link User} table.  
     */
    private final String columnNameLatestTimelinePosition;
    private final String columnNameLatestTimelineItemDate;
    /**
     * Date when this timeline was last time fetched (downloaded). 
     * Name of the column in the {@link User} table.
     */
    private final String columnNameTimelineDownloadedDate;
    /**
     * Api routine to download this timeline
     */
    private final Connection.ApiRoutineEnum connectionApiRoutine;
    private final boolean mAtOrigin;
    
    public String columnNameLatestTimelinePosition() {
        return columnNameLatestTimelinePosition;
    }

    public String columnNameLatestTimelineItemDate() {
        return columnNameLatestTimelineItemDate;
    }
    
    public String columnNameTimelineDownloadedDate() {
        return columnNameTimelineDownloadedDate;
    }

    private TimelineType(String code, int resId, 
            String columnNameLatestTimelinePosition, String columnNameLatestTimelineItemDate, 
            String columnNameTimelineDownloadedDate, 
            Connection.ApiRoutineEnum connectionApiRoutine) {
        this(code, resId, 
                columnNameLatestTimelinePosition, columnNameLatestTimelineItemDate, 
                columnNameTimelineDownloadedDate, 
                connectionApiRoutine, false);
    }
    
    private TimelineType(String code, int resId, 
            String columnNameLatestTimelinePosition, String columnNameLatestTimelineItemDate, 
            String columnNameTimelineDownloadedDate, 
            Connection.ApiRoutineEnum connectionApiRoutine,
            boolean atOrigin) {
        this.code = code;
        this.titleResId = resId;
        this.columnNameLatestTimelinePosition = columnNameLatestTimelinePosition;
        this.columnNameLatestTimelineItemDate = columnNameLatestTimelineItemDate;
        this.columnNameTimelineDownloadedDate = columnNameTimelineDownloadedDate;
        this.connectionApiRoutine = connectionApiRoutine;
        this.mAtOrigin = atOrigin;
    }
    
    /**
     * String to be used for persistence
     */
    public String save() {
        return code;
    }
    
    @Override
    public String toString() {
        return "timeline:" + code;
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
        } else if (atOrigin()) {
            return context.getText(R.string.combined_timeline_off_origin);
        } else {
            return context.getText(R.string.combined_timeline_off_account);
        }
    }
    
    public boolean atOrigin() {
        return mAtOrigin;
    }
    
    public Connection.ApiRoutineEnum getConnectionApiRoutine() {
        return connectionApiRoutine;
    }
    
    /**
     * Returns the enum or UNKNOWN
     */
    public static TimelineType load(String strCode) {
        for (TimelineType tt : TimelineType.values()) {
            if (tt.code.equals(strCode)) {
                return tt;
            }
        }
        return UNKNOWN;
    }
}