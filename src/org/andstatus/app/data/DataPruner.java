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
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;

import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SelectionAndArgs;

/**
 * Clean database from outdated information
 * currently only old Messages are being deleted 
 */
public class DataPruner {
    private static final String TAG = DataPruner.class.getSimpleName();

    private ContentResolver mContentResolver;
    private int mDeleted = 0;
    
    public DataPruner(Context context) {
        mContentResolver = context.getContentResolver();
    }

    /**
     * Do prune the data!

     * Remove old records to ensure that the database does not grow too large.
     * Maximum number of records is configured in "history_size" preference
     * @return true if succeeded
     */
    public boolean prune() {
        boolean ok = true;
       
        mDeleted = 0;
        int nDeletedTime = 0;
        // We're using global preferences here
        SharedPreferences sp = MyPreferences
                .getDefaultSharedPreferences();

        // Don't delete messages which are favorited by any user
        String sqlNotFavorited = "NOT EXISTS ("
                + "SELECT * FROM " + MyDatabase.MSGOFUSER_TABLE_NAME + " AS gnf WHERE "
                + MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg._ID + "=gnf." + MyDatabase.MsgOfUser.MSG_ID
                + " AND gnf." + MyDatabase.MsgOfUser.FAVORITED + "=1" 
                + ")";
        
        int maxDays = Integer.parseInt(sp.getString(MyPreferences.KEY_HISTORY_TIME, "3"));
        long sinceTimestamp = 0;

        int nTweets = 0;
        int nToDeleteSize = 0;
        int nDeletedSize = 0;
        int maxSize = Integer.parseInt(sp.getString(MyPreferences.KEY_HISTORY_SIZE, "2000"));
        long sinceTimestampSize = 0;
        try {
            if (maxDays > 0) {
                sinceTimestamp = System.currentTimeMillis() - maxDays * (1000L * 60 * 60 * 24);
                SelectionAndArgs sa = new SelectionAndArgs();
                sa.addSelection(MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg.INS_DATE + " <  ?", new String[] {
                    String.valueOf(sinceTimestamp)
                });
                sa.selection += " AND " + sqlNotFavorited;
                nDeletedTime = mContentResolver.delete(MyDatabase.Msg.CONTENT_URI, sa.selection, sa.selectionArgs);
            }

            if (maxSize > 0) {
                nDeletedSize = 0;
                Cursor cursor = mContentResolver.query(MyDatabase.Msg.CONTENT_COUNT_URI, null, null, null, null);
                if (cursor.moveToFirst()) {
                    // Count is in the first column
                    nTweets = cursor.getInt(0);
                    nToDeleteSize = nTweets - maxSize;
                }
                cursor.close();
                if (nToDeleteSize > 0) {
                    // Find INS_DATE of the most recent tweet to delete
                    cursor = mContentResolver.query(MyDatabase.Msg.CONTENT_URI, new String[] {
                            MyDatabase.Msg.INS_DATE
                    }, null, null, MyDatabase.Msg.INS_DATE + " ASC LIMIT 0," + nToDeleteSize);
                    if (cursor.moveToLast()) {
                        sinceTimestampSize = cursor.getLong(0);
                    }
                    cursor.close();
                    if (sinceTimestampSize > 0) {
                        SelectionAndArgs sa = new SelectionAndArgs();
                        sa.addSelection(MyDatabase.MSG_TABLE_NAME + "." + MyDatabase.Msg.INS_DATE + " <=  ?", new String[] {
                            String.valueOf(sinceTimestampSize)
                        });
                        sa.selection += " AND " + sqlNotFavorited;
                        nDeletedSize = mContentResolver.delete(MyDatabase.Msg.CONTENT_URI, sa.selection,
                                sa.selectionArgs);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "pruneOldRecords failed");
            e.printStackTrace();
        }
        mDeleted = nDeletedTime + nDeletedSize;
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG,
                    "pruneOldRecords; History time=" + maxDays + " days; deleted " + nDeletedTime
                            + " , since " + sinceTimestamp + ", now=" + System.currentTimeMillis());
            Log.v(TAG, "pruneOldRecords; History size=" + maxSize + " messages; deleted "
                    + nDeletedSize + " of " + nTweets + " messages, since " + sinceTimestampSize);
        }
        
        return ok;
    }

    /**
     * @return number of Messages deleted
     */
    public int getDeleted() {
        return mDeleted;
    }
}
