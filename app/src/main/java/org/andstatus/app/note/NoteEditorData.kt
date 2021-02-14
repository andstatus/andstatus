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

import androidx.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.actor.ActorViewItem;
import org.andstatus.app.actor.MentionedActorsLoader;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.AttachedImageFiles;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.DownloadType;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.data.TextMediaType;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.graphics.CacheName;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Attachments;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.Visibility;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.andstatus.app.data.DownloadStatus.UNKNOWN;
import static org.andstatus.app.util.MyStringBuilder.COMMA;
import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;

public class NoteEditorData implements IsEmpty {
    public static final String TAG = NoteEditorData.class.getSimpleName();
    static final NoteEditorData EMPTY = NoteEditorData.newEmpty(MyAccount.EMPTY);

    public final AActivity activity;
    private AttachedImageFiles attachedImageFiles = AttachedImageFiles.EMPTY;

    private boolean replyToConversationParticipants = false;
    private boolean replyToMentionedActors = false;
    final MyContext myContext;
    public final MyAccount ma;
    public Timeline timeline = Timeline.EMPTY;

    public NoteEditorData(@NonNull MyAccount myAccount, long noteId, boolean initialize,
                          long inReplyToNoteId, boolean andLoad) {
        this(myAccount, toActivity(myAccount, noteId, andLoad));
        if (andLoad) {
            load(inReplyToNoteId);
        }
        if (initialize) {
            activity.initializePublicAndFollowers();
        }
    }

    @NonNull
    private static AActivity toActivity(@NonNull MyAccount ma, long noteId, boolean andLoad) {
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
            activity.setId(MyQuery.oidToId(ma.getOrigin().myContext, OidEnum.ACTIVITY_OID,
                    activity.accountActor.origin.getId(),
                    activity.getOid()));
            if (activity.getId() == 0) {
                activity.setId(MyQuery.noteIdToLongColumnValue(ActivityTable.LAST_UPDATE_ID, noteId));
            }
        }
        activity.getNote().noteId = noteId;
        return activity;
    }

    private NoteEditorData(MyAccount myAccount, @NonNull AActivity activity) {
        ma = myAccount;
        this.myContext = ma.getOrigin().myContext;
        this.activity = activity;
    }

    private void load(long inReplyToNoteIdIn) {
        Note note = activity.getNote();
        long noteId = note.noteId;
        note.setName(MyQuery.noteIdToStringColumnValue(NoteTable.NAME, noteId));
        note.setSummary(MyQuery.noteIdToStringColumnValue(NoteTable.SUMMARY, noteId));
        note.setSensitive(MyQuery.isSensitive(noteId));
        note.setContentStored(MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId));
        note.setAudience(Audience.load(activity.accountActor.origin, noteId, Optional.empty()));

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
                    MyQuery.idToOid(myContext, OidEnum.NOTE_OID, inReplyToNoteId, 0),
                    DATETIME_MILLIS_NEVER,
                    UNKNOWN);
            final Note inReplyToNote = inReplyTo.getNote();
            inReplyToNote.noteId = inReplyToNoteId;
            inReplyToNote.setName(MyQuery.noteIdToStringColumnValue(NoteTable.NAME, inReplyToNoteId));
            inReplyToNote.setSummary(MyQuery.noteIdToStringColumnValue(NoteTable.SUMMARY, inReplyToNoteId));
            inReplyToNote.audience().setVisibility(Visibility.fromNoteId(inReplyToNoteId));
            inReplyToNote.setSensitive(MyQuery.isSensitive(inReplyToNoteId));
            inReplyToNote.setContentStored(MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, inReplyToNoteId));
            note.setInReplyTo(inReplyTo);
        }

        attachedImageFiles = AttachedImageFiles.load(myContext, noteId);
        attachedImageFiles.list.forEach( imageFile -> {
            imageFile.preloadImageAsync(CacheName.ATTACHED_IMAGE);
        });
        activity.setNote(note.withAttachments(Attachments.load(myContext, noteId)));
        MyLog.v(TAG, () -> "Loaded " + this);
    }

    public static NoteEditorData newEmpty(MyAccount myAccount) {
        return newReplyTo(0, myAccount);
    }

    static NoteEditorData newReplyTo(long inReplyToNoteId, MyAccount myAccount) {
        return new NoteEditorData(myAccount.getValidOrCurrent(myContextHolder.getNow()), 0, true,
                inReplyToNoteId, inReplyToNoteId != 0);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ma.hashCode();
        result = prime * result + attachedImageFiles.hashCode();
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
        if (!attachedImageFiles.equals(other.attachedImageFiles))
            return false;
        return activity.equals(other.activity);
    }

    @Override
    public String toString() {
        MyStringBuilder builder = MyStringBuilder.of(activity.toString());
        if(attachedImageFiles.nonEmpty()) {
            builder.withComma(attachedImageFiles.toString());
        }
        if(replyToConversationParticipants) {
            builder.withComma("ReplyAll");
        }
        builder.withComma("ma", ma.getAccountName());
        return MyStringBuilder.formatKeyValue(this, builder);
    }

    String toTestSummary() {
        ContentValues values = new ContentValues();
        values.put(ActorTable.WEBFINGER_ID, activity.getActor().getWebFingerId());
        values.put(NoteTable.NAME, activity.getNote().getName());
        values.put(NoteTable.SUMMARY, activity.getNote().getSummary());
        values.put(NoteTable.SENSITIVE, activity.getNote().isSensitive());
        values.put(NoteTable.CONTENT, activity.getNote().getContent());
        if(attachedImageFiles.nonEmpty()) {
            values.put(DownloadType.ATTACHMENT.name(), attachedImageFiles.list.toString());
        }
        if(replyToConversationParticipants) {
            values.put("Reply", "all");
        }
        AActivity inReplyTo = activity.getNote().getInReplyTo();
        if(inReplyTo.nonEmpty()) {
            String name = inReplyTo.getNote().getName();
            String summary = inReplyTo.getNote().getSummary();
            values.put("InReplyTo", (StringUtil.nonEmpty(name) ? name + COMMA : "") +
                    (StringUtil.nonEmpty(summary) ? summary + COMMA : "") +
                    inReplyTo.getNote().getContent());
        }
        values.put("audience", activity.getNote().audience().toAudienceString(inReplyTo.getAuthor()));
        values.put("ma", ma.getAccountName());
        return values.toString();
    }

    static NoteEditorData load(MyContext myContext, Long noteId) {
        long authorId = MyQuery.noteIdToLongColumnValue(NoteTable.AUTHOR_ID, noteId);
        MyAccount ma = myContext.accounts().fromActorId(authorId).getValidOrCurrent(myContext);
        return new NoteEditorData(ma, noteId, false, 0, true);
    }

    NoteEditorData copy() {
        if (this.isValid()) {
            NoteEditorData data = new NoteEditorData(ma, activity);
            data.attachedImageFiles = attachedImageFiles;
            data.replyToConversationParticipants = replyToConversationParticipants;
            return data;
        } else {
            return EMPTY;
        }
    }

    public void addAttachment(Uri uri, Optional<String> mediaType) {
        activity.addAttachment(
                Attachment.fromUriAndMimeType(uri, mediaType.orElse("")),
                ma.getOrigin().getOriginType().getMaxAttachmentsToSend()
        );
    }

    public void save() {
        recreateKnownAudience(activity);
        new DataUpdater(getMyAccount()).onActivity(activity);
        // TODO: Delete previous draft activities of this note
    }

    public static void recreateKnownAudience(AActivity activity) {
        Note note = activity.getNote();
        if (note == Note.EMPTY) return;

        Audience audience = new Audience(activity.accountActor.origin).withVisibility(note.audience().getVisibility());
        audience.add(note.getInReplyTo().getActor());
        audience.addActorsFromContent(note.getContent(), activity.getAuthor(), note.getInReplyTo().getActor());
        note.setAudience(audience);
    }

    MyAccount getMyAccount() {
        return ma;
    }

    @Override
    public boolean isEmpty() {
        return activity.getNote().isEmpty();
    }

    public boolean isValid() {
        return this != EMPTY && ma.isValid();
    }

    public boolean mayBeEdited() {
        return Note.mayBeEdited(ma.getOrigin().getOriginType(), activity.getNote().getStatus());
    }

    public NoteEditorData setContent(String content, TextMediaType mediaType) {
        activity.getNote().setContent(content, mediaType);
        return this;
    }

    @NonNull
    public String getContent() {
        return activity.getNote().getContent();
    }

    @NonNull
    public AttachedImageFiles getAttachedImageFiles() {
        return attachedImageFiles;
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
        ConversationLoader loader =
                new ConversationLoaderFactory().getLoader(
                ConversationViewItem.EMPTY, myContextHolder.getNow(), ma.getOrigin(), getInReplyToNoteId(), false);
        loader.load(progress -> {});
        addActorsBeforeText(loader.getList().stream()
                .filter(ConversationViewItem::isActorAConversationParticipant)
                .map(o -> o.author.getActor()).collect(Collectors.toList()));
    }

    private void addMentionedActorsBeforeText() {
        MentionedActorsLoader loader = new MentionedActorsLoader(myContext, ma.getOrigin(),
                getInReplyToNoteId());
        loader.load(null);
        addActorsBeforeText(loader.getList().stream().map(ActorViewItem::getActor).collect(Collectors.toList()));
    }

    public NoteEditorData addActorsBeforeText(List<Actor> toMention) {
        if (getInReplyToNoteId() != 0) {
            toMention.add(0, Actor.load(myContext, MyQuery.noteIdToLongColumnValue(NoteTable.AUTHOR_ID, getInReplyToNoteId())));
        }
        List<String> mentionedNames = new ArrayList<>();
        mentionedNames.add(ma.getActor().getUniqueName());  // Don't mention the author of this note
        MyStringBuilder mentions = new MyStringBuilder();
        for(Actor actor : toMention) {
            if (actor.isEmpty()) continue;

            String name = actor.getUniqueName();
            if (!StringUtil.isEmpty(name) && !mentionedNames.contains(name)) {
                mentionedNames.add(name);
                String mentionText = "@" + name;
                if (StringUtil.isEmpty(getContent()) || !(getContent() + " ").contains(mentionText + " ")) {
                    mentions.withSpace(mentionText);
                }
            }
        }
        if (mentions.nonEmpty()) {
            setContent(mentions.toString() + " " + getContent(), TextMediaType.HTML);
        }
        return this;
    }

    public long getInReplyToNoteId() {
        return activity.getNote().getInReplyTo().getNote().noteId;
    }

    public NoteEditorData appendMentionedActorToText(Actor mentionedActor) {
        String name = mentionedActor.getUniqueName();
        if (!StringUtil.isEmpty(name)) {
            String bodyText2 = "@" + name + " ";
            if (!StringUtil.isEmpty(getContent()) && !(getContent() + " ").contains(bodyText2)) {
                bodyText2 = getContent().trim() + " " + bodyText2;
            }
            setContent(bodyText2, TextMediaType.HTML);
        }
        return this;
    }

    public NoteEditorData addToAudience(long actorId) {
        return addToAudience(Actor.load(myContextHolder.getNow(), actorId));
    }

    public NoteEditorData addToAudience(Actor actor) {
        activity.getNote().audience().add(actor);
        return this;
    }

    public Visibility getVisibility() {
        return activity.getNote().audience().getVisibility();
    }

    public NoteEditorData setPublicAndFollowers(boolean isPublic, boolean isFollowers) {
        if (canChangeVisibility()) {
            this.activity.getNote().audience().withVisibility(Visibility
                        .fromCheckboxes(isPublic, canChangeIsFollowers() && isFollowers));
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

    public NoteEditorData setSummary(String summary) {
        activity.getNote().setSummary(summary);
        return this;
    }

    public boolean canChangeVisibility() {
        return ma.getOrigin().getOriginType().visibilityChangeAllowed
                && (getInReplyToNoteId() == 0 || ma.getOrigin().getOriginType().isPrivateNoteAllowsReply());
    }

    public boolean canChangeIsFollowers() {
        return ma.getOrigin().getOriginType().isFollowersChangeAllowed
                && (getInReplyToNoteId() == 0 || ma.getOrigin().getOriginType().isPrivateNoteAllowsReply());
    }

    public boolean getSensitive() {
        return activity.getNote().isSensitive();
    }

    public NoteEditorData setSensitive(boolean isSensitive) {
        if (canChangeIsSensitive()) {
            this.activity.getNote().setSensitive(isSensitive);
        }
        return this;
    }

    public boolean canChangeIsSensitive() {
        return ma.getOrigin().getOriginType().isSensitiveChangeAllowed;
    }

    NoteEditorData copySensitiveProperty() {
        if (MyQuery.isSensitive(getInReplyToNoteId())) {
            activity.getNote().setSensitive(true);
            StringUtil.optNotEmpty(MyQuery.noteIdToStringColumnValue(NoteTable.SUMMARY, getInReplyToNoteId()))
                    .ifPresent(this::setSummary);
        }
        return this;
    }
}
