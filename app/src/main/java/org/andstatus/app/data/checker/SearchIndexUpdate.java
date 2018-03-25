/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data.checker;

import android.database.Cursor;

import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;

import static org.andstatus.app.data.MyQuery.quoteIfNotQuoted;

/**
 * @author yvolk@yurivolkov.com
 */
class SearchIndexUpdate extends DataChecker {

    @Override
    boolean notLong() {
        return false;
    }

    @Override
    long fixInternal(boolean countOnly) {
        String sql = "SELECT " + NoteTable._ID
                + ", " + NoteTable.CONTENT
                + ", " + NoteTable.CONTENT_TO_SEARCH
                + " FROM " + NoteTable.TABLE_NAME
                ;
        long rowsCount = 0;
        long changedCount = 0;
        try (Cursor c = myContext.getDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                rowsCount++;
                long id = c.getLong(0);
                String body = c.getString(1);
                String bodyToSearch = c.getString(2);
                String bodyToSearchExpected = MyHtml.getContentToSearch(body);
                if (!bodyToSearchExpected.equals(bodyToSearch)) {
                    changedCount++;
                    MyLog.i(this, "Wrong body to search for " + id + ": " + quoteIfNotQuoted(body));
                    sql = "UPDATE " + NoteTable.TABLE_NAME
                            + " SET "
                            + NoteTable.CONTENT_TO_SEARCH + "=" + quoteIfNotQuoted(bodyToSearchExpected)
                            + " WHERE " + NoteTable._ID + "=" + id;
                    myContext.getDatabase().execSQL(sql);
                }
                if (logger.loggedMoreSecondsAgoThan(PROGRESS_REPORT_PERIOD_SECONDS)) {
                    logger.logProgress("Updating search index"
                            + (changedCount == 0 ? ". " : ", changed " + changedCount + " of ")
                            + rowsCount + " notes"
                    );
                    MyServiceManager.setServiceUnavailable();
                }
            }
        } catch (Exception e) {
            String logMsg = "Error: " + e.getMessage() + ", SQL:" + sql;
            logger.logProgress(logMsg);
            MyLog.e(this, logMsg, e);
        }
        logger.logProgress(changedCount == 0
                ? "No changes to search index were needed. " + rowsCount + " notes"
                : "Changed search index for " + changedCount + " of " + rowsCount + " notes");
        return changedCount;
    }

}
