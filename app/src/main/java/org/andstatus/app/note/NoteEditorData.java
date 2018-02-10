/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.graphics.Point;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.ActorInTimeline;
import org.andstatus.app.data.AttachedImageFile;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.graphics.CachedImage;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.actor.ActorListType;
import org.andstatus.app.actor.ActorViewItem;
import org.andstatus.app.actor.ActorsOfNoteListLoader;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;

import java.util.ArrayList;
import java.util.List;

import static org.andstatus.app.data.DownloadStatus.LOADED;
import static org.andstatus.app.data.DownloadStatus.UNKNOWN;

public class NoteEditorData {
    public static final String TAG = NoteEditorData.class.getSimpleName();
    static final NoteEditorData EMPTY = NoteEditorData.newEmpty(null);

    private long noteId = 0;
    String noteOid = "";
    private long activityId = 0;
    public DownloadStatus status = DownloadStatus.DRAFT;
    @NonNull
    public volatile String body = "";

    private DownloadData downloadData = DownloadData.EMPTY;
    CachedImage image = null;

    private TriState isPrivate = TriState.UNKNOWN;

    /**
     * Id of the note to which we are replying.
     *  0 - This note is not a Reply.
     * -1 - is non-existent id.
     */
    public long inReplyToNoteId = 0;
    private long inReplyToActorId = 0;
    String inReplyToBody = "";
    private boolean replyToConversationParticipants = false;
    private boolean replyToMentionedActors = false;
    public Audience recipients = new Audience();
    public MyAccount ma = MyAccount.EMPTY;

    private NoteEditorData(MyAccount myAccount) {
        ma = myAccount == null ? MyAccount.EMPTY : myAccount;
    }

    public static NoteEditorData newEmpty(MyAccount myAccount) {
        return new NoteEditorData(myAccount);
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((ma == null) ? 0 : ma.hashCode());
        result = prime * result + getMediaUri().hashCode();
        result = prime * result + body.hashCode();
        result = prime * result + recipients.hashCode();
        result = prime * result + (int) (inReplyToNoteId ^ (inReplyToNoteId >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        NoteEditorData other = (NoteEditorData) o;
        if (ma == null) {
            if (other.ma != null)
                return false;
        } else if (!ma.equals(other.ma))
            return false;
        if (!getMediaUri().equals(other.getMediaUri()))
            return false;
        if (!body.equals(other.body))
            return false;
        if (!recipients.equals(other.recipients))
            return false;
        return inReplyToNoteId == other.inReplyToNoteId;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("id:" + noteId + ",");
        builder.append("status:" + status + ",");
        if(!TextUtils.isEmpty(body)) {
            builder.append("text:'" + body + "',");
        }
        if(!UriUtils.isEmpty(downloadData.getUri())) {
            builder.append("downloadData:" + downloadData + ",");
        }
        if(inReplyToNoteId != 0) {
            builder.append("inReplyTo:" + inReplyToNoteId + " by " + inReplyToActorId + ",");
        }
        if(replyToConversationParticipants) {
            builder.append("ReplyAll,");
        }
        if(recipients.nonEmpty()) {
            builder.append("recipients:" + recipients + ",");
        }
        builder.append("ma:" + ma.getAccountName() + ",");
        return MyLog.formatKeyValue(this, builder.toString());
    }

    static NoteEditorData load(Long noteId) {
        NoteEditorData data;
        if (noteId != 0) {
            MyAccount ma = MyContextHolder.get().accounts().fromActorId(
                    MyQuery.noteIdToLongColumnValue(NoteTable.AUTHOR_ID, noteId));
            data = new NoteEditorData(ma);
            data.noteId = noteId;
            data.noteOid = MyQuery.noteIdToStringColumnValue(NoteTable.NOTE_OID, noteId);
            data.activityId = MyQuery.noteIdToLongColumnValue(ActivityTable.LAST_UPDATE_ID, noteId);
            data.status = DownloadStatus.load(MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, noteId));
            data.setBody(MyQuery.noteIdToStringColumnValue(NoteTable.BODY, noteId));
            data.downloadData = DownloadData.getSingleForNote(noteId, MyContentType.IMAGE, Uri.EMPTY);
            if (data.downloadData.getStatus() == LOADED) {
                AttachedImageFile imageFile = new AttachedImageFile(data.downloadData.getDownloadId(),
                        data.downloadData.getFilename());
                data.image = imageFile.loadAndGetImage();
            }
            data.inReplyToNoteId = MyQuery.noteIdToLongColumnValue(NoteTable.IN_REPLY_TO_NOTE_ID, noteId);
            data.inReplyToActorId = MyQuery.noteIdToLongColumnValue(NoteTable.IN_REPLY_TO_ACTOR_ID, noteId);
            data.inReplyToBody = MyQuery.noteIdToStringColumnValue(NoteTable.BODY, data.inReplyToNoteId);
            data.recipients = Audience.fromNoteId(ma.getOrigin(), noteId);
            data.isPrivate = MyQuery.noteIdToTriState(NoteTable.PRIVATE, noteId);
            MyLog.v(TAG, "Loaded " + data);
        } else {
            data = new NoteEditorData(MyContextHolder.get().accounts().getCurrentAccount());
            MyLog.v(TAG, "Empty data created");
        }
        return data;
    }

    NoteEditorData copy() {
        if (this.isValid()) {
            NoteEditorData data = NoteEditorData.newEmpty(ma);
            data.noteId = noteId;
            data.noteOid = noteOid;
            data.status = status;
            data.setBody(body);
            data.downloadData = downloadData;
            data.image = image;
            data.inReplyToNoteId = inReplyToNoteId;
            data.inReplyToActorId = inReplyToActorId;
            data.inReplyToBody = inReplyToBody;
            data.replyToConversationParticipants = replyToConversationParticipants;
            data.recipients.addAll(recipients);
            return data;
        } else {
            return EMPTY;
        }
    }

    public void save(Uri imageUriToSave) {
        AActivity activity = AActivity.newPartialNote(getMyAccount().getActor(), noteOid,
                System.currentTimeMillis(), status);
        activity.setActor(activity.accountActor);
        Note note = activity.getNote();
        note.noteId = getNoteId();
        note.setBody(body);
        note.addRecipients(recipients);
        if (inReplyToNoteId != 0) {
            final AActivity inReplyTo = AActivity.newPartialNote(getMyAccount().getActor(),
                    MyQuery.idToOid(OidEnum.NOTE_OID, inReplyToNoteId, 0), 0, UNKNOWN);
            if (inReplyToActorId == 0) {
                inReplyToActorId = MyQuery.noteIdToLongColumnValue(NoteTable.AUTHOR_ID, inReplyToNoteId);
            }
            inReplyTo.setActor(Actor.fromOriginAndActorId(getMyAccount().getOrigin(), inReplyToActorId));
            note.setInReplyTo(inReplyTo);
        }
        Uri mediaUri = imageUriToSave.equals(Uri.EMPTY) ? downloadData.getUri() : imageUriToSave;
        if (!mediaUri.equals(Uri.EMPTY)) {
            note.attachments.add(
                    Attachment.fromUriAndContentType(mediaUri, MyContentType.IMAGE));
        }
        DataUpdater di = new DataUpdater(getMyAccount());
        setNoteId(di.onActivity(activity).getNote().noteId);
        if (activity.getId() != 0 && activityId != activity.getId()) {
            if (activityId != 0 && status != LOADED) {
                MyProvider.deleteActivity(MyContextHolder.get(), activityId, noteId, false);
            }
            activityId = activity.getId();
        }
    }

    MyAccount getMyAccount() {
        return ma;
    }
    
    boolean isEmpty() {
        return TextUtils.isEmpty(body) && getMediaUri().equals(Uri.EMPTY) && noteId == 0;
    }

    public boolean isValid() {
        return this != EMPTY && ma.isValid();
    }

    public boolean mayBeEdited() {
        return Note.mayBeEdited(ma.getOrigin().getOriginType(), status);
    }

    public NoteEditorData setBody(String bodyIn) {
        body = bodyIn == null ? "" : bodyIn.trim();
        return this;
    }

    public Uri getMediaUri() {
        return downloadData.getUri();
    }

    public long getImageFileSize() {
        return downloadData.getFile().getSize();
    }

    public Point getImageSize() {
        return image == null ? new Point(0,0) : image.getImageSize();
    }

    public NoteEditorData setNoteId(long msgIdIn) {
        noteId = msgIdIn;
        return this;
    }

    public long getNoteId() {
        return noteId;
    }

    public NoteEditorData setInReplyToNoteId(long noteId) {
        inReplyToNoteId = noteId;
        return this;
    }
    
    public NoteEditorData setReplyToConversationParticipants(boolean replyToConversationParticipants) {
        this.replyToConversationParticipants = replyToConversationParticipants;
        return this;
    }

    public NoteEditorData setReplyToMentionedActors(boolean replyToMentionedUsers) {
        this.replyToMentionedActors = replyToMentionedUsers;
        return this;
    }

    public NoteEditorData addMentionsToText() {
        if (ma.isValid() && inReplyToNoteId != 0) {
            if (replyToConversationParticipants) {
                addConversationParticipantsBeforeText();
            } else if (replyToMentionedActors) {
                addMentionedActorsBeforeText();
            } else {
                addActorsBeforeText(new ArrayList<Long>());
            }
        }
        return this;
    }

    private void addConversationParticipantsBeforeText() {
        ConversationLoader<? extends ConversationMemberItem> loader =
                new ConversationLoaderFactory<ConversationMemberItem>().getLoader(
                ConversationMemberItem.EMPTY, MyContextHolder.get(), ma, inReplyToNoteId, false);
        loader.load(null);
        List<Long> toMention = new ArrayList<>();
        for(ConversationMemberItem item : loader.getList()) {
            if (!item.isFavoritingAction) {
                toMention.add(item.authorId);
            }
        }
        addActorsBeforeText(toMention);
    }

    private void addMentionedActorsBeforeText() {
        ActorsOfNoteListLoader loader = new ActorsOfNoteListLoader(ActorListType.ACTORS_OF_NOTE, ma, inReplyToNoteId
                , "").setMentionedOnly(true);
        loader.load(null);
        List<Long> toMention = new ArrayList<>();
        for(ActorViewItem item : loader.getList()) {
            toMention.add(item.getActorId());
        }
        addActorsBeforeText(toMention);
    }

    private void addActorsBeforeText(List<Long> toMention) {
        toMention.add(0, MyQuery.noteIdToLongColumnValue(NoteTable.AUTHOR_ID, inReplyToNoteId));
        List<Long> mentioned = new ArrayList<>();
        mentioned.add(ma.getActorId());  // Don't mention an author of this note
        String mentions = "";
        for(Long actorId : toMention) {
            if (actorId != 0 && !mentioned.contains(actorId)) {
                mentioned.add(actorId);
                String name = MyQuery.actorIdToName(null, actorId, getActorInTimeline());
                if (!TextUtils.isEmpty(name)) {
                    String mentionText = "@" + name + " ";
                    if (TextUtils.isEmpty(body) || !(body + " ").contains(mentionText)) {
                        mentions = mentions.trim() + " " + mentionText;
                    }
                }
            }
        }
        if (!TextUtils.isEmpty(mentions)) {
            setBody(mentions.trim() + " " + body);
        }
    }

    public NoteEditorData appendMentionedActorToText(long mentionedActorId) {
        String name = MyQuery.actorIdToName(null, mentionedActorId, getActorInTimeline());
        if (!TextUtils.isEmpty(name)) {
            String bodyText2 = "@" + name + " ";
            if (!TextUtils.isEmpty(body) && !(body + " ").contains(bodyText2)) {
                bodyText2 = body.trim() + " " + bodyText2;
            }
            setBody(bodyText2);
        }
        return this;
    }

    private ActorInTimeline getActorInTimeline() {
        return ma.getOrigin().isMentionAsWebFingerId() ? ActorInTimeline.WEBFINGER_ID : ActorInTimeline.USERNAME;
    }

    public NoteEditorData addRecipientId(long actorId) {
        recipients.add(Actor.fromOriginAndActorId(getMyAccount().getOrigin(), actorId));
        return this;
    }

    public boolean isPrivate() {
        return isPrivate == TriState.TRUE;
    }

    public boolean nonPrivate() {
        return !isPrivate();
    }

    public NoteEditorData setPrivate(TriState isPrivate) {
        this.isPrivate = isPrivate;
        return this;
    }
}
