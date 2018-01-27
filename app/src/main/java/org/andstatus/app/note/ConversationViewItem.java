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

package org.andstatus.app.note;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.text.Html;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AttachedImageFile;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.TriState;

public class ConversationViewItem extends ConversationItem<ConversationViewItem> {
    public static final ConversationViewItem EMPTY = new ConversationViewItem(true);

    private ConversationViewItem(boolean isEmpty) {
        super(isEmpty);
    }

    @NonNull
    @Override
    public ConversationViewItem getNew() {
        return new ConversationViewItem(false);
    }

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
        /* IDs of all known senders of this message except for the Author
         * These "senders" reblogged the message
         */
        int ind=0;
        do {
            long msgId = DbUtils.getLong(cursor, ActivityTable.MSG_ID);
            if (msgId != getMsgId()) {
                if (ind > 0) {
                    cursor.moveToPrevious();
                }
                break;
            }

            authorId = DbUtils.getLong(cursor, NoteTable.AUTHOR_ID);
            super.load(cursor);
            msgStatus = DownloadStatus.load(DbUtils.getLong(cursor, NoteTable.NOTE_STATUS));
            authorName = TimelineSql.userColumnNameToNameAtTimeline(cursor, ActorTable.AUTHOR_NAME, false);
            setBody(MyHtml.prepareForView(DbUtils.getString(cursor, NoteTable.BODY)));
            String via = DbUtils.getString(cursor, NoteTable.VIA);
            if (!TextUtils.isEmpty(via)) {
                messageSource = Html.fromHtml(via).toString().trim();
            }
            avatarFile = AvatarFile.fromCursor(authorId, cursor, DownloadTable.AVATAR_FILE_NAME);
            if (MyPreferences.getDownloadAndDisplayAttachedImages()) {
                attachedImageFile = AttachedImageFile.fromCursor(cursor);
            }
            inReplyToMsgId = DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_NOTE_ID);
            inReplyToUserId = DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_ACTOR_ID);
            inReplyToName = TimelineSql.userColumnNameToNameAtTimeline(cursor, ActorTable.IN_REPLY_TO_NAME, false);
            //TODO:  recipientName = TimelineSql.userColumnNameToNameAtTimeline(cursor, UserTable.RECIPIENT_NAME, false);

            if (DbUtils.getTriState(cursor, NoteTable.REBLOGGED) == TriState.TRUE) {
                reblogged = true;
            }
            if (DbUtils.getTriState(cursor, NoteTable.FAVORITED) == TriState.TRUE) {
                favorited = true;
            }

            ind++;
        } while (cursor.moveToNext());

        for (Actor actor : MyQuery.getRebloggers(MyContextHolder.get().getDatabase(), getOrigin(), getMsgId())) {
            rebloggers.put(actor.actorId, actor.getWebFingerId());
        }
    }
}
