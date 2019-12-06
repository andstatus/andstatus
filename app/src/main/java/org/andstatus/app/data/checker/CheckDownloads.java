/*
 * Copyright (c) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.DownloadTable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

class CheckDownloads extends DataChecker {

    private static class Results {
        long totalCount = 0;
        List<Result> toFix = new ArrayList<>();
    }

    private static class Result {
        final long downloadId;

        private Result(long downloadId) {
            this.downloadId = downloadId;
        }
    }

    @Override
    long fixInternal() {
        Results results = getResults();
        if (countOnly) return results.toFix.size();

        logger.logProgress("Marking " + getSomeOfTotal(results.toFix.size(), results.totalCount) + " downloads as absent");
        AtomicLong fixedCount = new AtomicLong();
        results.toFix.forEach(result -> {
            String sql = "UPDATE " + DownloadTable.TABLE_NAME +
                    " SET " + DownloadTable.DOWNLOAD_STATUS + "=" + DownloadStatus.ABSENT.save() +
                    ", " + DownloadTable.FILE_NAME + "=null" +
                    ", " + DownloadTable.FILE_SIZE + "=0" +
                    " WHERE " + DownloadTable._ID + "=" + result.downloadId;
            myContext.getDatabase().execSQL(sql);
            fixedCount.incrementAndGet();
        });
        return fixedCount.get();
    }

    private Results getResults() {
        String sql = "SELECT *"
                + " FROM " + DownloadTable.TABLE_NAME
                + " WHERE " + DownloadTable.DOWNLOAD_STATUS + "=" + DownloadStatus.LOADED.save();
        return MyQuery.foldLeft(myContext, sql, new Results(), results -> cursor -> {
            DownloadData dd = DownloadData.fromCursor(cursor);
            results.totalCount++;
            if (!dd.getFile().existsNow()) {
                results.toFix.add(new Result(dd.getDownloadId()));
            }
            logger.logProgressIfLongProcess(() -> "Will mark " +
                    getSomeOfTotal(results.toFix.size(), results.totalCount) + " downloads as absent");
            return results;
        });
    }
}
