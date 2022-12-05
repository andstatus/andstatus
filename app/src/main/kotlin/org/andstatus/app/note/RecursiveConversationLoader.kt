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
package org.andstatus.app.note

import org.andstatus.app.context.MyContext
import org.andstatus.app.data.ProjectionMap
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog

/**
 * @author yvolk@yurivolkov.com
 */
class RecursiveConversationLoader(emptyItem: ConversationViewItem, myContext: MyContext, origin: Origin,
                                  selectedNoteId: Long, syncWithInternet: Boolean) :
        ConversationLoader(emptyItem, myContext, origin, selectedNoteId, syncWithInternet) {

    override fun load2(nonLoaded: ConversationViewItem) {
        findPreviousNotesRecursively(nonLoaded, 1)
    }

    override fun cacheConversation(item: ConversationViewItem) {
        if (conversationIds.contains(item.conversationId) || item.conversationId == 0L) {
            return
        }
        if (conversationIds.isNotEmpty()) {
            fixConversation = true
            MyLog.d(this, "Another conversationId:$item")
        }
        conversationIds.add(item.conversationId)
        val selection = (ProjectionMap.NOTE_TABLE_ALIAS + "."
                + NoteTable.CONVERSATION_ID + "=" + item.conversationId)
        val uri = myContext.timelines[TimelineType.EVERYTHING, Actor.EMPTY, ma.origin].getUri()
        myContext.context.contentResolver.query(uri,
                item.getProjection().toTypedArray(),
                selection, null, null).use { cursor ->
            while (cursor != null && cursor.moveToNext()) {
                val itemLoaded = item.fromCursor(myContext, cursor)
                cachedConversationItems[itemLoaded.getNoteId()] = itemLoaded
            }
        }
    }

    private fun findPreviousNotesRecursively(itemIn: ConversationViewItem, level: Int) {
        val MAX_RECURSIVE_LEVEL = 50
        if (level > MAX_RECURSIVE_LEVEL || !addNoteIdToFind(itemIn.getNoteId())) {
            return
        }
        val item = loadItemFromDatabase(itemIn)
        findRepliesRecursively(item, level)
        MyLog.v(this) { "findPreviousNotesRecursively " + level + ", id=" + item.getNoteId() + " replies:" + item.nReplies }
        if (item.isLoaded()) {
            if (addItemToList(item) && item.inReplyToNoteId != 0L) {
                findPreviousNotesRecursively(
                    getItem(item.inReplyToNoteId, item.conversationId, item.replyLevel - 1),
                    level + 1
                )
            }
        } else if (mAllowLoadingFromInternet) {
            loadFromInternet(item.getNoteId())
        }
    }

    private fun findRepliesRecursively(item: ConversationViewItem, level: Int) {
        MyLog.v(this) { "findReplies for id=" + item.getNoteId() }
        for (reply in cachedConversationItems.values) {
            if (reply.inReplyToNoteId == item.getNoteId()) {
                item.nReplies++
                reply.replyLevel = item.replyLevel + 1
                findPreviousNotesRecursively(reply, level + 1)
            }
        }
    }
}
