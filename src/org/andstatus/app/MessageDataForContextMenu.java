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

package org.andstatus.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;


/**
 * Helper class for the message context menu creation 
 * @author yvolk@yurivolkov.com
 */
class MessageDataForContextMenu {
    /**
     * MyAccount, suitable for this message, null if nothing was found
     */
    public MyAccount ma = null;
    
    public String body = "";
    boolean isDirect = false;
    long authorId = 0;
    long senderId = 0;
    boolean favorited = false;
    boolean reblogged = false;
    boolean senderFollowed = false;
    boolean authorFollowed = false;
    /**
     * If this message was sent by current User, we may delete it.
     */
    boolean isSender = false;
    boolean isAuthor = false;

    /**
     * Sometimes current message already tied to the first user (favorited by him...)
     */
    boolean canUseSecondAccountInsteadOfFirst = false;
    
    public MessageDataForContextMenu(Context context, long userIdForThisMessage, long preferredOtherUserId, TimelineTypeEnum timelineType, long msgId) {
        ma = MyContextHolder.get().persistentAccounts().getAccountWhichMayBeLinkedToThisMessage(msgId, userIdForThisMessage,
                preferredOtherUserId);
        if (ma == null) {
            return;
        }

        // Get the record for the currently selected item
        Uri uri = MyProvider.getTimelineMsgUri(ma.getUserId(), TimelineTypeEnum.MESSAGESTOACT, false, msgId);
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[] {
                    BaseColumns._ID, MyDatabase.Msg.BODY, MyDatabase.Msg.SENDER_ID,
                    MyDatabase.Msg.AUTHOR_ID, MyDatabase.MsgOfUser.FAVORITED,
                    MyDatabase.Msg.RECIPIENT_ID,
                    MyDatabase.MsgOfUser.REBLOGGED,
                    MyDatabase.FollowingUser.SENDER_FOLLOWED,
                    MyDatabase.FollowingUser.AUTHOR_FOLLOWED
            }, null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                isDirect = !cursor.isNull(cursor.getColumnIndex(MyDatabase.Msg.RECIPIENT_ID));
                authorId = cursor.getLong(cursor.getColumnIndex(MyDatabase.Msg.AUTHOR_ID));
                senderId = cursor.getLong(cursor.getColumnIndex(MyDatabase.Msg.SENDER_ID));
                favorited = cursor.getInt(cursor.getColumnIndex(MyDatabase.MsgOfUser.FAVORITED)) == 1;
                reblogged = cursor.getInt(cursor.getColumnIndex(MyDatabase.MsgOfUser.REBLOGGED)) == 1;
                senderFollowed = cursor.getInt(cursor
                        .getColumnIndex(MyDatabase.FollowingUser.SENDER_FOLLOWED)) == 1;
                authorFollowed = cursor.getInt(cursor
                        .getColumnIndex(MyDatabase.FollowingUser.AUTHOR_FOLLOWED)) == 1;
                /**
                 * If this message was sent by current User, we may delete it.
                 */
                isSender = (ma.getUserId() == senderId);
                isAuthor = (ma.getUserId() == authorId);

                body = cursor.getString(cursor.getColumnIndex(MyDatabase.Msg.BODY));

                if ( timelineType != TimelineTypeEnum.FOLLOWING_USER 
                        && !isDirect && !favorited && !reblogged && !isSender && !senderFollowed && !authorFollowed
                        && ma.getUserId() != preferredOtherUserId) {
                    MyAccount ma2 = MyContextHolder.get().persistentAccounts().fromUserId(preferredOtherUserId);
                    if (ma2 != null && ma.getOriginId() == ma2.getOriginId()) {
                        canUseSecondAccountInsteadOfFirst = true;
                    }
                }
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
    }
}
