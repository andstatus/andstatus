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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

import org.andstatus.app.ClassInApplicationPackage;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase.Avatar;
import org.andstatus.app.data.MyDatabase.FollowingUser;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.Origin;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
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

    public static final String MSG_TABLE_ALIAS = "msg1";
    /**
     * Projection map used by SQLiteQueryBuilder
     * Projection map for the {@link MyDatabase.Msg} table
     * @see android.database.sqlite.SQLiteQueryBuilder#setProjectionMap
     */
    private static final Map<String, String> MSG_PROJECTION_MAP = new HashMap<String, String>();
    static {
        MSG_PROJECTION_MAP.put(BaseColumns._ID, MSG_TABLE_ALIAS + "." + BaseColumns._ID + " AS " + BaseColumns._ID);
        MSG_PROJECTION_MAP.put(Msg.MSG_ID, MSG_TABLE_ALIAS + "." + BaseColumns._ID + " AS " + Msg.MSG_ID);
        MSG_PROJECTION_MAP.put(Msg.ORIGIN_ID, Msg.ORIGIN_ID);
        MSG_PROJECTION_MAP.put(Msg.MSG_OID, Msg.MSG_OID);
        MSG_PROJECTION_MAP.put(Msg.AUTHOR_ID, Msg.AUTHOR_ID);
        MSG_PROJECTION_MAP.put(User.AUTHOR_NAME, User.AUTHOR_NAME);
        MSG_PROJECTION_MAP.put(Avatar.FILE_NAME, Avatar.FILE_NAME);
        MSG_PROJECTION_MAP.put(Avatar.STATUS, Avatar.STATUS);
        MSG_PROJECTION_MAP.put(Msg.SENDER_ID, Msg.SENDER_ID);
        MSG_PROJECTION_MAP.put(User.SENDER_NAME, User.SENDER_NAME);
        MSG_PROJECTION_MAP.put(Msg.BODY, Msg.BODY);
        MSG_PROJECTION_MAP.put(Msg.VIA, Msg.VIA);
        MSG_PROJECTION_MAP.put(Msg.URL, Msg.URL);
        MSG_PROJECTION_MAP.put(Msg.IN_REPLY_TO_MSG_ID, Msg.IN_REPLY_TO_MSG_ID);
        MSG_PROJECTION_MAP.put(User.IN_REPLY_TO_NAME, User.IN_REPLY_TO_NAME);
        MSG_PROJECTION_MAP.put(Msg.RECIPIENT_ID, Msg.RECIPIENT_ID);
        MSG_PROJECTION_MAP.put(User.RECIPIENT_NAME, User.RECIPIENT_NAME);
        MSG_PROJECTION_MAP.put(User.LINKED_USER_ID, User.LINKED_USER_ID);
        MSG_PROJECTION_MAP.put(MsgOfUser.USER_ID, MsgOfUser.TABLE_NAME + "." + MsgOfUser.USER_ID + " AS " + MsgOfUser.USER_ID);
        MSG_PROJECTION_MAP.put(MsgOfUser.DIRECTED, MsgOfUser.DIRECTED);
        MSG_PROJECTION_MAP.put(MsgOfUser.FAVORITED, MsgOfUser.FAVORITED);
        MSG_PROJECTION_MAP.put(MsgOfUser.REBLOGGED, MsgOfUser.REBLOGGED);
        MSG_PROJECTION_MAP.put(MsgOfUser.REBLOG_OID, MsgOfUser.REBLOG_OID);
        MSG_PROJECTION_MAP.put(Msg.CREATED_DATE, Msg.CREATED_DATE);
        MSG_PROJECTION_MAP.put(Msg.SENT_DATE, Msg.SENT_DATE);
        MSG_PROJECTION_MAP.put(Msg.INS_DATE, Msg.INS_DATE);
        MSG_PROJECTION_MAP.put(FollowingUser.AUTHOR_FOLLOWED, FollowingUser.AUTHOR_FOLLOWED);
        MSG_PROJECTION_MAP.put(FollowingUser.SENDER_FOLLOWED, FollowingUser.SENDER_FOLLOWED);
    }

    /**
     * Projection map for the {@link MyDatabase.User} table
     */
    private static final Map<String, String> USER_PROJECTION_MAP = new HashMap<String, String>();
    static {
        USER_PROJECTION_MAP.put(BaseColumns._ID, User.TABLE_NAME + "." + BaseColumns._ID + " AS " + BaseColumns._ID);
        USER_PROJECTION_MAP.put(User.USER_ID, User.TABLE_NAME + "." + BaseColumns._ID + " AS " + User.USER_ID);
        USER_PROJECTION_MAP.put(User.USER_OID, User.USER_OID);
        USER_PROJECTION_MAP.put(User.ORIGIN_ID, User.ORIGIN_ID);
        USER_PROJECTION_MAP.put(User.USERNAME, User.USERNAME);
        USER_PROJECTION_MAP.put(User.AVATAR_URL, User.AVATAR_URL);
        USER_PROJECTION_MAP.put(User.URL, User.URL);
        USER_PROJECTION_MAP.put(User.CREATED_DATE, User.CREATED_DATE);
        USER_PROJECTION_MAP.put(User.INS_DATE, User.INS_DATE);
        
        USER_PROJECTION_MAP.put(User.HOME_TIMELINE_POSITION, User.HOME_TIMELINE_POSITION);
        USER_PROJECTION_MAP.put(User.HOME_TIMELINE_DATE, User.HOME_TIMELINE_DATE);
        USER_PROJECTION_MAP.put(User.FAVORITES_TIMELINE_POSITION, User.FAVORITES_TIMELINE_POSITION);
        USER_PROJECTION_MAP.put(User.FAVORITES_TIMELINE_DATE, User.FAVORITES_TIMELINE_DATE);
        USER_PROJECTION_MAP.put(User.DIRECT_TIMELINE_POSITION, User.DIRECT_TIMELINE_POSITION);
        USER_PROJECTION_MAP.put(User.DIRECT_TIMELINE_DATE, User.DIRECT_TIMELINE_DATE);
        USER_PROJECTION_MAP.put(User.MENTIONS_TIMELINE_POSITION, User.MENTIONS_TIMELINE_POSITION);
        USER_PROJECTION_MAP.put(User.MENTIONS_TIMELINE_DATE, User.MENTIONS_TIMELINE_DATE);
        USER_PROJECTION_MAP.put(User.USER_TIMELINE_POSITION, User.USER_TIMELINE_POSITION);
        USER_PROJECTION_MAP.put(User.USER_TIMELINE_DATE, User.USER_TIMELINE_DATE);
        USER_PROJECTION_MAP.put(User.USER_MSG_ID, User.USER_MSG_ID);
        USER_PROJECTION_MAP.put(User.USER_MSG_DATE, User.USER_MSG_DATE);
    }
    
    /**
     * "Authority", represented by this ContentProvider subclass 
     *   and declared in the application's manifest.
     *   
     * As Android documentation states:
     * "The authority therefore must be unique. 
     *  Typically, it's the fully qualified name of a ContentProvider subclass.
     *  The path part of a URI may be used by a content provider to identify particular data subsets,
     *  but those paths are not declared in the manifest."
     * (see <a href="http://developer.android.com/guide/topics/manifest/provider-element.html">&lt;provider&gt;</a>)
     * 
     * Note: This is historical constant, remained to preserve compatibility without reinstallation
     */
    public static final String AUTHORITY = ClassInApplicationPackage.PACKAGE_NAME + ".data.MyProvider";
    /**
     * Used for URIs referring to timelines 
     */
    public static final String TIMELINE_PATH = "timeline";
    public static final Uri TIMELINE_URI = Uri.parse("content://" + AUTHORITY + "/" + TIMELINE_PATH);
    /**
     * We add this path segment after the {@link #TIMELINE_URI} to form search URI 
     */
    public static final String SEARCH_SEGMENT = "search";

    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        /** 
         * The order of PathSegments (parameters of timelines) in the URI
         * 1. MyAccount USER_ID is the first parameter (this is his timeline of the type specified below!)
         * 2 - 3. "tt/" +  {@link MyDatabase.TimelineTypeEnum.save()} - The timeline type 
         * 4 - 5. "combined/" +  0 or 1  (1 for combined timeline) 
         * 6 - 7. MyDatabase.MSG_TABLE_NAME + "/" + MSG_ID  (optional, used to access specific Message)
         */
        URI_MATCHER.addURI(AUTHORITY, TIMELINE_PATH + "/#/tt/*/combined/#/search/*", MatchedUri.TIMELINE_SEARCH.code);
        URI_MATCHER.addURI(AUTHORITY, TIMELINE_PATH + "/#/tt/*/combined/#/" + Msg.TABLE_NAME + "/#", MatchedUri.TIMELINE_MSG_ID.code);
        URI_MATCHER.addURI(AUTHORITY, TIMELINE_PATH + "/#/tt/*/combined/#", MatchedUri.TIMELINE.code);

        URI_MATCHER.addURI(AUTHORITY, Msg.TABLE_NAME + "/count", MatchedUri.MSG_COUNT.code);
        URI_MATCHER.addURI(AUTHORITY, Msg.TABLE_NAME, MatchedUri.MSG.code);

        URI_MATCHER.addURI(AUTHORITY, Origin.TABLE_NAME, MatchedUri.ORIGIN.code);
        
        /** 
         * The order of PathSegments in the URI
         * 1. MyAccount USER_ID is the first parameter (so we can add 'following' information...)
         * 2 - 3. "su/" + USER_ID  (optional, used to access specific User)
         */
        URI_MATCHER.addURI(AUTHORITY, User.TABLE_NAME + "/#/su/#", MatchedUri.USER.code);
        URI_MATCHER.addURI(AUTHORITY, User.TABLE_NAME + "/#", MatchedUri.USERS.code);
    }
    
    /**
     * Matched codes, returned by {@link UriMatcher#match(Uri)}
     * This first member is for a Timeline of selected User (Account) (or all timelines...) and it corresponds to the {@link #TIMELINE_URI}
     */
    private enum MatchedUri {
        TIMELINE(1),
        /**
         * Operations on {@link MyDatabase.Msg} table itself
         */
        MSG(7),
        MSG_COUNT(2),
        TIMELINE_SEARCH(3),
        /**
         * The Timeline URI contains Message id 
         */
        TIMELINE_MSG_ID(4),
        ORIGIN(8),
        /**
         * Matched code for the list of Users
         */
        USERS(5),
        /**
         * Matched code for the User
         */
        USER(6),
        
        UNKNOWN(0);
        
        private int code = 0;
        private MatchedUri(int codeIn) {
            code = codeIn;
        }
        
        private static MatchedUri fromInt(int codeIn) {
            for (MatchedUri matched : MatchedUri.values()) {
                if (matched.code == codeIn) {
                    return matched;
                }
            }
            return UNKNOWN;
        }
    }

    private static final String CONTENT_URI_PREFIX = "content://" + AUTHORITY + "/";
    /**
     * These are in fact definitions for Timelines based on the Msg table, 
     * not for the Msg table itself.
     * Because we always filter the table by current MyAccount (USER_ID joined through {@link MsgOfUser} ) etc.
     */
    public static final Uri MSG_CONTENT_URI = Uri.parse(CONTENT_URI_PREFIX + Msg.TABLE_NAME);
    public static final Uri MSG_CONTENT_COUNT_URI = Uri.parse(CONTENT_URI_PREFIX + Msg.TABLE_NAME + "/count");
    public static final Uri ORIGIN_CONTENT_URI = Uri.parse(CONTENT_URI_PREFIX + Origin.TABLE_NAME);
    public static final Uri USER_CONTENT_URI = Uri.parse(CONTENT_URI_PREFIX + User.TABLE_NAME);
    
    /**
     *  Content types should be like in AndroidManifest.xml
     */
    private static final String CONTENT_TYPE_PREFIX = "vnd.android.cursor.dir/"
            + ClassInApplicationPackage.PACKAGE_NAME + ".provider.";
    private static final String CONTENT_ITEM_TYPE_PREFIX = "vnd.android.cursor.item/"
            + ClassInApplicationPackage.PACKAGE_NAME + ".provider.";
    public static final String MSG_CONTENT_TYPE = CONTENT_TYPE_PREFIX + Msg.TABLE_NAME;
    public static final String MSG_CONTENT_ITEM_TYPE = CONTENT_ITEM_TYPE_PREFIX + Msg.TABLE_NAME;
    public static final String ORIGIN_CONTENT_TYPE = CONTENT_TYPE_PREFIX + Origin.TABLE_NAME;
    public static final String ORIGIN_CONTENT_ITEM_TYPE = CONTENT_ITEM_TYPE_PREFIX + Origin.TABLE_NAME;
    public static final String USER_CONTENT_TYPE = CONTENT_TYPE_PREFIX + User.TABLE_NAME;
    public static final String USER_CONTENT_ITEM_TYPE = CONTENT_ITEM_TYPE_PREFIX + User.TABLE_NAME;
    
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
     */
    @Override
    public String getType(Uri uri) {
        String type = null;
        switch (MatchedUri.fromInt(URI_MATCHER.match(uri))) {
            case MSG:
            case TIMELINE:
            case TIMELINE_SEARCH:
            case MSG_COUNT:
                type = MyProvider.MSG_CONTENT_TYPE;
                break;
            case TIMELINE_MSG_ID:
                type = MyProvider.MSG_CONTENT_ITEM_TYPE;
                break;
            case ORIGIN:
                type = MyProvider.ORIGIN_CONTENT_ITEM_TYPE;
                break;
            case USERS:
                type = MyProvider.USER_CONTENT_TYPE;
                break;
            case USER:
                type = MyProvider.USER_CONTENT_ITEM_TYPE;
                break;
            default:
                break;
        }
        return type;
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
        switch (MatchedUri.fromInt(URI_MATCHER.match(uri))) {
            case MSG:
                db.beginTransaction();
                try {
                    // Delete all related records from MyDatabase.MsgOfUser for these messages
                    String selectionG = " EXISTS ("
                            + "SELECT * FROM " + Msg.TABLE_NAME + " WHERE ("
                            + Msg.TABLE_NAME + "." + BaseColumns._ID + "=" + MsgOfUser.TABLE_NAME + "." + MyDatabase.MsgOfUser.MSG_ID
                            + ") AND ("
                            + selection
                            + "))";
                    String descSuffix = "; args=" + Arrays.toString(selectionArgs);
                    sqlDesc = selectionG + descSuffix;
                    count = db.delete(MsgOfUser.TABLE_NAME, selectionG, selectionArgs);
                    // Now delete messages themselves
                    sqlDesc = selection + descSuffix;
                    count = db.delete(Msg.TABLE_NAME, selection, selectionArgs);
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
                } catch(Exception e) {
                    MyLog.d(TAG, "; SQL='" + sqlDesc + "'", e);
                } finally {
                    db.endTransaction();
                }
                if (count > 0) {
                    getContext().getContentResolver().notifyChange(MyProvider.TIMELINE_URI, null);
                }
                break;

            case USERS:
                count = db.delete(User.TABLE_NAME, selection, selectionArgs);
                break;

            case USER:
                // TODO: Delete related records also... 
                long userId = uriToUserId(uri);
                count = db.delete(User.TABLE_NAME, BaseColumns._ID + "=" + userId
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
        MsgOfUserValues msgOfUserValues = new MsgOfUserValues(0);
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

            if (initialValues != null) {
                values = new ContentValues(initialValues);
            } else {
                values = new ContentValues();
            }

            MatchedUri matchedUri = MatchedUri.fromInt(URI_MATCHER.match(uri));
            switch (matchedUri) {
                case TIMELINE:
                    accountUserId = uriToAccountUserId(uri);
                    
                    table = Msg.TABLE_NAME;
                    /**
                     * Add default values for missed required fields
                     */
                    if (!values.containsKey(Msg.AUTHOR_ID) && values.containsKey(Msg.SENDER_ID)) {
                        values.put(Msg.AUTHOR_ID, values.get(Msg.SENDER_ID).toString());
                    }
                    if (!values.containsKey(Msg.BODY)) {
                        values.put(Msg.BODY, "");
                    }
                    if (!values.containsKey(Msg.VIA)) {
                        values.put(Msg.VIA, "");
                    }
                    values.put(Msg.INS_DATE, now);
                    
                    msgOfUserValues = MsgOfUserValues.valueOf(accountUserId, values);
                    break;
                    
                case ORIGIN:
                    table = Origin.TABLE_NAME;
                    break;

                case USER:
                    table = User.TABLE_NAME;
                    values.put(User.INS_DATE, now);
                    accountUserId = uriToAccountUserId(uri);
                    followingUserValues = FollowingUserValues.valueOf(accountUserId, 0, values);
                    break;
                    
                default:
                    throw new IllegalArgumentException("Unknown URI " + uri);
            }

            rowId = db.insert(table, null, values);
            if (rowId == -1) {
                throw new SQLException("Failed to insert row into " + uri);
            } else if ( User.TABLE_NAME.equals(table)) {
                loadAvatar(rowId, values);
            }
            
            msgOfUserValues.setMsgId(rowId);
            msgOfUserValues.insert(db);

            if (followingUserValues != null) {
                followingUserValues.followingUserId =  rowId;
                followingUserValues.update(db);
            }

            switch (matchedUri) {
                case TIMELINE:
                    // The resulted Uri has several parameters...
                    newUri = MyProvider.getTimelineMsgUri(accountUserId, TimelineTypeEnum.HOME , true, rowId);
                    break;
                case USER:
                    newUri = MyProvider.getUserUri(accountUserId, rowId);
                    break;
                case ORIGIN:
                    newUri = MyProvider.getOriginUri(rowId);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
          MyLog.e(this, "Insert", e);
        }
        return newUri;
    }

    public static Uri getOriginUri(long rowId) {
        return ContentUris.withAppendedId(MyProvider.ORIGIN_CONTENT_URI, rowId);
    }

    private void loadAvatar(long rowId, ContentValues values) {
        if (MyPreferences.showAvatars() && values.containsKey(User.AVATAR_URL)) {
            MyServiceManager.sendCommand(new CommandData(CommandEnum.FETCH_AVATAR, null, rowId));
        }
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
        if (valuesIn != null && valuesIn.containsKey(key)) {
            ret = SharedPreferencesUtil.isTrueAsInt(valuesIn.get(key));
            valuesIn.remove(key);
            if (valuesOut != null) {
                valuesOut.put(key, ret);
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
    static int moveStringKey(String key, ContentValues valuesIn, ContentValues valuesOut) {
        int ret = 2;
        if (valuesIn != null && valuesIn.containsKey(key)) {
            String value = valuesIn.getAsString(key);
            ret = SharedPreferencesUtil.isEmpty(value) ? 0 : 1;
            valuesIn.remove(key);
            if (valuesOut != null) {
                valuesOut.put(key, value);
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

        MatchedUri matchedUri = MatchedUri.fromInt(URI_MATCHER.match(uri));
        switch (matchedUri) {
            case TIMELINE:
                qb.setDistinct(true);
                qb.setTables(tablesForTimeline(uri, projection));
                qb.setProjectionMap(MSG_PROJECTION_MAP);
                break;

            case MSG_COUNT:
                sql = "SELECT count(*) FROM " + Msg.TABLE_NAME + " AS " + MSG_TABLE_ALIAS;
                if (selection != null && selection.length() > 0) {
                    sql += " WHERE " + selection;
                }
                break;

            case TIMELINE_MSG_ID:
                qb.setTables(tablesForTimeline(uri, projection));
                qb.setProjectionMap(MSG_PROJECTION_MAP);
                qb.appendWhere(MSG_TABLE_ALIAS + "." + BaseColumns._ID + "=" + uriToMessageId(uri));
                break;

            case TIMELINE_SEARCH:
                qb.setTables(tablesForTimeline(uri, projection));
                qb.setProjectionMap(MSG_PROJECTION_MAP);
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
                qb.setTables(Msg.TABLE_NAME + " AS " + MSG_TABLE_ALIAS);
                qb.setProjectionMap(MSG_PROJECTION_MAP);
                break;

            case USERS:
                qb.setTables(User.TABLE_NAME);
                qb.setProjectionMap(USER_PROJECTION_MAP);
                break;

            case USER:
                qb.setTables(User.TABLE_NAME);
                qb.setProjectionMap(USER_PROJECTION_MAP);
                qb.appendWhere(BaseColumns._ID + "=" + uriToUserId(uri));
                break;

            default:
                throw new IllegalArgumentException("Unknown URI \"" + uri + "\"; matchedUri="
                        + matchedUri);
        }

        // If no sort order is specified use the default
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            switch (matchedUri) {
                case TIMELINE:
                case TIMELINE_MSG_ID:
                    orderBy = Msg.DEFAULT_SORT_ORDER;
                    break;

                case MSG_COUNT:
                    orderBy = "";
                    break;

                case USERS:
                case USER:
                    orderBy = User.DEFAULT_SORT_ORDER;
                    break;

                default:
                    throw new IllegalArgumentException("Unknown URI \"" + uri + "\"; matchedCode="
                            + matchedUri);
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
                    // TODO: We cannot use this method in API 10...
                    // sql = qb.buildQuery(projection, selection, null, null, orderBy, null);
                    built = true;
                }
                // Here we substitute ?-s in selection with values from selectionArgs
                c = db.rawQuery(sql, selectionArgs);
            } catch (Exception e) {
                logQuery = true;
                MyLog.e(this, "Database query failed", e);
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
        TimelineTypeEnum tt = uriToTimelineType(uri);
        boolean isCombined = uriToIsCombined(uri);
        AccountUserIds userIds = new AccountUserIds(isCombined, uriToAccountUserId(uri));

        Collection<String> columns = new java.util.HashSet<String>(Arrays.asList(projection));

        String tables = Msg.TABLE_NAME + " AS " + MSG_TABLE_ALIAS;
        boolean linkedUserDefined = false;
        boolean authorNameDefined = false;
        String authorTableName = "";
        switch (tt) {
            case FOLLOWING_USER:
                tables = "(SELECT " + FollowingUser.FOLLOWING_USER_ID + ", "
                        + MyDatabase.FollowingUser.USER_FOLLOWED + ", "
                        + FollowingUser.USER_ID + " AS " + User.LINKED_USER_ID
                        + " FROM " + FollowingUser.TABLE_NAME
                        + " WHERE (" + MyDatabase.User.LINKED_USER_ID + userIds.getSqlUserIds()
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
                tables  += " LEFT JOIN " + Msg.TABLE_NAME + " AS " + MSG_TABLE_ALIAS
                        + " ON (" 
                        + MSG_TABLE_ALIAS + "." + MyDatabase.Msg.SENDER_ID 
                        + "=fuser." + MyDatabase.FollowingUser.FOLLOWING_USER_ID 
                        + " AND " + MSG_TABLE_ALIAS + "." + BaseColumns._ID 
                        + "=u1." + MyDatabase.User.USER_MSG_ID
                        + ")";
                break;
            case MESSAGESTOACT:
                if (userIds.getnIds() == 1) {
                    tables = "(SELECT " + userIds.getAccountUserId() + " AS " + MyDatabase.User.LINKED_USER_ID
                            + ", * FROM " + Msg.TABLE_NAME + ") AS " + MSG_TABLE_ALIAS;
                    linkedUserDefined = true;
                }
                break;
            case PUBLIC:
                String where = Msg.PUBLIC + "=1";
                if (!isCombined) {
                    MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(uriToAccountUserId(uri));
                    if (ma != null) {
                        where += " AND " + Msg.ORIGIN_ID + "=" + ma.getOriginId();
                    }
                }
                tables = "(SELECT * FROM " + Msg.TABLE_NAME + " WHERE (" + where + ")) AS " + MSG_TABLE_ALIAS;
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
                    + MSG_TABLE_ALIAS + "." + BaseColumns._ID + "="
                    + "mou." + MyDatabase.MsgOfUser.MSG_ID;
            switch (tt) {
                case FOLLOWING_USER:
                case MESSAGESTOACT:
                    tbl += " AND mou." + MyDatabase.MsgOfUser.USER_ID 
                    + "=" + MyDatabase.User.LINKED_USER_ID;
                    tables += " LEFT JOIN " + tbl;
                    break;
                default:
                    tbl += " AND " + MyDatabase.User.LINKED_USER_ID + userIds.getSqlUserIds();
                    if (isCombined || tt == TimelineTypeEnum.PUBLIC) {
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
                    + MyDatabase.User.USERNAME + " AS " + MyDatabase.User.AUTHOR_NAME
                    + " FROM " + User.TABLE_NAME + ") AS author ON "
                    + MSG_TABLE_ALIAS + "." + MyDatabase.Msg.AUTHOR_ID + "=author."
                    + BaseColumns._ID;
            authorNameDefined = true;
            authorTableName = "author";
        }
        if (authorNameDefined && columns.contains(MyDatabase.Avatar.FILE_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + MyDatabase.Avatar.USER_ID + ", "
                    + MyDatabase.Avatar.STATUS + ", "
                    + MyDatabase.Avatar.FILE_NAME
                    + " FROM " + MyDatabase.Avatar.TABLE_NAME + ") AS av ON "
                    + "av." + Avatar.STATUS 
                    + "=" + AvatarStatus.LOADED.save() + " AND " 
                    + "av." + MyDatabase.Avatar.USER_ID 
                    + "=" + authorTableName + "." + BaseColumns._ID;
        }
        if (columns.contains(MyDatabase.User.SENDER_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT " + BaseColumns._ID + ", "
                    + MyDatabase.User.USERNAME + " AS " + MyDatabase.User.SENDER_NAME
                    + " FROM " + User.TABLE_NAME + ") AS sender ON "
                    + MSG_TABLE_ALIAS + "." + MyDatabase.Msg.SENDER_ID + "=sender."
                    + BaseColumns._ID;
        }
        if (columns.contains(MyDatabase.User.IN_REPLY_TO_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT " + BaseColumns._ID + ", "
                    + MyDatabase.User.USERNAME + " AS " + MyDatabase.User.IN_REPLY_TO_NAME
                    + " FROM " + User.TABLE_NAME + ") AS prevauthor ON "
                    + MSG_TABLE_ALIAS + "." + MyDatabase.Msg.IN_REPLY_TO_USER_ID
                    + "=prevauthor." + BaseColumns._ID;
        }
        if (columns.contains(MyDatabase.User.RECIPIENT_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT " + BaseColumns._ID + ", "
                    + MyDatabase.User.USERNAME + " AS " + MyDatabase.User.RECIPIENT_NAME
                    + " FROM " + User.TABLE_NAME + ") AS recipient ON "
                    + MSG_TABLE_ALIAS + "." + MyDatabase.Msg.RECIPIENT_ID + "=recipient."
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
                    + MSG_TABLE_ALIAS + "." + MyDatabase.Msg.AUTHOR_ID
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
                    + MSG_TABLE_ALIAS + "." + MyDatabase.Msg.SENDER_ID
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
        String[] ans = new String[length + 1];
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
        MatchedUri matchedUri = MatchedUri.fromInt(URI_MATCHER.match(uri));
        switch (matchedUri) {
            case MSG:
                count = db.update(Msg.TABLE_NAME, values, selection, selectionArgs);
                break;

            case TIMELINE_MSG_ID:
                accountUserId = uriToAccountUserId(uri);
                long rowId = uriToMessageId(uri);
                MsgOfUserValues msgOfUserValues = MsgOfUserValues.valueOf(accountUserId, values);
                msgOfUserValues.setMsgId(rowId);
                if (values.size() > 0) {
                    count = db.update(Msg.TABLE_NAME, values, BaseColumns._ID + "=" + rowId
                            + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                            selectionArgs);
                }
                count += msgOfUserValues.update(db);
                break;

            case USERS:
                count = db.update(User.TABLE_NAME, values, selection, selectionArgs);
                break;
            case USER:
                accountUserId = uriToAccountUserId(uri);
                long selectedUserId = uriToUserId(uri);
                FollowingUserValues followingUserValues = FollowingUserValues.valueOf(accountUserId, selectedUserId, values);
                count = db.update(User.TABLE_NAME, values, BaseColumns._ID + "=" + selectedUserId
                        + (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : ""),
                        selectionArgs);
                followingUserValues.update(db);
                loadAvatar(selectedUserId, values);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI \"" + uri + "\"; matchedCode="
                        + matchedUri);
        }

        return count;
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
        MyDatabase myDb = MyContextHolder.get().getDatabase();
        if (myDb == null) {
            MyLog.v(TAG, "oidToId: MyDatabase is null, oid=" + oid);
            return 0;
        } else {
            SQLiteDatabase db = myDb.getReadableDatabase();
            return oidToId(db, oidEnum, originId, oid);
        }
    }
    
    public static long oidToId(SQLiteDatabase db, MyDatabase.OidEnum oidEnum, long originId, String oid) {
        long id = 0;
        String sql = "";

        SQLiteStatement prog = null;
        try {
            switch (oidEnum) {
                case MSG_OID:
                    sql = "SELECT " + BaseColumns._ID + " FROM " + Msg.TABLE_NAME
                            + " WHERE " + Msg.ORIGIN_ID + "=" + originId + " AND " + Msg.MSG_OID
                            + "=" + quoteIfNotQuoted(oid);
                    break;

                case USER_OID:
                    sql = "SELECT " + BaseColumns._ID + " FROM " + User.TABLE_NAME
                            + " WHERE " + User.ORIGIN_ID + "=" + originId + " AND " + User.USER_OID
                            + "=" + quoteIfNotQuoted(oid);
                    break;

                default:
                    throw new IllegalArgumentException("oidToId; Unknown oidEnum \"" + oidEnum);
            }
            prog = db.compileStatement(sql);
            id = prog.simpleQueryForLong();
            if ((id == 1 || id == 388) 
                && MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(TAG, "oidToId: sql='" + sql +"'");
            }
        } catch (SQLiteDoneException e) {
            MyLog.ignored(TAG, e);
            id = 0;
        } catch (Exception e) {
            MyLog.e(TAG, "oidToId: sql='" + sql +"'", e);
            id = 0;
        } finally {
            DbUtils.closeSilently(prog);
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
            MyLog.v(TAG, "idToOid: MyDatabase is null, oe=" + oe + " id=" + entityId);
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
        String method = "idToOid";
        String oid = "";
        SQLiteStatement prog = null;
        String sql = "";

        if (entityId > 0) {
            try {
                switch (oe) {
                    case MSG_OID:
                        sql = "SELECT " + MyDatabase.Msg.MSG_OID + " FROM "
                                + Msg.TABLE_NAME + " WHERE " + BaseColumns._ID + "=" + entityId;
                        break;

                    case USER_OID:
                        sql = "SELECT " + MyDatabase.User.USER_OID + " FROM "
                                + User.TABLE_NAME + " WHERE " + BaseColumns._ID + "="
                                + entityId;
                        break;

                    case REBLOG_OID:
                        if (rebloggerUserId == 0) {
                            MyLog.e(TAG, method + ": userId was not defined");
                        }
                        sql = "SELECT " + MyDatabase.MsgOfUser.REBLOG_OID + " FROM "
                                + MsgOfUser.TABLE_NAME + " WHERE " 
                                + MsgOfUser.MSG_ID + "=" + entityId + " AND "
                                + MsgOfUser.USER_ID + "=" + rebloggerUserId;
                        break;

                    default:
                        throw new IllegalArgumentException(method + "; Unknown parameter: " + oe);
                }
                prog = db.compileStatement(sql);
                oid = prog.simpleQueryForString();
                
                if (TextUtils.isEmpty(oid) && oe == OidEnum.REBLOG_OID) {
                    // This not reblogged message
                    oid = idToOid(db, OidEnum.MSG_OID, entityId, 0);
                }
                
            } catch (SQLiteDoneException e) {
                MyLog.ignored(TAG, e);
                oid = "";
            } catch (Exception e) {
                MyLog.e(TAG, method, e);
                oid = "";
            } finally {
                DbUtils.closeSilently(prog);
            }
            if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(TAG, method + ": " + oe + " + " + entityId + " -> " + oid);
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
                    sql = "SELECT " + MyDatabase.User.USERNAME + " FROM " + User.TABLE_NAME
                            + " INNER JOIN " + Msg.TABLE_NAME + " ON "
                            + Msg.TABLE_NAME + "." + msgUserColumnName + "=" + User.TABLE_NAME + "." + BaseColumns._ID
                            + " WHERE " + Msg.TABLE_NAME + "." + BaseColumns._ID + "=" + messageId;
                } else {
                    throw new IllegalArgumentException("msgIdToUsername; Unknown name \"" + msgUserColumnName);
                }
                SQLiteDatabase db = MyContextHolder.get().getDatabase().getReadableDatabase();
                prog = db.compileStatement(sql);
                userName = prog.simpleQueryForString();
            } catch (SQLiteDoneException e) {
                MyLog.ignored(TAG, e);
                userName = "";
            } catch (Exception e) {
                MyLog.e(TAG, "msgIdToUsername", e);
                userName = "";
            } finally {
                DbUtils.closeSilently(prog);
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
                sql = "SELECT " + MyDatabase.User.USERNAME + " FROM " + User.TABLE_NAME
                        + " WHERE " + User.TABLE_NAME + "." + BaseColumns._ID + "=" + userId;
                SQLiteDatabase db = MyContextHolder.get().getDatabase().getReadableDatabase();
                prog = db.compileStatement(sql);
                userName = prog.simpleQueryForString();
            } catch (SQLiteDoneException e) {
                MyLog.ignored(TAG, e);
                userName = "";
            } catch (Exception e) {
                MyLog.e(TAG, "userIdToName", e);
                userName = "";
            } finally {
                DbUtils.closeSilently(prog);
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
        return idToLongColumnValue(User.TABLE_NAME, columnName, systemId);
    }

    private static long idToLongColumnValue(String tableName, String columnName, long systemId) {
        if (systemId == 0) {
            return 0;
        } else {
            return conditionToLongColumnValue(tableName, columnName, "t._id=" + systemId);
        }
    }


    /**
     * Convenience method to get long column value from the 'tableName' table
     * @param tableName e.g. {@link Msg#TABLE_NAME} 
     * @param columnName without table name
     * @param condition WHERE part of SQL statement
     * @return 0 in case not found or error or systemId==0
     */
    static long conditionToLongColumnValue(String tableName, String columnName, String condition) {
        final String method = "conditionToLongColumnValue";
        long columnValue = 0;
        if (TextUtils.isEmpty(tableName) || TextUtils.isEmpty(columnName)) {
            throw new IllegalArgumentException(method + " tableName or columnName are empty");
        } else if (!TextUtils.isEmpty(condition)) {
            SQLiteStatement prog = null;
            String sql = "";
            try {
                sql = "SELECT t." + columnName
                        + " FROM " + tableName + " AS t"
                        + " WHERE " + condition;
                SQLiteDatabase db = MyContextHolder.get().getDatabase().getReadableDatabase();
                prog = db.compileStatement(sql);
                columnValue = prog.simpleQueryForLong();
            } catch (SQLiteDoneException e) {
                MyLog.ignored(TAG, e);
                columnValue = 0;
            } catch (Exception e) {
                MyLog.e(TAG, method + " table='" + tableName 
                        + "', column='" + columnName + "'"
                        + " where '" + condition + "'", e);
                return 0;
            } finally {
                DbUtils.closeSilently(prog);
            }
            if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(TAG, method + " table=" + tableName + ", column=" + columnName + " where '" + condition + "' -> " + columnValue );
            }
        }
        return columnValue;
    }
    
    public static String msgIdToStringColumnValue(String columnName, long systemId) {
        return idToStringColumnValue(Msg.TABLE_NAME, columnName, systemId);
    }
    
    public static String userIdToStringColumnValue(String columnName, long systemId) {
        return idToStringColumnValue(User.TABLE_NAME, columnName, systemId);
    }
    
    /**
     * Convenience method to get String column value from the 'tableName' table
     * @param tableName e.g. {@link Msg#TABLE_NAME} 
     * @param columnName without table name
     * @param systemId tableName._id
     * @return "" in case not found or error or systemId==0
     */
    private static String idToStringColumnValue(String tableName, String columnName, long systemId) {
        String method = "idToStringColumnValue";
        String columnValue = "";
        if (TextUtils.isEmpty(tableName) || TextUtils.isEmpty(columnName)) {
            throw new IllegalArgumentException(method + " tableName or columnName are empty");
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
            } catch (SQLiteDoneException e) {
                MyLog.ignored(TAG, e);
                columnValue = "";
            } catch (Exception e) {
                MyLog.e(TAG, method + " table='" + tableName 
                        + "', column='" + columnName + "'", e);
                return "";
            } finally {
                DbUtils.closeSilently(prog);
            }
            if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(TAG, method + " table=" + tableName + ", column=" + columnName + ", id=" + systemId + " -> " + columnValue );
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
            MyLog.e(TAG, "msgIdToUserId", e);
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
        return idToLongColumnValue(Msg.TABLE_NAME, columnName, systemId);
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
            sql = "SELECT " + BaseColumns._ID + " FROM " + User.TABLE_NAME
                    + " WHERE " + User.ORIGIN_ID + "=" + originId + " AND " + User.USERNAME + "='"
                    + userName + "'";
            prog = db.compileStatement(sql);
            id = prog.simpleQueryForLong();
        } catch (SQLiteDoneException e) {
            MyLog.ignored(TAG, e);
            id = 0;
        } catch (Exception e) {
            MyLog.e(TAG, "userNameToId", e);
            id = 0;
        } finally {
            DbUtils.closeSilently(prog);
        }
        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(TAG, "userNameToId:" + originId + "+" + userName + " -> " + id);
        }
        return id;
    }
    
    public static Uri getTimelineSearchUri(long accountUserId, TimelineTypeEnum timelineType, boolean isCombined, String queryString) {
        Uri uri = getTimelineUri(accountUserId, timelineType, isCombined);
        if (!TextUtils.isEmpty(queryString)) {
            uri = Uri.withAppendedPath(uri, SEARCH_SEGMENT);
            uri = Uri.withAppendedPath(uri, Uri.encode(queryString));
        }
        return uri;
    }

    /**
     * Uri for the message in the account's timeline
     */
    public static Uri getTimelineMsgUri(long accountUserId, TimelineTypeEnum timelineType, boolean isCombined, long msgId) {
        Uri uri = getTimelineUri(accountUserId, timelineType, isCombined);
        uri = Uri.withAppendedPath(uri,  Msg.TABLE_NAME);
        uri = ContentUris.withAppendedId(uri, msgId);
        return uri;
    }
    
    /**
     * Build a Timeline URI for this User / {@link MyAccount}
     * @param accountUserId {@link MyDatabase.User#USER_ID}. This user <i>may</i> be an account: {@link MyAccount#getUserId()} 
     * @return
     */
    public static Uri getTimelineUri(long accountUserId, TimelineTypeEnum timelineType, boolean isTimelineCombined) {
        Uri uri = ContentUris.withAppendedId(TIMELINE_URI, accountUserId);
        uri = Uri.withAppendedPath(uri, "tt/" + timelineType.save());
        uri = Uri.withAppendedPath(uri, "combined/" + (isTimelineCombined ? "1" : "0"));
        return uri;
    }

    /**
     * URI of the user as seen from the {@link MyAccount} User point of view
     * @param accountUserId userId of MyAccount
     * @param selectedUserId ID of the selected User; 0 - if the User doesn't exist
     */
    public static Uri getUserUri(long accountUserId, long selectedUserId) {
        Uri uri = ContentUris.withAppendedId(MyProvider.USER_CONTENT_URI, accountUserId);
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
            switch (MatchedUri.fromInt(URI_MATCHER.match(uri))) {
                case TIMELINE:
                case TIMELINE_SEARCH:
                case TIMELINE_MSG_ID:
                    isCombined = ( (Long.parseLong(uri.getPathSegments().get(5)) == 0) ? false : true);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.d(TAG, String.valueOf(uri), e);
        }
        return isCombined;        
    }


    /**
     * @param uri URI to decode, e.g. the one built by {@link MyProvider#getTimelineUri(long, boolean)}
     * @return The timeline combined. 
     */
    public static TimelineTypeEnum uriToTimelineType(Uri uri) {
        TimelineTypeEnum tt = TimelineTypeEnum.UNKNOWN;
        try {
            switch (MatchedUri.fromInt(URI_MATCHER.match(uri))) {
                case TIMELINE:
                case TIMELINE_SEARCH:
                case TIMELINE_MSG_ID:
                    tt = TimelineTypeEnum.load(uri.getPathSegments().get(3));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.d(TAG, String.valueOf(uri), e);
        }
        return tt;        
    }
    
    public static long uriToMessageId(Uri uri) {
        long messageId = 0;
        try {
            switch (MatchedUri.fromInt(URI_MATCHER.match(uri))) {
                case TIMELINE_MSG_ID:
                    messageId = Long.parseLong(uri.getPathSegments().get(7));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.v(TAG, e);
        }
        return messageId;        
    }
    
    public static long uriToAccountUserId(Uri uri) {
        long accountUserId = 0;
        try {
            switch (MatchedUri.fromInt(URI_MATCHER.match(uri))) {
                case TIMELINE:
                case TIMELINE_SEARCH:
                case TIMELINE_MSG_ID:
                case USERS:
                case USER:
                    accountUserId = Long.parseLong(uri.getPathSegments().get(1));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.v(TAG, e);
        }
        return accountUserId;        
    }
    
    public static long uriToUserId(Uri uri) {
        long userId = 0;
        try {
            switch (MatchedUri.fromInt(URI_MATCHER.match(uri))) {
                case USER:
                    userId = Long.parseLong(uri.getPathSegments().get(3));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.e(TAG, e);
        }
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
                + " FROM " + FollowingUser.TABLE_NAME 
                + " WHERE " + where;
        
        SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(sql, null);
            while (c.moveToNext()) {
                friends.add(c.getLong(0));
            }
        } finally {
            DbUtils.closeSilently(c);
        }
        return friends;
    }

    /**
     * Newest replies are the first
     */
    public static List<Long> getReplyIds(long msgId) {
        List<Long> replies = new ArrayList<Long>();
        String sql = "SELECT " + MyDatabase.Msg._ID 
                + " FROM " + Msg.TABLE_NAME 
                + " WHERE " + MyDatabase.Msg.IN_REPLY_TO_MSG_ID + "=" + msgId
                + " ORDER BY " + Msg.CREATED_DATE + " DESC";
        
        SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(sql, null);
            while (c.moveToNext()) {
                replies.add(c.getLong(0));
            }
        } finally {
            DbUtils.closeSilently(c);
        }
        return replies;
    }
}
