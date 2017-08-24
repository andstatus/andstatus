/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.database.ActivityTable;
import org.andstatus.app.database.DownloadTable;
import org.andstatus.app.database.FriendshipTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.TriState;

/**
 * Helper class to find out a relation of a Message to MyAccount 
 * @author yvolk@yurivolkov.com
 */
public class MessageForAccount {
    public static final MessageForAccount EMPTY = new MessageForAccount(0, 0, MyAccount.EMPTY);
    public final long msgId;
    @NonNull
    public final Origin origin;
    public DownloadStatus status = DownloadStatus.UNKNOWN;
    private String body = "";
    public long authorId = 0;
    public long senderId = 0;
    private boolean isSenderMySucceededAccount = false;
    Audience recipients = new Audience();
    public TriState isPrivate = TriState.UNKNOWN;
    public String imageFilename = null;
    @NonNull
    private final MyAccount myAccount;
    private final long userId;
    public boolean isSubscribed = false;
    public boolean isAuthor = false;
    public boolean isSender = false;
    public boolean isRecipient = false;
    public boolean favorited = false;
    public boolean reblogged = false;
    public boolean senderFollowed = false;
    public boolean authorFollowed = false;

    public MessageForAccount(long msgId, long originId, MyAccount myAccount) {
        this.msgId = msgId;
        this.origin = MyContextHolder.get().persistentOrigins().fromId(originId);
        this.myAccount = calculateMyAccount(origin, myAccount);
        this.userId = this.myAccount.getUserId();
        if (this.myAccount.isValid()) {
            getData();
        }
    }

    @NonNull
    private MyAccount calculateMyAccount(Origin origin, MyAccount ma) {
        if (ma == null || !ma.getOrigin().equals(origin) || !ma.isValid()) {
            return MyContextHolder.get().persistentAccounts().getFirstSucceededForOrigin(origin);
        }
        return ma;
    }

    public String getBodyTrimmed() {
        return I18n.trimTextAt(MyHtml.fromHtml(body), 80).toString();
    }

    private void getData() {
        // Get a database row for the currently selected item
        Uri uri = MatchedUri.getTimelineItemUri(
                Timeline.getTimeline(TimelineType.MESSAGES_TO_ACT, myAccount, 0, null), msgId);
        Cursor cursor = null;
        try {
            cursor = MyContextHolder.get().context().getContentResolver().query(uri, new String[]{
                    BaseColumns._ID,
                    MsgTable.MSG_STATUS,
                    MsgTable.BODY,
                    ActivityTable.ACTOR_ID,
                    MsgTable.AUTHOR_ID,
                    MsgTable.IN_REPLY_TO_USER_ID,
                    UserTable.LINKED_USER_ID,
                    MsgTable.SUBSCRIBED,
                    MsgTable.FAVORITED,
                    MsgTable.REBLOGGED,
                    MsgTable.PRIVATE,
                    FriendshipTable.SENDER_FOLLOWED,
                    FriendshipTable.AUTHOR_FOLLOWED,
                    DownloadTable.IMAGE_FILE_NAME
            }, null, null, null);
            while (cursor != null && cursor.moveToNext()) {
                status = DownloadStatus.load(DbUtils.getLong(cursor, MsgTable.MSG_STATUS));
                authorId = DbUtils.getLong(cursor, MsgTable.AUTHOR_ID);
                isAuthor = (userId == authorId);
                senderId = DbUtils.getLong(cursor, ActivityTable.ACTOR_ID);
                isSender = (userId == senderId);
                isSenderMySucceededAccount = MyContextHolder.get().persistentAccounts().fromUserId(senderId).isValidAndSucceeded();
                recipients = Audience.fromMsgId(origin.getId(), msgId);
                imageFilename = DbUtils.getString(cursor, DownloadTable.IMAGE_FILE_NAME);
                body = DbUtils.getString(cursor, MsgTable.BODY);
                isPrivate = DbUtils.getTriState(cursor, MsgTable.PRIVATE);
                isRecipient = recipients.has(userId);
                long linkedUserId = DbUtils.getLong(cursor, UserTable.LINKED_USER_ID);
                if (userId == linkedUserId) {
                    isSubscribed = DbUtils.getTriState(cursor, MsgTable.SUBSCRIBED).toBoolean(false);
                    favorited = DbUtils.getTriState(cursor, MsgTable.FAVORITED).toBoolean(false);
                    reblogged = DbUtils.getTriState(cursor, MsgTable.REBLOGGED).toBoolean(false);
                    senderFollowed = DbUtils.getBoolean(cursor, FriendshipTable.SENDER_FOLLOWED);
                    authorFollowed = DbUtils.getBoolean(cursor, FriendshipTable.AUTHOR_FOLLOWED);
                }
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
    }

    @NonNull
    public MyAccount getMyAccount() {
        return myAccount;
    }
    
    public boolean isPrivate() {
        return isPrivate.equals(TriState.TRUE);
    }

    public boolean isTiedToThisAccount() {
        return isRecipient || favorited || reblogged || isSender
                || senderFollowed || authorFollowed;
    }

    public boolean hasPrivateAccess() {
        return isRecipient || isSender;
    }

    public boolean isLoaded() {
        return status == DownloadStatus.LOADED;
    }

    public boolean isSenderMySucceededAccount() {
        return isSenderMySucceededAccount;
    }
}
