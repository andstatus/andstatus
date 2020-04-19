/*
 * Copyright (c) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.TriState;

/**
 * @author yvolk@yurivolkov.com
 */
class CheckAudience extends DataChecker {

    @Override
    long fixInternal() {
        return myContext.origins().collection().stream().mapToInt(o -> fixOneOrigin(o, countOnly)).sum();
    }

    private static class FixSummary {
        long rowsCount = 0;
        int toFixCount = 0;
    }

    private int fixOneOrigin(Origin origin, boolean countOnly) {
        if (logger.isCancelled()) return 0;

        MyAccount ma = myContext.accounts().getFirstSucceededForOrigin(origin);
        if (ma.isEmpty()) return 0;

        DataUpdater dataUpdater = new DataUpdater(ma);

        String sql = "SELECT " + NoteTable._ID + ", " +
                NoteTable.INS_DATE + ", " +
                NoteTable.VISIBILITY + ", " +
                NoteTable.CONTENT + ", " +
                NoteTable.ORIGIN_ID + ", " +
                NoteTable.AUTHOR_ID + ", " +
                NoteTable.IN_REPLY_TO_ACTOR_ID +
                " FROM " + NoteTable.TABLE_NAME +
                " WHERE " + NoteTable.ORIGIN_ID + "=" + origin.getId() +
                " AND " + NoteTable.NOTE_STATUS + "=" + DownloadStatus.LOADED.save() +
                " ORDER BY " + NoteTable._ID + " DESC" +
                (includeLong ? "" : " LIMIT 0, 500");

        FixSummary summary = MyQuery.foldLeft(myContext, sql, new FixSummary(),
                s -> cursor -> foldOneNote(ma, dataUpdater, countOnly, s, cursor));

        logger.logProgress(origin.getName() + ": " +
                (summary.toFixCount == 0
                ? "No changes to Audience were needed. " + summary.rowsCount + " notes"
                : (countOnly ? "Need to update " : "Updated") + " Audience for " + summary.toFixCount +
                        " of " + summary.rowsCount + " notes"));
        DbUtils.waitMs(this, 1000);
        return summary.toFixCount;
    }

    private FixSummary foldOneNote(MyAccount ma, DataUpdater dataUpdater, boolean countOnly, FixSummary s, Cursor cursor) {
        if (logger.isCancelled()) return s;

        Origin origin = ma.getOrigin();
        s.rowsCount++;
        long noteId = DbUtils.getLong(cursor, NoteTable._ID);
        long insDate = DbUtils.getLong(cursor, NoteTable.INS_DATE);
        TriState visibility = DbUtils.getTriState(cursor, NoteTable.VISIBILITY);
        String content = DbUtils.getString(cursor, NoteTable.CONTENT);
        Actor author = Actor.load(myContext, DbUtils.getLong(cursor, NoteTable.AUTHOR_ID));
        Actor inReplyToActor = Actor.load(myContext, DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_ACTOR_ID));

        if (origin.getOriginType() == OriginType.GNUSOCIAL || origin.getOriginType() == OriginType.TWITTER) {

            // See org.andstatus.app.note.NoteEditorData.recreateAudience
            Audience audience = new Audience(origin);
            audience.add(inReplyToActor);
            audience.addActorsFromContent(content, author, inReplyToActor);
            audience.setVisibility(visibility);
            audience.lookupUsers();

            if (!countOnly) {
                audience.getActorsToSave(author).stream().filter(a -> a.actorId == 0).forEach(actor ->
                        dataUpdater.updateObjActor(ma.getActor().update(actor), 0)
                );
            }
            if (audience.save(author, noteId, audience.getVisibility(), countOnly)) {
                s.toFixCount += 1;
            }
        }
        logger.logProgressIfLongProcess(() -> origin.getName() + ": need to fix " + s.toFixCount +
                " of " + s.rowsCount + " audiences;\n" +
                RelativeTime.getDifference(myContext.context(), insDate) + ", " +
                I18n.trimTextAt(MyHtml.htmlToCompactPlainText(content), 120));
        return s;
    }
}
