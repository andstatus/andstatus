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

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TextMediaType;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.LazyVal;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.andstatus.app.util.UriUtils.isEmptyOid;
import static org.andstatus.app.util.UriUtils.isRealOid;
import static org.andstatus.app.util.UriUtils.nonRealOid;

/**
 * Note ("Tweet", "toot" etc.) of a Social Network
 * @author yvolk@yurivolkov.com
 */
public class Note extends AObject {
    private static final String TAG = Note.class.getSimpleName();
    public static final Note EMPTY = new Note(Origin.EMPTY, getTempOid());

    private DownloadStatus status = DownloadStatus.UNKNOWN;
    
    public final String oid;
    private long updatedDate = 0;
    private volatile Audience audience;
    private String name = "";
    private String summary = "";
    private boolean isSensitive = false;
    private String content = "";
    private final LazyVal<String> contentToSearch = LazyVal.of(this::evalContentToSearch);

    private AActivity inReplyTo = AActivity.EMPTY;
    public final List<AActivity> replies;
    public String conversationOid="";
    public String via = "";
    public String url="";

    public final Attachments attachments;

    /** Some additional attributes may appear from "My account's" (authenticated Account's) point of view */

    // In our system
    public final Origin origin;
    public long noteId = 0L;
    private long conversationId = 0L;

    @NonNull
    public static Note fromOriginAndOid(@NonNull Origin origin, String oid, DownloadStatus status) {
        Note note = new Note(origin, fixedOid(oid));
        note.status = fixedStatus(note.oid, status);
        return note;
    }

    private static String fixedOid(String oid) {
        return isEmptyOid(oid) ? getTempOid() : oid;
    }

    private static DownloadStatus fixedStatus(String oid, DownloadStatus status) {
        if (StringUtil.isEmpty(oid) && status == DownloadStatus.LOADED) {
            return DownloadStatus.UNKNOWN;
        }
        return status;
    }

    @NonNull
    public static String getSqlToLoadContent(long id) {
        String sql = "SELECT " + NoteTable._ID
                + ", " + NoteTable.CONTENT
                + ", " + NoteTable.CONTENT_TO_SEARCH
                + ", " + NoteTable.NOTE_OID
                + ", " + NoteTable.ORIGIN_ID
                + ", " + NoteTable.NAME
                + ", " + NoteTable.SUMMARY
                + ", " + NoteTable.SENSITIVE
                + ", " + NoteTable.NOTE_STATUS
                + " FROM " + NoteTable.TABLE_NAME;
        return sql + (id == 0 ? "" : " WHERE " + NoteTable._ID + "=" + id);
    }

    @NonNull
    public static Note contentFromCursor(MyContext myContext, Cursor cursor) {
        Note note = fromOriginAndOid(myContext.origins().fromId(DbUtils.getLong(cursor, NoteTable.ORIGIN_ID)),
                DbUtils.getString(cursor, NoteTable.NOTE_OID),
                DownloadStatus.load(DbUtils.getLong(cursor, NoteTable.NOTE_STATUS)));
        note.noteId = DbUtils.getLong(cursor, NoteTable._ID);
        note.setName(DbUtils.getString(cursor, NoteTable.NAME));
        note.setSummary(DbUtils.getString(cursor, NoteTable.SUMMARY));
        note.setSensitive(DbUtils.getBoolean(cursor, NoteTable.SENSITIVE));
        note.setContentStored(DbUtils.getString(cursor, NoteTable.CONTENT));
        return note;
    }

    @NonNull
    public static Note loadContentById(MyContext myContext, long noteId) {
        return MyQuery.get(myContext, getSqlToLoadContent(noteId), cursor -> Note.contentFromCursor(myContext, cursor))
                .stream().findAny().map(Note::loadAudience).orElse(Note.EMPTY);
    }

    private Note loadAudience() {
        audience = Audience.load(origin, noteId, Optional.empty());
        return this;
    }

    private static String getTempOid() {
        return StringUtil.toTempOid("note:" + MyLog.uniqueCurrentTimeMS());
    }

    private Note(Origin origin, String oid) {
        this.origin = origin;
        this.oid = oid;
        audience = origin.isEmpty() ? Audience.EMPTY : new Audience(origin);
        replies = origin.isEmpty() ? Collections.emptyList() : new ArrayList<>();
        attachments = origin.isEmpty() ? Attachments.EMPTY : new Attachments();
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

    public String getSummary() {
        return summary;
    }

    public String getContent() {
        return content;
    }

    public String getContentToPost() {
        return MyHtml.fromContentStored(content, origin.getOriginType().textMediaTypeToPost);
    }

    public String getContentToSearch() {
        return contentToSearch.get();
    }

    private boolean isHtmlContentAllowed() {
        return origin.isHtmlContentAllowed();
    }

    public static boolean mayBeEdited(OriginType originType, DownloadStatus downloadStatus) {
        if (originType == null || downloadStatus == null) return false;
        return downloadStatus == DownloadStatus.DRAFT || downloadStatus.mayBeSent() ||
                (downloadStatus.isPresentAtServer() && originType.allowEditing());
    }

    private String evalContentToSearch() {
        return MyHtml.getContentToSearch(
                (StringUtil.nonEmpty(name) ? name + " " : "") +
                        (StringUtil.nonEmpty(summary) ? summary + " " : "") +
                        content);
    }

    public Note setName(String name) {
        this.name = MyHtml.htmlToCompactPlainText(name);
        contentToSearch.reset();
        return this;
    }

    public void setSummary(String summary) {
        this.summary = MyHtml.htmlToCompactPlainText(summary);
        contentToSearch.reset();
    }

    public void setContentStored(String content) {
        this.content = content;
        contentToSearch.reset();
    }

    public Note setContentPosted(String content) {
        setContent(content, origin.getOriginType().textMediaTypePosted);
        return this;
    }

    public void setContent(String content, TextMediaType mediaType) {
        this.content = MyHtml.toContentStored(content, mediaType, isHtmlContentAllowed());
        contentToSearch.reset();
    }

    public Note setConversationOid(String conversationOid) {
        if (StringUtil.isEmpty(conversationOid)) {
            this.conversationOid = "";
        } else {
            this.conversationOid = conversationOid;
        }
        return this;
    }

    public long lookupConversationId() {
        if (conversationId == 0  && !StringUtil.isEmpty(conversationOid)) {
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
        return !origin.isValid()
                || (nonRealOid(oid) && status != DownloadStatus.DELETED && (
                        (status != DownloadStatus.SENDING && status != DownloadStatus.DRAFT) || !hasSomeContent()
                    ));
    }

    public boolean hasSomeContent() {
        return StringUtil.nonEmpty(name) || StringUtil.nonEmpty(summary) || StringUtil.nonEmpty(content) ||
                attachments.nonEmpty();
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
            return MyStringBuilder.formatKeyValue(this, "EMPTY");
        }
        MyStringBuilder builder = new MyStringBuilder();
        builder.withComma("","empty", this::isEmpty);
        builder.withCommaNonEmpty("id", noteId);
        builder.withComma("conversation_id", conversationId, () -> conversationId != noteId);
        builder.withComma("status", status);
        builder.withCommaNonEmpty("name", name);
        builder.withCommaNonEmpty("summary", summary);
        builder.withCommaNonEmpty("content", content);
        builder.atNewLine("audience", audience.toAudienceString(Actor.EMPTY));
        builder.withComma("oid", oid, () -> isRealOid(oid));
        builder.withComma("conversation_oid",conversationOid, () -> isRealOid(conversationOid));
        builder.withCommaNonEmpty("url", url);
        builder.withCommaNonEmpty("via", via);
        builder.withComma("updated", MyLog.debugFormatOfDate(updatedDate));
        builder.withComma("origin", origin.getName());
        if (attachments.nonEmpty()) {
            builder.atNewLine(attachments.toString());
        }
        if(getInReplyTo().nonEmpty()) {
            builder.atNewLine("inReplyTo", getInReplyTo().toString());
        }
        if(replies.size() > 0) {
            builder.atNewLine("Replies", replies.toString());
        }
        return MyStringBuilder.formatKeyValue(this, builder.toString());
    }

    @NonNull
    public AActivity getInReplyTo() {
        return inReplyTo == null ? AActivity.EMPTY : inReplyTo;
    }

    public void setInReplyTo(AActivity activity) {
        if (activity != null && activity.nonEmpty()) {
            inReplyTo = activity;
        }
    }

    public TriState getVisibility() {
        return audience().getVisibility();
    }

    public Note setVisibility(TriState visibility) {
        audience().setVisibility(visibility);
        return this;
    }

    public boolean isSensitive() {
        return isSensitive;
    }

    public Note setSensitive(boolean sensitive) {
        isSensitive = sensitive;
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
        return audience;
    }

    public Note shallowCopy() {
        Note note = fromOriginAndOid(origin, oid, status);
        note.noteId = noteId;
        note.setUpdatedDate(updatedDate);
        return note;
    }

    public Note copy(Optional<String> oidNew, Optional<Attachments> attachments) {
        return oidNew.filter(StringUtil::nonEmpty).isPresent() || attachments.filter(Attachments::nonEmpty).isPresent()
            ? new Note(this, oidNew, attachments)
            : this;
    }

    private Note(Note note, Optional<String> oidNew, Optional<Attachments> attachments) {
        origin = note.origin;
        oid = fixedOid(oidNew.orElse(note.oid));
        status = fixedStatus(oid, note.status);
        audience = note.audience.copy();
        noteId = note.noteId;
        updatedDate = note.updatedDate;
        name = note.name;
        summary = note.summary;
        setContentStored(note.content);
        inReplyTo = note.inReplyTo;
        replies = note.replies;
        conversationOid = note.conversationOid;
        via = note.via;
        url = note.url;
        this.attachments = attachments.orElseGet(note.attachments::copy);
        conversationId = note.conversationId;
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

    public static void requestDownload(MyAccount ma, long noteId, boolean isManuallyLaunched) {
        MyLog.v(TAG, () -> "Note id:" + noteId + " will be loaded from the Internet");
        CommandData command = CommandData.newItemCommand(CommandEnum.GET_NOTE, ma, noteId)
                .setManuallyLaunched(isManuallyLaunched)
                .setInForeground(isManuallyLaunched);
        MyServiceManager.sendCommand(command);
    }

    public void setAudience(Audience audience) {
        this.audience = audience;
    }

    public void setUpdatedNow(int level) {
        if (isEmpty() || level > 10) return;

        setUpdatedDate(MyLog.uniqueCurrentTimeMS());
        inReplyTo.setUpdatedNow(level + 1);
    }
}
