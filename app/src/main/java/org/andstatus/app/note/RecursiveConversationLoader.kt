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

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.ProjectionMap;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class RecursiveConversationLoader extends ConversationLoader {
    public RecursiveConversationLoader(ConversationViewItem emptyItem, MyContext myContext, Origin origin,
                                       long selectedNoteId, boolean sync) {
        super(emptyItem, myContext, origin, selectedNoteId, sync);
    }

    @Override
    protected void load2(ConversationViewItem nonLoaded) {
        findPreviousNotesRecursively(nonLoaded);
    }

    @Override
    void cacheConversation(ConversationViewItem item) {
        if (conversationIds.contains(item.conversationId) || item.conversationId == 0) {
            return;
        }
        if (!conversationIds.isEmpty()) {
            fixConversation = true;
            MyLog.d(this, "Another conversationId:" + item);
        }
        conversationIds.add(item.conversationId);

        String selection = (ProjectionMap.NOTE_TABLE_ALIAS + "."
                + NoteTable.CONVERSATION_ID + "=" + item.conversationId);
        Uri uri = myContext.timelines().get(TimelineType.EVERYTHING, Actor.EMPTY, ma.getOrigin()).getUri();

        try (Cursor cursor = myContext.context().getContentResolver().query(uri,
                item.getProjection().toArray(new String[]{}),
                selection, null, null)) {
            while (cursor != null && cursor.moveToNext()) {
                ConversationViewItem itemLoaded = item.fromCursor(myContext, cursor);
                cachedConversationItems.put(itemLoaded.getNoteId(), itemLoaded);
            }
        }
    }

    private void findPreviousNotesRecursively(ConversationViewItem itemIn) {
        if (!addNoteIdToFind(itemIn.getNoteId())) {
            return;
        }
        ConversationViewItem item = loadItemFromDatabase(itemIn);
        findRepliesRecursively(item);
        MyLog.v(this, () -> "findPreviousNotesRecursively id=" + item.getNoteId() + " replies:" + item.nReplies);
        if (item.isLoaded()) {
            if (addItemToList(item) && item.inReplyToNoteId != 0) {
                findPreviousNotesRecursively(
                        getItem(item.inReplyToNoteId, item.conversationId, item.replyLevel - 1));
            }
        } else if (mAllowLoadingFromInternet) {
            loadFromInternet(item.getNoteId());
        }
    }

    private void findRepliesRecursively(ConversationViewItem item) {
        MyLog.v(this, () -> "findReplies for id=" + item.getNoteId());
        for (ConversationViewItem reply : cachedConversationItems.values()) {
            if (reply.inReplyToNoteId == item.getNoteId()) {
                item.nReplies++;
                reply.replyLevel = item.replyLevel + 1;
                findPreviousNotesRecursively(reply);
            }
        }
    }
}
