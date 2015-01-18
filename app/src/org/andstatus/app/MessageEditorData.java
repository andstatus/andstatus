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

package org.andstatus.app;

import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.util.UriUtils;

class MessageEditorData {
    String messageText = "";
    Uri mediaUri = Uri.EMPTY;
    /**
     * Id of the Message to which we are replying
     * -1 - is non-existent id
     */
    long replyToId = 0;
    long recipientId = 0;
    MyAccount ma = MyAccount.getEmpty(MyContextHolder.get(), "");

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
        result = prime * result + (int) (replyToId ^ (replyToId >>> 32));
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
        if (replyToId != other.replyToId)
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
        builder.append(", replyToId=");
        builder.append(replyToId);
        builder.append(", recipientId=");
        builder.append(recipientId);
        builder.append(", ma=");
        builder.append(ma.getAccountName());
        builder.append("]");
        return builder.toString();
    }
    
    public void save(SharedPreferences.Editor outState) {
        if (outState != null) {
            outState.putString(IntentExtra.EXTRA_MESSAGE_TEXT.key, messageText);
            outState.putString(IntentExtra.EXTRA_MEDIA_URI.key, mediaUri.toString());
            outState.putLong(IntentExtra.EXTRA_INREPLYTOID.key, replyToId);
            outState.putLong(IntentExtra.EXTRA_RECIPIENTID.key, recipientId);
            outState.putString(IntentExtra.EXTRA_ACCOUNT_NAME.key, getMyAccount().getAccountName());
        }
    }
    
    static MessageEditorData load(SharedPreferences savedInstanceState) {
        if (savedInstanceState != null 
                && savedInstanceState.contains(IntentExtra.EXTRA_INREPLYTOID.key) 
                && savedInstanceState.contains(IntentExtra.EXTRA_MESSAGE_TEXT.key)) {
            MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(
                    savedInstanceState.getString(IntentExtra.EXTRA_ACCOUNT_NAME.key, ""));
            MessageEditorData data = new MessageEditorData(ma);
            String messageText = savedInstanceState.getString(IntentExtra.EXTRA_MESSAGE_TEXT.key, "");
            Uri mediaUri = UriUtils.fromString(savedInstanceState.getString(IntentExtra.EXTRA_MEDIA_URI.key, ""));
            if (!TextUtils.isEmpty(messageText) || !UriUtils.isEmpty(mediaUri)) {
                data.messageText = messageText;
                data.mediaUri = mediaUri;
                data.replyToId = savedInstanceState.getLong(IntentExtra.EXTRA_INREPLYTOID.key, 0);
                data.recipientId = savedInstanceState.getLong(IntentExtra.EXTRA_RECIPIENTID.key, 0);
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
        addReplyToNameToMessageText();
        return this;
    }

    public MessageEditorData setMediaUri(Uri mediaUri) {
        this.mediaUri = UriUtils.notNull(mediaUri);
        return this;
    }
    
    public MessageEditorData setReplyToId(long msgId) {
        replyToId = msgId;
        addReplyToNameToMessageText();
        return this;
    }

    private void addReplyToNameToMessageText() {
        if (recipientId != 0 || replyToId == 0) {
            return;
        }
        String messageText1 = messageText;
        String replyToName = MyProvider.msgIdToUsername(MyDatabase.Msg.AUTHOR_ID, replyToId);
        if (!TextUtils.isEmpty(messageText1)) {
            messageText1 += " ";
        }
        messageText1 = "@" + replyToName + " ";
        messageText = messageText1;
    }

    public MessageEditorData setRecipientId(long userId) {
        recipientId = userId;
        return this;
    }
}
