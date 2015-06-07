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
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;

/**
 * Helper class to find out a relation of a Message to MyAccount 
 * @author yvolk@yurivolkov.com
 */
public class MessageForAccount {
    public final long msgId;
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
        Uri uri = MatchedUri.getTimelineItemUri(ma.getUserId(), TimelineType.MESSAGESTOACT, false, 0, msgId);
        Cursor cursor = null;
        try {
            cursor = MyContextHolder.get().context().getContentResolver().query(uri, new String[] {
                    BaseColumns._ID, MyDatabase.Msg.BODY, MyDatabase.Msg.SENDER_ID,
                    MyDatabase.Msg.AUTHOR_ID,
                    MyDatabase.Msg.IN_REPLY_TO_USER_ID, 
                    MyDatabase.Msg.RECIPIENT_ID,
                    MyDatabase.MsgOfUser.SUBSCRIBED,
                    MyDatabase.MsgOfUser.FAVORITED,
                    MyDatabase.MsgOfUser.REBLOGGED,
                    MyDatabase.FollowingUser.SENDER_FOLLOWED,
                    MyDatabase.FollowingUser.AUTHOR_FOLLOWED,
                    MyDatabase.Download.IMAGE_FILE_NAME
            }, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                authorId = cursor.getLong(cursor.getColumnIndex(MyDatabase.Msg.AUTHOR_ID));
                senderId = cursor.getLong(cursor.getColumnIndex(MyDatabase.Msg.SENDER_ID));
                recipientId = cursor.getLong(cursor.getColumnIndex(MyDatabase.Msg.RECIPIENT_ID));
                int columnIndex = cursor.getColumnIndex(MyDatabase.Download.IMAGE_FILE_NAME);
                if (columnIndex >= 0) {
                    imageFilename = cursor.getString(columnIndex);
                }
                bodyTrimmed = I18n.trimTextAt(
                                MyHtml.fromHtml(cursor.getString(cursor
                                        .getColumnIndex(MyDatabase.Msg.BODY))), 40).toString();
                inReplyToUserId = cursor.getLong(cursor.getColumnIndex(MyDatabase.Msg.IN_REPLY_TO_USER_ID));
                mayBePrivate = (recipientId != 0) || (inReplyToUserId != 0);
                
                isRecipient = (userId == recipientId) || (userId == inReplyToUserId);
                isSubscribed = cursor.getInt(cursor.getColumnIndex(MyDatabase.MsgOfUser.SUBSCRIBED)) == 1;
                favorited = cursor.getInt(cursor.getColumnIndex(MyDatabase.MsgOfUser.FAVORITED)) == 1;
                reblogged = cursor.getInt(cursor.getColumnIndex(MyDatabase.MsgOfUser.REBLOGGED)) == 1;
                senderFollowed = cursor.getInt(cursor
                        .getColumnIndex(MyDatabase.FollowingUser.SENDER_FOLLOWED)) == 1;
                authorFollowed = cursor.getInt(cursor
                        .getColumnIndex(MyDatabase.FollowingUser.AUTHOR_FOLLOWED)) == 1;
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
}
