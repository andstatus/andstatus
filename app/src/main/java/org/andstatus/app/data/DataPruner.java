/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SelectionAndArgs;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Clean database from outdated information
 * old Messages, log files...
 */
public class DataPruner {
    private MyContext mMyContext;
    private ContentResolver mContentResolver;
    private long mDeleted = 0;
    static final long MAX_DAYS_LOGS_TO_KEEP = 10;
    private static final long PRUNE_MIN_PERIOD_DAYS = 1;

    public DataPruner(MyContext myContext) {
        mMyContext = myContext;
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
        MyLog.v(this, method + " started");

        mDeleted = 0;
        int nDeletedTime = 0;
        // We're using global preferences here
        SharedPreferences sp = SharedPreferencesUtil.getDefaultSharedPreferences();

        // Don't delete my activities
        final SqlUserIds accountIds = SqlUserIds.fromIds(MyContextHolder.get().persistentAccounts().list().stream()
                .map(MyAccount::getUserId).collect(Collectors.toList()));
        String sqlNotMyActivity = ActivityTable.TABLE_NAME + "." + ActivityTable.ACTOR_ID + accountIds.getNotSql();
        String sqlNotLatestActivityByUser = ActivityTable.TABLE_NAME + "." + ActivityTable._ID + " NOT IN("
                + " SELECT " + UserTable.USER_ACTIVITY_ID + " FROM " + UserTable.TABLE_NAME + ")";

        long maxDays = Integer.parseInt(sp.getString(MyPreferences.KEY_HISTORY_TIME, "3"));
        long latestTimestamp = 0;

        long nActivities = 0;
        long nToDeleteSize = 0;
        long nDeletedSize = 0;
        long maxSize = Integer.parseInt(sp.getString(MyPreferences.KEY_HISTORY_SIZE, "2000"));
        long latestTimestampSize = 0;
        Cursor cursor = null;
        try {
            if (maxDays > 0) {
                latestTimestamp = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(maxDays);
                SelectionAndArgs sa = new SelectionAndArgs();
                sa.addSelection(ActivityTable.TABLE_NAME + "." + ActivityTable.INS_DATE + " <  ?",
                        Long.toString(latestTimestamp));
                sa.addSelection(sqlNotMyActivity);
                sa.addSelection(sqlNotLatestActivityByUser);
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
                        sa.addSelection(sqlNotLatestActivityByUser);
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
            pruneAttachments();
        }
        pruneLogs(MAX_DAYS_LOGS_TO_KEEP);
        setDataPrunedNow();
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this,
                    method + " " + (pruned ? "succeeded" : "failed") + "; History time=" + maxDays + " days; deleted " + nDeletedTime
                    + " , before " + new Date(latestTimestamp).toString());
            MyLog.v(this, method + "; History size=" + maxSize + " messages; deleted "
                    + nDeletedSize + " of " + nActivities + " messages, before " + new Date(latestTimestampSize).toString());
        }
        return pruned;
    }

    long pruneAttachments() {
        final String method = "pruneAttachments";
        String sql = "SELECT DISTINCT " + DownloadTable.MSG_ID + " FROM " + DownloadTable.TABLE_NAME
                + " WHERE " + DownloadTable.MSG_ID + " NOT NULL"
                + " AND NOT EXISTS (" 
                + "SELECT * FROM " + MsgTable.TABLE_NAME
                + " WHERE " + MsgTable.TABLE_NAME + "." + MsgTable._ID + "=" + DownloadTable.MSG_ID
                + ")";
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(this, method + "; Database is null");
            return 0;
        }
        long nDeleted = 0;
        List<Long> list = new ArrayList<Long>();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, null);
            while (cursor.moveToNext()) {
                list.add(cursor.getLong(0));
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
        for (Long msgId : list) {
            DownloadData.deleteAllOfThisMsg(db, msgId);
            nDeleted++;
        }
        if (nDeleted > 0) {
            MyLog.v(this, method + "; Attachments deleted for " + nDeleted + " messages");
        }
        return nDeleted;
    }

    public static void setDataPrunedNow() {
        SharedPreferencesUtil.putLong(MyPreferences.KEY_DATA_PRUNED_DATE, System.currentTimeMillis());
    }

    private boolean isTimeToPrune()	{
        return !mMyContext.isInForeground() && RelativeTime.moreSecondsAgoThan(
                SharedPreferencesUtil.getLong(MyPreferences.KEY_DATA_PRUNED_DATE),
                TimeUnit.DAYS.toSeconds(PRUNE_MIN_PERIOD_DAYS));
    }

    long pruneLogs(long maxDaysToKeep) {
        final String method = "pruneLogs";
        long latestTimestamp = System.currentTimeMillis() 
                - java.util.concurrent.TimeUnit.DAYS.toMillis(maxDaysToKeep);
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
     * @return number of Messages deleted
     */
    public long getDeleted() {
        return mDeleted;
    }
}
