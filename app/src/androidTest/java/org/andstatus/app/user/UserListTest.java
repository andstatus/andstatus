/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.user;

import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;

import org.andstatus.app.ListActivityTestHelper;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.msg.ContextMenuItem;
import org.andstatus.app.msg.TimelineActivity;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.util.MyLog;

import java.util.List;

public class UserListTest extends ActivityInstrumentationTestCase2<TimelineActivity> {
    public UserListTest() {
        super(TimelineActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);

        Intent intent = new Intent(Intent.ACTION_VIEW,
                MatchedUri.getTimelineUri(ma.getUserId(), TimelineType.HOME, false, 0));
        setActivityIntent(intent);

        MyLog.i(this, "setUp ended");
    }

    public void testUsersOfMessage() throws InterruptedException {
        final String method = "testUsersOfMessage";
        TestSuite.waitForListLoaded(this, getActivity(), 2);
        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<>(this, UserList.class);
        long msgId = MyQuery.oidToId(MyDatabase.OidEnum.MSG_OID, TestSuite.getConversationOriginId(),
                TestSuite.CONVERSATION_MENTIONS_MESSAGE_OID);
        String body = MyQuery.msgIdToStringColumnValue(MyDatabase.Msg.BODY, msgId);
        String logMsg = "msgId:" + msgId + "; text:'" + body + "'";

        List<MbUser> users = MbUser.fromOriginAndUserOid(TestSuite.getConversationMyAccount().getOriginId(), "").fromBodyText(body, false);
        assertEquals(logMsg, 3, users.size());
        assertEquals(logMsg, "unknownUser@example.com", users.get(2).getUserName());

        assertTrue("Invoked Context menu for " + logMsg, helper.invokeContextMenuAction4ListItemId(method, msgId, ContextMenuItem.USERS_OF_MESSAGE));

        UserList userList = (UserList) helper.waitForNextActivity(method, 15000);
        TestSuite.waitForListLoaded(this, userList, 1);

        List<UserListViewItem> listItems = userList.getListLoader().getList();
        assertEquals(listItems.toString(), 5, listItems.size());

        ListActivityTestHelper<UserList> userListHelper = new ListActivityTestHelper<>(this, userList);
        userListHelper.clickListAtPosition(method, userListHelper.getPositionOfListItemId(listItems.get(2).getUserId()));
        Thread.sleep(500);
    }

}
