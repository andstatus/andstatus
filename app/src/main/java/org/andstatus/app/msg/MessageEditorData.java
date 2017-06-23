/**
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

package org.andstatus.app.msg;

import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.UserInTimeline;
import org.andstatus.app.data.AttachedImageFile;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.user.UserListType;
import org.andstatus.app.user.UserListViewItem;
import org.andstatus.app.user.UsersOfMessageListLoader;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;

import java.util.ArrayList;
import java.util.List;

public class MessageEditorData {
    public static final String TAG = MessageEditorData.class.getSimpleName();
    static final MessageEditorData INVALID = MessageEditorData.newEmpty(null);

    private long msgId = 0;
    public DownloadStatus status = DownloadStatus.DRAFT;
    @NonNull
    public volatile String body = "";

    private DownloadData image = DownloadData.EMPTY;
    private Point imageSize = new Point();
    Drawable imageDrawable = null;

    /**
     * Id of the Message to which we are replying.
     *  0 - This message is not a Reply.
     * -1 - is non-existent id.
     */
    public long inReplyToId = 0;
    String inReplyToBody = "";
    private boolean replyToConversationParticipants = false;
    private boolean replyToMentionedUsers = false;
    public long recipientId = 0;
    public MyAccount ma = MyAccount.EMPTY;

    private MessageEditorData(MyAccount myAccount) {
        ma = myAccount == null ? MyAccount.EMPTY : myAccount;
    }

    public static MessageEditorData newEmpty(MyAccount myAccount) {
        return new MessageEditorData(myAccount);
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((ma == null) ? 0 : ma.hashCode());
        result = prime * result + getMediaUri().hashCode();
        result = prime * result + body.hashCode();
        result = prime * result + (int) (recipientId ^ (recipientId >>> 32));
        result = prime * result + (int) (inReplyToId ^ (inReplyToId >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MessageEditorData other = (MessageEditorData) o;
        if (ma == null) {
            if (other.ma != null)
                return false;
        } else if (!ma.equals(other.ma))
            return false;
        if (!getMediaUri().equals(other.getMediaUri()))
            return false;
        if (!body.equals(other.body))
            return false;
        if (recipientId != other.recipientId)
            return false;
        return inReplyToId == other.inReplyToId;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if(msgId != 0) {
            builder.append("msgId:" + msgId + ",");
        }
        builder.append("status:" + status + ",");
        if(!TextUtils.isEmpty(body)) {
            builder.append("text:'" + body + "',");
        }
        if(!UriUtils.isEmpty(image.getUri())) {
            builder.append("image:" + image + ",");
        }
        if(inReplyToId != 0) {
            builder.append("inReplyToId:" + inReplyToId + ",");
        }
        if(replyToConversationParticipants) {
            builder.append("ReplyAll,");
        }
        if(recipientId != 0) {
            builder.append("recipientId:" + recipientId + ",");
        }
        builder.append("ma:" + ma.getAccountName() + ",");
        return MyLog.formatKeyValue(this, builder.toString());
    }

    static MessageEditorData load(Long msgId) {
        MessageEditorData data;
        if (msgId != 0) {
            MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(
                    MyQuery.msgIdToLongColumnValue(MsgTable.ACTOR_ID, msgId));
            data = new MessageEditorData(ma);
            data.msgId = msgId;
            data.setBody(MyQuery.msgIdToStringColumnValue(MsgTable.BODY, msgId));
            data.image = DownloadData.getSingleForMessage(msgId, MyContentType.IMAGE, Uri.EMPTY);
            if (data.image.getStatus() == DownloadStatus.LOADED) {
                AttachedImageFile imageFile = new AttachedImageFile(data.image.getDownloadId(),
                        data.image.getFilename());
                data.imageSize = imageFile.getSize();
                data.imageDrawable = imageFile.getDrawableSync();
            }
            data.inReplyToId = MyQuery.msgIdToLongColumnValue(MsgTable.IN_REPLY_TO_MSG_ID, msgId);
            data.inReplyToBody = MyQuery.msgIdToStringColumnValue(MsgTable.BODY, data.inReplyToId);
            data.recipientId = MyQuery.msgIdToLongColumnValue(MsgTable.RECIPIENT_ID, msgId);
            MyLog.v(TAG, "Loaded " + data);
        } else {
            data = new MessageEditorData(MyContextHolder.get().persistentAccounts().getCurrentAccount());
            MyLog.v(TAG, "Empty data created");
        }
        return data;
    }

    MessageEditorData copy() {
        if (this.isValid()) {
            MessageEditorData data = MessageEditorData.newEmpty(ma);
            data.msgId = msgId;
            data.status = status;
            data.setBody(body);
            data.image = image;
            data.imageSize = imageSize;
            data.imageDrawable = imageDrawable;
            data.inReplyToId = inReplyToId;
            data.inReplyToBody = inReplyToBody;
            data.replyToConversationParticipants = replyToConversationParticipants;
            data.recipientId = recipientId;
            return data;
        } else {
            return INVALID;
        }
    }

    public void save(Uri imageUriToSave) {
        MbMessage message = MbMessage.fromOriginAndOid(getMyAccount().getOriginId(), getMyAccount().getUserOid(), "",
                status);
        message.msgId = getMsgId();
        message.setAuthor(getMyAccount().toMbUser());
        message.setUpdatedDate(System.currentTimeMillis());
        message.setBody(body);
        if (recipientId != 0) {
            message.recipient = MbUser.fromOriginAndUserOid(getMyAccount().getOriginId(),
                    MyQuery.idToOid(OidEnum.USER_OID, recipientId, 0));
        }
        if (inReplyToId != 0) {
            message.setInReplyTo(MbMessage.fromOriginAndOid(getMyAccount().getOriginId(), getMyAccount().getUserOid(),
                    MyQuery.idToOid(OidEnum.MSG_OID, inReplyToId, 0),
                    DownloadStatus.UNKNOWN));
        }
        Uri mediaUri = imageUriToSave.equals(Uri.EMPTY) ? image.getUri() : imageUriToSave;
        if (!mediaUri.equals(Uri.EMPTY)) {
            message.attachments.add(
                    MbAttachment.fromUriAndContentType(mediaUri, MyContentType.IMAGE));
        }
        DataUpdater di = new DataUpdater(getMyAccount());
        setMsgId(di.onActivity(message.getActor(), MbActivityType.UPDATE, message));
    }

    MyAccount getMyAccount() {
        return ma;
    }
    
    boolean isEmpty() {
        return TextUtils.isEmpty(body) && getMediaUri().equals(Uri.EMPTY) && msgId == 0;
    }

    public boolean isValid() {
        return this != INVALID && ma.isValid();
    }

    public MessageEditorData setBody(String bodyIn) {
        body = bodyIn == null ? "" : bodyIn.trim();
        return this;
    }

    public Uri getMediaUri() {
        return image.getUri();
    }

    public long getImageFileSize() {
        return image.getFile().getSize();
    }

    public Point getImageSize() {
        return imageSize;
    }

    public MessageEditorData setMsgId(long msgIdIn) {
        msgId = msgIdIn;
        return this;
    }

    public long getMsgId() {
        return msgId;
    }

    public MessageEditorData setInReplyToId(long msgId) {
        inReplyToId = msgId;
        return this;
    }
    
    public MessageEditorData setReplyToConversationParticipants(boolean replyToConversationParticipants) {
        this.replyToConversationParticipants = replyToConversationParticipants;
        return this;
    }

    public MessageEditorData setReplyToMentionedUsers(boolean replyToMentionedUsers) {
        this.replyToMentionedUsers = replyToMentionedUsers;
        return this;
    }

    public MessageEditorData addMentionsToText() {
        if (ma.isValid() && inReplyToId != 0) {
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
        ConversationLoader<ConversationMemberItem> loader =
                new ConversationLoaderFactory<ConversationMemberItem>().getLoader(
                ConversationMemberItem.class,
                MyContextHolder.get(), ma, inReplyToId, false);
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
        UsersOfMessageListLoader loader = new UsersOfMessageListLoader(UserListType.USERS_OF_MESSAGE, ma, inReplyToId)
                .setMentionedOnly(true);
        loader.load(null);
        List<Long> toMention = new ArrayList<>();
        for(UserListViewItem item : loader.getList()) {
            toMention.add(item.getUserId());
        }
        addUsersBeforeText(toMention);
    }

    private void addUsersBeforeText(List<Long> toMention) {
        toMention.add(0, MyQuery.msgIdToLongColumnValue(MsgTable.AUTHOR_ID, inReplyToId));
        List<Long> mentioned = new ArrayList<>();
        mentioned.add(ma.getUserId());  // Don't mention an author of this message
        String mentions = "";
        for(Long userId : toMention) {
            if (userId != 0 && !mentioned.contains(userId)) {
                mentioned.add(userId);
                String name = MyQuery.userIdToName(userId, getUserInTimeline());
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

    public MessageEditorData appendMentionedUserToText(long mentionedUserId) {
        String name = MyQuery.userIdToName(mentionedUserId, getUserInTimeline());
        if (!TextUtils.isEmpty(name)) {
            String messageText1 = "@" + name + " ";
            if (!TextUtils.isEmpty(body) && !(body + " ").contains(messageText1)) {
                messageText1 = body.trim() + " " + messageText1;
            }
            setBody(messageText1);
        }
        return this;
    }

    private UserInTimeline getUserInTimeline() {
        return ma
                .getOrigin().isMentionAsWebFingerId() ? UserInTimeline.WEBFINGER_ID
                : UserInTimeline.USERNAME;
    }

    public MessageEditorData setRecipientId(long userId) {
        recipientId = userId;
        return this;
    }
}
