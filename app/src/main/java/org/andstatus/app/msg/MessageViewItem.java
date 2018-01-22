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
import android.support.annotation.NonNull;
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
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

/**
 * @author yvolk@yurivolkov.com
 */
public class MessageViewItem extends BaseMessageViewItem<MessageViewItem> {
    public final static MessageViewItem EMPTY = new MessageViewItem(true);

    protected MessageViewItem(boolean isEmpty) {
        super(isEmpty);
    }

    @Override
    @NonNull
    public MessageViewItem fromCursor(Cursor cursor) {
        return getNew().fromCursorRow(getMyContext(), cursor);
    }

    @NonNull
    @Override
    public MessageViewItem getNew() {
        return new MessageViewItem(false);
    }

    public MessageViewItem fromCursorRow(MyContext myContext, Cursor cursor) {
        long startTime = System.currentTimeMillis();
        setMyContext(myContext);
        setMsgId(DbUtils.getLong(cursor, ActivityTable.MSG_ID));
        setOrigin(myContext.persistentOrigins().fromId(DbUtils.getLong(cursor, ActivityTable.ORIGIN_ID)));
        setLinkedAccount(DbUtils.getLong(cursor, ActivityTable.ACCOUNT_ID));

        authorName = TimelineSql.userColumnIndexToNameAtTimeline(cursor,
                cursor.getColumnIndex(UserTable.AUTHOR_NAME), MyPreferences.getShowOrigin());
        setBody(MyHtml.prepareForView(DbUtils.getString(cursor, MsgTable.BODY)));
        inReplyToMsgId = DbUtils.getLong(cursor, MsgTable.IN_REPLY_TO_MSG_ID);
        inReplyToUserId = DbUtils.getLong(cursor, MsgTable.IN_REPLY_TO_USER_ID);
        inReplyToName = DbUtils.getString(cursor, UserTable.IN_REPLY_TO_NAME);
        recipientName = DbUtils.getString(cursor, UserTable.RECIPIENT_NAME);
        activityUpdatedDate = DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE);
        updatedDate = DbUtils.getLong(cursor, MsgTable.UPDATED_DATE);
        msgStatus = DownloadStatus.load(DbUtils.getLong(cursor, MsgTable.MSG_STATUS));

        authorId = DbUtils.getLong(cursor, MsgTable.AUTHOR_ID);

        favorited = DbUtils.getTriState(cursor, MsgTable.FAVORITED) == TriState.TRUE;
        reblogged = DbUtils.getTriState(cursor, MsgTable.REBLOGGED) == TriState.TRUE;

        String via = DbUtils.getString(cursor, MsgTable.VIA);
        if (!TextUtils.isEmpty(via)) {
            messageSource = Html.fromHtml(via).toString().trim();
        }

        avatarFile = AvatarFile.fromCursor(authorId, cursor, DownloadTable.AVATAR_FILE_NAME);
        if (MyPreferences.getDownloadAndDisplayAttachedImages()) {
            attachedImageFile = new AttachedImageFile(
                    DbUtils.getLong(cursor, DownloadTable.IMAGE_ID),
                    DbUtils.getString(cursor, DownloadTable.IMAGE_FILE_NAME));
        }

        long beforeRebloggers = System.currentTimeMillis();
        for (MbUser user : MyQuery.getRebloggers(MyContextHolder.get().getDatabase(), getOrigin(), getMsgId())) {
            rebloggers.put(user.userId, user.getWebFingerId());
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, ": " + (System.currentTimeMillis() - startTime) + "ms, "
                    + rebloggers.size() + " rebloggers: " + (System.currentTimeMillis() - beforeRebloggers) + "ms");
        }
        return this;
    }

    @Override
    public String toString() {
        return MyLog.formatKeyValue(this, I18n.trimTextAt(MyHtml.fromHtml(getBody()), 40) + ","
                + getDetails(getMyContext().context()));
    }
}
