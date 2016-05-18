/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.database;

import android.provider.BaseColumns;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.net.social.Connection;

/**
 * Users table (they are both senders AND recipients in the {@link MsgTable} table)
 * Some of these Users are Accounts (connected to accounts in AndStatus),
 * see {@link MyAccount#getUserId()}
 */
public final class UserTable implements BaseColumns {
    public static final String TABLE_NAME = "user";

    private UserTable() {
    }

    // Table columns
    /* {@link BaseColumns#_ID} is primary key in this database  */

    /**
     * ID of the originating (source) system (twitter.com, identi.ca, ... ) where the row was created
     */
    public static final String ORIGIN_ID =  MsgTable.ORIGIN_ID;
    /**
     * ID in the originating system
     * The id is not unique for this table, because we have IDs from different systems in one column.
     */
    public static final String USER_OID = "user_oid";
    /** This is called "screen_name" in Twitter API */
    public static final String USERNAME = "username";
    /** It looks like an email address with your nickname then "@" then your server */
    public static final String WEBFINGER_ID = "webfinger_id";
    /** This is called "name" in Twitter API */
    public static final String REAL_NAME = "real_name";
    /** User's description / "About myself" */
    public static final String DESCRIPTION = "user_description";
    /** Location string */
    public static final String LOCATION = "location";
    /**
     * User's profile URL
     * A link to the representation of the resource. Currently this is simply URL to the HTML
     * representation of the resource (its "permalink")
     */
    public static final String PROFILE_URL = "profile_url";
    /** URL of User's web home page */
    public static final String HOMEPAGE = "homepage";
    /** The latest url of the avatar */
    public static final String AVATAR_URL = "avatar_url";
    public static final String BANNER_URL = "banner_url";

    public static final String MSG_COUNT = "msg_count";
    public static final String FAVORITES_COUNT = "favorited_count";
    public static final String FOLLOWING_COUNT = "following_count";
    public static final String FOLLOWERS_COUNT = "followers_count";

    /**
     * Date and time when the row was created in the originating system.
     * We store it as long returned by {@link Connection#dateFromJson}.
     * NULL means the row was not retrieved from the Internet yet
     * (And maybe there is no such User in the originating system...)
     */
    public static final String CREATED_DATE = "user_created_date";
    public static final String UPDATED_DATE = "user_updated_date";
    /** Date and time the row was inserted into this database */
    public static final String INS_DATE = "user_ins_date";

    /**
     * Columns holding information on timelines downloaded:
     * last message id and last date-time the timeline was downloaded.
     */
    public static final String HOME_TIMELINE_POSITION = "home_timeline_position";
    public static final String HOME_TIMELINE_ITEM_DATE = "home_timeline_item_date";
    public static final String HOME_TIMELINE_DATE = "home_timeline_date";
    public static final String FAVORITES_TIMELINE_POSITION = "favorites_timeline_position";
    public static final String FAVORITES_TIMELINE_ITEM_DATE = "favorites_timeline_item_date";
    public static final String FAVORITES_TIMELINE_DATE = "favorites_timeline_date";
    public static final String DIRECT_TIMELINE_POSITION = "direct_timeline_position";
    public static final String DIRECT_TIMELINE_ITEM_DATE = "direct_timeline_item_date";
    public static final String DIRECT_TIMELINE_DATE = "direct_timeline_date";
    public static final String MENTIONS_TIMELINE_POSITION = "mentions_timeline_position";
    public static final String MENTIONS_TIMELINE_ITEM_DATE = "mentions_timeline_item_date";
    public static final String MENTIONS_TIMELINE_DATE = "mentions_timeline_date";
    public static final String USER_TIMELINE_POSITION = "user_timeline_position";
    public static final String USER_TIMELINE_ITEM_DATE = "user_timeline_item_date";
    public static final String USER_TIMELINE_DATE = "user_timeline_date";

    /**
     * For the list ("collection") of following users
     * we store only the date-time of the last retrieval of the list
     */
    public static final String FOLLOWING_USER_DATE = "following_user_date";
    public static final String FOLLOWERS_USER_DATE = "followers_user_date";
    /**
     * Id of the latest message where this User was a Sender or an Author
     */
    public static final String USER_MSG_ID = "user_msg_id";
    /**
     * Date of the latest message where this User was a Sender or an Author
     */
    public static final String USER_MSG_DATE = "user_msg_date";

    /*
     * Derived columns (they are not stored in this table but are result of joins)
     */
    /** Alias for the primary key */
    public static final String USER_ID = "user_id";
    /** Alias used in a timeline to distinguish messages for different users */
    public static final String LINKED_USER_ID = "linked_user_id";
    /**
     * Derived from {@link MsgTable#SENDER_ID}
     * TODO: Whether this (and other similar...) is {@link #USERNAME} or {@link #REAL_NAME}, depends on settings
     */
    public static final String SENDER_NAME = "sender_name";
    /** Derived from {@link MsgTable#AUTHOR_ID} */
    public static final String AUTHOR_NAME = "author_name";
    /** Derived from {@link MsgTable#IN_REPLY_TO_USER_ID} */
    public static final String IN_REPLY_TO_NAME = "in_reply_to_name";
    /** Derived from {@link MsgTable#RECIPIENT_ID} */
    public static final String RECIPIENT_NAME = "recipient_name";

    public static final String DEFAULT_SORT_ORDER = USERNAME + " ASC";
}
