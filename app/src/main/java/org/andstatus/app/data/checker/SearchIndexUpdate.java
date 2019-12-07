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

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.andstatus.app.data.MyQuery.quoteIfNotQuoted;

/**
 * @author yvolk@yurivolkov.com
 */
class SearchIndexUpdate extends DataChecker {

    @Override
    long fixInternal() {
        String sql = Note.getSqlToLoadContent(0) +
                " ORDER BY " + NoteTable._ID + " DESC" +
                (includeLong ? "" : " LIMIT 0, 10000");
        List<Note> notesToFix = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger();
        try (Cursor cursor = myContext.getDatabase().rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                if (logger.isCancelled()) break;

                counter.incrementAndGet();
                Note note = Note.contentFromCursor(myContext, cursor);
                String contentToSearchStored = DbUtils.getString(cursor, NoteTable.CONTENT_TO_SEARCH);
                if (!contentToSearchStored.equals(note.getContentToSearch())) {
                    notesToFix.add(note);
                    logger.logProgressIfLongProcess(() -> "Need to fix " + notesToFix.size() + " of " + counter.get() + " notes, "
                            + ", id=" + note.noteId + "; "
                            + I18n.trimTextAt(note.getContentToSearch(), 120));
                }
            }
        } catch (Exception e) {
            String logMsg = "Error: " + e.getMessage() + ", SQL:" + sql;
            logger.logProgress(logMsg);
            MyLog.e(this, logMsg, e);
        }

        if (!countOnly) notesToFix.forEach(this::fixOneNote);

        logger.logProgress(notesToFix.isEmpty()
                ? "No changes to search index were needed. " + counter + " notes"
                : "Updated search index for " + notesToFix.size() + " of " + counter + " notes");
        return notesToFix.size();
    }

    private void fixOneNote(Note note) {
        if (logger.isCancelled()) return;

        String sql = "";
        try {
            sql = "UPDATE " + NoteTable.TABLE_NAME
                    + " SET "
                    + NoteTable.CONTENT_TO_SEARCH + "=" + quoteIfNotQuoted(note.getContentToSearch())
                    + " WHERE " + NoteTable._ID + "=" + note.noteId;
            myContext.getDatabase().execSQL(sql);
            logger.logProgressIfLongProcess(() -> "Updating search index for " +
                    I18n.trimTextAt(note.getContentToSearch(), 120) +
                    " id=" + note.noteId
            );
            MyServiceManager.setServiceUnavailable();
        } catch (Exception e) {
            String logMsg = "Error: " + e.getMessage() + ", SQL:" + sql;
            logger.logProgress(logMsg);
            MyLog.e(this, logMsg, e);
        }
    }
}
