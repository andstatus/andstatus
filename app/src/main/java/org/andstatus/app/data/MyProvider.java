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
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;

import androidx.annotation.NonNull;

import org.andstatus.app.actor.ActorListType;
import org.andstatus.app.actor.GroupType;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.ActorEndpointTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.AudienceTable;
import org.andstatus.app.database.table.GroupMembersTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.database.table.OriginTable;
import org.andstatus.app.note.KeywordsFilter;
import org.andstatus.app.notification.NotificationEventType;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

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
        myContextHolder.initialize(getContext(), this, false);
        return true;
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

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        throw new IllegalArgumentException("Delete method is not implemented " + uri.toString());
    }

    /** @return Number of deleted activities of this note */
    public static int deleteNoteAndItsActivities(MyContext context, long noteId) {
        if (context == null || noteId == 0) return 0;
        return deleteActivities(context.getDatabase(), ActivityTable.NOTE_ID + "=" + noteId, null, true);
    }

    public static int deleteActivities(SQLiteDatabase db, String selection, String[] selectionArgs, boolean inTransaction) {
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

    public static void deleteActor(MyContext myContext, long actorIdToDelete) {
        deleteActor(myContext, actorIdToDelete, 0);
    }

    private static void deleteActor(MyContext myContext, long actorId, long recursionLevel) {
        if (recursionLevel < 3) {
            MyQuery.foldLeft(myContext, "SELECT " + ActorTable._ID + " FROM " +
                            ActorTable.TABLE_NAME + " WHERE " + ActorTable.PARENT_ACTOR_ID + "=" + actorId,
                    new ArrayList<Long>(),
                    id -> cursor -> {
                        id.add(DbUtils.getLong(cursor, ActorTable._ID));
                        return id;
                    }
            ).forEach(childActorId -> deleteActor(myContext, childActorId, recursionLevel + 1));
        }

        delete(myContext, AudienceTable.TABLE_NAME, AudienceTable.ACTOR_ID, actorId);
        delete(myContext, GroupMembersTable.TABLE_NAME, GroupMembersTable.GROUP_ID, actorId);
        delete(myContext, GroupMembersTable.TABLE_NAME, GroupMembersTable.MEMBER_ID, actorId);
        DownloadData.deleteAllOfThisActor(myContext, actorId);
        delete(myContext, ActorEndpointTable.TABLE_NAME, ActorEndpointTable.ACTOR_ID, actorId);
        delete(myContext, ActorTable.TABLE_NAME, ActorTable._ID, actorId);
    }

    public static void delete(@NonNull MyContext myContext, @NonNull String tableName, @NonNull String column, Object value) {
        if (value == null) return;
        delete(myContext, tableName, column + "=" + value);
    }

    public static void delete(@NonNull MyContext myContext, @NonNull String tableName, String where) {
        final String method = "delete";
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.databaseIsNull(() -> method);
            return;
        }
        try {
            db.delete(tableName, where, null);
        } catch (Exception e) {
            MyLog.w(TAG, method + "; table:'" + tableName + "', where:'" + where + "'", e);
        }
    }

    // TODO: return Try<Long>
    public static long deleteActivity(MyContext myContext, long activityId, long noteId, boolean inTransaction) {
        SQLiteDatabase db = myContextHolder.getNow().getDatabase();
        if (db == null) {
            MyLog.databaseIsNull(() -> "deleteActivity");
            return 0;
        }
        long originId = MyQuery.activityIdToLongColumnValue(ActivityTable.ORIGIN_ID, activityId);
        if (originId == 0) return 0;
        Origin origin = myContextHolder.getNow().origins().fromId(originId);
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
        update(myContext, ActivityTable.TABLE_NAME,
                ActivityTable.NEW_NOTIFICATION_EVENT + "=0",
                (timeline.actor.isEmpty()
                                ? ""
                                : ActivityTable.NOTIFIED_ACTOR_ID + "=" + timeline.actor.actorId)
        );
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
            MyLog.databaseIsNull(() -> method);
            return;
        }
        String sql = "UPDATE " + tableName + " SET " + set + (StringUtil.isEmpty(where) ? "" : " WHERE " + where);
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
            SQLiteDatabase db = myContextHolder.getNow().getDatabase();
            if (db == null) {
                MyLog.databaseIsNull(() -> "insert");
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
            String sortOrderIn) {
        final int PAGE_SIZE = 400;
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        boolean built = false;
        final List<String> tables;
        final String where;
        final String selection;
        String[] selectionArgs = selectionArgsIn == null ? new String[]{} : selectionArgsIn;
        String[] selectionArgs2 = new String[]{};
        String limit = null;
        String sql = "";

        ParsedUri uriParser = ParsedUri.fromUri(uri);
        switch (uriParser.matched()) {
            case TIMELINE:
                qb.setDistinct(true);
                tables = TimelineSql.tablesForTimeline(uri, projection);
                qb.setProjectionMap(ProjectionMap.TIMELINE);
                selection = selectionIn;
                where = "";
                break;

            case TIMELINE_ITEM:
                tables = TimelineSql.tablesForTimeline(uri, projection);
                qb.setProjectionMap(ProjectionMap.TIMELINE);
                selection = selectionIn;
                where = ProjectionMap.ACTIVITY_TABLE_ALIAS + "."
                        + ActivityTable.NOTE_ID + "=" + uriParser.getNoteId();
                break;

            case TIMELINE_SEARCH:
                tables = TimelineSql.tablesForTimeline(uri, projection);
                qb.setProjectionMap(ProjectionMap.TIMELINE);
                String rawQuery = uriParser.getSearchQuery();
                if (StringUtil.nonEmpty(rawQuery)) {
                    KeywordsFilter searchQuery  = new KeywordsFilter(rawQuery);
                    selection = "(" + searchQuery.getSqlSelection(NoteTable.CONTENT_TO_SEARCH) + ")" +
                            (StringUtil.nonEmpty(selectionIn)
                                ? " AND (" + selectionIn + ")"
                                : "");
                    selectionArgs = searchQuery.prependSqlSelectionArgs(selectionArgs);
                } else {
                    selection = selectionIn;
                }
                where = "";
                break;

            case ACTIVITY:
                tables = Collections.singletonList(ActivityTable.TABLE_NAME + " AS " + ProjectionMap.ACTIVITY_TABLE_ALIAS);
                qb.setProjectionMap(ProjectionMap.TIMELINE);
                selection = selectionIn;
                where = "";
                break;

            case ACTOR:
            case ACTORLIST:
            case ACTORLIST_SEARCH:
                tables = Collections.singletonList(ActorSql.allTables());
                qb.setProjectionMap(ActorSql.fullProjectionMap);
                rawQuery = uriParser.getSearchQuery();
                SqlWhere actorWhere = new SqlWhere().append(selectionIn);
                if (uriParser.getActorListType() == ActorListType.GROUPS_AT_ORIGIN) {
                    actorWhere.append(ActorTable.GROUP_TYPE +
                        SqlIds.fromIds(GroupType.GENERIC.id, GroupType.ACTOR_OWNED.id).getSql());
                }
                if (StringUtil.nonEmpty(rawQuery)) {
                    actorWhere.append(ActorTable.WEBFINGER_ID + " LIKE ?" +
                        " OR " + ActorTable.REAL_NAME + " LIKE ?" +
                        " OR " + ActorTable.USERNAME + " LIKE ?");
                    selectionArgs = StringUtil.addBeforeArray(selectionArgs, "%" + rawQuery + "%");
                    selectionArgs = StringUtil.addBeforeArray(selectionArgs, "%" + rawQuery + "%");
                    selectionArgs = StringUtil.addBeforeArray(selectionArgs, "%" + rawQuery + "%");
                }
                selection = actorWhere.getCondition();
                where = "";
                limit =  String.valueOf(PAGE_SIZE);
                break;

            case ACTORLIST_ITEM:
                tables = Collections.singletonList(ActorSql.allTables());
                qb.setProjectionMap(ActorSql.fullProjectionMap);
                selection = selectionIn;
                where = BaseColumns._ID + "=" + uriParser.getActorId();
                break;

            case ACTOR_ITEM:
                tables = Collections.singletonList(ActorTable.TABLE_NAME);
                qb.setProjectionMap(ActorSql.fullProjectionMap);
                selection = selectionIn;
                where = BaseColumns._ID + "=" + uriParser.getActorId();
                break;

            default:
                throw new IllegalArgumentException(uriParser.toString());
        }

        // If no sort order is specified use the default
        final String sortOrder;
        if (StringUtil.isEmpty(sortOrderIn)) {
            switch (uriParser.matched()) {
                case TIMELINE:
                case TIMELINE_ITEM:
                case TIMELINE_SEARCH:
                    sortOrder = ActivityTable.getTimelineSortOrder(uriParser.getTimelineType(), false);
                    break;

                case ACTOR:
                case ACTORLIST:
                case ACTORLIST_ITEM:
                case ACTORLIST_SEARCH:
                case ACTOR_ITEM:
                    sortOrder = ActorTable.DEFAULT_SORT_ORDER;
                    break;

                default:
                    throw new IllegalArgumentException(uriParser.toString());
            }
        } else {
            sortOrder = sortOrderIn;
        }

        Cursor c = null;
        if (myContextHolder.getNow().isReady()) {
            // Get the database and run the query
            SQLiteDatabase db = myContextHolder.getNow().getDatabase();
            boolean logQuery = MyLog.isDebugEnabled();
            try {
                if (StringUtil.nonEmpty(where)) {
                    qb.appendWhere(where);
                }
                if (sql.length() == 0) {
                    if (tables.size() == 1) {
                        qb.setTables(tables.get(0));
                        sql = qb.buildQuery(projection, selection, null, null, sortOrder, limit);
                        selectionArgs2 = selectionArgs;
                    } else {
                        String[] subQueries = tables.stream().map(str -> {
                            qb.setTables(str);
                            return qb.buildQuery(projection, selection, null, null, null, null);
                        }).collect(Collectors.toList()).toArray(new String[]{});
                        for (int ind = 0; ind < subQueries.length; ind++) {
                            // Concatenate two arrays
                            selectionArgs2 = Stream.of(selectionArgs2, selectionArgs)
                                    .flatMap(Stream::of)
                                    .toArray(String[]::new);
                        }
                        qb.setDistinct(true);
                        sql = qb.buildUnionQuery(subQueries, sortOrder, limit);
                    }
                    built = true;
                }
                // Here we substitute ?-s in selection with values from selectionArgs
                c = db.rawQuery(sql, selectionArgs2);
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
                if (selectionArgs2.length > 0) {
                    msg += "; selectionArgs=" + Arrays.toString(selectionArgs2);
                }
                MyLog.d(this, msg);
                if (built && MyLog.isVerboseEnabled()) {
                    msg = "uri=" + uri + "; projection=" + Arrays.toString(projection)
                    + "; selection=" + selection + "; sortOrder=" + sortOrderIn
                    + "; qb.getTables=" + qb.getTables() + "; orderBy=" + sortOrder;
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
        SQLiteDatabase db = myContextHolder.getNow().getDatabase();
        if (db == null) {
            MyLog.databaseIsNull(() -> "update");
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
                            + (StringUtil.nonEmpty(selection) ? " AND (" + selection + ')' : ""),
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
                                    + (StringUtil.nonEmpty(selection) ? " AND (" + selection + ')' : ""),
                            selectionArgs);
                }
                break;

            default:
                throw new IllegalArgumentException(uriParser.toString());
        }

        return count;
    }
}
