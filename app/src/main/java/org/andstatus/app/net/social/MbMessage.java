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

import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 'Mb' stands for "Microblogging system" 
 * @author yvolk@yurivolkov.com
 */
public class MbMessage {
    private boolean isEmpty = false;
    private DownloadStatus status = DownloadStatus.UNKNOWN;
    
    public String oid="";
    public long sentDate = 0;
    public MbUser sender = null;
    // TODO: Multiple recipients needed?!
    public MbUser recipient = null; 
    private String body = "";

    public MbMessage rebloggedMessage = null;
    public MbMessage inReplyToMessage = null;
    public final List<MbMessage> replies = new ArrayList<>();
    public String conversationOid="";
    public String via = "";
    public String url="";
    private boolean isPublic = false;
    private TriState isSubscribed = TriState.UNKNOWN;

    public final List<MbAttachment> attachments = new ArrayList<>();
    
    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    /**
     * Some additional attributes may appear from the Reader's
     * point of view (usually - from the point of view of the authenticated user)
     */
    public MbUser actor = null;
    public TriState favoritedByActor = TriState.UNKNOWN;
    
    // In our system
    public long originId = 0L;
    public long msgId = 0L;
    public long conversationId = 0L;

    public static MbMessage fromOriginAndOid(long originId, String oid, DownloadStatus status) {
        MbMessage message = new MbMessage();
        message.originId = originId;
        message.oid = oid;
        message.status = status;
        if (TextUtils.isEmpty(oid) && status == DownloadStatus.LOADED) {
            message.status = DownloadStatus.UNKNOWN;
        }
        return message;
    }
    
    public static MbMessage getEmpty() {
        return new MbMessage();
    }

    public MbMessage markAsEmpty() {
        isEmpty = true;
        return this;
    }
    
    private MbMessage() {
        // Empty
    }
    
    public String getBody() {
        return body;
    }
    public void setBody(String body) {
        if (TextUtils.isEmpty(body)) {
            this.body = "";
        } else if (MyContextHolder.get().persistentOrigins().isHtmlContentAllowed(originId)) {
            this.body = MyHtml.stripUnnecessaryNewlines(body);
        } else {
            this.body = MyHtml.fromHtml(body);
        }
    }

    public DownloadStatus getStatus() {
        return status;
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
        if(favoritedByActor != TriState.UNKNOWN) {
            builder.append("favorited:" + favoritedByActor + ",");
        }
        if(inReplyToMessage != null && !inReplyToMessage.isEmpty()) {
            builder.append("inReplyTo:" + inReplyToMessage + ",");
        }
        if(rebloggedMessage != null && !rebloggedMessage.isEmpty()) {
            builder.append("reblogged:" + rebloggedMessage + ",");
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
        builder.append("sender:" + sender + ",");
        builder.append("actor:" + actor + ",");
        builder.append("sent" + new Date(sentDate) + ",");
        builder.append("originId:" + originId + ",");
        return MyLog.formatKeyValue(this, builder.toString());
    }

    public TriState isSubscribed() {
        return isSubscribed;
    }

    public void setSubscribed(TriState isSubscribed) {
        this.isSubscribed = isSubscribed;
    }

    public long getSenderId() {
        return sender == null ? 0L : sender.userId;
    }
}
