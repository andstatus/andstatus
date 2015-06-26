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

import org.andstatus.app.data.MyDatabase.Download;
import org.andstatus.app.data.MyDatabase.FollowingUser;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.User;

import java.util.HashMap;
import java.util.Map;

public class ProjectionMap {
    public static final String MSG_TABLE_ALIAS = "msg1";
    public static final String ATTACHMENT_IMAGE_TABLE_ALIAS = "img";
    public static final String AVATAR_IMAGE_TABLE_ALIAS = "av";
 
    /**
     * Projection map used by SQLiteQueryBuilder
     * Projection map for the {@link MyDatabase.Msg} table
     * @see android.database.sqlite.SQLiteQueryBuilder#setProjectionMap
     */
    static final Map<String, String> MSG = new HashMap<String, String>();
    static {
        MSG.put(BaseColumns._ID, MSG_TABLE_ALIAS + "." + BaseColumns._ID + " AS " + BaseColumns._ID);
        MSG.put(Msg.MSG_ID, MSG_TABLE_ALIAS + "." + BaseColumns._ID + " AS " + Msg.MSG_ID);
        MSG.put(Msg.ORIGIN_ID, Msg.ORIGIN_ID);
        MSG.put(Msg.MSG_OID, Msg.MSG_OID);
        MSG.put(Msg.AUTHOR_ID, Msg.AUTHOR_ID);
        MSG.put(User.AUTHOR_NAME, User.AUTHOR_NAME);
        MSG.put(Download.DOWNLOAD_STATUS, Download.DOWNLOAD_STATUS);
        MSG.put(Download.FILE_NAME, Download.FILE_NAME);
        MSG.put(Download.AVATAR_FILE_NAME, AVATAR_IMAGE_TABLE_ALIAS + "." + Download.FILE_NAME + " AS " + Download.AVATAR_FILE_NAME);
        MSG.put(Download.IMAGE_FILE_NAME, ATTACHMENT_IMAGE_TABLE_ALIAS + "." + Download.FILE_NAME + " AS " + Download.IMAGE_FILE_NAME);
        MSG.put(Download.IMAGE_ID, ATTACHMENT_IMAGE_TABLE_ALIAS + "." + Download._ID + " AS " + Download.IMAGE_ID);
        MSG.put(Download.IMAGE_URL, ATTACHMENT_IMAGE_TABLE_ALIAS + "." + Download.URL + " AS " + Download.IMAGE_URL);
        MSG.put(Msg.SENDER_ID, Msg.SENDER_ID);
        MSG.put(User.SENDER_NAME, User.SENDER_NAME);
        MSG.put(Msg.BODY, Msg.BODY);
        MSG.put(Msg.VIA, Msg.VIA);
        MSG.put(Msg.URL, Msg.URL);
        MSG.put(Msg.IN_REPLY_TO_MSG_ID, Msg.IN_REPLY_TO_MSG_ID);
        MSG.put(Msg.IN_REPLY_TO_USER_ID, Msg.IN_REPLY_TO_USER_ID);
        MSG.put(User.IN_REPLY_TO_NAME, User.IN_REPLY_TO_NAME);
        MSG.put(Msg.RECIPIENT_ID, Msg.RECIPIENT_ID);
        MSG.put(User.RECIPIENT_NAME, User.RECIPIENT_NAME);
        MSG.put(User.LINKED_USER_ID, User.LINKED_USER_ID);
        MSG.put(MsgOfUser.USER_ID, MsgOfUser.TABLE_NAME + "." + MsgOfUser.USER_ID + " AS " + MsgOfUser.USER_ID);
        MSG.put(MsgOfUser.DIRECTED, MsgOfUser.DIRECTED);
        MSG.put(MsgOfUser.FAVORITED, MsgOfUser.FAVORITED);
        MSG.put(MsgOfUser.REBLOGGED, MsgOfUser.REBLOGGED);
        MSG.put(MsgOfUser.REBLOG_OID, MsgOfUser.REBLOG_OID);
        MSG.put(MsgOfUser.SUBSCRIBED, MsgOfUser.SUBSCRIBED);
        MSG.put(Msg.CREATED_DATE, Msg.CREATED_DATE);
        MSG.put(Msg.SENT_DATE, Msg.SENT_DATE);
        MSG.put(Msg.INS_DATE, Msg.INS_DATE);
        MSG.put(FollowingUser.AUTHOR_FOLLOWED, FollowingUser.AUTHOR_FOLLOWED);
        MSG.put(FollowingUser.SENDER_FOLLOWED, FollowingUser.SENDER_FOLLOWED);
    }

    /**
     * Projection map for the {@link MyDatabase.User} table
     */
    static final Map<String, String> USER = new HashMap<String, String>();
    static {
        USER.put(BaseColumns._ID, User.TABLE_NAME + "." + BaseColumns._ID + " AS " + BaseColumns._ID);
        USER.put(User.USER_ID, User.TABLE_NAME + "." + BaseColumns._ID + " AS " + User.USER_ID);
        USER.put(User.USER_OID, User.USER_OID);
        USER.put(User.ORIGIN_ID, User.ORIGIN_ID);
        USER.put(User.USERNAME, User.USERNAME);
        USER.put(User.AVATAR_URL, User.AVATAR_URL);
        USER.put(User.URL, User.URL);
        USER.put(User.CREATED_DATE, User.CREATED_DATE);
        USER.put(User.INS_DATE, User.INS_DATE);
        
        USER.put(User.HOME_TIMELINE_POSITION, User.HOME_TIMELINE_POSITION);
        USER.put(User.HOME_TIMELINE_DATE, User.HOME_TIMELINE_DATE);
        USER.put(User.FAVORITES_TIMELINE_POSITION, User.FAVORITES_TIMELINE_POSITION);
        USER.put(User.FAVORITES_TIMELINE_DATE, User.FAVORITES_TIMELINE_DATE);
        USER.put(User.DIRECT_TIMELINE_POSITION, User.DIRECT_TIMELINE_POSITION);
        USER.put(User.DIRECT_TIMELINE_DATE, User.DIRECT_TIMELINE_DATE);
        USER.put(User.MENTIONS_TIMELINE_POSITION, User.MENTIONS_TIMELINE_POSITION);
        USER.put(User.MENTIONS_TIMELINE_DATE, User.MENTIONS_TIMELINE_DATE);
        USER.put(User.USER_TIMELINE_POSITION, User.USER_TIMELINE_POSITION);
        USER.put(User.USER_TIMELINE_DATE, User.USER_TIMELINE_DATE);
        USER.put(User.USER_MSG_ID, User.USER_MSG_ID);
        USER.put(User.USER_MSG_DATE, User.USER_MSG_DATE);
    }
    
    private ProjectionMap() {
        // Empty
    }
}
