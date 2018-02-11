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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.ProjectionMap;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class RecursiveConversationLoader<T extends ConversationItem<T>> extends ConversationLoader<T> {
    public RecursiveConversationLoader(T emptyItem, MyContext myContext, MyAccount ma,
                                       long selectedNoteId, boolean sync) {
        super(emptyItem, myContext, ma, selectedNoteId, sync);
    }

    @Override
    protected void load2(T oMsg) {
        cacheConversation(oMsg);
        findPreviousNotesRecursively(getItem(oMsg.getNoteId(), 0));
    }

    private void cacheConversation(T oMsg) {
        long conversationId = MyQuery.noteIdToLongColumnValue(NoteTable.CONVERSATION_ID, oMsg.getNoteId());
        String selection = (conversationId == 0
                ? ProjectionMap.ACTIVITY_TABLE_ALIAS + "." + ActivityTable.NOTE_ID + "=" + oMsg.getNoteId()
                : ProjectionMap.NOTE_TABLE_ALIAS + "." + NoteTable.CONVERSATION_ID + "=" + conversationId);
        Uri uri = Timeline.getTimeline(TimelineType.EVERYTHING, ma, 0, null).getUri();

        try (Cursor cursor = myContext.context().getContentResolver().query(uri, oMsg.getProjection(),
                selection, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    T oMsg2 = newONote(DbUtils.getLong(cursor, ActivityTable.NOTE_ID));
                    oMsg2.load(cursor);
                    cachedItems.put(oMsg2.getNoteId(), oMsg2);
                }
            }
        }
    }

    private void findPreviousNotesRecursively(T oMsg) {
        if (!addNoteIdToFind(oMsg.getNoteId())) {
            return;
        }
        findRepliesRecursively(oMsg);
        MyLog.v(this, "findPreviousNotesRecursively id=" + oMsg.getNoteId() + " replies:" + oMsg.mNReplies);
        loadItemFromDatabase(oMsg);
        if (oMsg.isLoaded()) {
            if (addNoteToList(oMsg)) {
                if (oMsg.inReplyToNoteId != 0) {
                    findPreviousNotesRecursively(getItem(oMsg.inReplyToNoteId,
                            oMsg.replyLevel - 1));
                }
            }
        } else if (mAllowLoadingFromInternet) {
            loadFromInternet(oMsg.getNoteId());
        }
    }

    public void findRepliesRecursively(T oMsg) {
        MyLog.v(this, "findReplies for id=" + oMsg.getNoteId());
        for (T oMsgReply : cachedItems.values()) {
            if (oMsgReply.inReplyToNoteId == oMsg.getNoteId()) {
                oMsg.mNReplies++;
                oMsgReply.replyLevel = oMsg.replyLevel + 1;
                findPreviousNotesRecursively(oMsgReply);
            }
        }
    }
}
