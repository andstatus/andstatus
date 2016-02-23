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

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AttachedImageFile;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineViewItem {
    long msgId = 0;
    long originId = 0;
    long createdDate = 0;
    long sentDate = 0;
    DownloadStatus msgStatus = DownloadStatus.UNKNOWN;

    String authorName = "";
    long authorId = 0;

    String recipientName = "";

    long inReplyToMsgId = 0;
    long inReplyToUserId = 0;
    String inReplyToName = "";

    String body = "";
    boolean favorited = false;

    long attachedImageRowId = 0;
    String attachedImageFilename = "";

    AttachedImageFile attachedImageFile = null;
    long linkedUserId = 0;

    private final static TimelineViewItem EMPTY = new TimelineViewItem();
    private Drawable avatarDrawable = null;

    public static TimelineViewItem getEmpty() {
        return EMPTY;
    }

    public static TimelineViewItem fromCursorRow(Cursor cursor) {
        TimelineViewItem item = new TimelineViewItem();
        item.msgId = DbUtils.getLong(cursor, MyDatabase.Msg._ID);
        item.authorName = TimelineSql.userColumnIndexToNameAtTimeline(cursor,
                cursor.getColumnIndex(MyDatabase.User.AUTHOR_NAME), MyPreferences.showOrigin());
        item.body = DbUtils.getString(cursor, MyDatabase.Msg.BODY);
        item.inReplyToMsgId = DbUtils.getLong(cursor, MyDatabase.Msg.IN_REPLY_TO_MSG_ID);
        item.inReplyToName = DbUtils.getString(cursor, MyDatabase.User.IN_REPLY_TO_NAME);
        item.recipientName = DbUtils.getString(cursor, MyDatabase.User.RECIPIENT_NAME);
        item.favorited = DbUtils.getLong(cursor, MyDatabase.MsgOfUser.FAVORITED) == 1;
        item.sentDate = DbUtils.getLong(cursor, MyDatabase.Msg.SENT_DATE);
        item.createdDate = DbUtils.getLong(cursor, MyDatabase.Msg.CREATED_DATE);
        item.msgStatus = DownloadStatus.load(DbUtils.getLong(cursor, MyDatabase.Msg.MSG_STATUS));
        item.linkedUserId = DbUtils.getLong(cursor, MyDatabase.User.LINKED_USER_ID);
        item.authorId = DbUtils.getLong(cursor, MyDatabase.Msg.AUTHOR_ID);
        item.avatarDrawable = AvatarFile.getDrawable(item.authorId, cursor);
        if (MyPreferences.showAttachedImages()) {
            item.attachedImageRowId = DbUtils.getLong(cursor, MyDatabase.Download.IMAGE_ID);
            item.attachedImageFilename = DbUtils.getString(cursor, MyDatabase.Download.IMAGE_FILE_NAME);
            item.attachedImageFile = new AttachedImageFile(item.attachedImageRowId, item.attachedImageFilename);
        }
        item.inReplyToUserId = DbUtils.getLong(cursor, MyDatabase.Msg.IN_REPLY_TO_USER_ID);
        item.originId = DbUtils.getLong(cursor, MyDatabase.Msg.ORIGIN_ID);
        return item;
    }

    public Drawable getAvatar() {
        return avatarDrawable;
    }

    public Drawable getAttachedImage() {
        return attachedImageFile == null ? null : attachedImageFile.getDrawable();
    }

    public String getDetails(Context context) {
        StringBuilder messageDetails = new StringBuilder(
                RelativeTime.getDifference(context, createdDate));
        setInReplyTo(context, messageDetails);
        setRecipientName(context, messageDetails);
        setMessageStatus(context, messageDetails);
        return messageDetails.toString();
    }

    private void setInReplyTo(Context context, StringBuilder messageDetails) {
        if (inReplyToMsgId != 0 && TextUtils.isEmpty(inReplyToName)) {
            inReplyToName = "...";
        }
        if (!TextUtils.isEmpty(inReplyToName)) {
            messageDetails.append(" ").append(String.format(
                    context.getText(R.string.message_source_in_reply_to).toString(),
                    inReplyToName));
        }
    }

    private void setRecipientName(Context context, StringBuilder messageDetails) {
        if (!TextUtils.isEmpty(recipientName)) {
            messageDetails.append(" " + String.format(
                    context.getText(R.string.message_source_to).toString(),
                    recipientName));
        }
    }

    private void setMessageStatus(Context context, StringBuilder messageDetails) {
        if (msgStatus != DownloadStatus.LOADED) {
            messageDetails.append(" (").append(msgStatus.getTitle(context)).append(")");
        }
    }

    @Override
    public String toString() {
        return MyLog.formatKeyValue(this, I18n.trimTextAt(MyHtml.fromHtml(body), 40) + ","
                + getDetails(MyContextHolder.get().context()));
    }
}
