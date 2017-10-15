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

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.UserInTimeline;
import org.andstatus.app.database.ActivityTable;
import org.andstatus.app.database.DownloadTable;
import org.andstatus.app.database.FriendshipTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
        Timeline timeline = Timeline.fromParsedUri(MyContextHolder.get(), ParsedUri.fromUri(uri), "");
        SelectedUserIds selectedAccounts = new SelectedUserIds(timeline);
    
        Collection<String> columns = new java.util.HashSet<>(Arrays.asList(projection));

        final String msgTablePlaceholder = "$msgTable";
        String tables = msgTablePlaceholder;
        SqlWhere activityWhere = new SqlWhere();
        SqlWhere msgWhere = new SqlWhere();

        String linkedUserField = ActivityTable.ACCOUNT_ID;
        boolean authorNameDefined = false;
        String authorTableName = "";
        switch (timeline.getTimelineType()) {
            case FOLLOWERS:
            case MY_FOLLOWERS:
            case FRIENDS:
            case MY_FRIENDS:
                String fUserIdColumnName = FriendshipTable.FRIEND_ID;
                String fUserLinkedUserIdColumnName = FriendshipTable.USER_ID;
                if (timeline.getTimelineType() == TimelineType.FOLLOWERS ||
                        timeline.getTimelineType() == TimelineType.MY_FOLLOWERS) {
                    fUserIdColumnName = FriendshipTable.USER_ID;
                    fUserLinkedUserIdColumnName = FriendshipTable.FRIEND_ID;
                }
                tables = "(SELECT " + fUserIdColumnName + " AS fUserId, "
                        + fUserLinkedUserIdColumnName + " AS " + UserTable.LINKED_USER_ID
                        + " FROM " + FriendshipTable.TABLE_NAME
                        + " WHERE (" + UserTable.LINKED_USER_ID + selectedAccounts.getSql()
                        + " AND " + FriendshipTable.FOLLOWED + "=1 )"
                        + ") as fUser";
                boolean defineAuthorName = columns.contains(UserTable.AUTHOR_NAME);
                if (defineAuthorName) {
                    authorNameDefined = true;
                    authorTableName = "u1";
                }
                String userTable = "(SELECT "
                        + BaseColumns._ID
                        + (defineAuthorName ? ", " + TimelineSql.userNameField() + " AS " + UserTable.AUTHOR_NAME : "")
                        + ", " + UserTable.USER_ACTIVITY_ID
                        + " FROM " + UserTable.TABLE_NAME + ")";
                tables += " INNER JOIN " + userTable + " as u1"
                        + " ON (fUserId=u1." + BaseColumns._ID + ")";
                // Select only the latest message from each Friend's timeline
                tables  += " LEFT JOIN (" + msgTablePlaceholder + ") ON ("
                        + ProjectionMap.MSG_TABLE_ALIAS + "." + BaseColumns._ID + "=u1." + UserTable.USER_ACTIVITY_ID + ")";
                break;
            case MESSAGES_TO_ACT:
                if (selectedAccounts.size() == 1) {
                    linkedUserField = selectedAccounts.getList();  // Constant ID as a field
                }
                break;
            case HOME:
                msgWhere.append(MsgTable.SUBSCRIBED + "=" + TriState.TRUE.id);
                activityWhere.append(ActivityTable.ACCOUNT_ID + " " + selectedAccounts.getSql());
                break;
            case DIRECT:
                msgWhere.append(MsgTable.PRIVATE + "=" + TriState.TRUE.id);
                break;
            case FAVORITES:
                msgWhere.append(MsgTable.FAVORITED + "=" + TriState.TRUE.id);
                break;
            case MENTIONS:
                msgWhere.append(MsgTable.MENTIONED + "=" + TriState.TRUE.id);
                /*
                 * We already figured this out and set {@link MyDatabase.MsgOfUser.MENTIONED}:
                 *  add MyDatabase.Msg.BODY + " LIKE ?" ...
                 */
                break;
            case DRAFTS:
                msgWhere.append(MsgTable.MSG_STATUS + "=" + DownloadStatus.DRAFT.save());
                break;
            case OUTBOX:
                msgWhere.append(MsgTable.MSG_STATUS + "=" + DownloadStatus.SENDING.save());
                break;
            case USER:
            case SENT:
                SelectedUserIds userIds = new SelectedUserIds(timeline);
                // All actions by this User(s)
                activityWhere.append(ActivityTable.ACTOR_ID + " " + userIds.getSql());
                break;
            default:
                break;
        }

        if (tables.contains(msgTablePlaceholder)) {
            if (timeline.getTimelineType().isAtOrigin() && !timeline.isCombined()) {
                msgWhere.append(MsgTable.ORIGIN_ID + "=" + timeline.getOrigin().getId());
            }
            String activityTable = "(SELECT "
                    + ActivityTable._ID + ", "
                    + ActivityTable.INS_DATE + ", "
                    + (tables.contains(UserTable.LINKED_USER_ID) ? ""
                        : linkedUserField + " AS " + UserTable.LINKED_USER_ID + ", ")
                    + ActivityTable.ACTIVITY_TYPE + ", "
                    + ActivityTable.ACTOR_ID + ", "
                    + ActivityTable.MSG_ID + ", "
                    + ActivityTable.USER_ID + ", "
                    + ActivityTable.OBJ_ACTIVITY_ID + ", "
                    + ActivityTable.UPDATED_DATE
                    + " FROM " + ActivityTable.TABLE_NAME + activityWhere.getWhere()
                    + ") AS " + ProjectionMap.ACTIVITY_TABLE_ALIAS;
            String msgTable = activityTable
                    + (timeline.getTimelineType().showsActivities() ? " LEFT" : " INNER") + " JOIN "
                    + "(SELECT * FROM (" + MsgTable.TABLE_NAME + ")" + msgWhere.getWhere() + ")"
                        + " AS " + ProjectionMap.MSG_TABLE_ALIAS
                    + " ON (" + ProjectionMap.MSG_TABLE_ALIAS + "." + BaseColumns._ID + "="
                        + ProjectionMap.ACTIVITY_TABLE_ALIAS + "." + ActivityTable.MSG_ID + ")";
            tables = tables.replace(msgTablePlaceholder, msgTable);
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
                    + "=" + ProjectionMap.ACTIVITY_TABLE_ALIAS + "." + ActivityTable.MSG_ID;
        }
        if (columns.contains(UserTable.SENDER_NAME)) {  // TODO: Rename SENDER to ACTOR
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT " + BaseColumns._ID + ", "
                    + TimelineSql.userNameField() + " AS " + UserTable.SENDER_NAME
                    + " FROM " + UserTable.TABLE_NAME + ") AS sender ON "
                    + ProjectionMap.ACTIVITY_TABLE_ALIAS + "." + ActivityTable.ACTOR_ID + "=sender."
                    + BaseColumns._ID;

            if (columns.contains(DownloadTable.ACTOR_AVATAR_FILE_NAME)) {
                tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                        + DownloadTable.USER_ID + ", "
                        + DownloadTable.DOWNLOAD_STATUS + ", "
                        + DownloadTable.FILE_NAME
                        + " FROM " + DownloadTable.TABLE_NAME + ") AS " + ProjectionMap.ACTOR_AVATAR_IMAGE_TABLE_ALIAS
                        + " ON "
                        + ProjectionMap.ACTOR_AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.DOWNLOAD_STATUS
                        + "=" + DownloadStatus.LOADED.save() + " AND "
                        + ProjectionMap.ACTOR_AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.USER_ID
                        + "=" + ActivityTable.ACTOR_ID;
            }
        }
        if (columns.contains(UserTable.IN_REPLY_TO_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT " + BaseColumns._ID + ", "
                    + TimelineSql.userNameField() + " AS " + UserTable.IN_REPLY_TO_NAME
                    + " FROM " + UserTable.TABLE_NAME + ") AS prevAuthor ON "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MsgTable.IN_REPLY_TO_USER_ID
                    + "=prevAuthor." + BaseColumns._ID;
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
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + ActivityTable.ACTOR_ID
                    + "=followingSender." + FriendshipTable.FRIEND_ID
                    + ")";
        }
        return tables;
    }

    /**
     * Table columns to use for activities
     */
    public static Set<String> getActivityProjection() {
        Set<String> columnNames = getTimelineProjection();
        columnNames.add(ActivityTable.INS_DATE);
        columnNames.add(ActivityTable.ACTIVITY_TYPE);
        columnNames.add(ActivityTable.ACTOR_ID);
        if (MyPreferences.getShowAvatars()) {
            columnNames.add(DownloadTable.ACTOR_AVATAR_FILE_NAME);
        }
        columnNames.add(ActivityTable.MSG_ID);
        columnNames.add(ActivityTable.USER_ID);
        return columnNames;
    }

    /** 
     * Table columns to use for messages content
     */
    public static Set<String> getTimelineProjection() {
        Set<String> columnNames = getBaseProjection();
        columnNames.add(ActivityTable.ACTIVITY_ID);
        if (!columnNames.contains(MsgTable.AUTHOR_ID)) {
            columnNames.add(MsgTable.AUTHOR_ID);
        }
        columnNames.add(ActivityTable.ACTOR_ID);
        columnNames.add(UserTable.SENDER_NAME);
        columnNames.add(MsgTable.VIA);
        columnNames.add(MsgTable.REBLOGGED);
        return columnNames;
    }

    private static Set<String> getBaseProjection() {
        Set<String> columnNames = new HashSet<>();
        columnNames.add(ActivityTable.MSG_ID);
        columnNames.add(MsgTable.ORIGIN_ID);
        columnNames.add(UserTable.AUTHOR_NAME);
        columnNames.add(MsgTable.BODY);
        columnNames.add(MsgTable.IN_REPLY_TO_MSG_ID);
        columnNames.add(UserTable.IN_REPLY_TO_NAME);
        columnNames.add(MsgTable.FAVORITED);
        columnNames.add(ActivityTable.INS_DATE); // ??
        columnNames.add(MsgTable.UPDATED_DATE);
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
        if (SharedPreferencesUtil.getBoolean(MyPreferences.KEY_MARK_REPLIES_IN_TIMELINE, true)
                || SharedPreferencesUtil.getBoolean(
                MyPreferences.KEY_FILTER_HIDE_REPLIES_NOT_TO_ME_OR_FRIENDS, false)) {
            columnNames.add(MsgTable.IN_REPLY_TO_USER_ID);
        }
        return columnNames;
    }

    public static String[] getConversationProjection() {
        Set<String> columnNames = getBaseProjection();
        columnNames.add(MsgTable.AUTHOR_ID);
        columnNames.add(ActivityTable.ACTOR_ID);
        columnNames.add(MsgTable.VIA);
        columnNames.add(MsgTable.REBLOGGED);
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
                        MyPreferences.isShowDebuggingInfoInUi()) {
                    long authorId = DbUtils.getLong(cursor, MsgTable.AUTHOR_ID);
                    if (authorId != 0) {
                        userName += " id:" + MyQuery.idToOid(OidEnum.USER_OID, authorId, 0);
                    }
                }
            }
        }
        return userName;
    }

    public static String userNameField() {
        UserInTimeline userInTimeline = MyPreferences.getUserInTimeline();
        return MyQuery.userNameField(userInTimeline);
    }
    
}
