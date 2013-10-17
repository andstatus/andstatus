/* 
 * Copyright (C) 2012-2013 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2008 Torgny Bjers
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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

import org.andstatus.app.MyContextHolder;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase.FollowingUser;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

/**
 * Database provider for the MyDatabase database.
 * 
 * The code of this application accesses this class through {@link android.content.ContentResolver}.
 * ContentResolver in it's turn accesses this class.
 * 
 */
public class MyProvider extends ContentProvider {

    private static final String TAG = MyProvider.class.getSimpleName();

    /**
     * Projection map used by SQLiteQueryBuilder
     * Projection map for the {@link MyDatabase.Msg} table
     * @see android.database.sqlite.SQLiteQueryBuilder#setProjectionMap
     */
    private static HashMap<String, String> msgProjectionMap;
    /**
     * Projection map for the {@link MyDatabase.User} table
     */
    private static HashMap<String, String> userProjectionMap;
    
    /**
     * "Authority", represented by this ContentProvider subclass 
     *   and declared in the application's manifest.
     *   
     * As Android documentation states:
     * "The authority therefore must be unique. 
     *  Typically, as in this example, it's the fully qualified name of a ContentProvider subclass.
     *  The path part of a URI may be used by a content provider to identify particular data subsets,
     *  but those paths are not declared in the manifest."
     * (see <a href="http://developer.android.com/guide/topics/manifest/provider-element.html">&lt;provider&gt;</a>)
     */
    public static final String AUTHORITY = MyProvider.class.getName();
    /**
     * Used for URIs referring to timelines 
     */
    public static final String TIMELINE_PATH = "timeline";
    public static final Uri TIMELINE_URI = Uri.parse("content://" + AUTHORITY + "/" + TIMELINE_PATH);
    /**
     * We add this path segment after the {@link #TIMELINE_URI} to form search URI 
     */
    public static final String SEARCH_SEGMENT = "search";

    private static final UriMatcher sUriMatcher;
    /**
     * Matched codes, returned by {@link UriMatcher#match(Uri)}
     * This first member is for a Timeline of selected User (Account) (or all timelines...) and it corresponds to the {@link #TIMELINE_URI}
     */
    private static final int TIMELINE = 1;
    /**
     * Operations on {@link MyDatabase.Msg} table itself
     */
    private static final int MSG = 7;
    private static final int MSG_COUNT = 2;
    private static final int TIMELINE_SEARCH = 3;
    /**
     * The Timeline URI contains Message id 
     */
    private static final int TIMELINE_MSG_ID = 4;
    /**
     * Matched code for the list of Users
     */
    private static final int USERS = 5;
    /**
     * Matched code for the User
     */
    private static final int USER_ID = 6;
    
    /**
     * @see android.content.ContentProvider#onCreate()
     */
    @Override
    public boolean onCreate() {
        MyContextHolder.storeContextIfNotPresent(getContext(), this);
        return MyContextHolder.get().isReady();
    }

    /**
     * Get MIME type of the content, used for the supplied Uri
     * For discussion how this may be used see:
     * <a href="http://stackoverflow.com/questions/5351669/why-use-contentprovider-gettype-to-get-mime-type">Why use ContentProvider.getType() to get MIME type</a>
     * 
     * @see android.content.ContentProvider#getType(android.net.Uri)
     */
    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case MSG:
            case TIMELINE:
            case TIMELINE_SEARCH:
            case MSG_COUNT:
                return Msg.CONTENT_TYPE;

            case TIMELINE_MSG_ID:
                return Msg.CONTENT_ITEM_TYPE;

            case USERS:
                return User.CONTENT_TYPE;

            case USER_ID:
                return User.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /**
     * Delete a record(s) from the database.
     * 
     * @see android.content.ContentProvider#delete(android.net.Uri,
     *      java.lang.String, java.lang.String[])
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();
        String sqlDesc = "";
        int count = 0;
        switch (sUriMatcher.match(uri)) {
            case MSG:
                db.beginTransaction();
                try {
                    // Delete all related records from MyDatabase.MsgOfUser for these messages
                    String selectionG = " EXISTS ("
                            + "SELECT * FROM " + MyDatabase.MSG_TABLE_NAME + " WHERE ("
                            + MyDatabase.MSG_TABLE_NAME + "." + BaseColumns._ID + "=" + MyDatabase.MSGOFUSER_TABLE_NAME + "." + MyDatabase.MsgOfUser.MSG_ID
                            + ") AND ("
                            + selection
                            + "))";
                    sqlDesc = selectionG + (selectionArgs != null ? "; args=" + selectionArgs.toString() : "");
                    count = db.delete(MyDatabase.MSGOFUSER_TABLE_NAME, selectionG, selectionArgs);
                    // Now delete messages themselves
                    sqlDesc = selection + (selectionArgs != null ? "; args=" + selectionArgs.toString() : "");
                    count = db.delete(MyDatabase.MSG_TABLE_NAME, selection, selectionArgs);
                    /*
                    if (count > 0) {
                        // Now delete all related records from MyDatabase.MsgOfUser which don't have their messages

                        selectionG = "NOT EXISTS ("
                                + "SELECT * FROM " + MyDatabase.MSG_TABLE_NAME + " WHERE "
                                + MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg.MSG_ID + "=" + MyDatabase.MSGOFUSER_TABLE_NAME + "." + MyDatabase.MsgOfUser.MSG_ID
                                + ")";
                        db.delete(MyDatabase.MSG_TABLE_NAME, selectionG, null);
                    }
                    */
                    db.setTransactionSuccessful();
                    getContext().getContentResolver().notifyChange(MyProvider.TIMELINE_URI, null);
                } catch(Exception e) {
                    MyLog.d(TAG, e.toString() + "; SQL='" + sqlDesc + "'");
                } finally {
                    db.endTransaction();
                }
                break;

            case USERS:
                count = db.delete(MyDatabase.USER_TABLE_NAME, selection, selectionArgs);
                break;

            case USER_ID:
                // TODO: Delete related records also... 
                long userId = uriToUserId(uri);
                count = db.delete(MyDatabase.USER_TABLE_NAME, BaseColumns._ID + "=" + userId
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        return count;
    }

    /**
     * Insert a new record into the database.
     * 
     * @see android.content.ContentProvider#insert(android.net.Uri,
     *      android.content.ContentValues)
     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        ContentValues values;
        ContentValues msgOfUserValues = null;
        FollowingUserValues followingUserValues = null;
        long accountUserId = 0;
        
        long rowId;
        Uri newUri = null;
        try {
            // 2010-07-21 yvolk: "now" is calculated exactly like it is in other
            // parts of the code
            Long now = System.currentTimeMillis();
            SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();

            String table;
            String nullColumnHack;
            Uri contentUri;

            if (initialValues != null) {
                values = new ContentValues(initialValues);
            } else {
                values = new ContentValues();
            }

            switch (sUriMatcher.match(uri)) {
                case TIMELINE:
                    accountUserId = uriToAccountUserId(uri);
                    MyDatabase.TimelineTypeEnum timelineType = uriToTimelineType(uri);
                    boolean isCombined = uriToIsCombined(uri);
                    
                    table = MyDatabase.MSG_TABLE_NAME;
                    nullColumnHack = Msg.BODY;
                    contentUri = TIMELINE_URI;
                    /**
                     * Add default values for missed required fields
                     */
                    if (!values.containsKey(Msg.AUTHOR_ID))
                        if (values.containsKey(Msg.SENDER_ID)) {
                            values.put(Msg.AUTHOR_ID, values.get(Msg.SENDER_ID).toString());
                        }
                    if (!values.containsKey(Msg.BODY))
                        values.put(Msg.BODY, "");
                    if (!values.containsKey(Msg.VIA))
                        values.put(Msg.VIA, "");
                    values.put(Msg.INS_DATE, now);
                    
                    msgOfUserValues = prepareMsgOfUserValues(accountUserId, timelineType, isCombined, values);
                    break;

                case USER_ID:
                    accountUserId = uriToAccountUserId(uri);
                    table = MyDatabase.USER_TABLE_NAME;
                    nullColumnHack = User.USERNAME;
                    contentUri = User.CONTENT_URI;
                    values.put(User.INS_DATE, now);

                    followingUserValues = FollowingUserValues.valueOf(accountUserId, 0, values);
                    break;

                default:
                    throw new IllegalArgumentException("Unknown URI " + uri);
            }

            rowId = db.insert(table, nullColumnHack, values);
            if (rowId == -1) {
                throw new SQLException("Failed to insert row into " + uri);
            }
            
            if (msgOfUserValues != null) {
                // We need to insert the row:
                msgOfUserValues.put(MsgOfUser.MSG_ID, rowId);
                long msgOfUserRowId = db.insert(MyDatabase.MSGOFUSER_TABLE_NAME, MsgOfUser.MSG_ID, msgOfUserValues);
                if (msgOfUserRowId == -1) {
                    throw new SQLException("Failed to insert row into " + MyDatabase.MSGOFUSER_TABLE_NAME);
                }
            }
            if (followingUserValues != null) {
                followingUserValues.followingUserId =  rowId;
                followingUserValues.update(db);
            }

            if (contentUri.compareTo(TIMELINE_URI) == 0) {
                // The resulted Uri has several parameters...
                newUri = MyProvider.getTimelineMsgUri(accountUserId, TimelineTypeEnum.HOME , true, rowId);
            } else {
                newUri = MyProvider.getUserUri(accountUserId, rowId);
            }
        } catch (Exception e) {
          e.printStackTrace();
          MyLog.e(this, "Insert:" + e.getMessage());
        }
        return newUri;
    }

    /**
     * Move all keys that belong to MsgOfUser table from values to the newly created ContentValues. 
     * Returns null if we don't need MsgOfUser for this Msg
     * @param values
     * @return
     */
    private ContentValues prepareMsgOfUserValues(long userId, MyDatabase.TimelineTypeEnum timelineType, boolean isCombined, ContentValues values) {
        ContentValues msgOfUserValues = null;
        if (userId != 0) {
            // Add MsgOfUser link to Current User MyAccount
            msgOfUserValues = new ContentValues();
            if (values.containsKey(BaseColumns._ID) ) {
                msgOfUserValues.put(MsgOfUser.MSG_ID, values.getAsString(BaseColumns._ID));
            }
            msgOfUserValues.put(MsgOfUser.USER_ID, userId);
            moveBooleanKey(MsgOfUser.SUBSCRIBED, values, msgOfUserValues);
            moveBooleanKey(MsgOfUser.FAVORITED, values, msgOfUserValues);
            moveBooleanKey(MsgOfUser.REBLOGGED, values, msgOfUserValues);
            // The value is String!
            moveStringKey(MsgOfUser.REBLOG_OID, values, msgOfUserValues);
            moveBooleanKey(MsgOfUser.MENTIONED, values, msgOfUserValues);
            moveBooleanKey(MsgOfUser.REPLIED, values, msgOfUserValues);
            moveBooleanKey(MsgOfUser.DIRECTED, values, msgOfUserValues);
        }
        return msgOfUserValues;
    }
    
    /**
     * Move boolean value of the key from valuesIn to valuesOut and remove it from valuesIn
     * @param key
     * @param valuesIn
     * @param valuesOut  may be null
     * @return 1 for true, 0 for false and 2 for "not present" 
     */
    protected static int moveBooleanKey(String key, ContentValues valuesIn, ContentValues valuesOut) {
        int ret = 2;
        if (valuesIn != null) {
            if (valuesIn.containsKey(key) ) {
                ret = SharedPreferencesUtil.isTrueAsInt(valuesIn.get(key));
                valuesIn.remove(key);
                if (valuesOut != null) {
                    valuesOut.put(key, ret);
                }
            }
        }
        return ret;
    }

    /**
     * Move String value of the key from valuesIn to valuesOut and remove it from valuesIn
     * @param key
     * @param valuesIn
     * @param valuesOut  may be null
     * @return 1 for not empty, 0 for empty and 2 for "not present" 
     */
    private int moveStringKey(String key, ContentValues valuesIn, ContentValues valuesOut) {
        int ret = 2;
        if (valuesIn != null) {
            if (valuesIn.containsKey(key) ) {
                String value =  valuesIn.getAsString(key);
                ret = SharedPreferencesUtil.isEmpty(value) ? 0 : 1;
                valuesIn.remove(key);
                if (valuesOut != null) {
                    valuesOut.put(key, value);
                }
            }
        }
        return ret;
    }
    
    /**
     * Get a cursor to the database
     * 
     * @see android.content.ContentProvider#query(android.net.Uri,
     *      java.lang.String[], java.lang.String, java.lang.String[],
     *      java.lang.String)
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        boolean built = false;
        String sql = "";

        int matchedCode = sUriMatcher.match(uri);
        switch (matchedCode) {
            case TIMELINE:
                qb.setDistinct(true);
                qb.setTables(tablesForTimeline(uri, projection));
                qb.setProjectionMap(msgProjectionMap);
                break;

            case MSG_COUNT:
                sql = "SELECT count(*) FROM " + MyDatabase.MSG_TABLE_NAME;
                if (selection != null && selection.length() > 0) {
                    sql += " WHERE " + selection;
                }
                break;

            case TIMELINE_MSG_ID:
                qb.setTables(tablesForTimeline(uri, projection));
                qb.setProjectionMap(msgProjectionMap);
                qb.appendWhere(MyDatabase.MSG_TABLE_NAME + "." + BaseColumns._ID + "=" + uriToMessageId(uri));
                break;

            case TIMELINE_SEARCH:
                qb.setTables(tablesForTimeline(uri, projection));
                qb.setProjectionMap(msgProjectionMap);
                String s1 = uri.getLastPathSegment();
                if (s1 != null) {
                    // These two lines don't work:
                    // qb.appendWhere(Msg.SENDER_ID + " LIKE '%" + s1 +
                    // "%' OR " + Msg.BODY + " LIKE '%" + s1 + "%'");
                    // qb.appendWhere(Msg.SENDER_ID + " LIKE \"%" + s1 +
                    // "%\" OR " + Msg.BODY + " LIKE \"%" + s1 + "%\"");
                    // ...so we have to use selectionArgs

                    // 1. This works:
                    // qb.appendWhere(Msg.SENDER_ID + " LIKE ?  OR " +
                    // Msg.BODY + " LIKE ?");

                    // 2. This works also, but yvolk likes it more :-)
                    if (selection != null && selection.length() > 0) {
                        selection = " AND (" + selection + ")";
                    } else {
                        selection = "";
                    }
                    /// TODO: Search in MyDatabase.User.USERNAME also
                    selection = "(" + User.AUTHOR_NAME + " LIKE ?  OR " + Msg.BODY
                            + " LIKE ?)" + selection;

                    selectionArgs = addBeforeArray(selectionArgs, "%" + s1 + "%");
                    selectionArgs = addBeforeArray(selectionArgs, "%" + s1 + "%");
                }
                break;

            case MSG:
                qb.setTables(MyDatabase.MSG_TABLE_NAME);
                qb.setProjectionMap(msgProjectionMap);
                break;

            case USERS:
                qb.setTables(MyDatabase.USER_TABLE_NAME);
                qb.setProjectionMap(userProjectionMap);
                break;

            case USER_ID:
                qb.setTables(MyDatabase.USER_TABLE_NAME);
                qb.setProjectionMap(userProjectionMap);
                qb.appendWhere(BaseColumns._ID + "=" + uriToUserId(uri));
                break;

            default:
                throw new IllegalArgumentException("Unknown URI \"" + uri + "\"; matchedCode="
                        + matchedCode);
        }

        // If no sort order is specified use the default
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            switch (matchedCode) {
                case TIMELINE:
                case TIMELINE_MSG_ID:
                    orderBy = Msg.DEFAULT_SORT_ORDER;
                    break;

                case MSG_COUNT:
                    orderBy = "";
                    break;

                case USERS:
                case USER_ID:
                    orderBy = User.DEFAULT_SORT_ORDER;
                    break;

                default:
                    throw new IllegalArgumentException("Unknown URI \"" + uri + "\"; matchedCode="
                            + matchedCode);
            }
        } else {
            orderBy = sortOrder;
        }

        Cursor c = null;
        if (MyContextHolder.get().isReady()) {
            // Get the database and run the query
            SQLiteDatabase db = MyContextHolder.get().getDatabase().getReadableDatabase();
            boolean logQuery = MyLog.isLoggable(TAG, MyLog.VERBOSE);
            try {
                if (sql.length() == 0) {
                    /* We don't use selectionArgs here, they will be actually used (substitute ?-s in selection)
                     * when the query is executed. 
                     * See <a href="http://stackoverflow.com/questions/2481322/sqlitequerybuilder-buildquery-not-using-selectargs">SQLiteQueryBuilder.buildQuery not using selectArgs?</a> 
                     * and here: <a href="http://code.google.com/p/android/issues/detail?id=4467">SQLiteQueryBuilder.buildQuery ignores selectionArgs</a>
                     */
                    sql = qb.buildQuery(projection, selection, selectionArgs, null, null, orderBy, null);
                    // We cannot use this method in API 10...
                    // sql = qb.buildQuery(projection, selection, null, null, orderBy, null);
                    built = true;
                }
                // Here we substitute ?-s in selection with values from selectionArgs
                c = db.rawQuery(sql, selectionArgs);
            } catch (Exception e) {
                logQuery = true;
                MyLog.e(this, "Database query failed");
                e.printStackTrace();
            }

            if (logQuery) {
                String msg = "query, SQL=\"" + sql + "\"";
                if (selectionArgs != null && selectionArgs.length > 0) {
                    msg += "; selectionArgs=" + Arrays.toString(selectionArgs);
                }
                MyLog.v(TAG, msg);
                if (built) {
                    msg = "uri=" + uri + "; projection=" + Arrays.toString(projection)
                    + "; selection=" + selection + "; sortOrder=" + sortOrder
                    + "; qb.getTables=" + qb.getTables() + "; orderBy=" + orderBy;
                    MyLog.v(TAG, msg);
                }
            }
        }

        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    /**
     * @param uri the same as uri for
     *            {@link MyProvider#query(Uri, String[], String, String[], String)}
     * @param projection
     * @return String for {@link SQLiteQueryBuilder#setTables(String)}
     */
    private static String tablesForTimeline(Uri uri, String[] projection) {
        MyDatabase.TimelineTypeEnum tt = uriToTimelineType(uri);
        // long selectedUserId = uriToUserId(uri);
        boolean isCombined = uriToIsCombined(uri);
        long accountUserId = uriToAccountUserId(uri);
        int nAccounts = 1;
        // Allows to link to one or more accounts
        String accountUserIds = "";
        if (isCombined || accountUserId == 0) {
            for (MyAccount ma : MyContextHolder.get().persistentAccounts().list()) {
                if (!TextUtils.isEmpty(accountUserIds)) {
                    accountUserIds += ", ";
                    nAccounts += 1;
                }
                accountUserIds += Long.toString(ma.getUserId());
                accountUserId = ma.getUserId();
            }

        } else {
            accountUserIds = Long.toString(accountUserId);
        }
        if (nAccounts == 1) {
            accountUserIds = "=" + accountUserIds;
        } else {
            accountUserIds = " IN (" + accountUserIds + ")";
        }

        Collection<String> columns = new java.util.HashSet<String>(Arrays.asList(projection));

        String tables = MyDatabase.MSG_TABLE_NAME;
        boolean linkedUserDefined = false;
        boolean authorNameDefined = false;
        switch (tt) {
            case FOLLOWING_USER:
                tables = "(SELECT " + FollowingUser.FOLLOWING_USER_ID + ", "
                        + MyDatabase.FollowingUser.USER_FOLLOWED + ", "
                        + FollowingUser.USER_ID + " AS " + User.LINKED_USER_ID
                        + " FROM " + MyDatabase.FOLLOWING_USER_TABLE_NAME
                        + " WHERE (" + MyDatabase.User.LINKED_USER_ID + accountUserIds
                        + " AND " + MyDatabase.FollowingUser.USER_FOLLOWED + "=1 )"
                        + ") as fuser";
                String userTable = MyDatabase.USER_TABLE_NAME;
                if (!authorNameDefined && columns.contains(MyDatabase.User.AUTHOR_NAME)) {
                    userTable = "(SELECT "
                            + BaseColumns._ID + ", " + MyDatabase.User.USERNAME + " AS "
                            + MyDatabase.User.AUTHOR_NAME
                            + ", " + MyDatabase.User.USER_MSG_ID
                            + " FROM " + MyDatabase.USER_TABLE_NAME + ")";
                    authorNameDefined = true;
                }
                tables += " INNER JOIN " + userTable + " as u1"
                        + " ON (" + FollowingUser.FOLLOWING_USER_ID + "=u1." + BaseColumns._ID + ")";
                linkedUserDefined = true;
                /**
                 * Select only the latest message from each following User's
                 * timeline
                 */
                tables  += " LEFT JOIN " + MyDatabase.MSG_TABLE_NAME
                        + " ON (" 
                        + "msg." + MyDatabase.Msg.SENDER_ID 
                        + "=fuser." + MyDatabase.FollowingUser.FOLLOWING_USER_ID 
                        + " AND msg." + BaseColumns._ID 
                        + "=u1." + MyDatabase.User.USER_MSG_ID
                        + ")";
                break;
            case MESSAGESTOACT:
                if (nAccounts == 1) {
                    tables = "(SELECT " + accountUserId + " AS " + MyDatabase.User.LINKED_USER_ID
                            + ", * FROM " + MyDatabase.MSG_TABLE_NAME + ") AS msg";
                    linkedUserDefined = true;
                }
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
                    + " FROM " +  MyDatabase.MSGOFUSER_TABLE_NAME + ") AS mou ON "
                    + MyDatabase.MSG_TABLE_NAME + "." + BaseColumns._ID + "="
                    + "mou." + MyDatabase.MsgOfUser.MSG_ID;
            switch (tt) {
                case FOLLOWING_USER:
                case MESSAGESTOACT:
                    tbl += " AND mou." + MyDatabase.MsgOfUser.USER_ID 
                    + "=" + MyDatabase.User.LINKED_USER_ID;
                    tables += " LEFT JOIN " + tbl;
                    break;
                default:
                    tbl += " AND " + MyDatabase.User.LINKED_USER_ID + accountUserIds;
                    if (isCombined) {
                        tables += " LEFT OUTER JOIN " + tbl;
                    } else {
                        tables += " INNER JOIN " + tbl;
                    }
            }
        }

        if (!authorNameDefined && columns.contains(MyDatabase.User.AUTHOR_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + BaseColumns._ID + ", " + MyDatabase.User.USERNAME + " AS "
                    + MyDatabase.User.AUTHOR_NAME
                    + " FROM " + MyDatabase.USER_TABLE_NAME + ") AS author ON "
                    + MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg.AUTHOR_ID + "=author."
                    + BaseColumns._ID;
        }
        if (columns.contains(MyDatabase.User.SENDER_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT " + BaseColumns._ID + ", "
                    + MyDatabase.User.USERNAME + " AS " + MyDatabase.User.SENDER_NAME
                    + " FROM " + MyDatabase.USER_TABLE_NAME + ") AS sender ON "
                    + MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg.SENDER_ID + "=sender."
                    + BaseColumns._ID;
        }
        if (columns.contains(MyDatabase.User.IN_REPLY_TO_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT " + BaseColumns._ID + ", "
                    + MyDatabase.User.USERNAME + " AS " + MyDatabase.User.IN_REPLY_TO_NAME
                    + " FROM " + MyDatabase.USER_TABLE_NAME + ") AS prevauthor ON "
                    + MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg.IN_REPLY_TO_USER_ID
                    + "=prevauthor." + BaseColumns._ID;
        }
        if (columns.contains(MyDatabase.User.RECIPIENT_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT " + BaseColumns._ID + ", "
                    + MyDatabase.User.USERNAME + " AS " + MyDatabase.User.RECIPIENT_NAME
                    + " FROM " + MyDatabase.USER_TABLE_NAME + ") AS recipient ON "
                    + MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg.RECIPIENT_ID + "=recipient."
                    + BaseColumns._ID;
        }
        if (columns.contains(MyDatabase.FollowingUser.AUTHOR_FOLLOWED)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + MyDatabase.FollowingUser.USER_ID + ", "
                    + MyDatabase.FollowingUser.FOLLOWING_USER_ID + ", "
                    + MyDatabase.FollowingUser.USER_FOLLOWED + " AS "
                    + MyDatabase.FollowingUser.AUTHOR_FOLLOWED
                    + " FROM " + MyDatabase.FOLLOWING_USER_TABLE_NAME + ") AS followingauthor ON ("
                    + "followingauthor." + MyDatabase.FollowingUser.USER_ID + "=" + MyDatabase.User.LINKED_USER_ID
                    + " AND "
                    + MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg.AUTHOR_ID
                    + "=followingauthor." + MyDatabase.FollowingUser.FOLLOWING_USER_ID
                    + ")";
        }
        if (columns.contains(MyDatabase.FollowingUser.SENDER_FOLLOWED)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + MyDatabase.FollowingUser.USER_ID + ", "
                    + MyDatabase.FollowingUser.FOLLOWING_USER_ID + ", "
                    + MyDatabase.FollowingUser.USER_FOLLOWED + " AS "
                    + MyDatabase.FollowingUser.SENDER_FOLLOWED
                    + " FROM " + MyDatabase.FOLLOWING_USER_TABLE_NAME + ") AS followingsender ON ("
                    + "followingsender." + MyDatabase.FollowingUser.USER_ID + "=" + MyDatabase.User.LINKED_USER_ID
                    + " AND "
                    + MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg.SENDER_ID
                    + "=followingsender." + MyDatabase.FollowingUser.FOLLOWING_USER_ID
                    + ")";
        }
        return tables;
    }
    
    private static String[] addBeforeArray(String[] array, String s) {
        int length = 0;
        if (array != null) {
            length = array.length;
        }
        String ans[] = new String[length + 1];
        if (length > 0) {
            System.arraycopy(array, 0, ans, 1, array.length);
        }
        ans[0] = s;
        return ans;
    }

    /**
     * Update objects (one or several records) in the database
     * 
     * @see android.content.ContentProvider#update(android.net.Uri,
     *      android.content.ContentValues, java.lang.String, java.lang.String[])
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();
        int count = 0;
        long accountUserId = 0;
        int matchedCode = sUriMatcher.match(uri);
        //MyLog.v(this, "update, matched=" + matchedCode + "; PID=" + android.os.Process.myPid() + "; TID=" + android.os.Process.myTid());
        switch (matchedCode) {
            case MSG:
                count = db.update(MyDatabase.MSG_TABLE_NAME, values, selection, selectionArgs);
                break;

            case TIMELINE_MSG_ID:
                accountUserId = uriToAccountUserId(uri);
                MyDatabase.TimelineTypeEnum timelineType = uriToTimelineType(uri);
                boolean isCombined = uriToIsCombined(uri);
                long rowId = uriToMessageId(uri);
                ContentValues msgOfUserValues = prepareMsgOfUserValues(accountUserId, timelineType, isCombined, values);
                if (values.size() > 0) {
                    count = db.update(MyDatabase.MSG_TABLE_NAME, values, BaseColumns._ID + "=" + rowId
                            + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                            selectionArgs);
                }
                if (msgOfUserValues != null) {
                    String where = "(" + MsgOfUser.MSG_ID + "=" + rowId + " AND "
                            + MsgOfUser.USER_ID + "="
                            + accountUserId + ")";
                    String sql = "SELECT * FROM " + MyDatabase.MSGOFUSER_TABLE_NAME + " WHERE "
                            + where;
                    Cursor c = db.rawQuery(sql, null);
                    if (c == null || c.getCount() == 0) {
                        // There was no such row
                        msgOfUserValues.put(MsgOfUser.MSG_ID, rowId);
                        count += db.insert(MyDatabase.MSGOFUSER_TABLE_NAME, MsgOfUser.MSG_ID,
                                msgOfUserValues);
                    } else {
                        c.close();
                        count += db.update(MyDatabase.MSGOFUSER_TABLE_NAME, msgOfUserValues, where,
                                null);
                    }
                }
                break;

            case USERS:
                count = db.update(MyDatabase.USER_TABLE_NAME, values, selection, selectionArgs);
                break;

            case USER_ID:
                accountUserId = uriToAccountUserId(uri);
                long selectedUserId = uriToUserId(uri);
                FollowingUserValues followingUserValues = FollowingUserValues.valueOf(accountUserId, selectedUserId, values);
                count = db.update(MyDatabase.USER_TABLE_NAME, values, BaseColumns._ID + "=" + selectedUserId
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                followingUserValues.update(db);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI \"" + uri + "\"; matchedCode="
                        + matchedCode);
        }

        return count;
    }

    /**
     *  Static Definitions for UriMatcher and Projection Maps
     */
    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        /** 
         * The order of PathSegments (parameters of timelines) in the URI
         * 1. MyAccount USER_ID is the first parameter (this is his timeline of the type specified below!)
         * 2 - 3. "tt/" +  {@link MyDatabase.TimelineTypeEnum.save()} - The timeline type 
         * 4 - 5. "combined/" +  0 or 1  (1 for combined timeline) 
         * 6 - 7. MyDatabase.MSG_TABLE_NAME + "/" + MSG_ID  (optional, used to access specific Message)
         */
        sUriMatcher.addURI(AUTHORITY, TIMELINE_PATH + "/#/tt/*/combined/#/search/*", TIMELINE_SEARCH);
        sUriMatcher.addURI(AUTHORITY, TIMELINE_PATH + "/#/tt/*/combined/#/" + MyDatabase.MSG_TABLE_NAME + "/#", TIMELINE_MSG_ID);
        sUriMatcher.addURI(AUTHORITY, TIMELINE_PATH + "/#/tt/*/combined/#", TIMELINE);

        sUriMatcher.addURI(AUTHORITY, MyDatabase.MSG_TABLE_NAME + "/count", MSG_COUNT);
        sUriMatcher.addURI(AUTHORITY, MyDatabase.MSG_TABLE_NAME, MSG);

        /** 
         * The order of PathSegments in the URI
         * 1. MyAccount USER_ID is the first parameter (so we can add 'following' information...)
         * 2 - 3. "su/" + USER_ID  (optional, used to access specific User)
         */
        sUriMatcher.addURI(AUTHORITY, MyDatabase.USER_TABLE_NAME + "/#/su/#", USER_ID);
        sUriMatcher.addURI(AUTHORITY, MyDatabase.USER_TABLE_NAME + "/#", USERS);

        msgProjectionMap = new HashMap<String, String>();
        msgProjectionMap.put(BaseColumns._ID, MyDatabase.MSG_TABLE_NAME + "." + BaseColumns._ID + " AS " + BaseColumns._ID);
        msgProjectionMap.put(Msg.MSG_ID, MyDatabase.MSG_TABLE_NAME + "." + BaseColumns._ID + " AS " + Msg.MSG_ID);
        msgProjectionMap.put(Msg.ORIGIN_ID, Msg.ORIGIN_ID);
        msgProjectionMap.put(Msg.MSG_OID, Msg.MSG_OID);
        msgProjectionMap.put(Msg.AUTHOR_ID, Msg.AUTHOR_ID);
        msgProjectionMap.put(User.AUTHOR_NAME, User.AUTHOR_NAME);
        msgProjectionMap.put(Msg.SENDER_ID, Msg.SENDER_ID);
        msgProjectionMap.put(User.SENDER_NAME, User.SENDER_NAME);
        msgProjectionMap.put(Msg.BODY, Msg.BODY);
        msgProjectionMap.put(Msg.VIA, Msg.VIA);
        msgProjectionMap.put(Msg.URL, Msg.URL);
        msgProjectionMap.put(Msg.IN_REPLY_TO_MSG_ID, Msg.IN_REPLY_TO_MSG_ID);
        msgProjectionMap.put(User.IN_REPLY_TO_NAME, User.IN_REPLY_TO_NAME);
        msgProjectionMap.put(Msg.RECIPIENT_ID, Msg.RECIPIENT_ID);
        msgProjectionMap.put(User.RECIPIENT_NAME, User.RECIPIENT_NAME);
        msgProjectionMap.put(User.LINKED_USER_ID, User.LINKED_USER_ID);
        msgProjectionMap.put(MsgOfUser.USER_ID, MyDatabase.MSGOFUSER_TABLE_NAME + "." + MsgOfUser.USER_ID + " AS " + MsgOfUser.USER_ID);
        msgProjectionMap.put(MsgOfUser.DIRECTED, MsgOfUser.DIRECTED);
        msgProjectionMap.put(MsgOfUser.FAVORITED, MsgOfUser.FAVORITED);
        msgProjectionMap.put(MsgOfUser.REBLOGGED, MsgOfUser.REBLOGGED);
        msgProjectionMap.put(MsgOfUser.REBLOG_OID, MsgOfUser.REBLOG_OID);
        msgProjectionMap.put(Msg.CREATED_DATE, Msg.CREATED_DATE);
        msgProjectionMap.put(Msg.SENT_DATE, Msg.SENT_DATE);
        msgProjectionMap.put(Msg.INS_DATE, Msg.INS_DATE);
        msgProjectionMap.put(FollowingUser.AUTHOR_FOLLOWED, FollowingUser.AUTHOR_FOLLOWED);
        msgProjectionMap.put(FollowingUser.SENDER_FOLLOWED, FollowingUser.SENDER_FOLLOWED);

        userProjectionMap = new HashMap<String, String>();
        userProjectionMap.put(BaseColumns._ID, MyDatabase.USER_TABLE_NAME + "." + BaseColumns._ID + " AS " + BaseColumns._ID);
        userProjectionMap.put(User.USER_ID, MyDatabase.USER_TABLE_NAME + "." + BaseColumns._ID + " AS " + User.USER_ID);
        userProjectionMap.put(User.USER_OID, User.USER_OID);
        userProjectionMap.put(User.ORIGIN_ID, User.ORIGIN_ID);
        userProjectionMap.put(User.USERNAME, User.USERNAME);
        userProjectionMap.put(User.AVATAR_BLOB, User.AVATAR_BLOB);
        userProjectionMap.put(User.URL, User.URL);
        userProjectionMap.put(User.CREATED_DATE, User.CREATED_DATE);
        userProjectionMap.put(User.INS_DATE, User.INS_DATE);
        
        userProjectionMap.put(User.HOME_TIMELINE_POSITION, User.HOME_TIMELINE_POSITION);
        userProjectionMap.put(User.HOME_TIMELINE_DATE, User.HOME_TIMELINE_DATE);
        userProjectionMap.put(User.FAVORITES_TIMELINE_POSITION, User.FAVORITES_TIMELINE_POSITION);
        userProjectionMap.put(User.FAVORITES_TIMELINE_DATE, User.FAVORITES_TIMELINE_DATE);
        userProjectionMap.put(User.DIRECT_TIMELINE_POSITION, User.DIRECT_TIMELINE_POSITION);
        userProjectionMap.put(User.DIRECT_TIMELINE_DATE, User.DIRECT_TIMELINE_DATE);
        userProjectionMap.put(User.MENTIONS_TIMELINE_POSITION, User.MENTIONS_TIMELINE_POSITION);
        userProjectionMap.put(User.MENTIONS_TIMELINE_DATE, User.MENTIONS_TIMELINE_DATE);
        userProjectionMap.put(User.USER_TIMELINE_POSITION, User.USER_TIMELINE_POSITION);
        userProjectionMap.put(User.USER_TIMELINE_DATE, User.USER_TIMELINE_DATE);
        userProjectionMap.put(User.USER_MSG_ID, User.USER_MSG_ID);
        userProjectionMap.put(User.USER_MSG_DATE, User.USER_MSG_DATE);
    }
    
    /**
     * Lookup the System's (AndStatus) id from the Originated system's id
     * 
     * @param uri - URI of the database table
     * @param originId - see {@link MyDatabase.Msg#ORIGIN_ID}
     * @param oid - see {@link MyDatabase.Msg#MSG_OID}
     * @return - id in our System (i.e. in the table, e.g.
     *         {@link MyDatabase.Msg#_ID} ). Or 0 if nothing was found.
     */
    public static long oidToId(MyDatabase.OidEnum oidEnum, long originId, String oid) {
        long id = 0;
        String sql = "";

        try {
            switch (oidEnum) {
                case MSG_OID:
                    sql = "SELECT " + BaseColumns._ID + " FROM " + MyDatabase.MSG_TABLE_NAME
                            + " WHERE " + Msg.ORIGIN_ID + "=" + originId + " AND " + Msg.MSG_OID
                            + "=" + quoteIfNotQuoted(oid);
                    break;

                case USER_OID:
                    sql = "SELECT " + BaseColumns._ID + " FROM " + MyDatabase.USER_TABLE_NAME
                            + " WHERE " + User.ORIGIN_ID + "=" + originId + " AND " + User.USER_OID
                            + "=" + quoteIfNotQuoted(oid);
                    break;

                default:
                    throw new IllegalArgumentException("oidToId; Unknown oidEnum \"" + oidEnum);
            }
            SQLiteDatabase db = MyContextHolder.get().getDatabase().getReadableDatabase();
            SQLiteStatement prog = db.compileStatement(sql);
            id = prog.simpleQueryForLong();
            prog.releaseReference();
            if (id == 1 || id == 388) {
                if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                    MyLog.v(TAG, "oidToId: sql=" + sql );
                }
            }
        } catch (SQLiteDoneException ed) {
            id = 0;
        } catch (Exception e) {
            MyLog.e(TAG, "oidToId: " + e.toString());
            e.printStackTrace();
            return 0;
        }
        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(TAG, "oidToId:" + originId + "+" + oid + " -> " + id + " oidEnum=" + oidEnum );
        }
        return id;
    }
    
    /**
     * @return two single quotes for empty/null strings (Use single quotes!)
     */
    public static String quoteIfNotQuoted(String original) {
        if (TextUtils.isEmpty(original)) {
            return "\'\'";
        }
        String quoted = original.trim();
        int firstQuoteIndex = quoted.indexOf('\'');
        if (firstQuoteIndex < 0) {
            return '\'' + quoted + '\'';
        }
        int lastQuoteIndex = quoted.lastIndexOf('\'');
        if (firstQuoteIndex == 0 && lastQuoteIndex == quoted.length()-1) {
            // Already quoted, search quotes inside
            quoted = quoted.substring(1, lastQuoteIndex);
        }
        quoted = quoted.replace("'", "''");
        quoted = '\'' + quoted + '\'';
        return quoted;
    }
    
    /**
     * Lookup Originated system's id from the System's (AndStatus) id
     * 
     * @param oe what oid we need
     * @param entityId - see {@link MyDatabase.Msg#_ID}
     * @param rebloggerUserId Is needed to find reblog by this user
     * @return - oid in Originated system (i.e. in the table, e.g.
     *         {@link MyDatabase.Msg#MSG_OID} empty string in case of an error
     */
    public static String idToOid(OidEnum oe, long entityId, long rebloggerUserId) {
        MyDatabase myDb = MyContextHolder.get().getDatabase();
        if (myDb == null) {
            return "";
        } else {
            SQLiteDatabase db = myDb.getReadableDatabase();
            return idToOid(db, oe, entityId, rebloggerUserId);
        }
    }

    
    /**
     * Lookup Originated system's id from the System's (AndStatus) id
     * 
     * @param oe what oid we need
     * @param entityId - see {@link MyDatabase.Msg#_ID}
     * @param rebloggerUserId Is needed to find reblog by this user
     * @return - oid in Originated system (i.e. in the table, e.g.
     *         {@link MyDatabase.Msg#MSG_OID} empty string in case of an error
     */
    public static String idToOid(SQLiteDatabase db, OidEnum oe, long entityId, long rebloggerUserId) {
        String oid = "";
        SQLiteStatement prog = null;
        String sql = "";

        if (entityId > 0) {
            try {
                switch (oe) {
                    case MSG_OID:
                        sql = "SELECT " + MyDatabase.Msg.MSG_OID + " FROM "
                                + MyDatabase.MSG_TABLE_NAME + " WHERE " + BaseColumns._ID + "=" + entityId;
                        break;

                    case USER_OID:
                        sql = "SELECT " + MyDatabase.User.USER_OID + " FROM "
                                + MyDatabase.USER_TABLE_NAME + " WHERE " + BaseColumns._ID + "="
                                + entityId;
                        break;

                    case REBLOG_OID:
                        if (rebloggerUserId == 0) {
                            MyLog.e(TAG, "idToOid: userId was not defined");
                        }
                        sql = "SELECT " + MyDatabase.MsgOfUser.REBLOG_OID + " FROM "
                                + MyDatabase.MSGOFUSER_TABLE_NAME + " WHERE " 
                                + MsgOfUser.MSG_ID + "=" + entityId + " AND "
                                + MsgOfUser.USER_ID + "=" + rebloggerUserId;
                        break;

                    default:
                        throw new IllegalArgumentException("idToOid; Unknown parameter: " + oe);
                }
                prog = db.compileStatement(sql);
                oid = prog.simpleQueryForString();
                prog.releaseReference();
                
                if (TextUtils.isEmpty(oid) && oe == OidEnum.REBLOG_OID) {
                    // This not reblogged message
                    oid = idToOid(db, OidEnum.MSG_OID, entityId, 0);
                }
                
            } catch (SQLiteDoneException ed) {
                oid = "";
            } catch (Exception e) {
                MyLog.e(TAG, "idToOid: " + e.toString());
                return "";
            }
            if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(TAG, "idToOid: " + oe + " + " + entityId + " -> " + oid);
            }
        }
        return oid;
    }
    
    public static String msgIdToUsername(String msgUserColumnName, long messageId) {
        String userName = "";
        if (messageId != 0) {
            SQLiteStatement prog = null;
            String sql = "";
            try {
                if (msgUserColumnName.contentEquals(MyDatabase.Msg.SENDER_ID) ||
                        msgUserColumnName.contentEquals(MyDatabase.Msg.AUTHOR_ID) ||
                        msgUserColumnName.contentEquals(MyDatabase.Msg.IN_REPLY_TO_USER_ID) ||
                        msgUserColumnName.contentEquals(MyDatabase.Msg.RECIPIENT_ID)) {
                    sql = "SELECT " + MyDatabase.User.USERNAME + " FROM " + MyDatabase.USER_TABLE_NAME
                            + " INNER JOIN " + MyDatabase.MSG_TABLE_NAME + " ON "
                            + MyDatabase.MSG_TABLE_NAME + "." + msgUserColumnName + "=" + MyDatabase.USER_TABLE_NAME + "." + BaseColumns._ID
                            + " WHERE " + MyDatabase.MSG_TABLE_NAME + "." + BaseColumns._ID + "=" + messageId;
                } else {
                    throw new IllegalArgumentException("msgIdToUsername; Unknown name \"" + msgUserColumnName);
                }
                SQLiteDatabase db = MyContextHolder.get().getDatabase().getReadableDatabase();
                prog = db.compileStatement(sql);
                userName = prog.simpleQueryForString();
                prog.releaseReference();
            } catch (SQLiteDoneException ed) {
                userName = "";
            } catch (Exception e) {
                MyLog.e(TAG, "msgIdToUsername: " + e.toString());
                return "";
            }
            if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(TAG, "msgIdTo" + msgUserColumnName + ": " + messageId + " -> " + userName );
            }
        }
        return userName;
    }


    public static String userIdToName(long userId) {
        String userName = "";
        if (userId != 0) {
            SQLiteStatement prog = null;
            String sql = "";
            try {
                sql = "SELECT " + MyDatabase.User.USERNAME + " FROM " + MyDatabase.USER_TABLE_NAME
                        + " WHERE " + MyDatabase.USER_TABLE_NAME + "." + BaseColumns._ID + "=" + userId;
                SQLiteDatabase db = MyContextHolder.get().getDatabase().getReadableDatabase();
                prog = db.compileStatement(sql);
                userName = prog.simpleQueryForString();
                prog.releaseReference();
            } catch (SQLiteDoneException ed) {
                userName = "";
            } catch (Exception e) {
                MyLog.e(TAG, "userIdToName: " + e.toString());
                return "";
            }
            if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(TAG, "userIdToName: " + userId + " -> " + userName );
            }
        }
        return userName;
    }

    /**
     * Convenience method to get column value from {@link MyDatabase.User} table
     * @param columnName without table name
     * @param systemId {@link MyDatabase.User#USER_ID}
     * @return 0 in case not found or error
     */
    public static long userIdToLongColumnValue(String columnName, long systemId) {
        return idToLongColumnValue(MyDatabase.USER_TABLE_NAME, columnName, systemId);
    }

    /**
     * Convenience method to get long column value from the 'tableName' table
     * @param tableName e.g. {@link MyDatabase#MSG_TABLE_NAME} 
     * @param columnName without table name
     * @param systemId tableName._id
     * @return 0 in case not found or error or systemId==0
     */
    private static long idToLongColumnValue(String tableName, String columnName, long systemId) {
        long columnValue = 0;
        if (TextUtils.isEmpty(tableName) || TextUtils.isEmpty(columnName)) {
            throw new IllegalArgumentException("idToLongColumnValue; tableName or columnName are empty");
        } else if (systemId != 0) {
            SQLiteStatement prog = null;
            String sql = "";
            try {
                sql = "SELECT t." + columnName
                        + " FROM " + tableName + " AS t"
                        + " WHERE t._id=" + systemId;
                SQLiteDatabase db = MyContextHolder.get().getDatabase().getReadableDatabase();
                prog = db.compileStatement(sql);
                columnValue = prog.simpleQueryForLong();
                prog.releaseReference();
            } catch (SQLiteDoneException ed) {
                columnValue = 0;
            } catch (Exception e) {
                MyLog.e(TAG, "idToLongColumnValue table='" + tableName + "', column='" + columnName + "': " + e.toString());
                return 0;
            }
            if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(TAG, "idToLongColumnValue table=" + tableName + ", column=" + columnName + ", id=" + systemId + " -> " + columnValue );
            }
        }
        return columnValue;
    }
    
    public static String msgIdToStringColumnValue(String columnName, long systemId) {
        return idToStringColumnValue(MyDatabase.MSG_TABLE_NAME, columnName, systemId);
    }
    
    public static String userIdToStringColumnValue(String columnName, long systemId) {
        return idToStringColumnValue(MyDatabase.USER_TABLE_NAME, columnName, systemId);
    }
    
    /**
     * Convenience method to get String column value from the 'tableName' table
     * @param tableName e.g. {@link MyDatabase#MSG_TABLE_NAME} 
     * @param columnName without table name
     * @param systemId tableName._id
     * @return "" in case not found or error or systemId==0
     */
    private static String idToStringColumnValue(String tableName, String columnName, long systemId) {
        String columnValue = "";
        if (TextUtils.isEmpty(tableName) || TextUtils.isEmpty(columnName)) {
            throw new IllegalArgumentException("idToLongColumnValue; tableName or columnName are empty");
        } else if (systemId != 0) {
            SQLiteStatement prog = null;
            String sql = "";
            try {
                sql = "SELECT t." + columnName
                        + " FROM " + tableName + " AS t"
                        + " WHERE t._id=" + systemId;
                SQLiteDatabase db = MyContextHolder.get().getDatabase().getReadableDatabase();
                prog = db.compileStatement(sql);
                columnValue = prog.simpleQueryForString();
                prog.releaseReference();
            } catch (SQLiteDoneException ed) {
                columnValue = "";
            } catch (Exception e) {
                MyLog.e(TAG, "idToLongColumnValue table='" + tableName + "', column='" + columnName + "': " + e.toString());
                return "";
            }
            if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(TAG, "idToLongColumnValue table=" + tableName + ", column=" + columnName + ", id=" + systemId + " -> " + columnValue );
            }
        }
        return columnValue;
    }
    
    public static long msgIdToUserId(String msgUserColumnName, long systemId) {
        long userId = 0;
        try {
            if (msgUserColumnName.contentEquals(MyDatabase.Msg.SENDER_ID) ||
                    msgUserColumnName.contentEquals(MyDatabase.Msg.AUTHOR_ID) ||
                    msgUserColumnName.contentEquals(MyDatabase.Msg.IN_REPLY_TO_USER_ID) ||
                    msgUserColumnName.contentEquals(MyDatabase.Msg.RECIPIENT_ID)) {
                userId = msgIdToLongColumnValue(msgUserColumnName, systemId);
            } else {
                throw new IllegalArgumentException("msgIdToUserId; Unknown name \"" + msgUserColumnName);
            }
        } catch (Exception e) {
            MyLog.e(TAG, "msgIdToUserId: " + e.toString());
            return 0;
        }
        return userId;
    }

    /**
     * Convenience method to get column value from {@link MyDatabase.Msg} table
     * @param columnName without table name
     * @param systemId  MyDatabase.MSG_TABLE_NAME + "." + Msg._ID
     * @return 0 in case not found or error
     */
    public static long msgIdToLongColumnValue(String columnName, long systemId) {
        return idToLongColumnValue(MyDatabase.MSG_TABLE_NAME, columnName, systemId);
    }
    
    /**
     * Lookup the User's id based on the Username in the Originating system
     * 
     * @param originId - see {@link MyDatabase.Msg#ORIGIN_ID}
     * @param userName - see {@link MyDatabase.User#USERNAME}
     * @return - id in our System (i.e. in the table, e.g.
     *         {@link MyDatabase.User#_ID} ), 0 if not found
     */
    public static long userNameToId(long originId, String userName) {
        SQLiteDatabase db = MyContextHolder.get().getDatabase().getReadableDatabase();
        return userNameToId(db, originId, userName);
    }
    
    public static long userNameToId(SQLiteDatabase db, long originId, String userName) {
        long id = 0;
        SQLiteStatement prog = null;
        String sql = "";
        try {
            sql = "SELECT " + BaseColumns._ID + " FROM " + MyDatabase.USER_TABLE_NAME
                    + " WHERE " + User.ORIGIN_ID + "=" + originId + " AND " + User.USERNAME + "='"
                    + userName + "'";
            prog = db.compileStatement(sql);
            id = prog.simpleQueryForLong();
            prog.releaseReference();
        } catch (SQLiteDoneException ed) {
            id = 0;
        } catch (Exception e) {
            MyLog.e(TAG, "userNameToId: " + e.toString());
            return 0;
        }
        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(TAG, "userNameToId:" + originId + "+" + userName + " -> " + id);
        }
        return id;
    }
    
    /**
     * Build a Timeline URI for this User / {@link MyAccount}
     * @param accountUserId {@link MyDatabase.User#USER_ID}. This user <i>may</i> be an account: {@link MyAccount#getUserId()} 
     * @param isCombined true for a Combined Timeline
     * @return
     */
    public static Uri getTimelineUri(long accountUserId, MyDatabase.TimelineTypeEnum timelineType, boolean isCombined) {
        Uri uri = ContentUris.withAppendedId(TIMELINE_URI, accountUserId);
        uri = Uri.withAppendedPath(uri, "tt/" + timelineType.save());
        uri = Uri.withAppendedPath(uri, "combined/" + (isCombined ? "1" : "0"));
        return uri;
    }

    /**
     * @param accountUserId
     * @param msgId
     * @param isCombined Combined timeline?
     * @return Uri for the message in the account's <u>HOME</u> timeline
     */
    public static Uri getTimelineMsgUri(long accountUserId, MyDatabase.TimelineTypeEnum timelineType, boolean isCombined, long msgId) {
        return ContentUris.withAppendedId(Uri.withAppendedPath(getTimelineUri(accountUserId, timelineType, isCombined), MyDatabase.MSG_TABLE_NAME), msgId);
    }
    
    public static Uri getTimelineSearchUri(long accountUserId, MyDatabase.TimelineTypeEnum timelineType, boolean isCombined, String queryString) {
        Uri uri = Uri.withAppendedPath(getTimelineUri(accountUserId, timelineType, isCombined), SEARCH_SEGMENT);
        if (!TextUtils.isEmpty(queryString)) {
            uri = Uri.withAppendedPath(uri, Uri.encode(queryString));
        }
        return uri;
    }

    /**
     * URI of the user as seen from the {@link MyAccount} User point of view
     * @param accountUserId userId of MyAccount
     * @param selectedUserId ID of the selected User; 0 - if the User doesn't exist
     */
    public static Uri getUserUri(long accountUserId, long selectedUserId) {
        Uri uri = ContentUris.withAppendedId(MyDatabase.User.CONTENT_URI, accountUserId);
        uri = Uri.withAppendedPath(uri, "su");
        uri = ContentUris.withAppendedId(uri, selectedUserId);
        return uri;
    }

    /**
     * @param uri URI to decode, e.g. the one built by {@link MyProvider#getTimelineUri(long, boolean)}
     * @return Is the timeline combined. false for URIs that don't contain such information
     */
    public static boolean uriToIsCombined(Uri uri) {
        boolean isCombined = false;
        try {
            int matchedCode = sUriMatcher.match(uri);
            switch (matchedCode) {
                case TIMELINE:
                case TIMELINE_SEARCH:
                case TIMELINE_MSG_ID:
                    isCombined = ( (Long.parseLong(uri.getPathSegments().get(5)) == 0) ? false : true);
            }
        } catch (Exception e) {}
        return isCombined;        
    }


    /**
     * @param uri URI to decode, e.g. the one built by {@link MyProvider#getTimelineUri(long, boolean)}
     * @return The timeline combined. 
     */
    public static MyDatabase.TimelineTypeEnum uriToTimelineType(Uri uri) {
        MyDatabase.TimelineTypeEnum tt = MyDatabase.TimelineTypeEnum.UNKNOWN;
        try {
            int matchedCode = sUriMatcher.match(uri);
            switch (matchedCode) {
                case TIMELINE:
                case TIMELINE_SEARCH:
                case TIMELINE_MSG_ID:
                    tt = MyDatabase.TimelineTypeEnum.load(uri.getPathSegments().get(3));
            }
        } catch (Exception e) {}
        return tt;        
    }
    
    public static long uriToMessageId(Uri uri) {
        long messageId = 0;
        try {
            int matchedCode = sUriMatcher.match(uri);
            switch (matchedCode) {
                case TIMELINE_MSG_ID:
                    messageId = Long.parseLong(uri.getPathSegments().get(7));
            }
        } catch (Exception e) {}
        return messageId;        
    }
    
    public static long uriToAccountUserId(Uri uri) {
        long accountUserId = 0;
        try {
            int matchedCode = sUriMatcher.match(uri);
            switch (matchedCode) {
                case TIMELINE:
                case TIMELINE_SEARCH:
                case TIMELINE_MSG_ID:
                case USERS:
                case USER_ID:
                    accountUserId = Long.parseLong(uri.getPathSegments().get(1));
            }
        } catch (Exception e) {}
        return accountUserId;        
    }
    
    public static long uriToUserId(Uri uri) {
        long userId = 0;
        try {
            int matchedCode = sUriMatcher.match(uri);
            switch (matchedCode) {
                case USER_ID:
                    userId = Long.parseLong(uri.getPathSegments().get(3));
            }
        } catch (Exception e) {}
        return userId;        
    }
    
    /**
     * Following users' Id's (Friends of the specified User) stored in the database
     * @return IDs, the set is empty if no friends
     */
    public static Set<Long> getIdsOfUsersFollowedBy(long userId) {
        Set<Long> friends = new HashSet<Long>();
        String where = MyDatabase.FollowingUser.USER_ID + "=" + userId
                + " AND " + MyDatabase.FollowingUser.USER_FOLLOWED + "=1";
        String sql = "SELECT " + MyDatabase.FollowingUser.FOLLOWING_USER_ID 
                + " FROM " + MyDatabase.FOLLOWING_USER_TABLE_NAME 
                + " WHERE " + where;
        
        SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(sql, null);
            if (c != null && c.getCount() > 0) {
                c.moveToFirst();
                do {
                    friends.add(c.getLong(0));
                } while (c.moveToNext());
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return friends;
    }
}
