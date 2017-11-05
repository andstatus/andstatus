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

package org.andstatus.app.data;

import android.database.Cursor;

import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;

import static org.andstatus.app.data.MyQuery.quoteIfNotQuoted;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyDataCheckerSearchIndex {
    private static final int PROGRESS_REPORT_PERIOD_SECONDS = 20;
    private final MyContext myContext;
    private final ProgressLogger logger;

    public MyDataCheckerSearchIndex(MyContext myContext, ProgressLogger logger) {
        this.myContext = myContext;
        this.logger = logger;
    }

    public void fixData() {
        logger.logProgress("Search index update started");
        String sql = "SELECT " + MsgTable._ID
                + ", " + MsgTable.BODY
                + ", " + MsgTable.BODY_TO_SEARCH
                + " FROM " + MsgTable.TABLE_NAME
                ;
        long rowsCount = 0;
        long changedCount = 0;
        try (Cursor c = myContext.getDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                rowsCount++;
                long id = c.getLong(0);
                String body = c.getString(1);
                String bodyToSearch = c.getString(2);
                String bodyToSearchExpected = MyHtml.getBodyToSearch(body);
                if (!bodyToSearchExpected.equals(bodyToSearch)) {
                    changedCount++;
                    MyLog.i(this, "Wrong body to search for " + id + ": " + quoteIfNotQuoted(body));
                    sql = "UPDATE " + MsgTable.TABLE_NAME
                            + " SET "
                            + MsgTable.BODY_TO_SEARCH + "=" + quoteIfNotQuoted(bodyToSearchExpected)
                            + " WHERE " + MsgTable._ID + "=" + id;
                    myContext.getDatabase().execSQL(sql);
                }
                if (logger.loggedMoreSecondsAgoThan(PROGRESS_REPORT_PERIOD_SECONDS)) {
                    logger.logProgress("Updating search index"
                            + (changedCount == 0 ? ". " : ", changed " + changedCount + " of ")
                            + rowsCount + " messages"
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
                ? "No changes to search index were needed. " + rowsCount + " messages"
                : "Changed search index for " + changedCount + " of " + rowsCount + " messages");
        DbUtils.waitMs(this, changedCount == 0 ? 1000 : 3000);
    }

}
