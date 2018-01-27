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

    private long msgId = 0;
    String msgOid = "";
    private long activityId = 0;
    public DownloadStatus status = DownloadStatus.DRAFT;
    @NonNull
    public volatile String body = "";

    private DownloadData downloadData = DownloadData.EMPTY;
    CachedImage image = null;

    private TriState isPrivate = TriState.UNKNOWN;

    /**
     * Id of the Message to which we are replying.
     *  0 - This message is not a Reply.
     * -1 - is non-existent id.
     */
    public long inReplyToMsgId = 0;
    private long inReplyToUserId = 0;
    String inReplyToBody = "";
    private boolean replyToConversationParticipants = false;
    private boolean replyToMentionedUsers = false;
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
        result = prime * result + (int) (inReplyToMsgId ^ (inReplyToMsgId >>> 32));
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
        return inReplyToMsgId == other.inReplyToMsgId;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("id:" + msgId + ",");
        builder.append("status:" + status + ",");
        if(!TextUtils.isEmpty(body)) {
            builder.append("text:'" + body + "',");
        }
        if(!UriUtils.isEmpty(downloadData.getUri())) {
            builder.append("downloadData:" + downloadData + ",");
        }
        if(inReplyToMsgId != 0) {
            builder.append("inReplyTo:" + inReplyToMsgId + " by " + inReplyToUserId + ",");
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

    static NoteEditorData load(Long msgId) {
        NoteEditorData data;
        if (msgId != 0) {
            MyAccount ma = MyContextHolder.get().persistentAccounts().fromActorId(
                    MyQuery.msgIdToLongColumnValue(NoteTable.AUTHOR_ID, msgId));
            data = new NoteEditorData(ma);
            data.msgId = msgId;
            data.msgOid = MyQuery.msgIdToStringColumnValue(NoteTable.NOTE_OID, msgId);
            data.activityId = MyQuery.msgIdToLongColumnValue(ActivityTable.LAST_UPDATE_ID, msgId);
            data.status = DownloadStatus.load(MyQuery.msgIdToLongColumnValue(NoteTable.NOTE_STATUS, msgId));
            data.setBody(MyQuery.msgIdToStringColumnValue(NoteTable.BODY, msgId));
            data.downloadData = DownloadData.getSingleForMessage(msgId, MyContentType.IMAGE, Uri.EMPTY);
            if (data.downloadData.getStatus() == LOADED) {
                AttachedImageFile imageFile = new AttachedImageFile(data.downloadData.getDownloadId(),
                        data.downloadData.getFilename());
                data.image = imageFile.loadAndGetImage();
            }
            data.inReplyToMsgId = MyQuery.msgIdToLongColumnValue(NoteTable.IN_REPLY_TO_NOTE_ID, msgId);
            data.inReplyToUserId = MyQuery.msgIdToLongColumnValue(NoteTable.IN_REPLY_TO_ACTOR_ID, msgId);
            data.inReplyToBody = MyQuery.msgIdToStringColumnValue(NoteTable.BODY, data.inReplyToMsgId);
            data.recipients = Audience.fromMsgId(ma.getOrigin(), msgId);
            data.isPrivate = MyQuery.msgIdToTriState(NoteTable.PRIVATE, msgId);
            MyLog.v(TAG, "Loaded " + data);
        } else {
            data = new NoteEditorData(MyContextHolder.get().persistentAccounts().getCurrentAccount());
            MyLog.v(TAG, "Empty data created");
        }
        return data;
    }

    NoteEditorData copy() {
        if (this.isValid()) {
            NoteEditorData data = NoteEditorData.newEmpty(ma);
            data.msgId = msgId;
            data.msgOid = msgOid;
            data.status = status;
            data.setBody(body);
            data.downloadData = downloadData;
            data.image = image;
            data.inReplyToMsgId = inReplyToMsgId;
            data.inReplyToUserId = inReplyToUserId;
            data.inReplyToBody = inReplyToBody;
            data.replyToConversationParticipants = replyToConversationParticipants;
            data.recipients.addAll(recipients);
            return data;
        } else {
            return EMPTY;
        }
    }

    public void save(Uri imageUriToSave) {
        AActivity activity = AActivity.newPartialMessage(getMyAccount().getActor(), msgOid,
                System.currentTimeMillis(), status);
        activity.setActor(activity.accountActor);
        Note message = activity.getMessage();
        message.msgId = getMsgId();
        message.setBody(body);
        message.addRecipients(recipients);
        if (inReplyToMsgId != 0) {
            final AActivity inReplyTo = AActivity.newPartialMessage(getMyAccount().getActor(),
                    MyQuery.idToOid(OidEnum.MSG_OID, inReplyToMsgId, 0), 0, UNKNOWN);
            if (inReplyToUserId == 0) {
                inReplyToUserId = MyQuery.msgIdToLongColumnValue(NoteTable.AUTHOR_ID, inReplyToMsgId);
            }
            inReplyTo.setActor(Actor.fromOriginAndActorId(getMyAccount().getOrigin(), inReplyToUserId));
            message.setInReplyTo(inReplyTo);
        }
        Uri mediaUri = imageUriToSave.equals(Uri.EMPTY) ? downloadData.getUri() : imageUriToSave;
        if (!mediaUri.equals(Uri.EMPTY)) {
            message.attachments.add(
                    Attachment.fromUriAndContentType(mediaUri, MyContentType.IMAGE));
        }
        DataUpdater di = new DataUpdater(getMyAccount());
        setMsgId(di.onActivity(activity).getMessage().msgId);
        if (activity.getId() != 0 && activityId != activity.getId()) {
            if (activityId != 0 && status != LOADED) {
                MyProvider.deleteActivity(MyContextHolder.get(), activityId, msgId, false);
            }
            activityId = activity.getId();
        }
    }

    MyAccount getMyAccount() {
        return ma;
    }
    
    boolean isEmpty() {
        return TextUtils.isEmpty(body) && getMediaUri().equals(Uri.EMPTY) && msgId == 0;
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

    public NoteEditorData setMsgId(long msgIdIn) {
        msgId = msgIdIn;
        return this;
    }

    public long getMsgId() {
        return msgId;
    }

    public NoteEditorData setInReplyToMsgId(long msgId) {
        inReplyToMsgId = msgId;
        return this;
    }
    
    public NoteEditorData setReplyToConversationParticipants(boolean replyToConversationParticipants) {
        this.replyToConversationParticipants = replyToConversationParticipants;
        return this;
    }

    public NoteEditorData setReplyToMentionedUsers(boolean replyToMentionedUsers) {
        this.replyToMentionedUsers = replyToMentionedUsers;
        return this;
    }

    public NoteEditorData addMentionsToText() {
        if (ma.isValid() && inReplyToMsgId != 0) {
            if (replyToConversationParticipants) {
                addConversationParticipantsBeforeText();
            } else if (replyToMentionedUsers) {
                addMentionedUsersBeforeText();
            } else {
                addUsersBeforeText(new ArrayList<Long>());
            }
        }
        return this;
    }

    private void addConversationParticipantsBeforeText() {
        ConversationLoader<? extends ConversationMemberItem> loader =
                new ConversationLoaderFactory<ConversationMemberItem>().getLoader(
                ConversationMemberItem.EMPTY, MyContextHolder.get(), ma, inReplyToMsgId, false);
        loader.load(null);
        List<Long> toMention = new ArrayList<>();
        for(ConversationMemberItem item : loader.getList()) {
            if (!item.isFavoritingAction) {
                toMention.add(item.authorId);
            }
        }
        addUsersBeforeText(toMention);
    }

    private void addMentionedUsersBeforeText() {
        ActorsOfNoteListLoader loader = new ActorsOfNoteListLoader(ActorListType.ACTORS_OF_NOTE, ma, inReplyToMsgId
                , "").setMentionedOnly(true);
        loader.load(null);
        List<Long> toMention = new ArrayList<>();
        for(ActorViewItem item : loader.getList()) {
            toMention.add(item.getActorId());
        }
        addUsersBeforeText(toMention);
    }

    private void addUsersBeforeText(List<Long> toMention) {
        toMention.add(0, MyQuery.msgIdToLongColumnValue(NoteTable.AUTHOR_ID, inReplyToMsgId));
        List<Long> mentioned = new ArrayList<>();
        mentioned.add(ma.getActorId());  // Don't mention an author of this message
        String mentions = "";
        for(Long actorId : toMention) {
            if (actorId != 0 && !mentioned.contains(actorId)) {
                mentioned.add(actorId);
                String name = MyQuery.actorIdToName(actorId, getActorInTimeline());
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

    public NoteEditorData appendMentionedUserToText(long mentionedUserId) {
        String name = MyQuery.actorIdToName(mentionedUserId, getActorInTimeline());
        if (!TextUtils.isEmpty(name)) {
            String messageText1 = "@" + name + " ";
            if (!TextUtils.isEmpty(body) && !(body + " ").contains(messageText1)) {
                messageText1 = body.trim() + " " + messageText1;
            }
            setBody(messageText1);
        }
        return this;
    }

    private ActorInTimeline getActorInTimeline() {
        return ma.getOrigin().isMentionAsWebFingerId() ? ActorInTimeline.WEBFINGER_ID : ActorInTimeline.ACTORNAME;
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
