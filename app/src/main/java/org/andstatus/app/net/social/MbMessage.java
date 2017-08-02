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

import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Message of a Social Network
 * @author yvolk@yurivolkov.com
 */
public class MbMessage {
    public static final MbMessage EMPTY = new MbMessage(0);

    private boolean isEmpty = false;
    private DownloadStatus status = DownloadStatus.UNKNOWN;
    
    public String oid="";
    private long updatedDate = 0;
    @NonNull
    private MbUser author = MbUser.EMPTY;
    // TODO: Multiple recipients needed?!
    private MbUser recipient = MbUser.EMPTY;
    private String body = "";

    private MbMessage inReplyTo = null;
    public final List<MbMessage> replies = new ArrayList<>();
    public String conversationOid="";
    public String via = "";
    public String url="";
    private boolean isPublic = false;

    public final List<MbAttachment> attachments = new ArrayList<>();

    public long sentDate = 0;
    private TriState favorited = TriState.UNKNOWN;
    private String reblogOid = "";

    /** Some additional attributes may appear from "My account's" (authenticated User's) point of view */
    private TriState subscribedByMe = TriState.UNKNOWN;
    private TriState favoritedByMe = TriState.UNKNOWN;
    
    // In our system
    public final long originId;
    public long msgId = 0L;
    private long conversationId = 0L;

    public static MbMessage fromOriginAndOid(long originId, String oid, DownloadStatus status) {
        MbMessage message = new MbMessage(originId);
        message.oid = oid;
        message.status = status;
        if (TextUtils.isEmpty(oid) && status == DownloadStatus.LOADED) {
            message.status = DownloadStatus.UNKNOWN;
        }
        return message;
    }

    private MbMessage(long originId) {
        this.originId = originId;
    }

    @NonNull
    public MbActivity update(MbUser accountUser) {
        return act(accountUser, MbUser.EMPTY, MbActivityType.UPDATE);
    }

    @NonNull
    public MbActivity act(MbUser accountUser, @NonNull MbUser actor, @NonNull MbActivityType activityType) {
        MbActivity mbActivity = MbActivity.from(accountUser, activityType);
        mbActivity.setActor(actor);
        mbActivity.setMessage(this);
        return mbActivity;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public String getBody() {
        return body;
    }
    public String getBodyToSearch() {
        return MyHtml.getBodyToSearch(body);
    }

    private boolean isHtmlContentAllowed() {
        return MyContextHolder.get().persistentOrigins().isHtmlContentAllowed(originId);
    }

    public void setBody(String body) {
        if (TextUtils.isEmpty(body)) {
            this.body = "";
        } else if (isHtmlContentAllowed()) {
            this.body = MyHtml.stripUnnecessaryNewlines(MyHtml.unescapeHtml(body));
        } else {
            this.body = MyHtml.fromHtml(body);
        }
    }

    public MbMessage setConversationOid(String conversationOid) {
        if (TextUtils.isEmpty(conversationOid)) {
            this.conversationOid = "";
        } else {
            this.conversationOid = conversationOid;
        }
        return this;
    }

    public long lookupConversationId() {
        if (conversationId == 0 && msgId != 0) {
            conversationId = MyQuery.msgIdToLongColumnValue(MsgTable.CONVERSATION_ID, msgId);
        }
        if (conversationId == 0 && !TextUtils.isEmpty(conversationOid)) {
            conversationId = MyQuery.conversationOidToId(originId, conversationOid);
        }
        if (conversationId == 0 && getInReplyTo().nonEmpty()) {
            if (getInReplyTo().msgId != 0) {
                conversationId = MyQuery.msgIdToLongColumnValue(MsgTable.CONVERSATION_ID, getInReplyTo().msgId);
            }
        }
        return setConversationIdFromMsgId();
    }

    public long setConversationIdFromMsgId() {
        if (conversationId == 0 && msgId != 0) {
            conversationId = msgId;
        }
        return conversationId;
    }

    public long getConversationId() {
        return conversationId;
    }

    public DownloadStatus getStatus() {
        return status;
    }

    public boolean nonEmpty() {
        return !isEmpty();
    }

    public boolean isEmpty() {
        return this.isEmpty
                || originId == 0
                || (TextUtils.isEmpty(oid)
                    && ((status != DownloadStatus.SENDING && status != DownloadStatus.DRAFT)
                        || (TextUtils.isEmpty(body) && attachments.isEmpty())));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MbMessage other = (MbMessage) o;
        return hashCode() == other.hashCode();
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if(msgId != 0) {
            builder.append("msgId:" + msgId + ",");
        }
        builder.append("status:" + status + ",");
        if(!TextUtils.isEmpty(body)) {
            builder.append("body:'" + body + "',");
        }
        if(getInReplyTo().nonEmpty()) {
            builder.append("inReplyTo:" + getInReplyTo() + ",");
        }
        if(recipient != null && !recipient.isEmpty()) {
            builder.append("recipient:" + recipient + ",");
        }
        if (!attachments.isEmpty()) {
            builder.append("attachments:" + attachments + ",");
        }
        if(isEmpty) {
            builder.append("isEmpty,");
        }
        if(isPublic) {
            builder.append("isPublic,");
        }
        if(!TextUtils.isEmpty(oid)) {
            builder.append("oid:'" + oid + "',");
        }
        if(!TextUtils.isEmpty(url)) {
            builder.append("url:'" + url + "',");
        }
        if(!TextUtils.isEmpty(via)) {
            builder.append("via:'" + via + "',");
        }
        builder.append("author:" + author + ",");
        builder.append("updated" + new Date(updatedDate) + ",");
        if (sentDate != updatedDate) {
            builder.append("sent" + new Date(sentDate) + ",");
        }
        if(favorited != TriState.UNKNOWN) {
            builder.append("favorited:" + favorited + ",");
        }
        if(isReblogged()) {
            builder.append("reblogged,");
        }
        if(subscribedByMe != TriState.UNKNOWN) {
            builder.append("subscribedByMe:" + subscribedByMe + ",");
        }
        if(favoritedByMe != TriState.UNKNOWN) {
            builder.append("favoritedByMe:" + favoritedByMe + ",");
        }
        builder.append("originId:" + originId + ",");
        return MyLog.formatKeyValue(this, builder.toString());
    }

    public TriState getFavorited() {
        return favorited;
    }

    public void setFavorited(TriState favorited) {
        this.favorited = favorited;
    }

    public boolean isReblogged() {
        return !SharedPreferencesUtil.isEmpty(reblogOid);
    }

    public void setReblogOid(String reblogOid) {
        this.reblogOid = reblogOid;
    }

    public String getReblogOid() {
        return reblogOid;
    }

    @NonNull
    public MbMessage getInReplyTo() {
        return inReplyTo == null ? EMPTY : inReplyTo;
    }

    public void setInReplyTo(MbMessage message) {
        if (message != null && message.nonEmpty()) {
            this.inReplyTo = message;
        }
    }

    public TriState isSubscribedByMe() {
        return subscribedByMe;
    }

    public void setSubscribedByMe(TriState isSubscribed) {
        this.subscribedByMe = isSubscribed;
    }

    public TriState getFavoritedByMe() {
        return favoritedByMe;
    }

    public MbMessage setFavoritedByMe(TriState favoritedByMe) {
        this.favoritedByMe = favoritedByMe;
        return this;
    }

    @NonNull
    public MbUser getAuthor() {
        return author;
    }

    public void setAuthor(MbUser author) {
        if (author != null && author.nonEmpty()) {
            this.author = author;
        }
    }

    public long getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(long updatedDate) {
        this.updatedDate = updatedDate;
        if (sentDate < updatedDate) {
            sentDate = updatedDate;
        }
    }

    @NonNull
    public MbUser getRecipient() {
        return recipient;
    }

    public void setRecipient(MbUser recipient) {
        if (recipient != null) {
            this.recipient = recipient;
        }
    }
}
