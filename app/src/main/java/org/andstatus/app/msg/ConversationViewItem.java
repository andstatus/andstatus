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

import android.content.Context;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.text.Html;
import android.text.TextUtils;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AttachedImageFile;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.database.MsgOfUserTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;

import java.util.HashSet;
import java.util.Set;

public class ConversationViewItem extends ConversationItem {
    public static final ConversationViewItem EMPTY = new ConversationViewItem();

    @Override
    String[] getProjection() {
        return TimelineSql.getConversationProjection();        
    }

    @Override
    public StringBuilder getDetails(Context context) {
        StringBuilder builder = super.getDetails(context);
        if (MyPreferences.isShowDebuggingInfoInUi()) {
            I18n.appendWithSpace(builder, "(i" + indentLevel + ",r" + replyLevel + ")");
        }
        return builder;
    }

    @Override
    void load(Cursor cursor) {
        /**
         * IDs of all known senders of this message except for the Author
         * These "senders" reblogged the message
         */
        Set<Long> rebloggerIds = new HashSet<>();
        int ind=0;
        do {
            long msgId = DbUtils.getLong(cursor, BaseColumns._ID);
            if (msgId != getMsgId()) {
                if (ind > 0) {
                    cursor.moveToPrevious();
                }
                break;
            }
            long senderId = DbUtils.getLong(cursor, MsgTable.ACTOR_ID);
            authorId = DbUtils.getLong(cursor, MsgTable.AUTHOR_ID);
            long linkedUserId = DbUtils.getLong(cursor, UserTable.LINKED_USER_ID);
    
            if (ind == 0) {
                // This is the same for all retrieved rows
                super.load(cursor);
                msgStatus = DownloadStatus.load(DbUtils.getLong(cursor, MsgTable.MSG_STATUS));
                authorName = TimelineSql.userColumnNameToNameAtTimeline(cursor, UserTable.AUTHOR_NAME, false);
                setBody(MyHtml.prepareForView(DbUtils.getString(cursor, MsgTable.BODY)));
                String via = DbUtils.getString(cursor, MsgTable.VIA);
                if (!TextUtils.isEmpty(via)) {
                    messageSource = Html.fromHtml(via).toString().trim();
                }
                avatarFile = AvatarFile.fromCursor(authorId, cursor);
                if (MyPreferences.getDownloadAndDisplayAttachedImages()) {
                    attachedImageFile = AttachedImageFile.fromCursor(cursor);
                }
                inReplyToMsgId = DbUtils.getLong(cursor, MsgTable.IN_REPLY_TO_MSG_ID);
                inReplyToUserId = DbUtils.getLong(cursor, MsgTable.IN_REPLY_TO_USER_ID);
                inReplyToName = TimelineSql.userColumnNameToNameAtTimeline(cursor, UserTable.IN_REPLY_TO_NAME, false);
                recipientName = TimelineSql.userColumnNameToNameAtTimeline(cursor, UserTable.RECIPIENT_NAME, false);
            }
    
            if (senderId != authorId) {
                rebloggerIds.add(senderId);
            }
            if (linkedUserId != 0) {
                if (getLinkedUserId() == 0 || !getLinkedMyAccount().isValid()) {
                    setLinkedUserAndAccount(linkedUserId);
                }
                if (DbUtils.getInt(cursor, MsgOfUserTable.REBLOGGED) == 1) {
                    if (linkedUserId != authorId) {
                        rebloggerIds.add(linkedUserId);
                    }
                    if (getLinkedMyAccount().getUserId() == linkedUserId) {
                        reblogged = true;
                    }
                }
                if (getLinkedMyAccount().getUserId() == linkedUserId & DbUtils.getInt(cursor, MsgOfUserTable.FAVORITED) == 1) {
                    favorited = true;
                }
            }
            
            ind++;
        } while (cursor.moveToNext());

        for (long rebloggerId : MyQuery.getRebloggers(getMsgId())) {
            if (!rebloggerIds.contains(rebloggerId)) {
                rebloggerIds.add(rebloggerId);
            }
        }

        for (long rebloggerId : rebloggerIds) {
            rebloggers.put(rebloggerId, MyQuery.userIdToWebfingerId(rebloggerId));
        }
    }
}
