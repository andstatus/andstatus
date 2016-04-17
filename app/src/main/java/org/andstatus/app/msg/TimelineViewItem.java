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
import android.text.Html;
import android.text.TextUtils;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
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
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.HashMap;
import java.util.Map;

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

    Map<Long, String> rebloggers = new HashMap<>();

    String recipientName = "";

    long inReplyToMsgId = 0;
    long inReplyToUserId = 0;
    String inReplyToName = "";

    String body = "";
    String messageSource = "";
    boolean favorited = false;

    AttachedImageFile attachedImageFile = AttachedImageFile.EMPTY;
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
        item.body = MyHtml.htmlifyIfPlain(DbUtils.getString(cursor, MyDatabase.Msg.BODY));
        item.inReplyToMsgId = DbUtils.getLong(cursor, MyDatabase.Msg.IN_REPLY_TO_MSG_ID);
        item.inReplyToName = DbUtils.getString(cursor, MyDatabase.User.IN_REPLY_TO_NAME);
        item.recipientName = DbUtils.getString(cursor, MyDatabase.User.RECIPIENT_NAME);
        item.favorited = DbUtils.getLong(cursor, MyDatabase.MsgOfUser.FAVORITED) == 1;
        item.sentDate = DbUtils.getLong(cursor, MyDatabase.Msg.SENT_DATE);
        item.createdDate = DbUtils.getLong(cursor, MyDatabase.Msg.CREATED_DATE);
        item.msgStatus = DownloadStatus.load(DbUtils.getLong(cursor, MyDatabase.Msg.MSG_STATUS));
        item.linkedUserId = DbUtils.getLong(cursor, MyDatabase.User.LINKED_USER_ID);
        item.authorId = DbUtils.getLong(cursor, MyDatabase.Msg.AUTHOR_ID);

        long senderId = DbUtils.getLong(cursor, MyDatabase.Msg.SENDER_ID);
        if (senderId != item.authorId) {
            String senderName = DbUtils.getString(cursor, MyDatabase.User.SENDER_NAME);
            if (TextUtils.isEmpty(senderName)) {
                senderName = "(id" + senderId + ")";
            }
            item.rebloggers.put(senderId, senderName);
        }

        if (item.linkedUserId != 0) {
            if (DbUtils.getInt(cursor, MyDatabase.MsgOfUser.REBLOGGED) == 1
                    &&  !item.rebloggers.containsKey(item.linkedUserId)) {
                MyAccount myAccount = MyContextHolder.get().persistentAccounts()
                        .fromUserId(item.linkedUserId);
                if (myAccount.isValid()) {
                    item.rebloggers.put(item.linkedUserId, myAccount.getAccountName());
                }
            }
        }

        String via = DbUtils.getString(cursor, MyDatabase.Msg.VIA);
        if (!TextUtils.isEmpty(via)) {
            item.messageSource = Html.fromHtml(via).toString().trim();
        }

        item.avatarDrawable = AvatarFile.getDrawable(item.authorId, cursor);
        if (MyPreferences.showAttachedImages()) {
            item.attachedImageFile = new AttachedImageFile(
                    DbUtils.getLong(cursor, MyDatabase.Download.IMAGE_ID),
                    DbUtils.getString(cursor, MyDatabase.Download.IMAGE_FILE_NAME));
        }
        item.inReplyToUserId = DbUtils.getLong(cursor, MyDatabase.Msg.IN_REPLY_TO_USER_ID);
        item.originId = DbUtils.getLong(cursor, MyDatabase.Msg.ORIGIN_ID);
        return item;
    }

    public Drawable getAvatar() {
        return avatarDrawable;
    }

    public AttachedImageFile getAttachedImageFile() {
        return attachedImageFile;
    }

    public String getDetails(Context context) {
        StringBuilder messageDetails = new StringBuilder(
                RelativeTime.getDifference(context, createdDate));
        setInReplyTo(context, messageDetails);
        setRecipientName(context, messageDetails);
        setMessageSource(context, messageDetails);
        setMessageStatus(context, messageDetails);
        return messageDetails.toString();
    }

    private void setMessageSource(Context context, StringBuilder messageDetails) {
        if (!SharedPreferencesUtil.isEmpty(messageSource) && !"ostatus".equals(messageSource)) {
            messageDetails.append(" " + String.format(
                    context.getText(R.string.message_source_from).toString(), messageSource));
        }
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

    public boolean isReblogged() {
        return !rebloggers.isEmpty();
    }

    @Override
    public String toString() {
        return MyLog.formatKeyValue(this, I18n.trimTextAt(MyHtml.fromHtml(body), 40) + ","
                + getDetails(MyContextHolder.get().context()));
    }
}
