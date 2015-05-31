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
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.UserInTimeline;
import org.andstatus.app.data.MyDatabase.Download;
import org.andstatus.app.data.MyDatabase.FollowingUser;
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
     * @param projection
     * @return String for {@link SQLiteQueryBuilder#setTables(String)}
     */
    static String tablesForTimeline(Uri uri, String[] projection) {
        ParsedUri uriParser = ParsedUri.fromUri(uri);
        TimelineTypeEnum tt = uriParser.getTimelineType();
        AccountUserIds accountUserIds = new AccountUserIds(uriParser.isCombined(), uriParser.getAccountUserId());
    
        Collection<String> columns = new java.util.HashSet<String>(Arrays.asList(projection));
    
        String tables = Msg.TABLE_NAME + " AS " + ProjectionMap.MSG_TABLE_ALIAS;
        boolean linkedUserDefined = false;
        boolean authorNameDefined = false;
        String authorTableName = "";
        switch (tt) {
            case FOLLOWING_USER:
                tables = "(SELECT " + FollowingUser.FOLLOWING_USER_ID + ", "
                        + MyDatabase.FollowingUser.USER_FOLLOWED + ", "
                        + FollowingUser.USER_ID + " AS " + User.LINKED_USER_ID
                        + " FROM " + FollowingUser.TABLE_NAME
                        + " WHERE (" + MyDatabase.User.LINKED_USER_ID + accountUserIds.getSqlUserIds()
                        + " AND " + MyDatabase.FollowingUser.USER_FOLLOWED + "=1 )"
                        + ") as fuser";
                String userTable = User.TABLE_NAME;
                if (!authorNameDefined && columns.contains(MyDatabase.User.AUTHOR_NAME)) {
                    userTable = "(SELECT "
                            + BaseColumns._ID + ", " 
                            + MyDatabase.User.USERNAME + " AS " + MyDatabase.User.AUTHOR_NAME
                            + ", " + MyDatabase.User.USER_MSG_ID
                            + " FROM " + User.TABLE_NAME + ")";
                    authorNameDefined = true;
                    authorTableName = "u1";
                }
                tables += " INNER JOIN " + userTable + " as u1"
                        + " ON (" + FollowingUser.FOLLOWING_USER_ID + "=u1." + BaseColumns._ID + ")";
                linkedUserDefined = true;
                /**
                 * Select only the latest message from each following User's
                 * timeline
                 */
                tables  += " LEFT JOIN " + Msg.TABLE_NAME + " AS " + ProjectionMap.MSG_TABLE_ALIAS
                        + " ON (" 
                        + ProjectionMap.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.SENDER_ID 
                        + "=fuser." + MyDatabase.FollowingUser.FOLLOWING_USER_ID 
                        + " AND " + ProjectionMap.MSG_TABLE_ALIAS + "." + BaseColumns._ID 
                        + "=u1." + MyDatabase.User.USER_MSG_ID
                        + ")";
                break;
            case MESSAGESTOACT:
                if (accountUserIds.size() == 1) {
                    tables = "(SELECT " + accountUserIds.getAccountUserId() + " AS " + MyDatabase.User.LINKED_USER_ID
                            + ", * FROM " + Msg.TABLE_NAME + ") AS " + ProjectionMap.MSG_TABLE_ALIAS;
                    linkedUserDefined = true;
                }
                break;
            case PUBLIC:
                String where = Msg.PUBLIC + "=1";
                if (!uriParser.isCombined()) {
                    MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(uriParser.getAccountUserId());
                    if (ma.isValid()) {
                        where += " AND " + Msg.ORIGIN_ID + "=" + ma.getOriginId();
                    }
                }
                tables = "(SELECT * FROM " + Msg.TABLE_NAME + " WHERE (" + where + ")) AS " + ProjectionMap.MSG_TABLE_ALIAS;
                break;
            case EVERYTHING:
                where = "";
                if (!uriParser.isCombined()) {
                    MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(uriParser.getAccountUserId());
                    if (ma.isValid()) {
                        where = Msg.ORIGIN_ID + "=" + ma.getOriginId();
                    }
                }
                tables = "(SELECT *"
                        + " FROM " + Msg.TABLE_NAME
                        + (uriParser.isCombined() ? "" : " WHERE (" + where + ")")
                        + ") AS " + ProjectionMap.MSG_TABLE_ALIAS;
                break;
            default:
                break;
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
                case MESSAGESTOACT:
                    tbl += " AND mou." + MyDatabase.MsgOfUser.USER_ID 
                    + "=" + MyDatabase.User.LINKED_USER_ID;
                    tables += " LEFT JOIN " + tbl;
                    break;
                default:
                    tbl += " AND " + MyDatabase.User.LINKED_USER_ID + accountUserIds.getSqlUserIds();
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
                    + "av." + Download.DOWNLOAD_STATUS 
                    + "=" + DownloadStatus.LOADED.save() + " AND " 
                    + "av." + MyDatabase.Download.USER_ID 
                    + "=" + authorTableName + "." + BaseColumns._ID;
        }
        if (columns.contains(MyDatabase.Download.IMAGE_FILE_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + MyDatabase.Download._ID + ", "
                    + MyDatabase.Download.MSG_ID + ", "
                    + MyDatabase.Download.CONTENT_TYPE + ", "
                    + (columns.contains(MyDatabase.Download.IMAGE_URL) ? MyDatabase.Download.URL + ", " : "")
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
                    + " FROM " + User.TABLE_NAME + ") AS prevauthor ON "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.IN_REPLY_TO_USER_ID
                    + "=prevauthor." + BaseColumns._ID;
        }
        if (columns.contains(MyDatabase.User.RECIPIENT_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT " + BaseColumns._ID + ", "
                    + TimelineSql.userNameField() + " AS " + MyDatabase.User.RECIPIENT_NAME
                    + " FROM " + User.TABLE_NAME + ") AS recipient ON "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.RECIPIENT_ID + "=recipient."
                    + BaseColumns._ID;
        }
        if (columns.contains(MyDatabase.FollowingUser.AUTHOR_FOLLOWED)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + MyDatabase.FollowingUser.USER_ID + ", "
                    + MyDatabase.FollowingUser.FOLLOWING_USER_ID + ", "
                    + MyDatabase.FollowingUser.USER_FOLLOWED + " AS "
                    + MyDatabase.FollowingUser.AUTHOR_FOLLOWED
                    + " FROM " + FollowingUser.TABLE_NAME + ") AS followingauthor ON ("
                    + "followingauthor." + MyDatabase.FollowingUser.USER_ID + "=" + MyDatabase.User.LINKED_USER_ID
                    + " AND "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.AUTHOR_ID
                    + "=followingauthor." + MyDatabase.FollowingUser.FOLLOWING_USER_ID
                    + ")";
        }
        if (columns.contains(MyDatabase.FollowingUser.SENDER_FOLLOWED)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + MyDatabase.FollowingUser.USER_ID + ", "
                    + MyDatabase.FollowingUser.FOLLOWING_USER_ID + ", "
                    + MyDatabase.FollowingUser.USER_FOLLOWED + " AS "
                    + MyDatabase.FollowingUser.SENDER_FOLLOWED
                    + " FROM " + FollowingUser.TABLE_NAME + ") AS followingsender ON ("
                    + "followingsender." + MyDatabase.FollowingUser.USER_ID + "=" + MyDatabase.User.LINKED_USER_ID
                    + " AND "
                    + ProjectionMap.MSG_TABLE_ALIAS + "." + MyDatabase.Msg.SENDER_ID
                    + "=followingsender." + MyDatabase.FollowingUser.FOLLOWING_USER_ID
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
        List<String> columnNames = new ArrayList<String>();
        columnNames.add(Msg._ID);
        columnNames.add(User.AUTHOR_NAME);
        columnNames.add(Msg.BODY);
        columnNames.add(Msg.IN_REPLY_TO_MSG_ID);
        columnNames.add(User.IN_REPLY_TO_NAME);
        columnNames.add(User.RECIPIENT_NAME);
        columnNames.add(MsgOfUser.FAVORITED);
        columnNames.add(Msg.SENT_DATE);
        columnNames.add(Msg.CREATED_DATE);
        columnNames.add(User.LINKED_USER_ID);
        if (MyPreferences.showAvatars()) {
            columnNames.add(Msg.AUTHOR_ID);
            columnNames.add(MyDatabase.Download.AVATAR_FILE_NAME);
        }
        if (MyPreferences.showAttachedImages()) {
            columnNames.add(Download.IMAGE_ID);
            columnNames.add(MyDatabase.Download.IMAGE_FILE_NAME);
        }
        if (MyPreferences.getBoolean(
                MyPreferences.KEY_MARK_REPLIES_IN_TIMELINE, false)) {
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

    public static String userColumnNameToNameAtTimeline(Cursor cursor, String columnName, boolean showOrigin) {
        return userColumnIndexToNameAtTimeline(cursor, cursor.getColumnIndex(columnName), showOrigin);
    }
    
    public static String userColumnIndexToNameAtTimeline(Cursor cursor, int columnIndex, boolean showOrigin) {
        String userName = "";
        if (columnIndex >= 0) {
            userName = cursor.getString(columnIndex);
            if (TextUtils.isEmpty(userName)) {
                userName = "";
            }
        }
        if (showOrigin) {
            int originIdColumn = cursor.getColumnIndex(Msg.ORIGIN_ID);
            if (originIdColumn >= 0) {
                Origin origin = MyContextHolder.get().persistentOrigins().fromId(cursor.getLong(originIdColumn));
                if (origin != null) {
                    userName += " / " + origin.getName();
                    if (origin.getOriginType() == OriginType.GNUSOCIAL && MyPreferences.getBoolean(MyPreferences.KEY_DEBUGGING_INFO_IN_UI, false)) {
                        int authorIdColumn = cursor.getColumnIndex(Msg.AUTHOR_ID);
                        if (authorIdColumn >= 0) {
                            userName += " id:" + MyQuery.idToOid(OidEnum.USER_OID, cursor.getLong(authorIdColumn), 0);
                        }
                    }
                }
            }
        }
        return userName;
    }

    public static String userNameField() {
        UserInTimeline userInTimeline = MyPreferences.userInTimeline();
        return MyQuery.userNameField(userInTimeline);
    }
    
}
