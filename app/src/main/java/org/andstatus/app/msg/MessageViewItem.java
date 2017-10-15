/*
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.text.Html;
import android.text.TextUtils;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AttachedImageFile;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.database.ActivityTable;
import org.andstatus.app.database.DownloadTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

/**
 * @author yvolk@yurivolkov.com
 */
public class MessageViewItem extends BaseMessageViewItem {
    public final static MessageViewItem EMPTY = new MessageViewItem();

    public static MessageViewItem fromCursorRow(MyContext myContext, Cursor cursor) {
        MessageViewItem item = new MessageViewItem();
        item.setMyContext(myContext);
        item.setMsgId(DbUtils.getLong(cursor, ActivityTable.MSG_ID));
        item.setOriginId(DbUtils.getLong(cursor, MsgTable.ORIGIN_ID));
        item.setLinkedUserAndAccount(DbUtils.getLong(cursor, UserTable.LINKED_USER_ID));

        item.authorName = TimelineSql.userColumnIndexToNameAtTimeline(cursor,
                cursor.getColumnIndex(UserTable.AUTHOR_NAME), MyPreferences.getShowOrigin());
        item.setBody(MyHtml.prepareForView(DbUtils.getString(cursor, MsgTable.BODY)));
        item.inReplyToMsgId = DbUtils.getLong(cursor, MsgTable.IN_REPLY_TO_MSG_ID);
        item.inReplyToUserId = DbUtils.getLong(cursor, MsgTable.IN_REPLY_TO_USER_ID);
        item.inReplyToName = DbUtils.getString(cursor, UserTable.IN_REPLY_TO_NAME);
        item.recipientName = DbUtils.getString(cursor, UserTable.RECIPIENT_NAME);
        item.activityInsDate = DbUtils.getLong(cursor, ActivityTable.INS_DATE);
        item.updatedDate = DbUtils.getLong(cursor, MsgTable.UPDATED_DATE);
        item.msgStatus = DownloadStatus.load(DbUtils.getLong(cursor, MsgTable.MSG_STATUS));

        item.authorId = DbUtils.getLong(cursor, MsgTable.AUTHOR_ID);

        item.favorited = DbUtils.getTriState(cursor, MsgTable.FAVORITED) == TriState.TRUE;
        item.reblogged = DbUtils.getTriState(cursor, MsgTable.REBLOGGED) == TriState.TRUE;

        for (MbUser user : MyQuery.getRebloggers(MyContextHolder.get().getDatabase(), item.getOriginId(),
                item.getMsgId())) {
            item.rebloggers.put(user.userId, user.getWebFingerId());
        }

        String via = DbUtils.getString(cursor, MsgTable.VIA);
        if (!TextUtils.isEmpty(via)) {
            item.messageSource = Html.fromHtml(via).toString().trim();
        }

        item.avatarFile = AvatarFile.fromCursor(item.authorId, cursor, DownloadTable.AVATAR_FILE_NAME);
        if (MyPreferences.getDownloadAndDisplayAttachedImages()) {
            item.attachedImageFile = new AttachedImageFile(
                    DbUtils.getLong(cursor, DownloadTable.IMAGE_ID),
                    DbUtils.getString(cursor, DownloadTable.IMAGE_FILE_NAME));
        }
        return item;
    }

    private void addReblogger(long userId, String userName) {
        rebloggers.put(userId, userName);
    }

    @Override
    public String toString() {
        return MyLog.formatKeyValue(this, I18n.trimTextAt(MyHtml.fromHtml(getBody()), 40) + ","
                + getDetails(getMyContext().context()));
    }

}
