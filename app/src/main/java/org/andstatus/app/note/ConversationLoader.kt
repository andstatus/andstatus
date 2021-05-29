/*
 * Copyright (C) 2013-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.net.Uri
import org.andstatus.app.account.MyAccount
import org.andstatus.app.actor.ActorsLoader
import org.andstatus.app.actor.ActorsScreenType
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.checker.CheckConversations
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.list.SyncLoader
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Note
import org.andstatus.app.origin.Origin
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.LoadableListActivity.ProgressPublisher
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import java.io.Serializable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.stream.Collectors

abstract class ConversationLoader(private val emptyItem: ConversationViewItem,
                                  protected val myContext: MyContext,
                                  origin: Origin,
                                  private val selectedNoteId: Long,
                                  sync: Boolean) :
        SyncLoader<ConversationViewItem>() {
    protected val ma: MyAccount = myContext.accounts().getFirstPreferablySucceededForOrigin(origin)
    var conversationIds: MutableSet<Long> = HashSet()
    var fixConversation = false
    private val sync: Boolean = sync || MyPreferences.isSyncWhileUsingApplicationEnabled()
    private var conversationSyncRequested = false
    var mAllowLoadingFromInternet = false
    private val replyLevelComparator: ReplyLevelComparator = ReplyLevelComparator()
    val cachedConversationItems: MutableMap<Long, ConversationViewItem> = ConcurrentHashMap()
    private var mProgress: ProgressPublisher? = null
    private val idsOfItemsToFind: MutableList<Long> = ArrayList()

    override fun load(publisher: ProgressPublisher?) {
        mProgress = publisher
        load1()
        if (fixConversation) {
            CheckConversations()
                    .setNoteIdsOfOneConversation(
                            items.stream().map { obj: ConversationViewItem -> obj.getNoteId() }.collect(Collectors.toSet()))
                    .setMyContext(myContext).fix()
            load1()
        }
        loadActors(items)
        items.sortWith(replyLevelComparator)
        enumerateNotes()
    }

    private fun load1() {
        conversationIds.clear()
        cachedConversationItems.clear()
        idsOfItemsToFind.clear()
        items.clear()
        if (sync) {
            requestConversationSync(selectedNoteId)
        }
        val nonLoaded = getItem(selectedNoteId,
                MyQuery.noteIdToLongColumnValue(NoteTable.CONVERSATION_ID, selectedNoteId), 0)
        cacheConversation(nonLoaded)
        load2(nonLoaded)
        addMissedFromCache()
    }

    protected abstract fun load2(nonLoaded: ConversationViewItem)

    open fun cacheConversation(item: ConversationViewItem) {
        // Empty
    }

    private fun addMissedFromCache() {
        if (cachedConversationItems.isEmpty()) return
        for (item in items) {
            cachedConversationItems.remove(item.getId())
            if (cachedConversationItems.isEmpty()) return
        }
        MyLog.v(this) { cachedConversationItems.size.toString() + " cached notes are not connected to selected" }
        for (oNote in cachedConversationItems.values) {
            addItemToList(oNote)
        }
    }

    private fun loadActors(items: MutableList<ConversationViewItem>) {
        if (items.isEmpty()) return
        val loader = ActorsLoader(myContext, ActorsScreenType.ACTORS_AT_ORIGIN,
                ma.origin, 0, "")
        items.forEach(Consumer { item: ConversationViewItem -> item.addActorsToLoad(loader) })
        if (loader.getList().isEmpty()) return
        loader.load()
        items.forEach(Consumer { item: ConversationViewItem -> item.setLoadedActors(loader) })
    }

    /** Returns true if note was added false in a case the note existed already  */
    protected fun addNoteIdToFind(noteId: Long): Boolean {
        if (noteId == 0L) {
            return false
        } else if (idsOfItemsToFind.contains(noteId)) {
            MyLog.v(this) { "find cycled on the id=$noteId" }
            return false
        }
        idsOfItemsToFind.add(noteId)
        return true
    }

    protected fun getItem(noteId: Long, conversationId: Long, replyLevel: Int): ConversationViewItem {
        var item = cachedConversationItems[noteId]
        if (item == null) {
            item = emptyItem.newNonLoaded(myContext, noteId)
            item.conversationId = conversationId
        }
        item.replyLevel = replyLevel
        return item
    }

    protected fun loadItemFromDatabase(item: ConversationViewItem): ConversationViewItem {
        if (item.isLoaded() || item.getNoteId() == 0L) {
            return item
        }
        val cachedItem = cachedConversationItems[item.getNoteId()]
        if (cachedItem != null) {
            return cachedItem
        }
        val uri: Uri = MatchedUri.getTimelineItemUri(
                myContext.timelines()[TimelineType.EVERYTHING, Actor.EMPTY, ma.origin], item.getNoteId())
        myContext.context().contentResolver
                .query(uri, item.getProjection().toTypedArray(), null, null, null).use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        val loadedItem = item.fromCursor(myContext, cursor)
                        loadedItem.replyLevel = item.replyLevel
                        cacheConversation(loadedItem)
                        MyLog.v(this) {
                            ("Loaded (" + loadedItem.isLoaded() + ")"
                                    + " from a database noteId=" + item.getNoteId())
                        }
                        return loadedItem
                    }
                }
        MyLog.v(this) { "Couldn't load from a database noteId=" + item.getNoteId() }
        return item
    }

    protected fun addItemToList(item: ConversationViewItem): Boolean {
        var added = false
        if (items.contains(item)) {
            MyLog.v(this) { "Note id=" + item.getNoteId() + " is in the list already" }
        } else {
            items.add(item)
            mProgress?.publish(items.size.toString())
            added = true
        }
        return added
    }

    protected fun loadFromInternet(noteId: Long) {
        if (requestConversationSync(noteId)) {
            return
        }
        Note.requestDownload(ma, noteId, true)
    }

    private fun requestConversationSync(noteId_in: Long): Boolean {
        if (conversationSyncRequested) {
            return true
        }
        var noteId = selectedNoteId
        var conversationOid = MyQuery.noteIdToConversationOid(myContext, noteId)
        if (conversationOid.isEmpty() && noteId_in != noteId) {
            noteId = noteId_in
            conversationOid = MyQuery.noteIdToConversationOid(myContext, noteId)
        }
        if (ma.connection.canGetConversation(conversationOid)) {
            conversationSyncRequested = true
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "Conversation oid=" + conversationOid + " for noteId=" + noteId
                        + " will be loaded from the Internet")
            }
            MyServiceManager.sendForegroundCommand(
                    CommandData.newItemCommand(CommandEnum.GET_CONVERSATION, ma, noteId))
            return true
        }
        return false
    }

    private class ReplyLevelComparator : Comparator<ConversationViewItem>, Serializable {
        override fun compare(lhs: ConversationViewItem, rhs: ConversationViewItem): Int {
            var compared = rhs.replyLevel - lhs.replyLevel
            if (compared == 0) {
                compared = if (lhs.updatedDate == rhs.updatedDate) {
                    if (lhs.getNoteId() == rhs.getNoteId()) {
                        0
                    } else {
                        if (rhs.getNoteId() - lhs.getNoteId() > 0) 1 else -1
                    }
                } else {
                    if (rhs.updatedDate - lhs.updatedDate > 0) 1 else -1
                }
            }
            return compared
        }

        companion object {
            private const val serialVersionUID = 1L
        }
    }

    private class OrderCounters {
        var list = -1
        var history = 1
    }

    private fun enumerateNotes() {
        idsOfItemsToFind.clear()
        for (item in items) {
            item.mListOrder = 0
            item.historyOrder = 0
        }
        val order = OrderCounters()
        for (ind in items.indices.reversed()) {
            val oMsg = items[ind]
            if (oMsg.mListOrder < 0) {
                continue
            }
            enumerateBranch(oMsg, order, 0)
        }
    }

    private fun enumerateBranch(oMsg: ConversationViewItem, order: OrderCounters, indent: Int) {
        if (!addNoteIdToFind(oMsg.getNoteId())) {
            return
        }
        var indentNext = indent
        oMsg.historyOrder = order.history++
        oMsg.mListOrder = order.list--
        oMsg.indentLevel = indent
        if ((oMsg.nReplies > 1 || oMsg.nParentReplies > 1)
                && indentNext < MAX_INDENT_LEVEL) {
            indentNext++
        }
        for (ind in items.indices.reversed()) {
            val reply = items[ind]
            if (reply.inReplyToNoteId == oMsg.getNoteId()) {
                reply.nParentReplies = oMsg.nReplies
                enumerateBranch(reply, order, indentNext)
            }
        }
    }

    override fun allowLoadingFromInternet() {
        mAllowLoadingFromInternet = true
    }

    companion object {
        private const val MAX_INDENT_LEVEL = 19
    }

}
