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
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;

import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public class UsersOfMessageListLoader extends UserListLoader {
    private final long mSelectedMessageId;
    private final Origin mOriginOfSelectedMessage;
    final String messageBody;

    public UsersOfMessageListLoader(UserListType userListType, MyAccount ma, long centralItemId, boolean isListCombined) {
        super(userListType, ma, centralItemId, isListCombined);

        mSelectedMessageId = centralItemId;
        messageBody = MyQuery.msgIdToStringColumnValue(MyDatabase.Msg.BODY, mSelectedMessageId);
        mOriginOfSelectedMessage = MyContextHolder.get().persistentOrigins().fromId(
                MyQuery.msgIdToOriginId(mSelectedMessageId));
    }

    @Override
    protected void loadInternal() {
        addFromMessageRow();
        super.loadInternal();
    }

    private void addFromMessageRow() {
        MbUser author = addUserIdToList(mOriginOfSelectedMessage,
                MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.AUTHOR_ID, mSelectedMessageId)).mbUser;
        addUserIdToList(mOriginOfSelectedMessage,
                MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.SENDER_ID, mSelectedMessageId));
        addUserIdToList(mOriginOfSelectedMessage,
                MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.IN_REPLY_TO_USER_ID, mSelectedMessageId));
        addUserIdToList(mOriginOfSelectedMessage,
                MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.RECIPIENT_ID, mSelectedMessageId));
        addUsersFromMessageBody(author);
        addRebloggers();
    }

    private void addUsersFromMessageBody(MbUser author) {
        List<MbUser> users = author.fromBodyText(
                MyQuery.msgIdToStringColumnValue(MyDatabase.Msg.BODY, mSelectedMessageId), false);
        for (MbUser mbUser: users) {
            addUserToList(UserListViewItem.fromMbUser(mbUser));
        }
    }

    private void addRebloggers() {
        for (long rebloggerId : MyQuery.getRebloggers(mSelectedMessageId)) {
            addUserIdToList(mOriginOfSelectedMessage, rebloggerId);
        }
    }

    @Override
    protected String getTitle() {
        return messageBody;
    }
}
