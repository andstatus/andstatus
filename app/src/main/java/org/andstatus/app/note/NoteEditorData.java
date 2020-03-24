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
import org.andstatus.app.actor.ActorListType;
import org.andstatus.app.actor.ActorViewItem;
import org.andstatus.app.actor.ActorsOfNoteListLoader;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
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
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.andstatus.app.data.DownloadStatus.UNKNOWN;
import static org.andstatus.app.util.MyStringBuilder.COMMA;

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
            if (ma.getOrigin().getOriginType().isPublicChangeAllowed) {
                activity.getNote().setPublic(TriState.TRUE);
            }
            if (ma.getOrigin().getOriginType().isFollowersChangeAllowed) {
                activity.getNote().audience().setFollowers(true);
            }

            TriState isPublic = activity.getNote().getInReplyTo().getNote().audience().getPublic();
            if (isPublic.known) {
                activity.getNote().setPublic(isPublic);
                if (ma.getOrigin().getOriginType().isFollowersChangeAllowed) {
                    activity.getNote().audience().setFollowers(isPublic.isTrue);
                }
            }
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
                    activity.getTimelinePosition().getPosition()));
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
        note.setSensitive(MyQuery.noteIdToLongColumnValue(NoteTable.SENSITIVE, noteId) == 1);
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
                    MyQuery.idToOid(myContext, OidEnum.NOTE_OID, inReplyToNoteId, 0), 0, UNKNOWN);
            final Note inReplyToNote = inReplyTo.getNote();
            inReplyToNote.noteId = inReplyToNoteId;
            inReplyToNote.setName(MyQuery.noteIdToStringColumnValue(NoteTable.NAME, inReplyToNoteId));
            inReplyToNote.setSummary(MyQuery.noteIdToStringColumnValue(NoteTable.SUMMARY, inReplyToNoteId));
            inReplyToNote.setPublic(MyQuery.noteIdToTriState(NoteTable.PUBLIC, inReplyToNoteId));
            inReplyToNote.setSensitive(MyQuery.noteIdToLongColumnValue(NoteTable.SENSITIVE, inReplyToNoteId) == 1);
            inReplyToNote.setContentStored(MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, inReplyToNoteId));
            note.setInReplyTo(inReplyTo);
        }

        attachedImageFiles = AttachedImageFiles.load(myContext, noteId);
        attachedImageFiles.list.forEach( imageFile -> {
            imageFile.preloadImageAsync(CacheName.ATTACHED_IMAGE);
        });
        activity.setNote(note.copy(Optional.empty(), Optional.of(Attachments.load(myContext, noteId))));
        MyLog.v(TAG, () -> "Loaded " + this);
    }

    public static NoteEditorData newEmpty(MyAccount myAccount) {
        return newReplyTo(0, myAccount);
    }

    static NoteEditorData newReplyTo(long inReplyToNoteId, MyAccount myAccount) {
        return new NoteEditorData(myAccount.getValidOrCurrent(MyContextHolder.get()), 0, true,
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

    public String toVisibleSummary() {
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
        if(activity.getNote().getInReplyTo().nonEmpty()) {
            String name = activity.getNote().getInReplyTo().getNote().getName();
            String summary = activity.getNote().getInReplyTo().getNote().getSummary();
            values.put("InReplyTo", (StringUtil.nonEmpty(name) ? name + COMMA : "") +
                    (StringUtil.nonEmpty(summary) ? summary + COMMA : "") +
                    activity.getNote().getInReplyTo().getNote().getContent());
        }
        values.put("audience", activity.getNote().audience().getUsernames());
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
        recreateAudience(activity);
        new DataUpdater(getMyAccount()).onActivity(activity);
        // TODO: Delete previous draft activities of this note
    }

    public static void recreateAudience(AActivity activity) {
        Audience audience = new Audience(activity.accountActor.origin);
        audience.add(activity.getNote().getInReplyTo().getActor());
        audience.addActorsFromContent(activity.getNote().getContent(),
                activity.getAuthor(), activity.getNote().getInReplyTo().getActor());
        audience.setPublic(activity.getNote().audience().getPublic());
        audience.setFollowers(activity.getNote().audience().isFollowers());
        activity.getNote().setAudience(audience);
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
        ConversationLoader<? extends ConversationMemberItem> loader =
                new ConversationLoaderFactory<ConversationMemberItem>().getLoader(
                ConversationMemberItem.EMPTY, MyContextHolder.get(), ma.getOrigin(), getInReplyToNoteId(), false);
        loader.load(progress -> {});
        addActorsBeforeText(loader.getList().stream()
                .filter(ConversationMemberItem::isActorAConversationParticipant)
                .map(o -> o.author.getActor()).collect(Collectors.toList()));
    }

    private void addMentionedActorsBeforeText() {
        ActorsOfNoteListLoader loader = new ActorsOfNoteListLoader(myContext, ActorListType.ACTORS_OF_NOTE, ma.getOrigin(),
                getInReplyToNoteId(), "").setMentionedOnly(true);
        loader.load(null);
        addActorsBeforeText(loader.getList().stream().map(ActorViewItem::getActor).collect(Collectors.toList()));
    }

    private void addActorsBeforeText(List<Actor> toMention) {
        toMention.add(0, Actor.load(myContext, MyQuery.noteIdToLongColumnValue(NoteTable.AUTHOR_ID, getInReplyToNoteId())));
        List<String> mentionedNames = new ArrayList<>();
        mentionedNames.add(ma.getActor().getUniqueName());  // Don't mention the author of this note
        MyStringBuilder mentions = new MyStringBuilder();
        for(Actor actor : toMention) {
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
        return addToAudience(Actor.load(MyContextHolder.get(), actorId));
    }

    public NoteEditorData addToAudience(Actor actor) {
        activity.getNote().audience().add(actor);
        return this;
    }

    public TriState getPublic() {
        return activity.getNote().getPublic();
    }

    public NoteEditorData setPublic(boolean isPublic) {
        if (canChangeIsPublic()) {
            this.activity.getNote().setPublic(isPublic ? TriState.TRUE : TriState.FALSE);
        }
        return this;
    }

    public boolean isFollowers() {
        return activity.getNote().audience().isFollowers();
    }

    public NoteEditorData setFollowers(boolean isFollowers) {
        if (canChangeIsFollowers()) {
            this.activity.getNote().audience().setFollowers(isFollowers);
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

    public boolean canChangeIsPublic() {
        return ma.getOrigin().getOriginType().isPublicChangeAllowed
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
        if (MyQuery.noteIdToLongColumnValue(NoteTable.SENSITIVE, getInReplyToNoteId()) != 1) return this;

        activity.getNote().setSensitive(true);
        StringUtil.optNotEmpty(MyQuery.noteIdToStringColumnValue(NoteTable.SUMMARY, getInReplyToNoteId()))
                .ifPresent(this::setSummary);
        return this;
    }
}
