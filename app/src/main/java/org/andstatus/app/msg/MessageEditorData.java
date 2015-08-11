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

import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.UserInTimeline;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.util.UriUtils;

import java.util.ArrayList;
import java.util.List;

public class MessageEditorData {
    public String messageText = "";
    Uri mediaUri = Uri.EMPTY;
    /**
     * Id of the Message to which we are replying
     * -1 - is non-existent id
     */
    public long inReplyToId = 0;
    boolean mReplyAll = false; 
    public long recipientId = 0;
    public MyAccount ma = MyAccount.getEmpty(MyContextHolder.get(), "");

    public MessageEditorData() {
        this(null);
    }
    
    public MessageEditorData(MyAccount myAccount) {
        if (myAccount == null) {
            ma = MyAccount.getEmpty(MyContextHolder.get(), "");
        } else {
            ma = myAccount;
        }
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((ma == null) ? 0 : ma.hashCode());
        result = prime * result + ((mediaUri == null) ? 0 : mediaUri.hashCode());
        result = prime * result + ((messageText == null) ? 0 : messageText.hashCode());
        result = prime * result + (int) (recipientId ^ (recipientId >>> 32));
        result = prime * result + (int) (inReplyToId ^ (inReplyToId >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MessageEditorData other = (MessageEditorData) obj;
        if (ma == null) {
            if (other.ma != null)
                return false;
        } else if (!ma.equals(other.ma))
            return false;
        if (mediaUri == null) {
            if (other.mediaUri != null)
                return false;
        } else if (!mediaUri.equals(other.mediaUri))
            return false;
        if (messageText == null) {
            if (other.messageText != null)
                return false;
        } else if (!messageText.equals(other.messageText))
            return false;
        if (recipientId != other.recipientId)
            return false;
        if (inReplyToId != other.inReplyToId)
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("MessageEditorData [messageText=");
        builder.append(messageText);
        builder.append(", mediaUri=");
        builder.append(mediaUri);
        builder.append(", inReplyToId=");
        builder.append(inReplyToId);
        builder.append(", mReplyAll=");
        builder.append(mReplyAll);
        builder.append(", recipientId=");
        builder.append(recipientId);
        builder.append(", ma=");
        builder.append(ma);
        builder.append("]");
        return builder.toString();
    }
    
    public void save(SharedPreferences.Editor outState) {
        if (outState != null) {
            outState.putString(IntentExtra.MESSAGE_TEXT.key, messageText);
            outState.putString(IntentExtra.MEDIA_URI.key, mediaUri.toString());
            outState.putLong(IntentExtra.INREPLYTOID.key, inReplyToId);
            outState.putLong(IntentExtra.RECIPIENTID.key, recipientId);
            outState.putString(IntentExtra.ACCOUNT_NAME.key, getMyAccount().getAccountName());
        }
    }
    
    static MessageEditorData load(SharedPreferences savedState) {
        if (savedState != null 
                && savedState.contains(IntentExtra.INREPLYTOID.key) 
                && savedState.contains(IntentExtra.MESSAGE_TEXT.key)) {
            MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(
                    savedState.getString(IntentExtra.ACCOUNT_NAME.key, ""));
            MessageEditorData data = new MessageEditorData(ma);
            String messageText = savedState.getString(IntentExtra.MESSAGE_TEXT.key, "");
            Uri mediaUri = UriUtils.fromString(savedState.getString(IntentExtra.MEDIA_URI.key, ""));
            if (!TextUtils.isEmpty(messageText) || !UriUtils.isEmpty(mediaUri)) {
                data.messageText = messageText;
                data.mediaUri = mediaUri;
                data.inReplyToId = savedState.getLong(IntentExtra.INREPLYTOID.key, 0);
                data.recipientId = savedState.getLong(IntentExtra.RECIPIENTID.key, 0);
            }
            return data;
        } else {
            return new MessageEditorData();
        }
    }

    MyAccount getMyAccount() {
        return ma;
    }
    
    boolean isEmpty() {
        return TextUtils.isEmpty(messageText) && UriUtils.isEmpty(mediaUri);
    }

    public MessageEditorData setMessageText(String textInitial) {
        messageText = textInitial;
        return this;
    }

    public MessageEditorData setMediaUri(Uri mediaUri) {
        this.mediaUri = UriUtils.notNull(mediaUri);
        return this;
    }
    
    public MessageEditorData setInReplyToId(long msgId) {
        inReplyToId = msgId;
        return this;
    }
    
    public MessageEditorData setReplyAll(boolean replyAll) {
        mReplyAll = replyAll;
        return this;
    }

    public MessageEditorData addMentionsToText() {
        if (inReplyToId != 0) {
            if (mReplyAll) {
                addConversationMembersToText();
            } else {
                addMentionedAuthorOfMessageToText(inReplyToId);
            }
        }
        return this;
    }
    
    private void addConversationMembersToText() {
        if (!ma.isValid()) {
            return;
        }
        ConversationLoader<ConversationMemberItem> loader = new ConversationLoader<ConversationMemberItem>(
                ConversationMemberItem.class,
                MyContextHolder.get().context(), ma, inReplyToId);
        loader.load(null);
        List<Long> mentioned = new ArrayList<Long>();
        mentioned.add(ma.getUserId());  // Skip an author of this message
        long authorWhomWeReply = getAuthorWhomWeReply(loader);
        mentioned.add(authorWhomWeReply);
        for(ConversationMemberItem item : loader.getMsgs()) {
            mentionConversationMember(mentioned, item);
        }
        addMentionedUserToText(authorWhomWeReply);  // He will be mentioned first
    }

    private long getAuthorWhomWeReply(ConversationLoader<ConversationMemberItem> loader) {
        for(ConversationMemberItem item : loader.getMsgs()) {
            if (item.getMsgId() == inReplyToId) {
                return item.authorId;
            }
        }
        return 0;
    }

    private void mentionConversationMember(List<Long> mentioned, ConversationMemberItem item) {
        if (!mentioned.contains(item.authorId)) {
            addMentionedUserToText(item.authorId);
            mentioned.add(item.authorId);
        }
    }

    public MessageEditorData addMentionedUserToText(long mentionedUserId) {
        String name = MyQuery.userIdToName(mentionedUserId, getUserInTimeline());
        addMentionedUsernameToText(name);
        return this;
    }

    private UserInTimeline getUserInTimeline() {
        return ma
                .getOrigin().isMentionAsWebFingerId() ? UserInTimeline.WEBFINGER_ID
                : UserInTimeline.USERNAME;
    }

    private void addMentionedAuthorOfMessageToText(long messageId) {
        String name = MyQuery.msgIdToUsername(MyDatabase.Msg.AUTHOR_ID, messageId, getUserInTimeline());
        addMentionedUsernameToText(name);
    }
    
    private void addMentionedUsernameToText(String name) {
        if (!TextUtils.isEmpty(name)) {
            String messageText1 = "@" + name + " ";
            if (!TextUtils.isEmpty(messageText)) {
                messageText1 += messageText;
            }
            messageText = messageText1;
        }
    }

    public MessageEditorData setRecipientId(long userId) {
        recipientId = userId;
        return this;
    }

    public boolean sameContext(MessageEditorData dataIn) {
        return inReplyToId == dataIn.inReplyToId
                && recipientId == dataIn.recipientId
                && getMyAccount().getAccountName()
                        .compareTo(dataIn.getMyAccount().getAccountName()) == 0
                && mReplyAll == dataIn.mReplyAll;
    }
}
