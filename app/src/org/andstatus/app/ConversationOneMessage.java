/**
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

import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.TextUtils;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AttachedImageDrawable;
import org.andstatus.app.data.AvatarDrawable;
import org.andstatus.app.data.MyDatabase.Download;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineSql;

import java.util.HashSet;
import java.util.Set;

/**
 * One message row
 */
class ConversationOneMessage implements Comparable<ConversationOneMessage> {
    long msgId;
    long inReplyToMsgId = 0;
    long createdDate = 0;
    long linkedUserId = 0;
    boolean favorited = false;
    String author = "";
    
    /**
     * Comma separated list of the names of all known rebloggers of the message
     */
    String rebloggersString = "";
    String body = "";
    String via = "";
    String inReplyToName = "";
    String recipientName = "";

    /** Numeration starts from 0 **/
    int listOrder = 0;
    /**
     * This order is reverse to the {@link #listOrder}. 
     * First message in the conversation has it == 1.
     * The number is visible to the user.
     */
    int historyOrder = 0;
    int nReplies = 0;
    int nParentReplies = 0;
    int indentLevel = 0;
    int replyLevel = 0;
    
    AvatarDrawable avatarDrawable = null;
    Drawable imageDrawable = null;
    
    public ConversationOneMessage(long msgId, int replyLevel) {
        this.msgId = msgId;
        this.replyLevel = replyLevel;
    }

    public boolean isLoaded() {
        return createdDate > 0;
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ConversationOneMessage)) {
            return false;
        }
        final ConversationOneMessage other = (ConversationOneMessage) o;
        return msgId == other.msgId;
    }

    @Override
    public int hashCode() {
        return Long.valueOf(msgId).hashCode();
    }

    /**
     * The newest replies are first, "branches" look up
     */
    @Override
    public int compareTo(ConversationOneMessage another) {
        int compared = listOrder - another.listOrder;
        if (compared == 0) {
            if (createdDate == another.createdDate) {
                if ( msgId == another.msgId) {
                    compared = 0;
                } else {
                    compared = (another.msgId - msgId > 0 ? 1 : -1);
                }
            } else {
                compared = (another.createdDate - createdDate > 0 ? 1 : -1);
            }
        }
        return compared;
    }

    void load(Cursor cursor) {
        /**
         * IDs of all known senders of this message except for the Author
         * These "senders" reblogged the message
         */
        Set<Long> rebloggers = new HashSet<Long>();
        int ind=0;
        do {
            long senderId = cursor.getLong(cursor.getColumnIndex(Msg.SENDER_ID));
            long authorId = cursor.getLong(cursor.getColumnIndex(Msg.AUTHOR_ID));
            long linkedUserId = cursor.getLong(cursor.getColumnIndex(User.LINKED_USER_ID));
    
            if (ind == 0) {
                // This is the same for all retrieved rows
                inReplyToMsgId = cursor.getLong(cursor.getColumnIndex(Msg.IN_REPLY_TO_MSG_ID));
                createdDate = cursor.getLong(cursor.getColumnIndex(Msg.CREATED_DATE));
                author = TimelineSql.userColumnNameToNameAtTimeline(cursor, User.AUTHOR_NAME, false);
                body = cursor.getString(cursor.getColumnIndex(Msg.BODY));
                String via = cursor.getString(cursor.getColumnIndex(Msg.VIA));
                if (!TextUtils.isEmpty(via)) {
                    via = Html.fromHtml(via).toString().trim();
                }
                if (MyPreferences.showAvatars()) {
                    avatarDrawable = new AvatarDrawable(authorId, cursor.getString(cursor.getColumnIndex(Download.AVATAR_FILE_NAME)));
                }
                if (MyPreferences.showAttachedImages()) {
                    imageDrawable = AttachedImageDrawable.drawableFromCursor(cursor);
                }
                inReplyToName = TimelineSql.userColumnNameToNameAtTimeline(cursor, User.IN_REPLY_TO_NAME, false);
                recipientName = TimelineSql.userColumnNameToNameAtTimeline(cursor, User.RECIPIENT_NAME, false);
            }
    
            if (senderId != authorId) {
                rebloggers.add(senderId);
            }
            if (linkedUserId != 0) {
                if (this.linkedUserId == 0) {
                    this.linkedUserId = linkedUserId;
                }
                if (cursor.getInt(cursor.getColumnIndex(MsgOfUser.REBLOGGED)) == 1
                        && linkedUserId != authorId) {
                    rebloggers.add(linkedUserId);
                }
                if (cursor.getInt(cursor.getColumnIndex(MsgOfUser.FAVORITED)) == 1) {
                    favorited = true;
                }
            }
            
            ind++;
        } while (cursor.moveToNext());
    
        for (long rebloggerId : rebloggers) {
            if (!TextUtils.isEmpty(rebloggersString)) {
                rebloggersString += ", ";
            }
            rebloggersString += MyProvider.userIdToName(rebloggerId);
        }
    }
}