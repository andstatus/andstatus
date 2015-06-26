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

package org.andstatus.app.msg;

import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.TextUtils;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AttachedImageDrawable;
import org.andstatus.app.data.AvatarDrawable;
import org.andstatus.app.data.MyDatabase.Download;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.HashSet;
import java.util.Set;

public class ConversationViewItem extends ConversationItem {
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

    AvatarDrawable mAvatarDrawable = null;
    Drawable mImageDrawable = null;

    public ConversationViewItem() {
        // Empty
    }
    
    @Override
    String[] getProjection() {
        return TimelineSql.getConversationProjection();        
    }

    @Override
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
                super.load(cursor);
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
            mRebloggersString += MyQuery.userIdToWebfingerId(rebloggerId);
        }
    }
    
    @Override
    protected boolean isWrongReply() {
        return mInReplyToMsgId==0 && !SharedPreferencesUtil.isEmpty(mInReplyToName);
    }
    
    @Override
    void copyFromWrongReply(ConversationItem aReply) {
        super.copyFromWrongReply(aReply);
        if (ConversationViewItem.class.isAssignableFrom(aReply.getClass())) {
            mAuthor =  ((ConversationViewItem)aReply).mInReplyToName;
            MyLog.v(this, "Message id=" + aReply.getMsgId() + " has reply to name ("
                    + mAuthor
                    + ") but no reply to message id");
            mBody = "("
                    + MyContextHolder.get().context().getText(R.string.id_of_this_message_was_not_specified)
                    + ")";
        }
    }
}