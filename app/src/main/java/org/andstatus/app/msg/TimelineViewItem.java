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
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.database.DownloadTable;
import org.andstatus.app.database.MsgOfUserTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineViewItem extends MessageViewItem {
    long originId = 0;
    long sentDate = 0;
    DownloadStatus msgStatus = DownloadStatus.UNKNOWN;

    String authorName = "";
    long authorId = 0;

    String recipientName = "";

    long inReplyToMsgId = 0;
    long inReplyToUserId = 0;
    String inReplyToName = "";

    String messageSource = "";

    AttachedImageFile attachedImageFile = AttachedImageFile.EMPTY;

    private final static TimelineViewItem EMPTY = new TimelineViewItem();
    private Drawable avatarDrawable = null;

    public static TimelineViewItem getEmpty() {
        return EMPTY;
    }

    public static TimelineViewItem fromCursorRow(Cursor cursor) {
        TimelineViewItem item = new TimelineViewItem();
        item.setMsgId(DbUtils.getLong(cursor, MsgTable._ID));
        item.authorName = TimelineSql.userColumnIndexToNameAtTimeline(cursor,
                cursor.getColumnIndex(UserTable.AUTHOR_NAME), MyPreferences.getShowOrigin());
        item.body = MyHtml.htmlifyIfPlain(DbUtils.getString(cursor, MsgTable.BODY));
        item.inReplyToMsgId = DbUtils.getLong(cursor, MsgTable.IN_REPLY_TO_MSG_ID);
        item.inReplyToName = DbUtils.getString(cursor, UserTable.IN_REPLY_TO_NAME);
        item.recipientName = DbUtils.getString(cursor, UserTable.RECIPIENT_NAME);
        item.favorited = DbUtils.getLong(cursor, MsgOfUserTable.FAVORITED) == 1;
        item.sentDate = DbUtils.getLong(cursor, MsgTable.SENT_DATE);
        item.createdDate = DbUtils.getLong(cursor, MsgTable.CREATED_DATE);
        item.msgStatus = DownloadStatus.load(DbUtils.getLong(cursor, MsgTable.MSG_STATUS));
        item.setLinkedUserId(DbUtils.getLong(cursor, UserTable.LINKED_USER_ID));
        item.authorId = DbUtils.getLong(cursor, MsgTable.AUTHOR_ID);

        long senderId = DbUtils.getLong(cursor, MsgTable.SENDER_ID);
        if (senderId != item.authorId) {
            String senderName = DbUtils.getString(cursor, UserTable.SENDER_NAME);
            if (TextUtils.isEmpty(senderName)) {
                senderName = "(id" + senderId + ")";
            }
            addReblogger(item, senderId, senderName);
        }

        if (item.getLinkedUserId() != 0) {
            if (DbUtils.getInt(cursor, MsgOfUserTable.REBLOGGED) == 1
                    &&  !item.rebloggers.containsKey(item.getLinkedUserId())) {
                MyAccount myAccount = MyContextHolder.get().persistentAccounts()
                        .fromUserId(item.getLinkedUserId());
                if (myAccount.isValid()) {
                    addReblogger(item, item.getLinkedUserId(), myAccount.getAccountName());
                }
            }
        }

        String via = DbUtils.getString(cursor, MsgTable.VIA);
        if (!TextUtils.isEmpty(via)) {
            item.messageSource = Html.fromHtml(via).toString().trim();
        }

        item.avatarDrawable = AvatarFile.getDrawable(item.authorId, cursor);
        if (MyPreferences.getDownloadAndDisplayAttachedImages()) {
            item.attachedImageFile = new AttachedImageFile(
                    DbUtils.getLong(cursor, DownloadTable.IMAGE_ID),
                    DbUtils.getString(cursor, DownloadTable.IMAGE_FILE_NAME));
        }
        item.inReplyToUserId = DbUtils.getLong(cursor, MsgTable.IN_REPLY_TO_USER_ID);
        item.originId = DbUtils.getLong(cursor, MsgTable.ORIGIN_ID);
        return item;
    }

    private static void addReblogger(TimelineViewItem item, long userId, String userName) {
        MyAccount myAccount = MyContextHolder.get().persistentAccounts().fromUserId(userId);
        if (myAccount.isValid()) {
            item.reblogged = true;
        }
        item.rebloggers.put(userId, userName);
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
        setCollapsedStatus(context, messageDetails);
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
