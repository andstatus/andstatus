/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.note;

import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.ProjectionMap;
import org.andstatus.app.data.SqlActorIds;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.TriState;

/**
 * @author yvolk@yurivolkov.com
 */
public class PrivateNotesConversationLoader<T extends ConversationItem<T>> extends ConversationLoader<T> {
    public PrivateNotesConversationLoader(T emptyItem, MyContext myContext, MyAccount ma,
                                          long selectedNoteId, boolean sync) {
        super(emptyItem, myContext, ma, selectedNoteId, sync);
    }

    @Override
    protected void load2(T oMsg) {
        long actorId = MyQuery.noteIdToLongColumnValue(ActivityTable.ACTOR_ID, oMsg.getNoteId());
        Audience recipients = Audience.fromNoteId(ma.getOrigin(), oMsg.getNoteId());
        String selection = getSelectionForActorAndRecipient("=" + Long.toString(actorId),
                SqlActorIds.fromActors(recipients.getRecipients()).getSql());
        Uri uri = Timeline.getTimeline(TimelineType.EVERYTHING, 0, ma.getOrigin()).getUri();
        Cursor cursor = null;
        try {
            cursor = myContext.context().getContentResolver().query(uri, oMsg.getProjection(),
                    selection, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    T oMsg2 = newONote(DbUtils.getLong(cursor, BaseColumns._ID));
                    oMsg2.load(cursor);
                    addNoteToList(oMsg2);
                }
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
    }

    @NonNull
    // TODO: Actually this is not exactly what we need, because we don't check recipients
    private String getSelectionForActorAndRecipient(String actor, String recipients) {
        return "(" + NoteTable.PUBLIC + "=" + TriState.FALSE.id
                + " AND (" + ProjectionMap.ACTIVITY_TABLE_ALIAS + "." + ActivityTable.ACTOR_ID + actor
                + " OR " + ProjectionMap.ACTIVITY_TABLE_ALIAS + "." + ActivityTable.ACTOR_ID + recipients + "))";
    }

}
