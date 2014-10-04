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
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase.FollowingUser;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.net.Connection;

/**
 * These values help set timeline filters closer to the database (in ContentProvider...)
 */
public enum TimelineTypeEnum {
    /**
     * The Timeline type is unknown
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
     * This User may be not the same as a user of current account ( {@link MyAccount#currentAccountName}}.
     * Moreover, the User may not be "AndStatus account" at all.
     * Hence this timeline type requires the User parameter.
     */
    USER("user", R.string.timeline_title_user, 
            User.USER_TIMELINE_POSITION, User.USER_TIMELINE_ITEM_DATE, User.USER_TIMELINE_DATE, Connection.ApiRoutineEnum.STATUSES_USER_TIMELINE),
    /**
     * For the selected user, the timeline includes all messages of the same origin irrespectively existence
     * of the link between the message and the User. So the User may "Act" on this message.
     */
    MESSAGESTOACT("messages_to_act", R.string.timeline_title_home, 
            "", "", "", Connection.ApiRoutineEnum.STATUSES_HOME_TIMELINE),
    /**
     * Latest messages of every user, followed by this User - AndStatus account. 
     * So this is essentially a list of "Following users". 
     * The timeline doesn't have Message ID because we download User IDs only 
     * See {@link FollowingUser}
     */
    FOLLOWING_USER("following_user", R.string.timeline_title_following_user, 
            "", "", User.FOLLOWING_USER_DATE, Connection.ApiRoutineEnum.GET_FRIENDS_IDS),
    /**
     * Replies
     */
    REPLIES("replies", R.string.timeline_title_replies, 
            "", "", "", Connection.ApiRoutineEnum.DUMMY),
    PUBLIC("public", R.string.timeline_title_public, 
            "", "", "", Connection.ApiRoutineEnum.PUBLIC_TIMELINE),
    /**
     * All timelines (e.g. for download of all timelines. 
     * This is generally done after addition of the new MyAccount).
     */
    ALL("all", R.string.timeline_title_all, 
            User.HOME_TIMELINE_POSITION, User.HOME_TIMELINE_ITEM_DATE, User.HOME_TIMELINE_DATE, Connection.ApiRoutineEnum.DUMMY);
    
    /**
     * code of the enum that is used in messages
     */
    private String code;
    /**
     * The id of the string resource with the localized name of this Timeline to use in UI
     */
    private int titleResId;
    /**
     * Position of the latest downloaded timeline item 
     * E.g. the "timeline item" is a "message" for Twitter and an "Activity" for Pump.Io.
     * Name of the column in the {@link User} table.  
     */
    private String columnNameLatestTimelinePosition;
    private String columnNameLatestTimelineItemDate;
    /**
     * Date when this timeline was last time fetched (downloaded). 
     * Name of the column in the {@link User} table.
     */
    private String columnNameTimelineDownloadedDate;
    /**
     * Api routine to download this timeline
     */
    private Connection.ApiRoutineEnum connectionApiRoutine;
    
    public String columnNameLatestTimelinePosition() {
        return columnNameLatestTimelinePosition;
    }

    public String columnNameLatestTimelineItemDate() {
        return columnNameLatestTimelineItemDate;
    }
    
    public String columnNameTimelineDownloadedDate() {
        return columnNameTimelineDownloadedDate;
    }
    
    private TimelineTypeEnum(String code, int resId, 
            String columnNameLatestTimelinePosition, String columnNameLatestTimelineItemDate, 
            String columnNameTimelineDownloadedDate, 
            Connection.ApiRoutineEnum connectionApiRoutine) {
        this.code = code;
        this.titleResId = resId;
        this.columnNameLatestTimelinePosition = columnNameLatestTimelinePosition;
        this.columnNameLatestTimelineItemDate = columnNameLatestTimelineItemDate;
        this.columnNameTimelineDownloadedDate = columnNameTimelineDownloadedDate;
        this.connectionApiRoutine = connectionApiRoutine;
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
        if (TimelineTypeEnum.PUBLIC.equals(this)) {
            return context.getText(R.string.combined_timeline_off_origin);
        } else {
            return context.getText(R.string.combined_timeline_off_account);
        }
    }
    
    public Connection.ApiRoutineEnum getConnectionApiRoutine() {
        return connectionApiRoutine;
    }
    
    /**
     * Returns the enum or UNKNOWN
     */
    public static TimelineTypeEnum load(String strCode) {
        for (TimelineTypeEnum tt : TimelineTypeEnum.values()) {
            if (tt.code.equals(strCode)) {
                return tt;
            }
        }
        return UNKNOWN;
    }
}