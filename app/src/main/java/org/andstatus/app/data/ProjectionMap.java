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
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.database.table.NoteTable;

import java.util.HashMap;
import java.util.Map;

public class ProjectionMap {
    public static final String ACTIVITY_TABLE_ALIAS = "act1";
    public static final String NOTE_TABLE_ALIAS = "msg1";
    public static final String ATTACHMENT_IMAGE_TABLE_ALIAS = "img";
    public static final String AVATAR_IMAGE_TABLE_ALIAS = "av";
    /**
     * Projection map used by SQLiteQueryBuilder
     * Projection map for a Timeline
     * @see android.database.sqlite.SQLiteQueryBuilder#setProjectionMap
     */
    static final Map<String, String> TIMELINE = new HashMap<>();
    static {
        TIMELINE.put(ActivityTable.ACTIVITY_ID, ACTIVITY_TABLE_ALIAS + "." + BaseColumns._ID
                + " AS " + ActivityTable.ACTIVITY_ID);
        TIMELINE.put(ActivityTable.ORIGIN_ID, ActivityTable.ORIGIN_ID);
        TIMELINE.put(ActivityTable.ACCOUNT_ID, ActivityTable.ACCOUNT_ID);
        TIMELINE.put(ActivityTable.ACTIVITY_TYPE, ActivityTable.ACTIVITY_TYPE);
        TIMELINE.put(ActivityTable.ACTOR_ID, ActivityTable.ACTOR_ID);
        TIMELINE.put(ActivityTable.AUTHOR_ID, ActivityTable.AUTHOR_ID);
        TIMELINE.put(ActivityTable.NOTE_ID, ActivityTable.NOTE_ID);
        TIMELINE.put(ActivityTable.OBJ_ACTOR_ID, ActivityTable.OBJ_ACTOR_ID);
        TIMELINE.put(ActivityTable.SUBSCRIBED, ActivityTable.SUBSCRIBED);
        TIMELINE.put(ActivityTable.NOTIFIED, ActivityTable.NOTIFIED);
        TIMELINE.put(ActivityTable.INS_DATE, ActivityTable.INS_DATE);
        TIMELINE.put(ActivityTable.UPDATED_DATE, ActivityTable.UPDATED_DATE);

        TIMELINE.put(BaseColumns._ID, NOTE_TABLE_ALIAS + "." + BaseColumns._ID + " AS " + BaseColumns._ID);
        TIMELINE.put(NoteTable.NOTE_ID, NOTE_TABLE_ALIAS + "." + BaseColumns._ID + " AS " + NoteTable.NOTE_ID);
        TIMELINE.put(NoteTable.ORIGIN_ID, NoteTable.ORIGIN_ID);
        TIMELINE.put(NoteTable.NOTE_OID, NoteTable.NOTE_OID);
        TIMELINE.put(NoteTable.CONVERSATION_ID, NoteTable.CONVERSATION_ID);
        TIMELINE.put(NoteTable.AUTHOR_ID, NoteTable.AUTHOR_ID);
        TIMELINE.put(ActorTable.AUTHOR_NAME, ActorTable.AUTHOR_NAME);
        TIMELINE.put(DownloadTable.DOWNLOAD_STATUS, DownloadTable.DOWNLOAD_STATUS);
        TIMELINE.put(DownloadTable.FILE_NAME, DownloadTable.FILE_NAME);
        TIMELINE.put(DownloadTable.AVATAR_FILE_NAME, AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.FILE_NAME
                + " AS " + DownloadTable.AVATAR_FILE_NAME);
        TIMELINE.put(DownloadTable.IMAGE_FILE_NAME, ATTACHMENT_IMAGE_TABLE_ALIAS + "." + DownloadTable.FILE_NAME
                + " AS " + DownloadTable.IMAGE_FILE_NAME);
        TIMELINE.put(DownloadTable.IMAGE_ID, ATTACHMENT_IMAGE_TABLE_ALIAS + "." + DownloadTable._ID
                + " AS " + DownloadTable.IMAGE_ID);
        TIMELINE.put(DownloadTable.IMAGE_URL, ATTACHMENT_IMAGE_TABLE_ALIAS + "." + DownloadTable.URI
                + " AS " + DownloadTable.IMAGE_URL);
        TIMELINE.put(DownloadTable.WIDTH, DownloadTable.WIDTH);
        TIMELINE.put(DownloadTable.HEIGHT, DownloadTable.HEIGHT);
        TIMELINE.put(DownloadTable.DURATION, DownloadTable.DURATION);
        TIMELINE.put(NoteTable.NAME, NoteTable.NAME);
        TIMELINE.put(NoteTable.CONTENT, NoteTable.CONTENT);
        TIMELINE.put(NoteTable.CONTENT_TO_SEARCH, NoteTable.CONTENT_TO_SEARCH);
        TIMELINE.put(NoteTable.VIA, NoteTable.VIA);
        TIMELINE.put(NoteTable.URL, NoteTable.URL);
        TIMELINE.put(NoteTable.IN_REPLY_TO_NOTE_ID, NoteTable.IN_REPLY_TO_NOTE_ID);
        TIMELINE.put(NoteTable.IN_REPLY_TO_ACTOR_ID, NoteTable.IN_REPLY_TO_ACTOR_ID);
        TIMELINE.put(NoteTable.PUBLIC, NoteTable.PUBLIC);
        TIMELINE.put(NoteTable.FAVORITED, NoteTable.FAVORITED);
        TIMELINE.put(NoteTable.REBLOGGED, NoteTable.REBLOGGED);
        TIMELINE.put(NoteTable.UPDATED_DATE, NoteTable.UPDATED_DATE);
        TIMELINE.put(NoteTable.NOTE_STATUS, NoteTable.NOTE_STATUS);
        TIMELINE.put(NoteTable.INS_DATE, NoteTable.INS_DATE);
    }

    /**
     * Projection map for the {@link ActorTable} table
     */
    public static final Map<String, String> ACTORLIST = new HashMap<>();
    static {
        ACTORLIST.put(BaseColumns._ID, ActorTable.TABLE_NAME + "." + BaseColumns._ID + " AS " + BaseColumns._ID);
        ACTORLIST.put(ActorTable.ORIGIN_ID, ActorTable.ORIGIN_ID);
        ACTORLIST.put(ActorTable.ACTOR_ID, ActorTable.TABLE_NAME + "." + BaseColumns._ID + " AS " + ActorTable.ACTOR_ID);
        ACTORLIST.put(ActorTable.ACTOR_OID, ActorTable.ACTOR_OID);
        ACTORLIST.put(ActorTable.USERNAME, ActorTable.USERNAME);
        ACTORLIST.put(ActorTable.WEBFINGER_ID, ActorTable.WEBFINGER_ID);
        ACTORLIST.put(ActorTable.REAL_NAME, ActorTable.REAL_NAME);
        ACTORLIST.put(ActorTable.DESCRIPTION, ActorTable.DESCRIPTION);
        ACTORLIST.put(ActorTable.LOCATION, ActorTable.LOCATION);

        ACTORLIST.put(ActorTable.PROFILE_URL, ActorTable.PROFILE_URL);
        ACTORLIST.put(ActorTable.HOMEPAGE, ActorTable.HOMEPAGE);
        ACTORLIST.put(ActorTable.AVATAR_URL, ActorTable.AVATAR_URL);
        ACTORLIST.put(DownloadTable.AVATAR_FILE_NAME, AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.FILE_NAME
                + " AS " + DownloadTable.AVATAR_FILE_NAME);
        ACTORLIST.put(ActorTable.BANNER_URL, ActorTable.BANNER_URL);

        ACTORLIST.put(ActorTable.NOTES_COUNT, ActorTable.NOTES_COUNT);
        ACTORLIST.put(ActorTable.FAVORITES_COUNT, ActorTable.FAVORITES_COUNT);
        ACTORLIST.put(ActorTable.FOLLOWING_COUNT, ActorTable.FOLLOWING_COUNT);
        ACTORLIST.put(ActorTable.FOLLOWERS_COUNT, ActorTable.FOLLOWERS_COUNT);

        ACTORLIST.put(ActorTable.CREATED_DATE, ActorTable.CREATED_DATE);
        ACTORLIST.put(ActorTable.UPDATED_DATE, ActorTable.UPDATED_DATE);
        ACTORLIST.put(ActorTable.INS_DATE, ActorTable.INS_DATE);
        
        ACTORLIST.put(ActorTable.ACTOR_ACTIVITY_ID, ActorTable.ACTOR_ACTIVITY_ID);
        ACTORLIST.put(ActorTable.ACTOR_ACTIVITY_DATE, ActorTable.ACTOR_ACTIVITY_DATE);
    }
    
    private ProjectionMap() {
        // Empty
    }
}
