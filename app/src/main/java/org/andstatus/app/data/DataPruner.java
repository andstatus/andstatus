/*
 * Copyright (C) 2013-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.timeline.meta.DisplayedInSelector;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SelectionAndArgs;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.io.File;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Clean database from outdated information
 * old Notes, log files...
 */
public class DataPruner {
    public static final long ATTACHMENTS_TO_STORE_MIN = 5;
    @NonNull
    private final MyContext myContext;
    @NonNull
    private final SQLiteDatabase db;
    private final ContentResolver mContentResolver;
    private long mDeleted = 0;
    static final long MAX_DAYS_LOGS_TO_KEEP = 10;
    static final long MAX_DAYS_UNUSED_TIMELINES_TO_KEEP = 31;
    private static final long PRUNE_MIN_PERIOD_DAYS = 1;
    private static final double ATTACHMENTS_SIZE_PART = 0.90;

    public static void prune(@NonNull MyContext myContext) {
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.d(DataPruner.class, "Database is null");
        } else {
            new DataPruner(myContext, db).prune();
        }
    }

    public DataPruner(@NonNull MyContext myContext, @NonNull SQLiteDatabase db) {
        this.myContext = myContext;
        this.db = db;
        mContentResolver = myContext.context().getContentResolver();
    }

    /**
     * @return true if done successfully, false if skipped or an error
     */
    public boolean prune() {
        final String method = "prune";
        boolean pruned = false;
        if (!isTimeToPrune()) {
            return pruned;
        }
        MyLog.v(this, () -> method + " started");

        mDeleted = 0;
        int nDeletedTime = 0;
        // We're using global preferences here
        SharedPreferences sp = SharedPreferencesUtil.getDefaultSharedPreferences();

        // Don't delete my activities
        final SqlIds accountIds = SqlIds.fromIds(MyContextHolder.get().accounts().get().stream()
                .map(MyAccount::getActorId).collect(Collectors.toList()));
        String sqlNotMyActivity = ActivityTable.TABLE_NAME + "." + ActivityTable.ACTOR_ID + accountIds.getNotSql();
        String sqlNotLatestActivityByActor = ActivityTable.TABLE_NAME + "." + ActivityTable._ID + " NOT IN("
                + " SELECT " + ActorTable.ACTOR_ACTIVITY_ID + " FROM " + ActorTable.TABLE_NAME + ")";

        long maxDays = Integer.parseInt(sp.getString(MyPreferences.KEY_HISTORY_TIME, "3"));
        long latestTimestamp = getLatestTimestamp(maxDays);

        long nActivities = 0;
        long nToDeleteSize = 0;
        long nDeletedSize = 0;
        long maxSize = Integer.parseInt(sp.getString(MyPreferences.KEY_HISTORY_SIZE, "2000"));
        long latestTimestampSize = 0;
        Cursor cursor = null;
        try {
            if (maxDays > 0) {
                SelectionAndArgs sa = new SelectionAndArgs();
                sa.addSelection(ActivityTable.TABLE_NAME + "." + ActivityTable.INS_DATE + " <  ?",
                        Long.toString(latestTimestamp));
                sa.addSelection(sqlNotMyActivity);
                sa.addSelection(sqlNotLatestActivityByActor);
                nDeletedTime = mContentResolver.delete(MatchedUri.ACTIVITY_CONTENT_URI, sa.selection, sa.selectionArgs);
            }

            if (maxSize > 0) {
                nActivities = MyQuery.getCountOfActivities("");
                nToDeleteSize = nActivities - maxSize;
                if (nToDeleteSize > 0) {
                    // Find INS_DATE of the most recent tweet to delete
                    cursor = mContentResolver.query(MatchedUri.ACTIVITY_CONTENT_URI, new String[] {
                            ActivityTable.INS_DATE
                    }, null, null, ActivityTable.INS_DATE + " ASC LIMIT 0," + nToDeleteSize);
                    if (cursor.moveToLast()) {
                        latestTimestampSize = cursor.getLong(0);
                    }
                    cursor.close();
                    if (latestTimestampSize > 0) {
                        SelectionAndArgs sa = new SelectionAndArgs();
                        sa.addSelection(ActivityTable.TABLE_NAME + "." + ActivityTable.INS_DATE + " <=  ?",
                                Long.toString(latestTimestampSize));
                        sa.addSelection(sqlNotMyActivity);
                        sa.addSelection(sqlNotLatestActivityByActor);
                        nDeletedSize = mContentResolver.delete(MatchedUri.ACTIVITY_CONTENT_URI, sa.selection,
                                sa.selectionArgs);
                    }
                }
            }
            pruned = true;
        } catch (Exception e) {
            MyLog.i(this, method + " failed", e);
        } finally {
            DbUtils.closeSilently(cursor);
        }
        mDeleted = nDeletedTime + nDeletedSize;
        if (mDeleted > 0) {
            pruneParentlessAttachments();
        }
        pruneMedia();
        pruneTimelines(Long.max(latestTimestamp, getLatestTimestamp(MAX_DAYS_UNUSED_TIMELINES_TO_KEEP)));
        pruneLogs(MAX_DAYS_LOGS_TO_KEEP);
        setDataPrunedNow();
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this,
                    method + " " + (pruned ? "succeeded" : "failed") + "; History time=" + maxDays + " days; deleted " + nDeletedTime
                    + " , before " + new Date(latestTimestamp).toString());
            MyLog.v(this, method + "; History size=" + maxSize + " notes; deleted "
                    + nDeletedSize + " of " + nActivities + " notes, before " + new Date(latestTimestampSize).toString());
        }
        return pruned;
    }

    long pruneMedia() {
        long dirSize = DownloadFile.getDirSize();
        long maxSize = MyPreferences.getMaximumSizeOfCachedMediaBytes();
        final long bytesToPrune = dirSize - maxSize;
        long bytesToPruneMin = ATTACHMENTS_TO_STORE_MIN * MyPreferences.getMaximumSizeOfAttachmentBytes();
        MyLog.i(this, "Size of media files: " + I18n.formatBytes(dirSize)
        + (bytesToPrune > bytesToPruneMin
                        ? " exceeds"
                        : " less than")
                + " maximum: " + I18n.formatBytes(maxSize) + " + min to prune: " + I18n.formatBytes(bytesToPruneMin)
        );
        if (bytesToPrune < bytesToPruneMin) return 0;

        DownloadData.ConsumedSummary pruned1 = DownloadData.pruneFiles(myContext, DownloadType.ATTACHMENT,
                Math.round(maxSize * ATTACHMENTS_SIZE_PART));

        MyLog.i(this, "Pruned " + pruned1.consumedCount + " attachment files, "
                + I18n.formatBytes(pruned1.consumedSize));
        DownloadData.ConsumedSummary pruned2 = DownloadData.pruneFiles(myContext, DownloadType.AVATAR,
                Math.round(maxSize * (1 - ATTACHMENTS_SIZE_PART)));
        MyLog.i(this, "Pruned " + pruned2.consumedCount + " avatar files, "
                + I18n.formatBytes(pruned2.consumedSize));
        return pruned1.consumedCount + pruned2.consumedCount;
    }

    public static long getLatestTimestamp(long maxDays) {
        return maxDays <=0 ? 0 : System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(maxDays);
    }

    long pruneParentlessAttachments() {
        final String method = "pruneParentlessAttachments";
        String sql = "SELECT DISTINCT " + DownloadTable.NOTE_ID + " FROM " + DownloadTable.TABLE_NAME
                + " WHERE " + DownloadTable.NOTE_ID + " <> 0"
                + " AND NOT EXISTS (" 
                + "SELECT * FROM " + NoteTable.TABLE_NAME
                + " WHERE " + NoteTable.TABLE_NAME + "." + NoteTable._ID + "=" + DownloadTable.NOTE_ID
                + ")";
        long nDeleted = 0;
        for (Long noteId : MyQuery.getLongs(myContext, sql)) {
            DownloadData.deleteAllOfThisNote(db, noteId);
            nDeleted++;
        }
        if (nDeleted > 0 && MyLog.isVerboseEnabled()) {
            MyLog.v(this, method + "; Attachments deleted for " + nDeleted + " notes");
        }
        return nDeleted;
    }

    private void pruneTimelines(long latestTimestamp) {
        myContext.timelines().stream().filter(t -> !t.isRequired()
                && t.isDisplayedInSelector() == DisplayedInSelector.NEVER
                && t.getLastChangedDate() < latestTimestamp).forEach(t -> t.delete(myContext));
    }

    public static void setDataPrunedNow() {
        SharedPreferencesUtil.putLong(MyPreferences.KEY_DATA_PRUNED_DATE, System.currentTimeMillis());
    }

    private boolean isTimeToPrune()	{
        return !myContext.isInForeground() && RelativeTime.moreSecondsAgoThan(
                SharedPreferencesUtil.getLong(MyPreferences.KEY_DATA_PRUNED_DATE),
                TimeUnit.DAYS.toSeconds(PRUNE_MIN_PERIOD_DAYS));
    }

    long pruneLogs(long maxDaysToKeep) {
        final String method = "pruneLogs";
        long latestTimestamp = getLatestTimestamp(maxDaysToKeep);
        long deletedCount = 0;
        File dir = MyLog.getLogDir(true);
        if (dir == null) {
            return deletedCount;
        }
        long errorCount = 0;
        long skippedCount = 0;
        for (String filename : dir.list()) {
            File file = new File(dir, filename);
            if (file.isFile() && (file.lastModified() < latestTimestamp)) {
                if (file.delete()) {
                    deletedCount++;
                    if (deletedCount < 10 && MyLog.isVerboseEnabled()) {
                        MyLog.v(this, method + "; deleted: " + file.getName());
                    }
                } else {
                    errorCount++;
                    if (errorCount < 10 && MyLog.isVerboseEnabled()) {
                        MyLog.v(this, method + "; couldn't delete: " + file.getAbsolutePath());
                    }
                }
            } else {
                skippedCount++;
                if (skippedCount < 10 && MyLog.isVerboseEnabled()) {
                    MyLog.v(this, method + "; skipped: " + file.getName() + ", modified " + new Date(file.lastModified()).toString());
                }
            }
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this,
                    method + "; deleted " + deletedCount
                    + " files, before " + new Date(latestTimestamp).toString()
                    + ", skipped " + skippedCount + ", couldn't delete " + errorCount);
        }
        return deletedCount;
    }

    /**
     * @return number of notes deleted
     */
    public long getDeleted() {
        return mDeleted;
    }
}
