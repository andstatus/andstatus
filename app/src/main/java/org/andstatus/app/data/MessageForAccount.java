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

package org.andstatus.app.data;

import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.database.DatabaseHolder;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;

/**
 * Helper class to find out a relation of a Message to MyAccount 
 * @author yvolk@yurivolkov.com
 */
public class MessageForAccount {
    public final long msgId;
    public DownloadStatus status = DownloadStatus.UNKNOWN;
    public String bodyTrimmed = "";
    public long authorId = 0;
    public long senderId = 0;
    public long inReplyToUserId = 0;
    public long recipientId = 0;
    public boolean mayBePrivate = false;
    public String imageFilename = null;

    private final MyAccount ma;
    private final long userId;
    public boolean isSubscribed = false;
    public boolean isAuthor = false;
    public boolean isSender = false;
    public boolean isRecipient = false;
    public boolean favorited = false;
    public boolean reblogged = false;
    public boolean senderFollowed = false;
    public boolean authorFollowed = false;
    
    public MessageForAccount(long msgId, MyAccount ma) {
        this.msgId = msgId;
        this.ma = ma;
        this.userId = ma.getUserId();
        if (ma.isValid()) {
            getData();
        }
    }

    private void getData() {
        // Get the record for the currently selected item
        Uri uri = MatchedUri.getTimelineItemUri(ma.getUserId(), TimelineType.MESSAGES_TO_ACT, false, 0, msgId);
        Cursor cursor = null;
        try {
            cursor = MyContextHolder.get().context().getContentResolver().query(uri, new String[]{
                    BaseColumns._ID,
                    DatabaseHolder.Msg.MSG_STATUS,
                    DatabaseHolder.Msg.BODY, DatabaseHolder.Msg.SENDER_ID,
                    DatabaseHolder.Msg.AUTHOR_ID,
                    DatabaseHolder.Msg.IN_REPLY_TO_USER_ID,
                    DatabaseHolder.Msg.RECIPIENT_ID,
                    DatabaseHolder.MsgOfUser.SUBSCRIBED,
                    DatabaseHolder.MsgOfUser.FAVORITED,
                    DatabaseHolder.MsgOfUser.REBLOGGED,
                    DatabaseHolder.Friendship.SENDER_FOLLOWED,
                    DatabaseHolder.Friendship.AUTHOR_FOLLOWED,
                    DatabaseHolder.Download.IMAGE_FILE_NAME
            }, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                status = DownloadStatus.load(DbUtils.getLong(cursor, DatabaseHolder.Msg.MSG_STATUS));
                authorId = DbUtils.getLong(cursor, DatabaseHolder.Msg.AUTHOR_ID);
                senderId = DbUtils.getLong(cursor, DatabaseHolder.Msg.SENDER_ID);
                recipientId = DbUtils.getLong(cursor, DatabaseHolder.Msg.RECIPIENT_ID);
                imageFilename = DbUtils.getString(cursor, DatabaseHolder.Download.IMAGE_FILE_NAME);
                bodyTrimmed = I18n.trimTextAt(MyHtml.fromHtml(
                        DbUtils.getString(cursor, DatabaseHolder.Msg.BODY)), 80).toString();
                inReplyToUserId = DbUtils.getLong(cursor, DatabaseHolder.Msg.IN_REPLY_TO_USER_ID);
                mayBePrivate = (recipientId != 0) || (inReplyToUserId != 0);
                
                isRecipient = (userId == recipientId) || (userId == inReplyToUserId);
                isSubscribed = DbUtils.getBoolean(cursor, DatabaseHolder.MsgOfUser.SUBSCRIBED);
                favorited = DbUtils.getBoolean(cursor, DatabaseHolder.MsgOfUser.FAVORITED);
                reblogged = DbUtils.getBoolean(cursor, DatabaseHolder.MsgOfUser.REBLOGGED);
                senderFollowed = DbUtils.getBoolean(cursor, DatabaseHolder.Friendship.SENDER_FOLLOWED);
                authorFollowed = DbUtils.getBoolean(cursor, DatabaseHolder.Friendship.AUTHOR_FOLLOWED);
                isSender = (userId == senderId);
                isAuthor = (userId == authorId);
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
    }

    public MyAccount myAccount() {
        return ma;
    }
    
    public boolean isDirect() {
        return recipientId != 0;
    }

    public boolean isTiedToThisAccount() {
        return isRecipient || favorited || reblogged || isSender
                || senderFollowed || authorFollowed;
    }

    public boolean hasPrivateAccess() {
        return isRecipient || isSender;
    }

    public boolean isLoaded() {
        return status == DownloadStatus.LOADED;
    }

}
