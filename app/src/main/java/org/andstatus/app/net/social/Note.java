/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social;

import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;

import java.util.ArrayList;
import java.util.List;

import static org.andstatus.app.util.UriUtils.TEMP_OID_PREFIX;
import static org.andstatus.app.util.UriUtils.isEmptyOid;
import static org.andstatus.app.util.UriUtils.isRealOid;
import static org.andstatus.app.util.UriUtils.nonRealOid;

/**
 * Note ("Tweet", "toot" etc.) of a Social Network
 * @author yvolk@yurivolkov.com
 */
public class Note extends AObject {
    public static final Note EMPTY = new Note(Origin.EMPTY, getTempOid());

    private boolean isEmpty = false;
    private DownloadStatus status = DownloadStatus.UNKNOWN;
    
    public final String oid;
    private long updatedDate = 0;
    private final Audience recipients;
    private String name = "";
    private String content = "";

    @NonNull
    private AActivity inReplyTo = AActivity.EMPTY;
    public final List<AActivity> replies = new ArrayList<>();
    public String conversationOid="";
    public String via = "";
    public String url="";

    public final Attachments attachments = new Attachments();

    /** Some additional attributes may appear from "My account's" (authenticated Account's) point of view */

    // In our system
    public final Origin origin;
    public long noteId = 0L;
    private long conversationId = 0L;

    @NonNull
    public static Note fromOriginAndOid(@NonNull Origin origin, String oid, DownloadStatus status) {
        Note note = new Note(origin, isEmptyOid(oid) ? getTempOid() : oid);
        note.status = status;
        if (StringUtils.isEmpty(oid) && status == DownloadStatus.LOADED) {
            note.status = DownloadStatus.UNKNOWN;
        }
        return note;
    }

    private static String getTempOid() {
        return TEMP_OID_PREFIX + "msg:" + MyLog.uniqueCurrentTimeMS() ;
    }

    private Note(Origin origin, String oid) {
        this.origin = origin;
        this.oid = oid;
        recipients = new Audience(origin);
    }

    @NonNull
    public AActivity update(Actor accountActor) {
        return act(accountActor, Actor.EMPTY, ActivityType.UPDATE);
    }

    @NonNull
    public AActivity act(Actor accountActor, @NonNull Actor actor, @NonNull ActivityType activityType) {
        AActivity mbActivity = AActivity.from(accountActor, activityType);
        mbActivity.setActor(actor);
        mbActivity.setNote(this);
        return mbActivity;
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }
    public String getContentToSearch() {
        return MyHtml.getContentToSearch(content);
    }

    private boolean isHtmlContentAllowed() {
        return origin.isHtmlContentAllowed();
    }

    public static boolean mayBeEdited(OriginType originType, DownloadStatus downloadStatus) {
        if (originType == null || downloadStatus == null) return false;
        return downloadStatus == DownloadStatus.DRAFT || downloadStatus.mayBeSent() ||
                (downloadStatus == DownloadStatus.LOADED && originType.allowEditing());
    }

    public void setName(String name) {
        if (StringUtils.isEmpty(name)) {
            this.name = "";
        } else if (isHtmlContentAllowed()) {
            this.name = MyHtml.stripUnnecessaryNewlines(MyHtml.unescapeHtml(name));
        } else {
            this.name = MyHtml.fromHtml(name);
        }
    }

    public void setContent(String content) {
        if (StringUtils.isEmpty(content)) {
            this.content = "";
        } else if (isHtmlContentAllowed()) {
            this.content = MyHtml.stripUnnecessaryNewlines(MyHtml.unescapeHtml(content));
        } else {
            this.content = MyHtml.fromHtml(content);
        }
    }

    public Note setConversationOid(String conversationOid) {
        if (StringUtils.isEmpty(conversationOid)) {
            this.conversationOid = "";
        } else {
            this.conversationOid = conversationOid;
        }
        return this;
    }

    public long lookupConversationId() {
        if (conversationId == 0  && !StringUtils.isEmpty(conversationOid)) {
            conversationId = MyQuery.conversationOidToId(origin.getId(), conversationOid);
        }
        if (conversationId == 0 && noteId != 0) {
            conversationId = MyQuery.noteIdToLongColumnValue(NoteTable.CONVERSATION_ID, noteId);
        }
        if (conversationId == 0 && getInReplyTo().nonEmpty()) {
            if (getInReplyTo().getNote().noteId != 0) {
                conversationId = MyQuery.noteIdToLongColumnValue(NoteTable.CONVERSATION_ID,
                        getInReplyTo().getNote().noteId);
            }
        }
        return setConversationIdFromMsgId();
    }

    public long setConversationIdFromMsgId() {
        if (conversationId == 0 && noteId != 0) {
            conversationId = noteId;
        }
        return conversationId;
    }

    public long getConversationId() {
        return conversationId;
    }

    public DownloadStatus getStatus() {
        return status;
    }

    public boolean isEmpty() {
        return this.isEmpty
                || !origin.isValid()
                || (nonRealOid(oid)
                    && ((status != DownloadStatus.SENDING && status != DownloadStatus.DRAFT)
                        || (StringUtils.isEmpty(name) && StringUtils.isEmpty(content) && attachments.isEmpty())));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Note other = (Note) o;
        return hashCode() == other.hashCode();
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        if (this == EMPTY) {
            return MyLog.formatKeyValue(this, "EMPTY");
        }
        StringBuilder builder = new StringBuilder();
        if (isEmpty()) {
            builder.append("empty,");
        }
        if(noteId != 0) {
            builder.append("id:" + noteId + ",");
        }
        if(conversationId != noteId) {
            builder.append("conversation_id:" + conversationId + ",");
        }
        builder.append("status:" + status + ",");
        if(StringUtils.nonEmpty(name)) {
            builder.append("name:'" + name + "',");
        }
        if(StringUtils.nonEmpty(content)) {
            builder.append("content:'" + content + "',");
        }
        if(isEmpty) {
            builder.append("isEmpty,");
        }
        if(getPublic().known) {
            builder.append(getPublic() == TriState.TRUE ? "public," : "nonpublic,");
        }
        if(isRealOid(oid)) {
            builder.append("oid:'" + oid + "',");
        }
        if(isRealOid(conversationOid)) {
            builder.append("conversation_oid:'" + conversationOid + "',");
        }
        if(!StringUtils.isEmpty(url)) {
            builder.append("url:'" + url + "',");
        }
        if(!StringUtils.isEmpty(via)) {
            builder.append("via:'" + via + "',");
        }
        builder.append("updated:" + MyLog.debugFormatOfDate(updatedDate) + ",");
        builder.append("origin:" + origin.getName() + ",");
        if(recipients.nonEmpty()) {
            builder.append("\nrecipients:" + recipients + ",");
        }
        if (!attachments.isEmpty()) {
            builder.append("\n" + attachments + ",");
        }
        if(getInReplyTo().nonEmpty()) {
            builder.append("\ninReplyTo:" + getInReplyTo() + ",");
        }
        if(replies.size() > 0) {
            builder.append("\nReplies:" + replies + ",");
        }
        return MyLog.formatKeyValue(this, builder.toString());
    }

    @NonNull
    public AActivity getInReplyTo() {
        return inReplyTo;
    }

    public void setInReplyTo(AActivity activity) {
        if (activity != null && activity.nonEmpty()) {
            inReplyTo = activity;
        }
    }

    public TriState getPublic() {
        return audience().getPublic();
    }

    public Note setPublic(TriState isPublic) {
        audience().setPublic(isPublic);
        return this;
    }

    public long getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(long updatedDate) {
        this.updatedDate = updatedDate;
    }

    @NonNull
    public Audience audience() {
        return recipients;
    }

    public void addRecipient(Actor recipient) {
        if (recipient != null && recipient.nonEmpty()) {
            recipients.add(recipient);
        }
    }

    public void addRecipientsFromBodyText(Actor author) {
        for (Actor actor : author.extractActorsFromContent(getContent(), false)) {
            addRecipient(actor);
        }
    }

    public Note shallowCopy() {
        Note note = fromOriginAndOid(origin, oid, status);
        note.noteId = noteId;
        note.setUpdatedDate(updatedDate);
        return note;
    }

    public Note copy(String oidNew) {
        Note note = fromOriginAndOid(origin, oidNew, status);
        note.noteId = noteId;
        note.setUpdatedDate(updatedDate);
        note.recipients.copy(recipients);
        note.setName(name);
        note.setContent(content);
        note.inReplyTo = getInReplyTo();
        note.replies.addAll(replies);
        note.setConversationOid(conversationOid);
        note.via = via;
        note.url = url;
        note.attachments.copy(attachments);
        note.conversationId = conversationId;
        return note;
    }

    public void addFavoriteBy(@NonNull Actor accountActor, @NonNull TriState favoritedByMe) {
        if (favoritedByMe != TriState.TRUE) {
            return;
        }
        AActivity favorite = AActivity.from(accountActor, ActivityType.LIKE);
        favorite.setActor(accountActor);
        favorite.setUpdatedDate(getUpdatedDate());
        favorite.setNote(shallowCopy());
        replies.add(favorite);
    }

    @NonNull
    public TriState getFavoritedBy(Actor accountActor) {
        if (noteId == 0) {
            for (AActivity reply : replies) {
                if (reply.type == ActivityType.LIKE && reply.getActor().equals(accountActor)
                        && reply.getNote().oid.equals(oid) ) {
                    return TriState.TRUE;
                }
            }
            return TriState.UNKNOWN;
        } else {
            final Pair<Long, ActivityType> favAndType = MyQuery.noteIdToLastFavoriting(MyContextHolder.get().getDatabase(),
                    noteId, accountActor.actorId);
            switch (favAndType.second) {
                case LIKE:
                    return TriState.TRUE;
                case UNDO_LIKE:
                    return TriState.FALSE;
                default:
                    return TriState.UNKNOWN;
            }
        }
    }

    public void setDiscarded() {
        status = UriUtils.isRealOid(oid) ? DownloadStatus.LOADED : DownloadStatus.DELETED;
    }

    public void setStatus(DownloadStatus status) {
        this.status = status;
    }
}
