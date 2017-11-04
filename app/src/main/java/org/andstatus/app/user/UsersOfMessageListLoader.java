/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.user;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;

import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public class UsersOfMessageListLoader extends UserListLoader {
    private final long selectedMessageId;
    private final Origin originOfSelectedMessage;
    final String messageBody;
    private boolean mentionedOnly = false;

    public UsersOfMessageListLoader(UserListType userListType, MyAccount ma, long centralItemId, String searchQuery) {
        super(userListType, ma, ma.getOrigin(), centralItemId, searchQuery);

        selectedMessageId = centralItemId;
        messageBody = MyQuery.msgIdToStringColumnValue(MsgTable.BODY, selectedMessageId);
        originOfSelectedMessage = MyContextHolder.get().persistentOrigins().fromId(
                MyQuery.msgIdToOriginId(selectedMessageId));
    }

    public UsersOfMessageListLoader setMentionedOnly(boolean mentionedOnly) {
        this.mentionedOnly = mentionedOnly;
        return this;
    }

    @Override
    protected void loadInternal() {
        addFromMessageRow();
        if (!mentionedOnly) {
            super.loadInternal();
        }
    }

    private void addFromMessageRow() {
        final long authorId = MyQuery.msgIdToLongColumnValue(MsgTable.AUTHOR_ID, selectedMessageId);
        if (mentionedOnly) {
            addUsersFromMessageBody(MbUser.fromOriginAndUserId(originOfSelectedMessage.getId(), authorId));
        } else {
            MbUser author = addUserIdToList(originOfSelectedMessage, authorId).mbUser;
            addUserIdToList(originOfSelectedMessage,
                    MyQuery.msgIdToLongColumnValue(ActivityTable.ACTOR_ID, selectedMessageId));
            addUserIdToList(originOfSelectedMessage,
                    MyQuery.msgIdToLongColumnValue(MsgTable.IN_REPLY_TO_USER_ID, selectedMessageId));
            // TODO: Add recipients
            addUsersFromMessageBody(author);
            addRebloggers();
        }
    }

    private void addUsersFromMessageBody(MbUser author) {
        List<MbUser> users = author.extractUsersFromBodyText(messageBody, false);
        for (MbUser mbUser: users) {
            addUserToList(UserViewItem.fromMbUser(mbUser));
        }
    }

    private void addRebloggers() {
        for (MbUser reblogger : MyQuery.getRebloggers(
                MyContextHolder.get().getDatabase(), origin.getId(), selectedMessageId)) {
            addUserIdToList(originOfSelectedMessage, reblogger.userId);
        }
    }

    @Override
    protected String getTitle() {
        return messageBody;
    }
}
