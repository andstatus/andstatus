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
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.list.SyncLoader;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.timeline.LoadableListActivity.ProgressPublisher;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ConversationLoader<T extends ConversationItem<T>> extends SyncLoader<T> {
    private static final int MAX_INDENT_LEVEL = 19;
    
    protected final MyContext myContext;
    protected final MyAccount ma;
    private final long selectedNoteId;
    private boolean sync = false;
    private boolean conversationSyncRequested = false;
    boolean mAllowLoadingFromInternet = false;
    private final ReplyLevelComparator<T> replyLevelComparator = new ReplyLevelComparator<>();
    private final T tFactory;

    final Map<Long, T> cachedItems = new ConcurrentHashMap<>();
    LoadableListActivity.ProgressPublisher mProgress;

    final List<Long> idsOfItemsToFind = new ArrayList<>();

    public ConversationLoader(T emptyItem, MyContext myContext, MyAccount ma, long selectedNoteId, boolean sync) {
        tFactory = emptyItem;
        this.myContext = myContext;
        this.ma = ma;
        this.selectedNoteId = selectedNoteId;
        this.sync = sync || MyPreferences.isSyncWhileUsingApplicationEnabled();
    }
    
    @Override
    public void load(ProgressPublisher publisher) {
        mProgress = publisher;
        cachedItems.clear();
        idsOfItemsToFind.clear();
        items.clear();
        if (sync) {
            requestConversationSync(selectedNoteId);
        }
        load2(newONote(selectedNoteId));
        addMissedFromCache();
        items.sort(replyLevelComparator);
        enumerateNotes();
    }

    protected abstract void load2(T oMsg);

    private void addMissedFromCache() {
        if (cachedItems.isEmpty()) return;
        for (ConversationItem item : items) {
            cachedItems.remove(item.getId());
            if (cachedItems.isEmpty()) return;
        }
        MyLog.v(this, cachedItems.size() + " cached notes are not connected to selected");
        for (T oNote : cachedItems.values()) {
            addNoteToList(oNote);
        }
    }

    /** Returns true if note was added false in a case the note existed already */
    protected boolean addNoteIdToFind(long noteId) {
        if (noteId == 0) {
            return false;
        } else if (idsOfItemsToFind.contains(noteId)) {
            MyLog.v(this, "find cycled on the id=" + noteId);
            return false;
        }
        idsOfItemsToFind.add(noteId);
        return true;
    }

    @NonNull
    protected T getItem(long noteId, int replyLevel) {
        T item = cachedItems.get(noteId);
        if (item == null) {
            item = newONote(noteId);
        }
        item.replyLevel = replyLevel;
        return item;
    }

    protected T newONote(long noteId) {
        T oMsg = tFactory.getNew();
        oMsg.setMyContext(myContext);
        oMsg.setNoteId(noteId);
        return oMsg;
    }

    protected void loadItemFromDatabase(T item) {
        if (item.isLoaded() || item.getNoteId() == 0 || cachedItems.containsKey(item.getNoteId())) {
            return;
        }
        Uri uri = MatchedUri.getTimelineItemUri(
                Timeline.getTimeline(TimelineType.EVERYTHING, 0, ma.getOrigin()), item.getNoteId());
        boolean loaded = false;
        try (Cursor cursor = myContext.context().getContentResolver()
                .query(uri, item.getProjection(), null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                item.load(cursor);
                loaded = true;
            }
        }
        MyLog.v(this, (loaded ? "Loaded (" + item.isLoaded() + ")"  : "Couldn't load")
                + " from a database noteId=" + item.getNoteId());
    }

    protected boolean addNoteToList(T oMsg) {
        boolean added = false;
        if (items.contains(oMsg)) {
            MyLog.v(this, "Note id=" + oMsg.getNoteId() + " is in the list already");
        } else {
            items.add(oMsg);
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
        MyLog.v(this, "Note id=" + noteId + " will be loaded from the Internet");
        MyServiceManager.sendForegroundCommand(
                CommandData.newItemCommand(CommandEnum.GET_NOTE, ma, noteId));
    }

    private boolean requestConversationSync(long noteId_in) {
        if (conversationSyncRequested) {
            return true;
        }
        if (ma.getConnection().isApiSupported(Connection.ApiRoutineEnum.GET_CONVERSATION)) {
            long noteId = selectedNoteId;
            String conversationOid = MyQuery.noteIdToConversationOid(noteId);
            if (TextUtils.isEmpty(conversationOid) && noteId_in != noteId) {
                noteId = noteId_in;
                conversationOid = MyQuery.noteIdToConversationOid(noteId);
            }
            if (!TextUtils.isEmpty(conversationOid)) {
                conversationSyncRequested = true;
                MyLog.v(this, "Conversation oid=" +  conversationOid + " for noteId=" + noteId
                        + " will be loaded from the Internet");
                MyServiceManager.sendForegroundCommand(
                        CommandData.newItemCommand(CommandEnum.GET_CONVERSATION, ma, noteId));
                return true;
            }
        }
        return false;
    }

    private static class ReplyLevelComparator<T extends ConversationItem> implements Comparator<T>, Serializable {
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
        for (ConversationItem item : items) {
            item.mListOrder = 0;
            item.historyOrder = 0;
        }
        OrderCounters order = new OrderCounters();
        for (int ind = items.size()-1; ind >= 0; ind--) {
            ConversationItem oMsg = items.get(ind);
            if (oMsg.mListOrder < 0 ) {
                continue;
            }
            enumerateBranch(oMsg, order, 0);
        }
    }

    private void enumerateBranch(ConversationItem oMsg, OrderCounters order, int indent) {
        if (!addNoteIdToFind(oMsg.getNoteId())) {
            return;
        }
        int indentNext = indent;
        oMsg.historyOrder = order.history++;
        oMsg.mListOrder = order.list--;
        oMsg.indentLevel = indent;
        if ((oMsg.mNReplies > 1 || oMsg.mNParentReplies > 1)
                && indentNext < MAX_INDENT_LEVEL) {
            indentNext++;
        }
        for (int ind = items.size() - 1; ind >= 0; ind--) {
           ConversationItem reply = items.get(ind);
           if (reply.inReplyToNoteId == oMsg.getNoteId()) {
               reply.mNParentReplies = oMsg.mNReplies;
               enumerateBranch(reply, order, indentNext);
           }
        }
    }

    public void allowLoadingFromInternet() {
        this.mAllowLoadingFromInternet = true;
    }
}
