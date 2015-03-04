/**
 * Copyright (C) 2013-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AttachedImageDrawable;
import org.andstatus.app.data.AvatarDrawable;
import org.andstatus.app.data.MyDatabase.Download;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * One message row
 */
public class ConversationOneMessage implements Comparable<ConversationOneMessage> {
    private long mMsgId;
    long mInReplyToMsgId = 0;
    long mCreatedDate = 0;
    long mLinkedUserId = 0;
    boolean mFavorited = false;
    String mAuthor = "";
    
    /**
     * Comma separated list of the names of all known rebloggers of the message
     */
    String mRebloggersString = "";
    String mBody = "";
    String mVia = "";
    String mInReplyToName = "";
    String mRecipientName = "";

    /** Numeration starts from 0 **/
    int mListOrder = 0;
    /**
     * This order is reverse to the {@link #mListOrder}. 
     * First message in the conversation has it == 1.
     * The number is visible to the user.
     */
    int mHistoryOrder = 0;
    int mNReplies = 0;
    int mNParentReplies = 0;
    int mIndentLevel = 0;
    int mReplyLevel = 0;
    
    AvatarDrawable mAvatarDrawable = null;
    Drawable mImageDrawable = null;

    public ConversationOneMessage() {
        // Empty
    }

    public boolean isLoaded() {
        return mCreatedDate > 0;
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
        return getMsgId() == other.getMsgId();
    }

    @Override
    public int hashCode() {
        return Long.valueOf(getMsgId()).hashCode();
    }

    /**
     * The newest replies are first, "branches" look up
     */
    @Override
    public int compareTo(ConversationOneMessage another) {
        int compared = mListOrder - another.mListOrder;
        if (compared == 0) {
            if (mCreatedDate == another.mCreatedDate) {
                if ( getMsgId() == another.getMsgId()) {
                    compared = 0;
                } else {
                    compared = (another.getMsgId() - getMsgId() > 0 ? 1 : -1);
                }
            } else {
                compared = (another.mCreatedDate - mCreatedDate > 0 ? 1 : -1);
            }
        }
        return compared;
    }

    String[] getProjection() {
        return TimelineSql.getConversationProjection();        
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
                mInReplyToMsgId = cursor.getLong(cursor.getColumnIndex(Msg.IN_REPLY_TO_MSG_ID));
                mCreatedDate = cursor.getLong(cursor.getColumnIndex(Msg.CREATED_DATE));
                mAuthor = TimelineSql.userColumnNameToNameAtTimeline(cursor, User.AUTHOR_NAME, false);
                mBody = cursor.getString(cursor.getColumnIndex(Msg.BODY));
                String via = cursor.getString(cursor.getColumnIndex(Msg.VIA));
                if (!TextUtils.isEmpty(via)) {
                    mVia = Html.fromHtml(via).toString().trim();
                }
                if (MyPreferences.showAvatars()) {
                    mAvatarDrawable = new AvatarDrawable(authorId, cursor.getString(cursor.getColumnIndex(Download.AVATAR_FILE_NAME)));
                }
                if (MyPreferences.showAttachedImages()) {
                    mImageDrawable = AttachedImageDrawable.drawableFromCursor(cursor);
                }
                mInReplyToName = TimelineSql.userColumnNameToNameAtTimeline(cursor, User.IN_REPLY_TO_NAME, false);
                mRecipientName = TimelineSql.userColumnNameToNameAtTimeline(cursor, User.RECIPIENT_NAME, false);
            }
    
            if (senderId != authorId) {
                rebloggers.add(senderId);
            }
            if (linkedUserId != 0) {
                if (mLinkedUserId == 0) {
                    mLinkedUserId = linkedUserId;
                }
                if (cursor.getInt(cursor.getColumnIndex(MsgOfUser.REBLOGGED)) == 1
                        && linkedUserId != authorId) {
                    rebloggers.add(linkedUserId);
                }
                if (cursor.getInt(cursor.getColumnIndex(MsgOfUser.FAVORITED)) == 1) {
                    mFavorited = true;
                }
            }
            
            ind++;
        } while (cursor.moveToNext());
    
        for (long rebloggerId : rebloggers) {
            if (!TextUtils.isEmpty(mRebloggersString)) {
                mRebloggersString += ", ";
            }
            mRebloggersString += MyProvider.userIdToName(rebloggerId);
        }
    }

    long getMsgId() {
        return mMsgId;
    }

    void setMsgId(long mMsgId) {
        this.mMsgId = mMsgId;
    }
    
    boolean noIdOfReply() {
        return mInReplyToMsgId !=0 && !SharedPreferencesUtil.isEmpty(mInReplyToName);
    }
    
    void copyFromWrongReply(ConversationOneMessage aReply) {
        MyLog.v(this, "Message id=" + aReply.getMsgId() + " has reply to name ("
                + aReply.mInReplyToName
                + ") but no reply to message id");
        // This allows to place the message on a Timeline correctly
        mCreatedDate = aReply.mCreatedDate - 60000;
        mAuthor = aReply.mInReplyToName;
        mBody = "("
                + MyContextHolder.get().context().getText(R.string.id_of_this_message_was_not_specified)
                + ")";
    }
}