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
    UNKNOWN("unknown", R.string.unimplemented, User.HOME_TIMELINE_POSITION, User.HOME_TIMELINE_ITEM_DATE, User.HOME_TIMELINE_DATE, Connection.ApiRoutineEnum.DUMMY),
    /**
     * The Home timeline and other information (replies...).
     */
    HOME("home", R.string.timeline_title_home, User.HOME_TIMELINE_POSITION, User.HOME_TIMELINE_ITEM_DATE, User.HOME_TIMELINE_DATE, Connection.ApiRoutineEnum.STATUSES_HOME_TIMELINE),
    /**
     * The Mentions timeline and other information (replies...).
     */
    MENTIONS("mentions", R.string.timeline_title_mentions, User.MENTIONS_TIMELINE_POSITION, User.MENTIONS_TIMELINE_ITEM_DATE, User.MENTIONS_TIMELINE_DATE, Connection.ApiRoutineEnum.STATUSES_MENTIONS_TIMELINE),
    /**
     * Direct messages (direct dents...)
     */
    DIRECT("direct", R.string.timeline_title_direct_messages, User.DIRECT_TIMELINE_POSITION, User.DIRECT_TIMELINE_ITEM_DATE, User.DIRECT_TIMELINE_DATE, Connection.ApiRoutineEnum.DIRECT_MESSAGES),
    /**
     * Favorites (favorited messages)
     */
    FAVORITES("favorites", R.string.timeline_title_favorites, User.FAVORITES_TIMELINE_POSITION, User.FAVORITES_TIMELINE_ITEM_DATE, User.FAVORITES_TIMELINE_DATE, Connection.ApiRoutineEnum.DUMMY),
    /**
     * Messages of the selected User (where he is an Author or a Sender only (e.g. for Reblog/Retweet). 
     * This User may be not the same as a user of current account ( {@link MyAccount#currentAccountName}}.
     * Moreover, the User may not be "AndStatus account" at all.
     * Hence this timeline type requires the User parameter.
     */
    USER("user", R.string.timeline_title_user, User.USER_TIMELINE_POSITION, User.USER_TIMELINE_ITEM_DATE, User.USER_TIMELINE_DATE, Connection.ApiRoutineEnum.STATUSES_USER_TIMELINE),
    /**
     * For the selected user, the timeline includes all messages of the same origin irrespectively existence
     * of the link between the message and the User. So the User may "Act" on this message.
     */
    MESSAGESTOACT("messages_to_act", R.string.timeline_title_home, User.HOME_TIMELINE_POSITION, User.HOME_TIMELINE_ITEM_DATE, User.HOME_TIMELINE_DATE, Connection.ApiRoutineEnum.STATUSES_HOME_TIMELINE),
    /**
     * Latest messages of every Following User (Following by this User - AndStatus account). 
     * So this is essentially a list of "Following users". 
     * The timeline doesn't have Message ID because we download User IDs only 
     * See {@link FollowingUser}
     */
    FOLLOWING_USER("following_user", R.string.timeline_title_following_user, "", "", User.FOLLOWING_USER_DATE, Connection.ApiRoutineEnum.GET_FRIENDS_IDS),
    /**
     * Replies
     */
    REPLIES("replies", R.string.timeline_title_replies, User.HOME_TIMELINE_POSITION, User.HOME_TIMELINE_ITEM_DATE, User.HOME_TIMELINE_DATE, Connection.ApiRoutineEnum.DUMMY),
    /**
     * All timelines (e.g. for download of all timelines. 
     * This is generally done after addition of the new MyAccount).
     */
    ALL("all", R.string.unimplemented, User.HOME_TIMELINE_POSITION, User.HOME_TIMELINE_ITEM_DATE, User.HOME_TIMELINE_DATE, Connection.ApiRoutineEnum.DUMMY);
    
    /**
     * code of the enum that is used in messages
     */
    private String code;
    /**
     * The id of the string resource with the localized name of this Timeline to use in UI
     */
    private int resId;
    /**
     * Name of the column in the {@link User} table. The column contains position
     * of the latest downloaded timeline item 
     * E.g. the "timeline item" is a "message" for Twitter and an "Activity" for Pump.Io.  
     */
    private String columnNameLatestTimelinePosition;
    private String columnNameLatestTimelineItemDate;
    /**
     * Name of the column in the {@link User} table. The column contains the date when 
     * last time this timeline was retrieved.
     */
    private String columnNameTimelineDate;
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
    
    public String columnNameTimelineDate() {
        return columnNameTimelineDate;
    }
    
    private TimelineTypeEnum(String code, int resId, String columnNameLatestTimelinePosition, String columnNameLatestTimelineItemDate, String columnNameTimelineDate, Connection.ApiRoutineEnum connectionApiRoutine) {
        this.code = code;
        this.resId = resId;
        this.columnNameLatestTimelinePosition = columnNameLatestTimelinePosition;
        this.columnNameLatestTimelineItemDate = columnNameLatestTimelineItemDate;
        this.columnNameTimelineDate = columnNameTimelineDate;
        this.connectionApiRoutine = connectionApiRoutine;
    }

    /**
     * String code for the Command to be used in messages
     */
    public String save() {
        return code;
    }
    
    /**
     * The id of the string resource with the localized name to use in UI
     */
    public int resId() {
        return resId;
    }
    
    public Connection.ApiRoutineEnum getConnectionApiRoutine() {
        return connectionApiRoutine;
    }
    
    /**
     * Returns the enum for a String action code or UNKNOWN
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