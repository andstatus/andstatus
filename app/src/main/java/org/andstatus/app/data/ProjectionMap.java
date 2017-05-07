/* 
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.provider.BaseColumns;

import org.andstatus.app.database.DownloadTable;
import org.andstatus.app.database.FriendshipTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.MsgOfUserTable;
import org.andstatus.app.database.UserTable;

import java.util.HashMap;
import java.util.Map;

public class ProjectionMap {
    public static final String MSG_TABLE_ALIAS = "msg1";
    public static final String ATTACHMENT_IMAGE_TABLE_ALIAS = "img";
    public static final String AVATAR_IMAGE_TABLE_ALIAS = "av";
 
    /**
     * Projection map used by SQLiteQueryBuilder
     * Projection map for the {@link MsgTable} table
     * @see android.database.sqlite.SQLiteQueryBuilder#setProjectionMap
     */
    static final Map<String, String> MSG = new HashMap<>();
    static {
        MSG.put(BaseColumns._ID, MSG_TABLE_ALIAS + "." + BaseColumns._ID + " AS " + BaseColumns._ID);
        MSG.put(MsgTable.MSG_ID, MSG_TABLE_ALIAS + "." + BaseColumns._ID + " AS " + MsgTable.MSG_ID);
        MSG.put(MsgTable.ORIGIN_ID, MsgTable.ORIGIN_ID);
        MSG.put(MsgTable.MSG_OID, MsgTable.MSG_OID);
        MSG.put(MsgTable.AUTHOR_ID, MsgTable.AUTHOR_ID);
        MSG.put(UserTable.AUTHOR_NAME, UserTable.AUTHOR_NAME);
        MSG.put(DownloadTable.DOWNLOAD_STATUS, DownloadTable.DOWNLOAD_STATUS);
        MSG.put(DownloadTable.FILE_NAME, DownloadTable.FILE_NAME);
        MSG.put(DownloadTable.AVATAR_FILE_NAME, AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.FILE_NAME + " AS " + DownloadTable.AVATAR_FILE_NAME);
        MSG.put(DownloadTable.IMAGE_FILE_NAME, ATTACHMENT_IMAGE_TABLE_ALIAS + "." + DownloadTable.FILE_NAME + " AS " + DownloadTable.IMAGE_FILE_NAME);
        MSG.put(DownloadTable.IMAGE_ID, ATTACHMENT_IMAGE_TABLE_ALIAS + "." + DownloadTable._ID + " AS " + DownloadTable.IMAGE_ID);
        MSG.put(DownloadTable.IMAGE_URL, ATTACHMENT_IMAGE_TABLE_ALIAS + "." + DownloadTable.URI + " AS " + DownloadTable.IMAGE_URL);
        MSG.put(MsgTable.ACTOR_ID, MsgTable.ACTOR_ID);
        MSG.put(UserTable.SENDER_NAME, UserTable.SENDER_NAME);
        MSG.put(MsgTable.BODY, MsgTable.BODY);
        MSG.put(MsgTable.VIA, MsgTable.VIA);
        MSG.put(MsgTable.URL, MsgTable.URL);
        MSG.put(MsgTable.IN_REPLY_TO_MSG_ID, MsgTable.IN_REPLY_TO_MSG_ID);
        MSG.put(MsgTable.IN_REPLY_TO_USER_ID, MsgTable.IN_REPLY_TO_USER_ID);
        MSG.put(UserTable.IN_REPLY_TO_NAME, UserTable.IN_REPLY_TO_NAME);
        MSG.put(MsgTable.RECIPIENT_ID, MsgTable.RECIPIENT_ID);
        MSG.put(UserTable.RECIPIENT_NAME, UserTable.RECIPIENT_NAME);
        MSG.put(UserTable.LINKED_USER_ID, UserTable.LINKED_USER_ID);
        MSG.put(MsgOfUserTable.USER_ID, MsgOfUserTable.TABLE_NAME + "." + MsgOfUserTable.USER_ID + " AS " + MsgOfUserTable.USER_ID);
        MSG.put(MsgOfUserTable.DIRECTED, MsgOfUserTable.DIRECTED);
        MSG.put(MsgOfUserTable.FAVORITED, MsgOfUserTable.FAVORITED);
        MSG.put(MsgOfUserTable.REBLOGGED, MsgOfUserTable.REBLOGGED);
        MSG.put(MsgOfUserTable.REBLOG_OID, MsgOfUserTable.REBLOG_OID);
        MSG.put(MsgOfUserTable.SUBSCRIBED, MsgOfUserTable.SUBSCRIBED);
        MSG.put(MsgTable.UPDATED_DATE, MsgTable.UPDATED_DATE);
        MSG.put(MsgTable.MSG_STATUS, MsgTable.MSG_STATUS);
        MSG.put(MsgTable.SENT_DATE, MsgTable.SENT_DATE);
        MSG.put(MsgTable.INS_DATE, MsgTable.INS_DATE);
        MSG.put(FriendshipTable.AUTHOR_FOLLOWED, FriendshipTable.AUTHOR_FOLLOWED);
        MSG.put(FriendshipTable.SENDER_FOLLOWED, FriendshipTable.SENDER_FOLLOWED);
    }

    /**
     * Projection map for the {@link UserTable} table
     */
    static final Map<String, String> USER = new HashMap<>();
    static {
        USER.put(BaseColumns._ID, UserTable.TABLE_NAME + "." + BaseColumns._ID + " AS " + BaseColumns._ID);
        USER.put(UserTable.ORIGIN_ID, UserTable.ORIGIN_ID);
        USER.put(UserTable.USER_ID, UserTable.TABLE_NAME + "." + BaseColumns._ID + " AS " + UserTable.USER_ID);
        USER.put(UserTable.USER_OID, UserTable.USER_OID);
        USER.put(UserTable.USERNAME, UserTable.USERNAME);
        USER.put(UserTable.WEBFINGER_ID, UserTable.WEBFINGER_ID);
        USER.put(UserTable.REAL_NAME, UserTable.REAL_NAME);
        USER.put(UserTable.DESCRIPTION, UserTable.DESCRIPTION);
        USER.put(UserTable.LOCATION, UserTable.LOCATION);

        USER.put(UserTable.PROFILE_URL, UserTable.PROFILE_URL);
        USER.put(UserTable.HOMEPAGE, UserTable.HOMEPAGE);
        USER.put(UserTable.AVATAR_URL, UserTable.AVATAR_URL);
        USER.put(DownloadTable.AVATAR_FILE_NAME, AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.FILE_NAME + " AS " + DownloadTable.AVATAR_FILE_NAME);
        USER.put(UserTable.BANNER_URL, UserTable.BANNER_URL);

        USER.put(UserTable.MSG_COUNT, UserTable.MSG_COUNT);
        USER.put(UserTable.FAVORITES_COUNT, UserTable.FAVORITES_COUNT);
        USER.put(UserTable.FOLLOWING_COUNT, UserTable.FOLLOWING_COUNT);
        USER.put(UserTable.FOLLOWERS_COUNT, UserTable.FOLLOWERS_COUNT);

        USER.put(UserTable.CREATED_DATE, UserTable.CREATED_DATE);
        USER.put(UserTable.UPDATED_DATE, UserTable.UPDATED_DATE);
        USER.put(UserTable.INS_DATE, UserTable.INS_DATE);
        
        USER.put(UserTable.USER_MSG_ID, UserTable.USER_MSG_ID);
        USER.put(UserTable.USER_MSG_DATE, UserTable.USER_MSG_DATE);
    }
    
    private ProjectionMap() {
        // Empty
    }
}
