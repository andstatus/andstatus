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

/**
 * Link tables: Msg to a User.
 * This User may be not even mentioned in this message
 *   (e.g. SUBSCRIBED by the User who is not sender not recipient...).
 * This table is used to filter User's timelines (based on flags: SUBSCRIBED etc.)
 */
public final class MsgOfUserTable {
    public static final String TABLE_NAME = "msgofuser";
    private MsgOfUserTable() {
    }

    /**
     * Fields for joining tables
     */
    public static final String MSG_ID =  MsgTable.MSG_ID;
    public static final String USER_ID = UserTable.USER_ID;

    /**
     * The message is in the User's Home timeline
     * i.e. it corresponds to the TIMELINE_TYPE = TIMELINE_TYPE_HOME
     */
    public static final String SUBSCRIBED = "subscribed";
    /**
     * The Msg is favorite for this User
     */
    public static final String FAVORITED = "favorited";
    /**
     * The Msg is reblogged by this User
     * In some sense REBLOGGED is like FAVORITED.
     * Main difference: visibility. REBLOGGED are shown for all followers in their Home timelines.
     */
    public static final String REBLOGGED = "reblogged";
    /**
     * ID in the originating system of the "reblog" message
     * null for the message that was not reblogged
     * We use THIS id when we need to "undo reblog"
     */
    public static final String REBLOG_OID = "reblog_oid";
    /**
     * User is mentioned in this message
     */
    public static final String MENTIONED = "mentioned";
    /**
     * This User is not only (optionally) mentioned in this message (explicitly using "@username" form)
     * but the message has explicit reference to the User's message for which this message is a reply.
     */
    public static final String REPLIED = "replied";
    /**
     * This is Direct message which was sent by (Sender) or to (Recipient) this User
     */
    public static final String DIRECTED = "directed";

    /** It's not in a database, for passing data only */
    public static final String SUFFIX_FOR_OTHER_USER = "_other";
}
