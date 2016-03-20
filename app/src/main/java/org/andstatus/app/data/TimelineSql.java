/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.database.Cursor;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.UserInTimeline;
import org.andstatus.app.data.MyDatabase.Download;
import org.andstatus.app.data.MyDatabase.Friendship;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class TimelineSql {
    private TimelineSql() {
        // Empty
    }

    /**
     * @param uri the same as uri for
     *            {@link MyProvider#query(Uri, String[], String, String[], String)}
     * @param projection Projection
     * @return String for {@link SQLiteQueryBuilder#setTables(String)}
     */
    static String tablesForTimeline(Uri uri, String[] projection) {
        ParsedUri uriParser = ParsedUri.fromUri(uri);
        TimelineType tt = uriParser.getTimelineType();
        SelectedUserIds selectedAccounts = new SelectedUserIds(uriParser.isCombined(), uriParser.getAccountUserId());
    
        Collection<String> columns = new java.util.HashSet<>(Arrays.asList(projection));
    
        String msgTable = Msg.TABLE_NAME;
        String where = "";

        boolean linkedUserDefined = false;
        boolean authorNameDefined = false;
        String authorTableName = "";
        switch (tt) {
            case FOLLOWING_USER:
                msgTable = "(SELECT " + Friendship.FRIEND_ID + ", "
                        + MyDatabase.Friendship.USER_ID + " AS " + User.LINKED_USER_ID
                        + " FROM " + MyDatabase.Friendship.TABLE_NAME
                        + " WHERE (" + MyDatabase.User.LINKED_USER_ID + selectedAccounts.getSql()
                        + " AND " + MyDatabase.Friendship.FOLLOWED + "=1 )"
                        + ") as fUser";
                linkedUserDefined = true;
                boolean defineAuthorName = columns.contains(MyDatabase.User.AUTHOR_NAME);
                if (defineAuthorName) {
                    authorNameDefined = true;
                    authorTableName = "u1";
                }
                String userTable = "(SELECT "
                        + BaseColumns._ID
                        + (defineAuthorName ? ", " + MyDatabase.User.USERNAME + " AS " + MyDatabase.User.AUTHOR_NAME : "")
                        + ", " + MyDatabase.User.USER_MSG_ID
                        + " FROM " + User.TABLE_NAME + ")";
                msgTable += " INNER JOIN " + userTable + " as u1"
                        + " ON (" + MyDatabase.Friendship.FRIEND_ID + "=u1." + BaseColumns._ID + ")";
                /**
                 * Select only the latest message from each following User's
                 * timeline
                 */
                msgTable  += " LEFT JOIN " + Msg.TABLE_NAME + " AS " + ProjectionMap.MSG_TABLE_ALIAS
                        + " ON ("
                        + ProjectionMap.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.SENDER_ID
                        + "=fUser." + Friendship.FRIEND_ID
                        + " AND " + ProjectionMap.MSG_TABLE_ALIAS + "." + BaseColumns._ID
                        + "=u1." + MyDatabase.User.USER_MSG_ID
                        + ")";
                break;
            case MESSAGES_TO_ACT:
                if (selectedAccounts.size() == 1) {
                    msgTable = "SELECT " + selectedAccounts.getList() + " AS " + MyDatabase.User.LINKED_USER_ID
                            + ", * FROM " + Msg.TABLE_NAME;
                    linkedUserDefined = true;
                }
                break;
            case PUBLIC:
                where = Msg.PUBLIC + "=1";
                break;
            case DRAFTS:
                where = Msg.MSG_STATUS + "=" + DownloadStatus.DRAFT.save();
                break;
            case OUTBOX:
                where = Msg.MSG_STATUS + "=" + DownloadStatus.SENDING.save();
                break;
            case EVERYTHING:
            default:
                break;
        }

        String tables = msgTable;
        if (!tables.contains(" AS " + ProjectionMap.MSG_TABLE_ALIAS)) {
            if (tt.atOrigin() && !uriParser.isCombined()) {
                MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(uriParser.getAccountUserId());
                if (ma.isValid()) {
                    if (!TextUtils.isEmpty(where)) {
                        where += " AND ";
                    }
                    where += Msg.ORIGIN_ID + "=" + ma.getOriginId();
                }
            }
            tables = "(SELECT * FROM (" + msgTable + ")"
                            + (TextUtils.isEmpty(where) ? "" : " WHERE (" + where + ")")
                            + ") AS " + ProjectionMap.MSG_TABLE_ALIAS;
        }

        if (columns.contains(MyDatabase.MsgOfUser.FAVORITED)
                || (columns.contains(MyDatabase.User.LINKED_USER_ID) && !linkedUserDefined)
                ) {
            String tbl = "(SELECT *" 
                    + (linkedUserDefined ? "" : ", " + MyDatabase.MsgOfUser.USER_ID + " AS " 
                    + MyDatabase.User.LINKED_USER_ID)   
                    + " FROM " +  MsgOfUser.TABLE_NAME + ") AS mou ON "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + BaseColumns._ID + "="
                    + "mou." + MyDatabase.MsgOfUser.MSG_ID;
            switch (tt) {
                case FOLLOWING_USER:
                case MESSAGES_TO_ACT:
                    tbl += " AND mou." + MyDatabase.MsgOfUser.USER_ID 
                    + "=" + MyDatabase.User.LINKED_USER_ID;
                    tables += " LEFT JOIN " + tbl;
                    break;
                default:
                    tbl += " AND " + MyDatabase.User.LINKED_USER_ID + selectedAccounts.getSql();
                    if (tt.atOrigin()) {
                        tables += " LEFT OUTER JOIN " + tbl;
                    } else {
                        tables += " INNER JOIN " + tbl;
                    }
                    break;
            }
        }
    
        if (!authorNameDefined && columns.contains(MyDatabase.User.AUTHOR_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + BaseColumns._ID + ", " 
                    + TimelineSql.userNameField() + " AS " + MyDatabase.User.AUTHOR_NAME
                    + " FROM " + User.TABLE_NAME + ") AS author ON "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.AUTHOR_ID + "=author."
                    + BaseColumns._ID;
            authorNameDefined = true;
            authorTableName = "author";
        }
        if (authorNameDefined && columns.contains(MyDatabase.Download.AVATAR_FILE_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + MyDatabase.Download.USER_ID + ", "
                    + MyDatabase.Download.DOWNLOAD_STATUS + ", "
                    + MyDatabase.Download.FILE_NAME
                    + " FROM " + MyDatabase.Download.TABLE_NAME + ") AS " + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS 
                    + " ON "
                    + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS + "." + Download.DOWNLOAD_STATUS
                    + "=" + DownloadStatus.LOADED.save() + " AND " 
                    + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS + "." + MyDatabase.Download.USER_ID
                    + "=" + authorTableName + "." + BaseColumns._ID;
        }
        if (columns.contains(MyDatabase.Download.IMAGE_FILE_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + MyDatabase.Download._ID + ", "
                    + MyDatabase.Download.MSG_ID + ", "
                    + MyDatabase.Download.CONTENT_TYPE + ", "
                    + (columns.contains(MyDatabase.Download.IMAGE_URL) ? MyDatabase.Download.URI + ", " : "")
                    + MyDatabase.Download.FILE_NAME
                    + " FROM " + MyDatabase.Download.TABLE_NAME + ") AS " + ProjectionMap.ATTACHMENT_IMAGE_TABLE_ALIAS 
                    +  " ON "
                    + ProjectionMap.ATTACHMENT_IMAGE_TABLE_ALIAS + "." + Download.CONTENT_TYPE 
                    + "=" + MyContentType.IMAGE.save() + " AND " 
                    + ProjectionMap.ATTACHMENT_IMAGE_TABLE_ALIAS + "." + MyDatabase.Download.MSG_ID 
                    + "=" + ProjectionMap.MSG_TABLE_ALIAS + "." + BaseColumns._ID;
        }
        if (columns.contains(MyDatabase.User.SENDER_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT " + BaseColumns._ID + ", "
                    + TimelineSql.userNameField() + " AS " + MyDatabase.User.SENDER_NAME
                    + " FROM " + User.TABLE_NAME + ") AS sender ON "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.SENDER_ID + "=sender."
                    + BaseColumns._ID;
        }
        if (columns.contains(MyDatabase.User.IN_REPLY_TO_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT " + BaseColumns._ID + ", "
                    + TimelineSql.userNameField() + " AS " + MyDatabase.User.IN_REPLY_TO_NAME
                    + " FROM " + User.TABLE_NAME + ") AS prevAuthor ON "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.IN_REPLY_TO_USER_ID
                    + "=prevAuthor." + BaseColumns._ID;
        }
        if (columns.contains(MyDatabase.User.RECIPIENT_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT " + BaseColumns._ID + ", "
                    + TimelineSql.userNameField() + " AS " + MyDatabase.User.RECIPIENT_NAME
                    + " FROM " + User.TABLE_NAME + ") AS recipient ON "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.RECIPIENT_ID + "=recipient."
                    + BaseColumns._ID;
        }
        if (columns.contains(Friendship.AUTHOR_FOLLOWED)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + MyDatabase.Friendship.USER_ID + ", "
                    + MyDatabase.Friendship.FRIEND_ID + ", "
                    + MyDatabase.Friendship.FOLLOWED + " AS "
                    + Friendship.AUTHOR_FOLLOWED
                    + " FROM " + MyDatabase.Friendship.TABLE_NAME + ") AS followingAuthor ON ("
                    + "followingAuthor." + Friendship.USER_ID + "=" + MyDatabase.User.LINKED_USER_ID
                    + " AND "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.AUTHOR_ID
                    + "=followingAuthor." + MyDatabase.Friendship.FRIEND_ID
                    + ")";
        }
        if (columns.contains(MyDatabase.Friendship.SENDER_FOLLOWED)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + Friendship.USER_ID + ", "
                    + MyDatabase.Friendship.FRIEND_ID + ", "
                    + MyDatabase.Friendship.FOLLOWED + " AS "
                    + MyDatabase.Friendship.SENDER_FOLLOWED
                    + " FROM " + MyDatabase.Friendship.TABLE_NAME + ") AS followingSender ON ("
                    + "followingSender." + MyDatabase.Friendship.USER_ID + "=" + MyDatabase.User.LINKED_USER_ID
                    + " AND "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.SENDER_ID
                    + "=followingSender." + MyDatabase.Friendship.FRIEND_ID
                    + ")";
        }
        return tables;
    }

    /** 
     * Table columns to use for the messages content
     */
    public static String[] getTimelineProjection() {
        return getBaseProjection().toArray(new String[]{});
    }

    private static List<String> getBaseProjection() {
        List<String> columnNames = new ArrayList<>();
        columnNames.add(Msg._ID);
        columnNames.add(User.AUTHOR_NAME);
        columnNames.add(Msg.BODY);
        columnNames.add(Msg.IN_REPLY_TO_MSG_ID);
        columnNames.add(User.IN_REPLY_TO_NAME);
        columnNames.add(User.RECIPIENT_NAME);
        columnNames.add(MsgOfUser.FAVORITED);
        columnNames.add(Msg.SENT_DATE);
        columnNames.add(Msg.CREATED_DATE);
        columnNames.add(Msg.MSG_STATUS);
        columnNames.add(User.LINKED_USER_ID);
        if (MyPreferences.showAvatars()) {
            columnNames.add(Msg.AUTHOR_ID);
            columnNames.add(MyDatabase.Download.AVATAR_FILE_NAME);
        }
        if (MyPreferences.showAttachedImages()) {
            columnNames.add(Download.IMAGE_ID);
            columnNames.add(MyDatabase.Download.IMAGE_FILE_NAME);
        }
        if (MyPreferences.getBoolean(MyPreferences.KEY_MARK_REPLIES_IN_TIMELINE, false)
                || MyPreferences.getBoolean(
                MyPreferences.KEY_FILTER_HIDE_REPLIES_NOT_TO_ME_OR_FRIENDS, false)) {
            columnNames.add(Msg.IN_REPLY_TO_USER_ID);
        }
        if (MyPreferences.showOrigin()) {
            columnNames.add(Msg.ORIGIN_ID);
        }
        return columnNames;
    }

    public static String[] getConversationProjection() {
        List<String> columnNames = getBaseProjection();
        if (!columnNames.contains(Msg.AUTHOR_ID)) {
            columnNames.add(Msg.AUTHOR_ID);
        }
        columnNames.add(Msg.SENDER_ID);
        columnNames.add(Msg.VIA);
        columnNames.add(MsgOfUser.REBLOGGED);
        return columnNames.toArray(new String[]{});
    }

    @NonNull
    public static String userColumnNameToNameAtTimeline(Cursor cursor, String columnName, boolean showOrigin) {
        return userColumnIndexToNameAtTimeline(cursor, cursor.getColumnIndex(columnName), showOrigin);
    }

    @NonNull
    public static String userColumnIndexToNameAtTimeline(Cursor cursor, int columnIndex, boolean showOrigin) {
        String userName = "";
        if (columnIndex >= 0) {
            userName = cursor.getString(columnIndex);
            if (TextUtils.isEmpty(userName)) {
                userName = "";
            }
        }
        if (showOrigin) {
            long originId = DbUtils.getLong(cursor, Msg.ORIGIN_ID);
            if (originId != 0) {
                Origin origin = MyContextHolder.get().persistentOrigins().fromId(originId);
                userName += " / " + origin.getName();
                if (origin.getOriginType() == OriginType.GNUSOCIAL &&
                        MyPreferences.getBoolean(MyPreferences.KEY_DEBUGGING_INFO_IN_UI, false)) {
                    long authorId = DbUtils.getLong(cursor, Msg.AUTHOR_ID);
                    if (authorId != 0) {
                        userName += " id:" + MyQuery.idToOid(OidEnum.USER_OID, authorId, 0);
                    }
                }
            }
        }
        return userName;
    }

    private static String userNameField() {
        UserInTimeline userInTimeline = MyPreferences.userInTimeline();
        return MyQuery.userNameField(userInTimeline);
    }
    
}
