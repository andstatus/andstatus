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

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AttachedImageFile;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineSql;

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
    DownloadStatus mStatus = DownloadStatus.UNKNOWN;

    Drawable mAvatarDrawable = null;
    Drawable mImageDrawable = null;

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
        Set<Long> rebloggers = new HashSet<>();
        int ind=0;
        do {
            long senderId = DbUtils.getLong(cursor, Msg.SENDER_ID);
            long authorId = DbUtils.getLong(cursor, Msg.AUTHOR_ID);
            long linkedUserId = DbUtils.getLong(cursor, User.LINKED_USER_ID);
    
            if (ind == 0) {
                // This is the same for all retrieved rows
                super.load(cursor);
                mStatus = DownloadStatus.load(DbUtils.getLong(cursor, Msg.MSG_STATUS));
                mAuthor = TimelineSql.userColumnNameToNameAtTimeline(cursor, User.AUTHOR_NAME, false);
                mBody = DbUtils.getString(cursor, Msg.BODY);
                String via = DbUtils.getString(cursor, Msg.VIA);
                if (!TextUtils.isEmpty(via)) {
                    mVia = Html.fromHtml(via).toString().trim();
                }
                mAvatarDrawable = AvatarFile.getDrawable(authorId, cursor);
                if (MyPreferences.showAttachedImages()) {
                    mImageDrawable = AttachedImageFile.drawableFromCursor(cursor);
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
                if (DbUtils.getInt(cursor, MsgOfUser.REBLOGGED) == 1
                        && linkedUserId != authorId) {
                    rebloggers.add(linkedUserId);
                }
                if (DbUtils.getInt(cursor, MsgOfUser.FAVORITED) == 1) {
                    mFavorited = true;
                }
            }
            
            ind++;
        } while (cursor.moveToNext());

        for (long rebloggerId : MyQuery.getRebloggers(getMsgId())) {
            if (!rebloggers.contains(rebloggerId)) {
                rebloggers.add(rebloggerId);
            }
        }

        for (long rebloggerId : rebloggers) {
            if (!TextUtils.isEmpty(mRebloggersString)) {
                mRebloggersString += ", ";
            }
            mRebloggersString += MyQuery.userIdToWebfingerId(rebloggerId);
        }
    }
}