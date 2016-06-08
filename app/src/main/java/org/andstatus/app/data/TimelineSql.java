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
import org.andstatus.app.database.DownloadTable;
import org.andstatus.app.database.FriendshipTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.MsgOfUserTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.SharedPreferencesUtil;

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
    
        String msgTable = MsgTable.TABLE_NAME;
        String where = "";

        boolean linkedUserDefined = false;
        boolean authorNameDefined = false;
        String authorTableName = "";
        switch (tt) {
            case FOLLOWERS:
            case FRIENDS:
                String fUserIdColumnName = FriendshipTable.FRIEND_ID;
                String fUserLinkedUserIdColumnName = FriendshipTable.USER_ID;
                if (tt == TimelineType.FOLLOWERS) {
                    fUserIdColumnName = FriendshipTable.USER_ID;
                    fUserLinkedUserIdColumnName = FriendshipTable.FRIEND_ID;
                }
                msgTable = "(SELECT " + fUserIdColumnName + " AS fUserId, "
                        + fUserLinkedUserIdColumnName + " AS " + UserTable.LINKED_USER_ID
                        + " FROM " + FriendshipTable.TABLE_NAME
                        + " WHERE (" + UserTable.LINKED_USER_ID + selectedAccounts.getSql()
                        + " AND " + FriendshipTable.FOLLOWED + "=1 )"
                        + ") as fUser";
                linkedUserDefined = true;
                boolean defineAuthorName = columns.contains(UserTable.AUTHOR_NAME);
                if (defineAuthorName) {
                    authorNameDefined = true;
                    authorTableName = "u1";
                }
                String userTable = "(SELECT "
                        + BaseColumns._ID
                        + (defineAuthorName ? ", " + UserTable.USERNAME + " AS " + UserTable.AUTHOR_NAME : "")
                        + ", " + UserTable.USER_MSG_ID
                        + " FROM " + UserTable.TABLE_NAME + ")";
                msgTable += " INNER JOIN " + userTable + " as u1"
                        + " ON (fUserId=u1." + BaseColumns._ID + ")";
                /**
                 * Select only the latest message from each Friend's timeline
                 */
                msgTable  += " LEFT JOIN " + MsgTable.TABLE_NAME + " AS " + ProjectionMap.MSG_TABLE_ALIAS
                        + " ON ("
                        + ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.SENDER_ID
                        + "=fUserId"
                        + " AND " + ProjectionMap.MSG_TABLE_ALIAS + "." + BaseColumns._ID
                        + "=u1." + UserTable.USER_MSG_ID
                        + ")";
                break;
            case MESSAGES_TO_ACT:
                if (selectedAccounts.size() == 1) {
                    msgTable = "SELECT " + selectedAccounts.getList() + " AS " + UserTable.LINKED_USER_ID
                            + ", * FROM " + MsgTable.TABLE_NAME;
                    linkedUserDefined = true;
                }
                break;
            case PUBLIC:
                where = MsgTable.PUBLIC + "=1";
                break;
            case DRAFTS:
                where = MsgTable.MSG_STATUS + "=" + DownloadStatus.DRAFT.save();
                break;
            case OUTBOX:
                where = MsgTable.MSG_STATUS + "=" + DownloadStatus.SENDING.save();
                break;
            case EVERYTHING:
            default:
                break;
        }

        String tables = msgTable;
        if (!tables.contains(" AS " + ProjectionMap.MSG_TABLE_ALIAS)) {
            if (tt.isAtOrigin() && !uriParser.isCombined()) {
                MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(uriParser.getAccountUserId());
                if (ma.isValid()) {
                    if (!TextUtils.isEmpty(where)) {
                        where += " AND ";
                    }
                    where += MsgTable.ORIGIN_ID + "=" + ma.getOriginId();
                }
            }
            tables = "(SELECT * FROM (" + msgTable + ")"
                            + (TextUtils.isEmpty(where) ? "" : " WHERE (" + where + ")")
                            + ") AS " + ProjectionMap.MSG_TABLE_ALIAS;
        }

        if (columns.contains(MsgOfUserTable.FAVORITED)
                || (columns.contains(UserTable.LINKED_USER_ID) && !linkedUserDefined)
                ) {
            String tbl = "(SELECT *" 
                    + (linkedUserDefined ? "" : ", " + MsgOfUserTable.USER_ID + " AS "
                    + UserTable.LINKED_USER_ID)
                    + " FROM " +  MsgOfUserTable.TABLE_NAME + ") AS mou ON "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + BaseColumns._ID + "="
                    + "mou." + MsgOfUserTable.MSG_ID;
            switch (tt) {
                case FOLLOWERS:
                case FRIENDS:
                case MESSAGES_TO_ACT:
                    tbl += " AND mou." + MsgOfUserTable.USER_ID
                    + "=" + UserTable.LINKED_USER_ID;
                    tables += " LEFT JOIN " + tbl;
                    break;
                default:
                    tbl += " AND " + UserTable.LINKED_USER_ID + selectedAccounts.getSql();
                    if (tt.isAtOrigin()) {
                        tables += " LEFT OUTER JOIN " + tbl;
                    } else {
                        tables += " INNER JOIN " + tbl;
                    }
                    break;
            }
        }
    
        if (!authorNameDefined && columns.contains(UserTable.AUTHOR_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + BaseColumns._ID + ", " 
                    + TimelineSql.userNameField() + " AS " + UserTable.AUTHOR_NAME
                    + " FROM " + UserTable.TABLE_NAME + ") AS author ON "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.AUTHOR_ID + "=author."
                    + BaseColumns._ID;
            authorNameDefined = true;
            authorTableName = "author";
        }
        if (authorNameDefined && columns.contains(DownloadTable.AVATAR_FILE_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + DownloadTable.USER_ID + ", "
                    + DownloadTable.DOWNLOAD_STATUS + ", "
                    + DownloadTable.FILE_NAME
                    + " FROM " + DownloadTable.TABLE_NAME + ") AS " + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS
                    + " ON "
                    + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.DOWNLOAD_STATUS
                    + "=" + DownloadStatus.LOADED.save() + " AND " 
                    + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.USER_ID
                    + "=" + authorTableName + "." + BaseColumns._ID;
        }
        if (columns.contains(DownloadTable.IMAGE_FILE_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + DownloadTable._ID + ", "
                    + DownloadTable.MSG_ID + ", "
                    + DownloadTable.CONTENT_TYPE + ", "
                    + (columns.contains(DownloadTable.IMAGE_URL) ? DownloadTable.URI + ", " : "")
                    + DownloadTable.FILE_NAME
                    + " FROM " + DownloadTable.TABLE_NAME + ") AS " + ProjectionMap.ATTACHMENT_IMAGE_TABLE_ALIAS
                    +  " ON "
                    + ProjectionMap.ATTACHMENT_IMAGE_TABLE_ALIAS + "." + DownloadTable.CONTENT_TYPE
                    + "=" + MyContentType.IMAGE.save() + " AND " 
                    + ProjectionMap.ATTACHMENT_IMAGE_TABLE_ALIAS + "." + DownloadTable.MSG_ID
                    + "=" + ProjectionMap.MSG_TABLE_ALIAS + "." + BaseColumns._ID;
        }
        if (columns.contains(UserTable.SENDER_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT " + BaseColumns._ID + ", "
                    + TimelineSql.userNameField() + " AS " + UserTable.SENDER_NAME
                    + " FROM " + UserTable.TABLE_NAME + ") AS sender ON "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.SENDER_ID + "=sender."
                    + BaseColumns._ID;
        }
        if (columns.contains(UserTable.IN_REPLY_TO_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT " + BaseColumns._ID + ", "
                    + TimelineSql.userNameField() + " AS " + UserTable.IN_REPLY_TO_NAME
                    + " FROM " + UserTable.TABLE_NAME + ") AS prevAuthor ON "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.IN_REPLY_TO_USER_ID
                    + "=prevAuthor." + BaseColumns._ID;
        }
        if (columns.contains(UserTable.RECIPIENT_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT " + BaseColumns._ID + ", "
                    + TimelineSql.userNameField() + " AS " + UserTable.RECIPIENT_NAME
                    + " FROM " + UserTable.TABLE_NAME + ") AS recipient ON "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.RECIPIENT_ID + "=recipient."
                    + BaseColumns._ID;
        }
        if (columns.contains(FriendshipTable.AUTHOR_FOLLOWED)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + FriendshipTable.USER_ID + ", "
                    + FriendshipTable.FRIEND_ID + ", "
                    + FriendshipTable.FOLLOWED + " AS "
                    + FriendshipTable.AUTHOR_FOLLOWED
                    + " FROM " + FriendshipTable.TABLE_NAME + ") AS followingAuthor ON ("
                    + "followingAuthor." + FriendshipTable.USER_ID + "=" + UserTable.LINKED_USER_ID
                    + " AND "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.AUTHOR_ID
                    + "=followingAuthor." + FriendshipTable.FRIEND_ID
                    + ")";
        }
        if (columns.contains(FriendshipTable.SENDER_FOLLOWED)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + FriendshipTable.USER_ID + ", "
                    + FriendshipTable.FRIEND_ID + ", "
                    + FriendshipTable.FOLLOWED + " AS "
                    + FriendshipTable.SENDER_FOLLOWED
                    + " FROM " + FriendshipTable.TABLE_NAME + ") AS followingSender ON ("
                    + "followingSender." + FriendshipTable.USER_ID + "=" + UserTable.LINKED_USER_ID
                    + " AND "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.SENDER_ID
                    + "=followingSender." + FriendshipTable.FRIEND_ID
                    + ")";
        }
        return tables;
    }

    /** 
     * Table columns to use for the messages content
     */
    public static String[] getTimelineProjection() {
        List<String> columnNames = getBaseProjection();
        if (!columnNames.contains(MsgTable.AUTHOR_ID)) {
            columnNames.add(MsgTable.AUTHOR_ID);
        }
        columnNames.add(MsgTable.SENDER_ID);
        columnNames.add(UserTable.SENDER_NAME);
        columnNames.add(MsgTable.VIA);
        columnNames.add(MsgOfUserTable.REBLOGGED);
        return columnNames.toArray(new String[]{});
    }

    private static List<String> getBaseProjection() {
        List<String> columnNames = new ArrayList<>();
        columnNames.add(MsgTable._ID);
        columnNames.add(UserTable.AUTHOR_NAME);
        columnNames.add(MsgTable.BODY);
        columnNames.add(MsgTable.IN_REPLY_TO_MSG_ID);
        columnNames.add(UserTable.IN_REPLY_TO_NAME);
        columnNames.add(UserTable.RECIPIENT_NAME);
        columnNames.add(MsgOfUserTable.FAVORITED);
        columnNames.add(MsgTable.SENT_DATE);
        columnNames.add(MsgTable.CREATED_DATE);
        columnNames.add(MsgTable.MSG_STATUS);
        columnNames.add(UserTable.LINKED_USER_ID);
        if (MyPreferences.getShowAvatars()) {
            columnNames.add(MsgTable.AUTHOR_ID);
            columnNames.add(DownloadTable.AVATAR_FILE_NAME);
        }
        if (MyPreferences.getDownloadAndDisplayAttachedImages()) {
            columnNames.add(DownloadTable.IMAGE_ID);
            columnNames.add(DownloadTable.IMAGE_FILE_NAME);
        }
        if (SharedPreferencesUtil.getBoolean(MyPreferences.KEY_MARK_REPLIES_IN_TIMELINE, false)
                || SharedPreferencesUtil.getBoolean(
                MyPreferences.KEY_FILTER_HIDE_REPLIES_NOT_TO_ME_OR_FRIENDS, false)) {
            columnNames.add(MsgTable.IN_REPLY_TO_USER_ID);
        }
        if (MyPreferences.getShowOrigin()) {
            columnNames.add(MsgTable.ORIGIN_ID);
        }
        return columnNames;
    }

    public static String[] getConversationProjection() {
        List<String> columnNames = getBaseProjection();
        if (!columnNames.contains(MsgTable.AUTHOR_ID)) {
            columnNames.add(MsgTable.AUTHOR_ID);
        }
        columnNames.add(MsgTable.SENDER_ID);
        columnNames.add(MsgTable.VIA);
        columnNames.add(MsgOfUserTable.REBLOGGED);
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
            long originId = DbUtils.getLong(cursor, MsgTable.ORIGIN_ID);
            if (originId != 0) {
                Origin origin = MyContextHolder.get().persistentOrigins().fromId(originId);
                userName += " / " + origin.getName();
                if (origin.getOriginType() == OriginType.GNUSOCIAL &&
                        MyPreferences.getShowDebuggingInfoInUi()) {
                    long authorId = DbUtils.getLong(cursor, MsgTable.AUTHOR_ID);
                    if (authorId != 0) {
                        userName += " id:" + MyQuery.idToOid(OidEnum.USER_OID, authorId, 0);
                    }
                }
            }
        }
        return userName;
    }

    private static String userNameField() {
        UserInTimeline userInTimeline = MyPreferences.getUserInTimeline();
        return MyQuery.userNameField(userInTimeline);
    }
    
}
