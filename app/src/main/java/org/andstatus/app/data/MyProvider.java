/* 
 * Copyright (C) 2012-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.AudienceTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.database.table.OriginTable;
import org.andstatus.app.note.KeywordsFilter;
import org.andstatus.app.notification.NotificationEventType;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Database provider for the MyDatabase database.
 * 
 * The code of this application accesses this class through {@link android.content.ContentResolver}.
 * ContentResolver in it's turn accesses this class.
 * 
 */
public class MyProvider extends ContentProvider {
    static final String TAG = MyProvider.class.getSimpleName();

    /**
     * @see android.content.ContentProvider#onCreate()
     */
    @Override
    public boolean onCreate() {
        return MyContextHolder.getMyFutureContext(getContext(), this).getNow().isReady();
    }

    /**
     * Get MIME type of the content, used for the supplied Uri
     * For discussion how this may be used see:
     * <a href="http://stackoverflow.com/questions/5351669/why-use-contentprovider-gettype-to-get-mime-type">Why use ContentProvider.getType() to get MIME type</a>
     */
    @Override
    public String getType(@NonNull Uri uri) {
        return MatchedUri.fromUri(uri).getMimeType();
    }

    /**
     * Delete a record(s) from the database.
     * 
     * @see android.content.ContentProvider#delete(android.net.Uri,
     *      java.lang.String, java.lang.String[])
     */
    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(this, "delete; Database is null");
            return 0;
        }
        int count;
        ParsedUri uriParser = ParsedUri.fromUri(uri);
        switch (uriParser.matched()) {
            case ACTIVITY:
                count = deleteActivities(db, selection, selectionArgs, false);
                break;

            case ACTOR:
                count = deleteActors(db, selection, selectionArgs);
                break;

            case ACTOR_ITEM:
                count = deleteActors(db, BaseColumns._ID + "=" + uriParser.getActorId(), null);
                break;

            default:
                throw new IllegalArgumentException(uriParser.toString());
        }
        return count;
    }

    /** @return Number of deleted activities of this note */
    public static int deleteNote(Context context, long noteId) {
        if (context == null || noteId == 0) return 0;
        try {
            return context.getContentResolver().delete(MatchedUri.ACTIVITY_CONTENT_URI,
                    ActivityTable.TABLE_NAME + "." + ActivityTable.NOTE_ID + "=" + noteId,
                    new String[]{});

        } catch (Exception e) {
            MyLog.e(TAG, "Error destroying note locally", e);
        }
        return 0;
    }

    private static int deleteActivities(SQLiteDatabase db, String selection, String[] selectionArgs, boolean inTransaction) {
        int count = 0;
        String sqlDesc = "";
        if (!inTransaction) {
            db.beginTransaction();
        }
        try {
            String descSuffix = "; args=" + Arrays.toString(selectionArgs);

            // Start from deletion of activities
            sqlDesc = selection + descSuffix;
            count += db.delete(ActivityTable.TABLE_NAME, selection, selectionArgs);

            // Notes, which don't have any activities
            String sqlNoteIds = "SELECT msgA." + NoteTable._ID +
                    " FROM " + NoteTable.TABLE_NAME + " AS msgA" +
                    " WHERE NOT EXISTS" +
                    " (SELECT " + ActivityTable.NOTE_ID + " FROM " + ActivityTable.TABLE_NAME +
                    " WHERE " + ActivityTable.NOTE_ID + "=msgA." + NoteTable._ID + ")";
            final Set<Long> noteIds = MyQuery.getLongs(sqlNoteIds);

            // Audience
            String selectionG = " EXISTS (" + sqlNoteIds +
                    " AND (msgA." + NoteTable._ID +
                    "=" + AudienceTable.TABLE_NAME + "." + AudienceTable.NOTE_ID + "))";
            sqlDesc = selectionG + descSuffix;
            count += db.delete(AudienceTable.TABLE_NAME, selectionG, new String[]{});

            for (long noteId : noteIds) {
                DownloadData.deleteAllOfThisNote(db, noteId);
            }

            // Notes
            selectionG = " EXISTS (" + sqlNoteIds +
                    " AND (msgA." + NoteTable._ID +
                    "=" + NoteTable.TABLE_NAME + "." + NoteTable._ID + "))";
            sqlDesc = selectionG + descSuffix;
            count += db.delete(NoteTable.TABLE_NAME, selectionG, new String[]{});

            if (!inTransaction) {
                db.setTransactionSuccessful();
            }
        } catch(Exception e) {
            MyLog.d(TAG, "; SQL='" + sqlDesc + "'", e);
        } finally {
            if (!inTransaction) {
                db.endTransaction();
            }
        }
        return count;
    }

    private int deleteActors(SQLiteDatabase db, String selection, String[] selectionArgs) {
        int count;
        // TODO: Delete related records also... 
        count = db.delete(ActorTable.TABLE_NAME, selection, selectionArgs);
        return count;
    }

    public static void delete(@NonNull MyContext myContext, @NonNull String tableName, @NonNull String column, Object value) {
        if (value == null) return;
        delete(myContext, tableName, column + "=" + value);
    }

    public static void delete(@NonNull MyContext myContext, @NonNull String tableName, String where) {
        final String method = "delete";
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.v(MyProvider.TAG, () -> method + "; Database is null");
            return;
        }
        try {
            db.delete(tableName, where, null);
        } catch (Exception e) {
            MyLog.w(TAG, method + "; table:'" + tableName + "', where:'" + where + "'", e);
        }
    }

    public static long deleteActivity(MyContext myContext, long activityId, long noteId, boolean inTransaction) {
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(MyProvider.TAG, "deleteActivity; Database is null");
            return 0;
        }
        long originId = MyQuery.activityIdToLongColumnValue(ActivityTable.ORIGIN_ID, activityId);
        if (originId == 0) return 0;
        Origin origin = MyContextHolder.get().origins().fromId(originId);
        // Was this the last activity for this note?
        final long activityId2 = MyQuery.conditionToLongColumnValue(db, null, ActivityTable.TABLE_NAME,
                BaseColumns._ID, ActivityTable.NOTE_ID + "=" + noteId +
                        " AND " + ActivityTable.TABLE_NAME + "." + ActivityTable._ID + "!=" + activityId);
        long count;
        if (noteId != 0 && activityId2 == 0) {
            // Delete related note if no more its activities left
            count = deleteActivities(db, ActivityTable.TABLE_NAME + "." + ActivityTable._ID +
                    "=" + activityId, new String[]{}, inTransaction);
        } else {
            // Delete this activity only
            count = db.delete(ActivityTable.TABLE_NAME, BaseColumns._ID + "=" + activityId, null);
            updateNoteFavorited(myContext, origin, noteId);
            updateNoteReblogged(myContext, origin, noteId);
        }
        return count;
    }

    public static void updateNoteReblogged(MyContext myContext, Origin origin, long noteId) {
        TriState reblogged = TriState.fromBoolean(
                myContext.users().containsMe(MyQuery.getRebloggers(myContext.getDatabase(), origin, noteId))
        );
        update(myContext, NoteTable.TABLE_NAME,
                NoteTable.REBLOGGED + "=" + reblogged.id,
                NoteTable._ID + "=" + noteId);
    }

    public static void updateNoteFavorited(@NonNull MyContext myContext, @NonNull Origin origin, long noteId) {
        TriState favorited = TriState.fromBoolean(
                myContext.users().containsMe(MyQuery.getStargazers(myContext.getDatabase(), origin, noteId))
        );
        update(myContext, NoteTable.TABLE_NAME,
                NoteTable.FAVORITED + "=" + favorited.id,
                NoteTable._ID + "=" + noteId);
    }

    public static void clearAllNotifications(@NonNull MyContext myContext) {
        update(myContext, ActivityTable.TABLE_NAME,
                ActivityTable.NEW_NOTIFICATION_EVENT + "=0",
                ActivityTable.NEW_NOTIFICATION_EVENT + "!=0");
    }

    public static void clearNotification(@NonNull MyContext myContext, @NonNull Timeline timeline) {
        final List<Long> ids = NotificationEventType.idsOfShownOn(timeline.getTimelineType());
        if (ids.size() > 0) {
            update(myContext, ActivityTable.TABLE_NAME,
                    ActivityTable.NEW_NOTIFICATION_EVENT + "=0",
                    ActivityTable.NEW_NOTIFICATION_EVENT + SqlIds.fromIds(ids).getSql() +
                            (timeline.actor.isEmpty()
                                    ? ""
                                    : " AND " + ActivityTable.NOTIFIED_ACTOR_ID + "=" + timeline.actor.actorId)
            );
        }
    }

    public static void setUnsentActivityNotification(@NonNull MyContext myContext, long activityId) {
        update(myContext, ActivityTable.TABLE_NAME,
                ActivityTable.NEW_NOTIFICATION_EVENT + "=" + NotificationEventType.OUTBOX.id
                + ", " + ActivityTable.NOTIFIED + "=" + TriState.TRUE.id
                + ", " + ActivityTable.NOTIFIED_ACTOR_ID + "=" + ActivityTable.ACTOR_ID,
                ActivityTable._ID + "=" + activityId);
    }

    public static void update(@NonNull MyContext myContext, @NonNull String tableName, @NonNull String set, String where) {
        final String method = "update";
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.v(MyProvider.TAG, method + "; Database is null");
            return;
        }
        String sql = "UPDATE " + tableName + " SET " + set + (StringUtils.isEmpty(where) ? "" : " WHERE " + where);
        try {
            db.execSQL(sql);
        } catch (Exception e) {
            MyLog.w(TAG, method + "; SQL:'" + sql + "'", e);
        }
    }

    public static long insert(MyContext myContext, String tableName, ContentValues values) {
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null || values.size() == 0) return -1;

        long rowId = db.insert(tableName, null, values);
        if (rowId == -1) {
            throw new SQLException("Failed to insert " + values);
        }
        return rowId;
    }

    /**
     * Insert a new record into the database.
     * 
     * @see android.content.ContentProvider#insert(android.net.Uri,
     *      android.content.ContentValues)
     */
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues initialValues) {

        ContentValues values;
        long accountActorId = 0;
        
        long rowId;
        Uri newUri = null;
        try {
            SQLiteDatabase db = MyContextHolder.get().getDatabase();
            if (db == null) {
                MyLog.v(this, "insert; Database is null");
                return null;
            }

            String table;

            if (initialValues != null) {
                values = new ContentValues(initialValues);
            } else {
                values = new ContentValues();
            }

            ParsedUri uriParser = ParsedUri.fromUri(uri);
            switch (uriParser.matched()) {
                case NOTE_ITEM:
                    accountActorId = uriParser.getAccountActorId();
                    
                    table = NoteTable.TABLE_NAME;
                    if (!values.containsKey(NoteTable.CONTENT)) {
                        values.put(NoteTable.CONTENT, "");
                    }
                    if (!values.containsKey(NoteTable.VIA)) {
                        values.put(NoteTable.VIA, "");
                    }
                    values.put(NoteTable.INS_DATE, MyLog.uniqueCurrentTimeMS());
                    
                    break;
                    
                case ORIGIN_ITEM:
                    table = OriginTable.TABLE_NAME;
                    break;

                case ACTOR_ITEM:
                    table = ActorTable.TABLE_NAME;
                    values.put(ActorTable.INS_DATE, MyLog.uniqueCurrentTimeMS());
                    accountActorId = uriParser.getAccountActorId();
                    break;
                    
                default:
                    throw new IllegalArgumentException(uriParser.toString());
            }

            rowId = db.insert(table, null, values);
            if (rowId == -1) {
                throw new SQLException("Failed to insert row into " + uri);
            }

            switch (uriParser.matched()) {
                case NOTE_ITEM:
                    newUri = MatchedUri.getMsgUri(accountActorId, rowId);
                    break;
                case ORIGIN_ITEM:
                    newUri = MatchedUri.getOriginUri(rowId);
                    break;
                case ACTOR_ITEM:
                    newUri = MatchedUri.getActorUri(accountActorId, rowId);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
          MyLog.e(this, "Insert " + uri, e);
        }
        return newUri;
    }

    /**
     * Get a cursor to the database
     * 
     * @see android.content.ContentProvider#query(android.net.Uri,
     *      java.lang.String[], java.lang.String, java.lang.String[],
     *      java.lang.String)
     */
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selectionIn, String[] selectionArgsIn,
            String sortOrder) {
        final int PAGE_SIZE = 400;
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        boolean built = false;
        String selection = selectionIn;
        String limit = null;
        String[] selectionArgs = selectionArgsIn; 
        String sql = "";

        ParsedUri uriParser = ParsedUri.fromUri(uri);
        switch (uriParser.matched()) {
            case TIMELINE:
                qb.setDistinct(true);
                qb.setTables(TimelineSql.tablesForTimeline(uri, projection));
                qb.setProjectionMap(ProjectionMap.TIMELINE);
                break;

            case TIMELINE_ITEM:
                qb.setTables(TimelineSql.tablesForTimeline(uri, projection));
                qb.setProjectionMap(ProjectionMap.TIMELINE);
                qb.appendWhere(ProjectionMap.ACTIVITY_TABLE_ALIAS + "."
                        + ActivityTable.NOTE_ID + "=" + uriParser.getNoteId());
                break;

            case TIMELINE_SEARCH:
                qb.setTables(TimelineSql.tablesForTimeline(uri, projection));
                qb.setProjectionMap(ProjectionMap.TIMELINE);
                String rawQuery = uriParser.getSearchQuery();
                if (StringUtils.nonEmpty(rawQuery)) {
                    if (StringUtils.nonEmpty(selection)) {
                        selection = " AND (" + selection + ")";
                    } else {
                        selection = "";
                    }
                    KeywordsFilter searchQuery  = new KeywordsFilter(rawQuery);
                    selection = "(" + searchQuery.getSqlSelection(NoteTable.CONTENT_TO_SEARCH) + ")" + selection;
                    selectionArgs = searchQuery.prependSqlSelectionArgs(selectionArgs);
                }
                break;

            case ACTIVITY:
                qb.setTables(ActivityTable.TABLE_NAME + " AS " + ProjectionMap.ACTIVITY_TABLE_ALIAS);
                qb.setProjectionMap(ProjectionMap.TIMELINE);
                break;

            case ACTOR:
            case ACTORLIST:
            case ACTORLIST_SEARCH:
                qb.setTables(ActorSql.tables());
                qb.setProjectionMap(ActorSql.fullProjectionMap);
                rawQuery = uriParser.getSearchQuery();
                if (StringUtils.nonEmpty(rawQuery)) {
                    if (StringUtils.nonEmpty(selection)) {
                        selection = " AND (" + selection + ")";
                    } else {
                        selection = "";
                    }
                    selection = "(" + ActorTable.WEBFINGER_ID + " LIKE ?" +
                            " OR " + ActorTable.REAL_NAME + " LIKE ? )" + selection;

                    selectionArgs = StringUtils.addBeforeArray(selectionArgs, "%" + rawQuery + "%");
                    selectionArgs = StringUtils.addBeforeArray(selectionArgs, "%" + rawQuery + "%");
                }
                limit =  String.valueOf(PAGE_SIZE);
                break;

            case ACTORLIST_ITEM:
                qb.setTables(ActorSql.tables());
                qb.setProjectionMap(ActorSql.fullProjectionMap);
                qb.appendWhere(BaseColumns._ID + "=" + uriParser.getActorId());
                break;

            case ACTOR_ITEM:
                qb.setTables(ActorTable.TABLE_NAME);
                qb.setProjectionMap(ActorSql.fullProjectionMap);
                qb.appendWhere(BaseColumns._ID + "=" + uriParser.getActorId());
                break;

            default:
                throw new IllegalArgumentException(uriParser.toString());
        }

        // If no sort order is specified use the default
        String orderBy;
        if (StringUtils.isEmpty(sortOrder)) {
            switch (uriParser.matched()) {
                case TIMELINE:
                case TIMELINE_ITEM:
                case TIMELINE_SEARCH:
                    orderBy = ActivityTable.getTimelineSortOrder(uriParser.getTimelineType(), false);
                    break;

                case ACTOR:
                case ACTORLIST:
                case ACTORLIST_ITEM:
                case ACTORLIST_SEARCH:
                case ACTOR_ITEM:
                    orderBy = ActorTable.DEFAULT_SORT_ORDER;
                    break;

                default:
                    throw new IllegalArgumentException(uriParser.toString());
            }
        } else {
            orderBy = sortOrder;
        }

        Cursor c = null;
        if (MyContextHolder.get().isReady()) {
            // Get the database and run the query
            SQLiteDatabase db = MyContextHolder.get().getDatabase();
            boolean logQuery = MyLog.isDebugEnabled();
            try {
                if (sql.length() == 0) {
                    sql = qb.buildQuery(projection, selection, null, null, orderBy, limit);
                    built = true;
                }
                // Here we substitute ?-s in selection with values from selectionArgs
                c = db.rawQuery(sql, selectionArgs);
                if (c == null) {
                    MyLog.e(this, "Null cursor returned");
                    logQuery = true;
                }
            } catch (Exception e) {
                logQuery = true;
                MyLog.e(this, "Database query failed", e);
            }

            if (logQuery) {
                String msg = "query, SQL=\"" + sql + "\"";
                if (selectionArgs != null && selectionArgs.length > 0) {
                    msg += "; selectionArgs=" + Arrays.toString(selectionArgs);
                }
                MyLog.d(this, msg);
                if (built && MyLog.isVerboseEnabled()) {
                    msg = "uri=" + uri + "; projection=" + Arrays.toString(projection)
                    + "; selection=" + selection + "; sortOrder=" + sortOrder
                    + "; qb.getTables=" + qb.getTables() + "; orderBy=" + orderBy;
                    MyLog.v(this, msg);
                }
            }
        }

        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    /**
     * Update objects (one or several records) in the database
     */
    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(this, "update; Database is null");
            return 0;
        }
        int count = 0;
        ParsedUri uriParser = ParsedUri.fromUri(uri);
        switch (uriParser.matched()) {
            case ACTIVITY:
                count = db.update(NoteTable.TABLE_NAME, values, selection, selectionArgs);
                break;

            case NOTE_ITEM:
                long rowId = uriParser.getNoteId();
                if (values.size() > 0) {
                    count = db.update(NoteTable.TABLE_NAME, values, BaseColumns._ID + "=" + rowId
                            + (StringUtils.nonEmpty(selection) ? " AND (" + selection + ')' : ""),
                            selectionArgs);
                }
                break;

            case ACTOR:
                count = db.update(ActorTable.TABLE_NAME, values, selection, selectionArgs);
                break;

            case ACTOR_ITEM:
                long selectedActorId = uriParser.getActorId();
                if (values.size() > 0) {
                    count = db.update(ActorTable.TABLE_NAME, values, BaseColumns._ID + "=" + selectedActorId
                                    + (StringUtils.nonEmpty(selection) ? " AND (" + selection + ')' : ""),
                            selectionArgs);
                }
                break;

            default:
                throw new IllegalArgumentException(uriParser.toString());
        }

        return count;
    }
}
