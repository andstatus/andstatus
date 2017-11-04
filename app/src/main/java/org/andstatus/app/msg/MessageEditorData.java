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

package org.andstatus.app.msg;

import android.graphics.Point;
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
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.graphics.CachedImage;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.MbActivity;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.user.UserListType;
import org.andstatus.app.user.UserViewItem;
import org.andstatus.app.user.UsersOfMessageListLoader;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;

import java.util.ArrayList;
import java.util.List;

import static org.andstatus.app.data.DownloadStatus.LOADED;
import static org.andstatus.app.data.DownloadStatus.UNKNOWN;

public class MessageEditorData {
    public static final String TAG = MessageEditorData.class.getSimpleName();
    static final MessageEditorData INVALID = MessageEditorData.newEmpty(null);

    private long msgId = 0;
    private long activityId = 0;
    public DownloadStatus status = DownloadStatus.DRAFT;
    @NonNull
    public volatile String body = "";

    private DownloadData downloadData = DownloadData.EMPTY;
    CachedImage image = null;

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
        result = prime * result + (int) (recipients.hashCode() ^ (recipients.hashCode() >>> 32));
        result = prime * result + (int) (inReplyToMsgId ^ (inReplyToMsgId >>> 32));
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

    static MessageEditorData load(Long msgId) {
        MessageEditorData data;
        if (msgId != 0) {
            MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(
                    MyQuery.msgIdToLongColumnValue(MsgTable.AUTHOR_ID, msgId));
            data = new MessageEditorData(ma);
            data.msgId = msgId;
            data.activityId = MyQuery.msgIdToLongColumnValue(ActivityTable.LAST_UPDATE_ID, msgId);
            data.status = DownloadStatus.load(MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, msgId));
            data.setBody(MyQuery.msgIdToStringColumnValue(MsgTable.BODY, msgId));
            data.downloadData = DownloadData.getSingleForMessage(msgId, MyContentType.IMAGE, Uri.EMPTY);
            if (data.downloadData.getStatus() == LOADED) {
                AttachedImageFile imageFile = new AttachedImageFile(data.downloadData.getDownloadId(),
                        data.downloadData.getFilename());
                data.image = imageFile.loadAndGetImage();
            }
            data.inReplyToMsgId = MyQuery.msgIdToLongColumnValue(MsgTable.IN_REPLY_TO_MSG_ID, msgId);
            data.inReplyToUserId = MyQuery.msgIdToLongColumnValue(MsgTable.IN_REPLY_TO_USER_ID, msgId);
            data.inReplyToBody = MyQuery.msgIdToStringColumnValue(MsgTable.BODY, data.inReplyToMsgId);
            data.recipients = Audience.fromMsgId(ma.getOriginId(), msgId);
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
            data.downloadData = downloadData;
            data.image = image;
            data.inReplyToMsgId = inReplyToMsgId;
            data.inReplyToUserId = inReplyToUserId;
            data.inReplyToBody = inReplyToBody;
            data.replyToConversationParticipants = replyToConversationParticipants;
            data.recipients.addAll(recipients);
            return data;
        } else {
            return INVALID;
        }
    }

    public void save(Uri imageUriToSave) {
        MbActivity activity = MbActivity.newPartialMessage(getMyAccount().toPartialUser(), "",
                System.currentTimeMillis(), status);
        activity.setActor(activity.accountUser);
        MbMessage message = activity.getMessage();
        message.msgId = getMsgId();
        message.setBody(body);
        message.addRecipients(recipients);
        if (inReplyToMsgId != 0) {
            final MbActivity inReplyTo = MbActivity.newPartialMessage(getMyAccount().toPartialUser(),
                    MyQuery.idToOid(OidEnum.MSG_OID, inReplyToMsgId, 0), 0, UNKNOWN);
            if (inReplyToUserId == 0) {
                inReplyToUserId = MyQuery.msgIdToLongColumnValue(MsgTable.AUTHOR_ID, inReplyToMsgId);
            }
            inReplyTo.setActor(MbUser.fromOriginAndUserId(message.originId, inReplyToUserId));
            message.setInReplyTo(inReplyTo);
        }
        Uri mediaUri = imageUriToSave.equals(Uri.EMPTY) ? downloadData.getUri() : imageUriToSave;
        if (!mediaUri.equals(Uri.EMPTY)) {
            message.attachments.add(
                    MbAttachment.fromUriAndContentType(mediaUri, MyContentType.IMAGE));
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
        return this != INVALID && ma.isValid();
    }

    public MessageEditorData setBody(String bodyIn) {
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

    public MessageEditorData setMsgId(long msgIdIn) {
        msgId = msgIdIn;
        return this;
    }

    public long getMsgId() {
        return msgId;
    }

    public MessageEditorData setInReplyToMsgId(long msgId) {
        inReplyToMsgId = msgId;
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
        ConversationLoader<ConversationMemberItem> loader =
                new ConversationLoaderFactory<ConversationMemberItem>().getLoader(
                ConversationMemberItem.class,
                MyContextHolder.get(), ma, inReplyToMsgId, false);
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
        UsersOfMessageListLoader loader = new UsersOfMessageListLoader(UserListType.USERS_OF_MESSAGE, ma, inReplyToMsgId
                , "").setMentionedOnly(true);
        loader.load(null);
        List<Long> toMention = new ArrayList<>();
        for(UserViewItem item : loader.getList()) {
            toMention.add(item.getUserId());
        }
        addUsersBeforeText(toMention);
    }

    private void addUsersBeforeText(List<Long> toMention) {
        toMention.add(0, MyQuery.msgIdToLongColumnValue(MsgTable.AUTHOR_ID, inReplyToMsgId));
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

    public MessageEditorData addRecipientId(long userId) {
        recipients.add(MbUser.fromOriginAndUserId(getMyAccount().getOriginId(), userId));
        return this;
    }
}
