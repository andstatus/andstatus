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

package org.andstatus.app.note;

import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.actor.ActorsLoader;
import org.andstatus.app.actor.ActorsScreenType;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.checker.CheckConversations;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.list.SyncLoader;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.timeline.LoadableListActivity.ProgressPublisher;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public abstract class ConversationLoader extends SyncLoader<ConversationViewItem> {
    private static final int MAX_INDENT_LEVEL = 19;
    
    protected final MyContext myContext;
    protected final MyAccount ma;
    private final long selectedNoteId;
    Set<Long> conversationIds = new HashSet<>();
    boolean fixConversation = false;
    private boolean sync = false;
    private boolean conversationSyncRequested = false;
    boolean mAllowLoadingFromInternet = false;
    private final ReplyLevelComparator<ConversationViewItem> replyLevelComparator = new ReplyLevelComparator<>();
    private final ConversationViewItem emptyItem;

    final Map<Long, ConversationViewItem> cachedConversationItems = new ConcurrentHashMap<>();
    LoadableListActivity.ProgressPublisher mProgress;

    final List<Long> idsOfItemsToFind = new ArrayList<>();

    public ConversationLoader(ConversationViewItem emptyItem, MyContext myContext, Origin origin, long selectedNoteId, boolean sync) {
        this.emptyItem = emptyItem;
        this.myContext = myContext;
        this.ma = myContext.accounts().getFirstPreferablySucceededForOrigin(origin);
        this.selectedNoteId = selectedNoteId;
        this.sync = sync || MyPreferences.isSyncWhileUsingApplicationEnabled();
    }
    
    @Override
    public void load(ProgressPublisher publisher) {
        mProgress = publisher;
        load1();
        if (fixConversation) {
            new CheckConversations()
                    .setNoteIdsOfOneConversation(
                            items.stream().map(ConversationViewItem::getNoteId).collect(Collectors.toSet()))
                    .setMyContext(myContext).fix();
            load1();
        }
        loadActors(items);
        items.sort(replyLevelComparator);
        enumerateNotes();
    }

    private void load1() {
        conversationIds.clear();
        cachedConversationItems.clear();
        idsOfItemsToFind.clear();
        items.clear();
        if (sync) {
            requestConversationSync(selectedNoteId);
        }
        final ConversationViewItem nonLoaded = getItem(selectedNoteId,
                MyQuery.noteIdToLongColumnValue(NoteTable.CONVERSATION_ID, selectedNoteId), 0);
        cacheConversation(nonLoaded);
        load2(nonLoaded);
        addMissedFromCache();
    }

    protected abstract void load2(ConversationViewItem nonLoaded);

    void cacheConversation(ConversationViewItem item) {
        // Empty
    }

    private void addMissedFromCache() {
        if (cachedConversationItems.isEmpty()) return;
        for (ConversationViewItem item : items) {
            cachedConversationItems.remove(item.getId());
            if (cachedConversationItems.isEmpty()) return;
        }
        MyLog.v(this, () -> cachedConversationItems.size() + " cached notes are not connected to selected");
        for (ConversationViewItem oNote : cachedConversationItems.values()) {
            addItemToList(oNote);
        }
    }

    private void loadActors(List<ConversationViewItem> items) {
        if (items.isEmpty()) return;
        ActorsLoader loader = new ActorsLoader(myContext, ActorsScreenType.ACTORS_AT_ORIGIN,
                ma.getOrigin(), 0, "");
        items.forEach(item -> item.addActorsToLoad(loader));
        if (loader.getList().isEmpty()) return;
        loader.load(progress -> {});
        items.forEach(item -> item.setLoadedActors(loader));
    }

    /** Returns true if note was added false in a case the note existed already */
    protected boolean addNoteIdToFind(long noteId) {
        if (noteId == 0) {
            return false;
        } else if (idsOfItemsToFind.contains(noteId)) {
            MyLog.v(this, () -> "find cycled on the id=" + noteId);
            return false;
        }
        idsOfItemsToFind.add(noteId);
        return true;
    }

    @NonNull
    protected ConversationViewItem getItem(long noteId, long conversationId, int replyLevel) {
        ConversationViewItem item = cachedConversationItems.get(noteId);
        if (item == null) {
            item = emptyItem.newNonLoaded(myContext, noteId);
            item.conversationId = conversationId;
        }
        item.replyLevel = replyLevel;
        return item;
    }

    @NonNull
    protected ConversationViewItem loadItemFromDatabase(ConversationViewItem item) {
        if (item.isLoaded() || item.getNoteId() == 0) {
            return item;
        }
        ConversationViewItem cachedItem = cachedConversationItems.get(item.getNoteId());
        if (cachedItem != null) {
            return cachedItem;
        }
        Uri uri = MatchedUri.getTimelineItemUri(
                myContext.timelines().get(TimelineType.EVERYTHING, Actor.EMPTY, ma.getOrigin()), item.getNoteId());
        try (Cursor cursor = myContext.context().getContentResolver()
                .query(uri, item.getProjection().toArray(new String[]{}), null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                ConversationViewItem loadedItem = item.fromCursor(myContext, cursor);
                loadedItem.replyLevel = item.replyLevel;
                cacheConversation(loadedItem);
                MyLog.v(this, () -> "Loaded (" + loadedItem.isLoaded() + ")"
                        + " from a database noteId=" + item.getNoteId());
                return loadedItem;
            }
        }
        MyLog.v(this, () -> "Couldn't load from a database noteId=" + item.getNoteId());
        return item;
    }

    protected boolean addItemToList(ConversationViewItem item) {
        boolean added = false;
        if (items.contains(item)) {
            MyLog.v(this, () -> "Note id=" + item.getNoteId() + " is in the list already");
        } else {
            items.add(item);
            if (mProgress != null) {
                mProgress.publish(Integer.toString(items.size()));
            }
            added = true;
        }
        return added;
    }

    protected void loadFromInternet(long noteId) {
        if (requestConversationSync(noteId)) {
            return;
        }
        Note.requestDownload(ma, noteId, true);
    }

    private boolean requestConversationSync(long noteId_in) {
        if (conversationSyncRequested) {
            return true;
        }
        long noteId = selectedNoteId;
        String conversationOid = MyQuery.noteIdToConversationOid(myContext, noteId);
        if (StringUtil.isEmpty(conversationOid) && noteId_in != noteId) {
            noteId = noteId_in;
            conversationOid = MyQuery.noteIdToConversationOid(myContext, noteId);
        }
        if (ma.getConnection().canGetConversation(conversationOid)) {
            conversationSyncRequested = true;
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "Conversation oid=" +  conversationOid + " for noteId=" + noteId
                        + " will be loaded from the Internet");
            }
            MyServiceManager.sendForegroundCommand(
                    CommandData.newItemCommand(CommandEnum.GET_CONVERSATION, ma, noteId));
            return true;
        }
        return false;
    }

    private static class ReplyLevelComparator<T extends ConversationViewItem> implements Comparator<T>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public int compare(T lhs, T rhs) {
            int compared = rhs.replyLevel - lhs.replyLevel;
            if (compared == 0) {
                if (lhs.updatedDate == rhs.updatedDate) {
                    if ( lhs.getNoteId() == rhs.getNoteId()) {
                        compared = 0;
                    } else {
                        compared = (rhs.getNoteId() - lhs.getNoteId() > 0 ? 1 : -1);
                    }
                } else {
                    compared = (rhs.updatedDate - lhs.updatedDate > 0 ? 1 : -1);
                }
            }
            return compared;
        }
    }

    private static class OrderCounters {
        int list = -1;
        int history = 1;
    }
    
    private void enumerateNotes() {
        idsOfItemsToFind.clear();
        for (ConversationViewItem item : items) {
            item.mListOrder = 0;
            item.historyOrder = 0;
        }
        OrderCounters order = new OrderCounters();
        for (int ind = items.size()-1; ind >= 0; ind--) {
            ConversationViewItem oMsg = items.get(ind);
            if (oMsg.mListOrder < 0 ) {
                continue;
            }
            enumerateBranch(oMsg, order, 0);
        }
    }

    private void enumerateBranch(ConversationViewItem oMsg, OrderCounters order, int indent) {
        if (!addNoteIdToFind(oMsg.getNoteId())) {
            return;
        }
        int indentNext = indent;
        oMsg.historyOrder = order.history++;
        oMsg.mListOrder = order.list--;
        oMsg.indentLevel = indent;
        if ((oMsg.nReplies > 1 || oMsg.nParentReplies > 1)
                && indentNext < MAX_INDENT_LEVEL) {
            indentNext++;
        }
        for (int ind = items.size() - 1; ind >= 0; ind--) {
           ConversationViewItem reply = items.get(ind);
           if (reply.inReplyToNoteId == oMsg.getNoteId()) {
               reply.nParentReplies = oMsg.nReplies;
               enumerateBranch(reply, order, indentNext);
           }
        }
    }

    public void allowLoadingFromInternet() {
        this.mAllowLoadingFromInternet = true;
    }
}
