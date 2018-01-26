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

import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.database.table.ActorTable;

import java.util.HashMap;
import java.util.Map;

public class ProjectionMap {
    public static final String ACTIVITY_TABLE_ALIAS = "act1";
    public static final String MSG_TABLE_ALIAS = "msg1";
    public static final String ATTACHMENT_IMAGE_TABLE_ALIAS = "img";
    public static final String ACTOR_AVATAR_IMAGE_TABLE_ALIAS = "acav";
    public static final String AVATAR_IMAGE_TABLE_ALIAS = "av";
    /**
     * Projection map used by SQLiteQueryBuilder
     * Projection map for a Timeline
     * @see android.database.sqlite.SQLiteQueryBuilder#setProjectionMap
     */
    static final Map<String, String> MSG = new HashMap<>();
    static {
        MSG.put(ActivityTable.ACTIVITY_ID, ACTIVITY_TABLE_ALIAS + "." + BaseColumns._ID
                + " AS " + ActivityTable.ACTIVITY_ID);
        MSG.put(ActivityTable.ORIGIN_ID, ActivityTable.ORIGIN_ID);
        MSG.put(ActivityTable.ACCOUNT_ID, ActivityTable.ACCOUNT_ID);
        MSG.put(ActivityTable.ACTIVITY_TYPE, ActivityTable.ACTIVITY_TYPE);
        MSG.put(ActivityTable.ACTOR_ID, ActivityTable.ACTOR_ID);
        MSG.put(ActivityTable.AUTHOR_ID, ActivityTable.AUTHOR_ID);
        MSG.put(ActivityTable.MSG_ID, ActivityTable.MSG_ID);
        MSG.put(ActivityTable.USER_ID, ActivityTable.USER_ID);
        MSG.put(ActivityTable.SUBSCRIBED, ActivityTable.SUBSCRIBED);
        MSG.put(ActivityTable.NOTIFIED, ActivityTable.NOTIFIED);
        MSG.put(ActivityTable.INS_DATE, ActivityTable.INS_DATE);
        MSG.put(ActivityTable.UPDATED_DATE, ActivityTable.UPDATED_DATE);

        MSG.put(BaseColumns._ID, MSG_TABLE_ALIAS + "." + BaseColumns._ID + " AS " + BaseColumns._ID);
        MSG.put(MsgTable.MSG_ID, MSG_TABLE_ALIAS + "." + BaseColumns._ID + " AS " + MsgTable.MSG_ID);
        MSG.put(MsgTable.ORIGIN_ID, MsgTable.ORIGIN_ID);
        MSG.put(MsgTable.MSG_OID, MsgTable.MSG_OID);
        MSG.put(MsgTable.AUTHOR_ID, MsgTable.AUTHOR_ID);
        MSG.put(ActorTable.AUTHOR_NAME, ActorTable.AUTHOR_NAME);
        MSG.put(DownloadTable.DOWNLOAD_STATUS, DownloadTable.DOWNLOAD_STATUS);
        MSG.put(DownloadTable.FILE_NAME, DownloadTable.FILE_NAME);
        MSG.put(DownloadTable.AVATAR_FILE_NAME, AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.FILE_NAME
                + " AS " + DownloadTable.AVATAR_FILE_NAME);
        MSG.put(DownloadTable.IMAGE_FILE_NAME, ATTACHMENT_IMAGE_TABLE_ALIAS + "." + DownloadTable.FILE_NAME
                + " AS " + DownloadTable.IMAGE_FILE_NAME);
        MSG.put(DownloadTable.IMAGE_ID, ATTACHMENT_IMAGE_TABLE_ALIAS + "." + DownloadTable._ID
                + " AS " + DownloadTable.IMAGE_ID);
        MSG.put(DownloadTable.IMAGE_URL, ATTACHMENT_IMAGE_TABLE_ALIAS + "." + DownloadTable.URI
                + " AS " + DownloadTable.IMAGE_URL);
        MSG.put(MsgTable.BODY, MsgTable.BODY);
        MSG.put(MsgTable.VIA, MsgTable.VIA);
        MSG.put(MsgTable.URL, MsgTable.URL);
        MSG.put(MsgTable.IN_REPLY_TO_MSG_ID, MsgTable.IN_REPLY_TO_MSG_ID);
        MSG.put(MsgTable.IN_REPLY_TO_USER_ID, MsgTable.IN_REPLY_TO_USER_ID);
        MSG.put(ActorTable.IN_REPLY_TO_NAME, ActorTable.IN_REPLY_TO_NAME);
        MSG.put(MsgTable.PRIVATE, MsgTable.PRIVATE);
        MSG.put(MsgTable.FAVORITED, MsgTable.FAVORITED);
        MSG.put(MsgTable.REBLOGGED, MsgTable.REBLOGGED);
        MSG.put(MsgTable.UPDATED_DATE, MsgTable.UPDATED_DATE);
        MSG.put(MsgTable.MSG_STATUS, MsgTable.MSG_STATUS);
        MSG.put(MsgTable.INS_DATE, MsgTable.INS_DATE);
    }

    /**
     * Projection map for the {@link ActorTable} table
     */
    static final Map<String, String> USER = new HashMap<>();
    static {
        USER.put(BaseColumns._ID, ActorTable.TABLE_NAME + "." + BaseColumns._ID + " AS " + BaseColumns._ID);
        USER.put(ActorTable.ORIGIN_ID, ActorTable.ORIGIN_ID);
        USER.put(ActorTable.ACTOR_ID, ActorTable.TABLE_NAME + "." + BaseColumns._ID + " AS " + ActorTable.ACTOR_ID);
        USER.put(ActorTable.ACTOR_OID, ActorTable.ACTOR_OID);
        USER.put(ActorTable.ACTORNAME, ActorTable.ACTORNAME);
        USER.put(ActorTable.WEBFINGER_ID, ActorTable.WEBFINGER_ID);
        USER.put(ActorTable.REAL_NAME, ActorTable.REAL_NAME);
        USER.put(ActorTable.DESCRIPTION, ActorTable.DESCRIPTION);
        USER.put(ActorTable.LOCATION, ActorTable.LOCATION);

        USER.put(ActorTable.PROFILE_URL, ActorTable.PROFILE_URL);
        USER.put(ActorTable.HOMEPAGE, ActorTable.HOMEPAGE);
        USER.put(ActorTable.AVATAR_URL, ActorTable.AVATAR_URL);
        USER.put(DownloadTable.AVATAR_FILE_NAME, AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.FILE_NAME + " AS " + DownloadTable.AVATAR_FILE_NAME);
        USER.put(ActorTable.BANNER_URL, ActorTable.BANNER_URL);

        USER.put(ActorTable.MSG_COUNT, ActorTable.MSG_COUNT);
        USER.put(ActorTable.FAVORITES_COUNT, ActorTable.FAVORITES_COUNT);
        USER.put(ActorTable.FOLLOWING_COUNT, ActorTable.FOLLOWING_COUNT);
        USER.put(ActorTable.FOLLOWERS_COUNT, ActorTable.FOLLOWERS_COUNT);

        USER.put(ActorTable.CREATED_DATE, ActorTable.CREATED_DATE);
        USER.put(ActorTable.UPDATED_DATE, ActorTable.UPDATED_DATE);
        USER.put(ActorTable.INS_DATE, ActorTable.INS_DATE);
        
        USER.put(ActorTable.ACTOR_ACTIVITY_ID, ActorTable.ACTOR_ACTIVITY_ID);
        USER.put(ActorTable.ACTOR_ACTIVITY_DATE, ActorTable.ACTOR_ACTIVITY_DATE);
    }
    
    private ProjectionMap() {
        // Empty
    }
}
