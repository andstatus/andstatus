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

import android.content.ContentValues;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.actor.ActorListType;
import org.andstatus.app.actor.ActorViewItem;
import org.andstatus.app.actor.ActorsOfNoteListLoader;
import org.andstatus.app.context.ActorInTimeline;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.AttachedImageFile;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.DownloadType;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.graphics.CachedImage;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.andstatus.app.data.DownloadStatus.LOADED;
import static org.andstatus.app.data.DownloadStatus.UNKNOWN;
import static org.andstatus.app.util.MyLog.COMMA;

public class NoteEditorData {
    public static final String TAG = NoteEditorData.class.getSimpleName();
    static final NoteEditorData EMPTY = NoteEditorData.newEmpty(MyAccount.EMPTY);

    public final AActivity activity;

    private DownloadData attachment = DownloadData.EMPTY;
    CachedImage image = null;

    private boolean replyToConversationParticipants = false;
    private boolean replyToMentionedActors = false;
    final MyContext myContext;
    public final MyAccount ma;
    public Timeline timeline = Timeline.EMPTY;

    public NoteEditorData(MyContext myContext, @NonNull MyAccount myAccount, long noteId, long inReplyToNoteId, boolean andLoad) {
        this(myAccount, myContext, toActivity(myContext, myAccount, noteId, andLoad), inReplyToNoteId, andLoad);
    }

    @NonNull
    private static AActivity toActivity(MyContext myContext, @NonNull MyAccount myAccount, long noteId, boolean andLoad) {
        MyAccount ma = myAccount.isValid() ? myAccount : myContext.accounts().getCurrentAccount();
        AActivity activity;
        if (noteId == 0 || !andLoad) {
            activity = AActivity.newPartialNote(ma.getActor(), ma.getActor(), "", System.currentTimeMillis(),
                    DownloadStatus.DRAFT);
        } else {
            final String noteOid = MyQuery.noteIdToStringColumnValue(NoteTable.NOTE_OID, noteId);
            activity = AActivity.newPartialNote(ma.getActor(),
                    ma.getActor(), noteOid,
                    System.currentTimeMillis(),
                    DownloadStatus.load(MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, noteId)));
            activity.setId(MyQuery.oidToId(myContext, OidEnum.ACTIVITY_OID, activity.accountActor.origin.getId(),
                    activity.getTimelinePosition().getPosition()));
            if (activity.getId() == 0) {
                activity.setId(MyQuery.noteIdToLongColumnValue(ActivityTable.LAST_UPDATE_ID, noteId));
            }
        }
        activity.getNote().noteId = noteId;
        return activity;
    }

    private NoteEditorData(MyAccount myAccount, MyContext myContext, @NonNull AActivity activity, long inReplyToNoteIdIn, boolean andLoad) {
        ma = myAccount.isValid() ? myAccount : myContext.accounts().getCurrentAccount();
        this.myContext = myContext;
        this.activity = activity;
        if (!andLoad) return;

        long noteId = activity.getNote().noteId;
        Note note = activity.getNote();
        note.setName(MyQuery.noteIdToStringColumnValue(NoteTable.NAME, noteId));
        note.setContent(MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId));
        note.audience().copy(Audience.load(myContext, activity.accountActor.origin, noteId));

        long inReplyToNoteId = inReplyToNoteIdIn == 0
                ? MyQuery.noteIdToLongColumnValue(NoteTable.IN_REPLY_TO_NOTE_ID, noteId)
                : inReplyToNoteIdIn;
        if (inReplyToNoteId != 0) {
            long inReplyToActorId = MyQuery.noteIdToLongColumnValue(NoteTable.IN_REPLY_TO_ACTOR_ID, noteId);
            if (inReplyToActorId == 0) {
                inReplyToActorId = MyQuery.noteIdToLongColumnValue(NoteTable.AUTHOR_ID, inReplyToNoteId);
            }
            final AActivity inReplyTo = AActivity.newPartialNote(getMyAccount().getActor(),
                    Actor.load(myContext, inReplyToActorId),
                    MyQuery.idToOid(OidEnum.NOTE_OID, inReplyToNoteId, 0), 0, UNKNOWN);
            final Note inReplyToNote = inReplyTo.getNote();
            inReplyToNote.noteId = inReplyToNoteId;
            inReplyToNote.setContent(MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, inReplyToNoteId));
            note.setInReplyTo(inReplyTo);
        }

        attachment = DownloadData.getSingleAttachment(noteId);
        if (attachment.getStatus() == LOADED) {
            AttachedImageFile imageFile = new AttachedImageFile(attachment.getDownloadId(),
                    attachment.getFilename(), attachment.mediaMetadata);
            image = imageFile.loadAndGetImage();
            note.attachments.add(Attachment.fromUri(attachment.getUri()));
        }
        MyLog.v(TAG, "Loaded " + this);
    }

    public static NoteEditorData newEmpty(MyAccount myAccount) {
        return newReply(myAccount, 0);
    }

    public static NoteEditorData newReply(MyAccount myAccount, long inReplyToNoteId) {
        return new NoteEditorData(MyContextHolder.get(), myAccount, 0, inReplyToNoteId,
                inReplyToNoteId != 0);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ma.hashCode();
        result = prime * result + attachment.getUri().hashCode();
        result = prime * result + activity.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        NoteEditorData other = (NoteEditorData) o;
        if (!ma.equals(other.ma))
            return false;
        if (!attachment.getUri().equals(other.attachment.getUri()))
            return false;
        return activity.equals(other.activity);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(activity.toString() + COMMA);
        if(attachment.nonEmpty()) {
            builder.append("attachment:" + attachment + COMMA);
        }
        if(replyToConversationParticipants) {
            builder.append("ReplyAll" + COMMA);
        }
        builder.append("ma:" + ma.getAccountName() + COMMA);
        return MyLog.formatKeyValue(this, builder.toString());
    }

    public String toVisibleSummary() {
        ContentValues values = new ContentValues();
        values.put(ActorTable.WEBFINGER_ID, activity.getActor().getWebFingerId());
        values.put(NoteTable.NAME, activity.getNote().getName());
        values.put(NoteTable.CONTENT, activity.getNote().getContent());
        if(attachment.nonEmpty()) {
            values.put(DownloadType.ATTACHMENT.name(), attachment.getUri().toString());
        }
        if(replyToConversationParticipants) {
            values.put("Reply", "all");
        }
        if(activity.getNote().getInReplyTo().nonEmpty()) {
            values.put("InReplyTo", activity.getNote().getInReplyTo().getNote().getName()
                    + COMMA
                    + activity.getNote().getInReplyTo().getNote().getContent());
        }
        values.put("recipients", activity.getNote().audience().getUsernames());
        values.put("ma", ma.getAccountName());
        return values.toString();
    }

    static NoteEditorData load(MyContext myContext, Long noteId) {
        return new NoteEditorData(myContext, myContext.accounts().fromActorId(
                MyQuery.noteIdToLongColumnValue(NoteTable.AUTHOR_ID, noteId)), noteId, 0, true);
    }

    NoteEditorData copy() {
        if (this.isValid()) {
            NoteEditorData data = new NoteEditorData(ma, MyContextHolder.get(), activity,
                    activity.getNote().getInReplyTo().getNote().noteId, false);
            data.attachment = attachment;
            data.image = image;
            data.replyToConversationParticipants = replyToConversationParticipants;
            return data;
        } else {
            return EMPTY;
        }
    }

    public void save(Uri imageUriToSave) {
        Note note = activity.getNote();
        Uri mediaUri = imageUriToSave.equals(Uri.EMPTY) ? attachment.getUri() : imageUriToSave;
        note.attachments.clear();
        if (!mediaUri.equals(Uri.EMPTY)) note.attachments.add(Attachment.fromUri(mediaUri));
        new DataUpdater(getMyAccount()).onActivity(activity);
        // TODO: Delete previous draft activities of this note
    }

    MyAccount getMyAccount() {
        return ma;
    }
    
    boolean isEmpty() {
        return activity.getNote().isEmpty();
    }

    public boolean isValid() {
        return this != EMPTY && ma.isValid();
    }

    public boolean mayBeEdited() {
        return Note.mayBeEdited(ma.getOrigin().getOriginType(), activity.getNote().getStatus());
    }

    public NoteEditorData setContent(String content) {
        activity.getNote().setContent(content);
        return this;
    }

    @NonNull
    public String getContent() {
        return activity.getNote().getContent();
    }

    @NonNull
    public DownloadData getAttachment() {
        return attachment;
    }

    public NoteEditorData setNoteId(long noteId) {
        activity.getNote().noteId = noteId;
        return this;
    }

    public long getNoteId() {
        return activity.getNote().noteId;
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
        if (ma.isValid() && getInReplyToNoteId() != 0) {
            if (replyToConversationParticipants) {
                addConversationParticipantsBeforeText();
            } else if (replyToMentionedActors) {
                addMentionedActorsBeforeText();
            } else {
                addActorsBeforeText(new ArrayList<>());
            }
        }
        return this;
    }

    private void addConversationParticipantsBeforeText() {
        ConversationLoader<? extends ConversationMemberItem> loader =
                new ConversationLoaderFactory<ConversationMemberItem>().getLoader(
                ConversationMemberItem.EMPTY, MyContextHolder.get(), ma, getInReplyToNoteId(), false);
        loader.load(progress -> {});
        addActorsBeforeText(loader.getList().stream()
                .filter(o -> o.activityType == ActivityType.UPDATE)
                .map(o -> o.author.getActorId()).collect(Collectors.toList()));
    }

    private void addMentionedActorsBeforeText() {
        ActorsOfNoteListLoader loader = new ActorsOfNoteListLoader(ActorListType.ACTORS_OF_NOTE, ma,
                getInReplyToNoteId(), "").setMentionedOnly(true);
        loader.load(null);
        List<Long> toMention = new ArrayList<>();
        for(ActorViewItem item : loader.getList()) {
            toMention.add(item.getActorId());
        }
        addActorsBeforeText(toMention);
    }

    private void addActorsBeforeText(List<Long> toMention) {
        toMention.add(0, MyQuery.noteIdToLongColumnValue(NoteTable.AUTHOR_ID, getInReplyToNoteId()));
        List<Long> mentioned = new ArrayList<>();
        mentioned.add(ma.getActorId());  // Don't mention an author of this note
        String mentions = "";
        for(Long actorId : toMention) {
            if (actorId != 0 && !mentioned.contains(actorId)) {
                mentioned.add(actorId);
                String name = MyQuery.actorIdToName(myContext, actorId, getActorInTimeline());
                if (!StringUtils.isEmpty(name)) {
                    String mentionText = "@" + name + " ";
                    if (StringUtils.isEmpty(getContent()) || !(getContent() + " ").contains(mentionText)) {
                        mentions = mentions.trim() + " " + mentionText;
                    }
                }
            }
        }
        if (!StringUtils.isEmpty(mentions)) {
            setContent(mentions.trim() + " " + getContent());
        }
    }

    public long getInReplyToNoteId() {
        return activity.getNote().getInReplyTo().getNote().noteId;
    }

    public NoteEditorData appendMentionedActorToText(long mentionedActorId) {
        String name = MyQuery.actorIdToName(myContext, mentionedActorId, getActorInTimeline());
        if (!StringUtils.isEmpty(name)) {
            String bodyText2 = "@" + name + " ";
            if (!StringUtils.isEmpty(getContent()) && !(getContent() + " ").contains(bodyText2)) {
                bodyText2 = getContent().trim() + " " + bodyText2;
            }
            setContent(bodyText2);
        }
        return this;
    }

    private ActorInTimeline getActorInTimeline() {
        return ma.getOrigin().isMentionAsWebFingerId() ? ActorInTimeline.WEBFINGER_ID : ActorInTimeline.USERNAME;
    }

    public NoteEditorData addRecipientId(long actorId) {
        activity.getNote().addRecipient(Actor.load(MyContextHolder.get(), actorId));
        return this;
    }

    public TriState getPublic() {
        return activity.getNote().getPublic();
    }

    public NoteEditorData setPublic(boolean isPublic) {
        if (canChangeIsPublic() && (isPublic || this.activity.getNote().getPublic().known)) {
            this.activity.getNote().setPublic(isPublic ? TriState.TRUE : TriState.FALSE);
        }
        return this;
    }

    public NoteEditorData setTimeline(Timeline timeline) {
        this.timeline = timeline;
        return this;
    }

    public NoteEditorData setName(String name) {
        activity.getNote().setName(name);
        return this;
    }

    public boolean canChangeIsPublic() {
        return activity.getNote().audience().hasNonPublic()
                && ma.getOrigin().getOriginType().isPublicWithAudienceAllowed
                && (getInReplyToNoteId() == 0 || ma.getOrigin().getOriginType().isPrivateNoteAllowsReply());
    }
}
