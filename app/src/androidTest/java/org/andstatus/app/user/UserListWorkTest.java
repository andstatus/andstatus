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
import org.andstatus.app.MyAction;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.database.DatabaseHolder;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.util.MyLog;

import java.util.List;

public class UserListWorkTest extends ActivityInstrumentationTestCase2<UserList> {
    public UserListWorkTest() {
        super(UserList.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);

        long msgId = MyQuery.oidToId(DatabaseHolder.OidEnum.MSG_OID, TestSuite.getConversationOriginId(),
                TestSuite.CONVERSATION_MENTIONS_MESSAGE_OID);
        assertTrue(msgId > 0);

        Intent intent = new Intent(MyAction.VIEW_USERS.getAction(),
                MatchedUri.getUserListUri(ma.getUserId(), UserListType.USERS_OF_MESSAGE, false, msgId));
        setActivityIntent(intent);

        MyLog.i(this, "setUp ended");
    }

    public void testFollowersList() throws InterruptedException {
        final String method = "testFollowersList";
        TestSuite.waitForListLoaded(this, getActivity(), 2);
        ListActivityTestHelper<UserList> helper = new ListActivityTestHelper<>(this, FollowersList.class);

        List<UserListViewItem> listItems = getActivity().getListLoader().getList();
        assertEquals(listItems.toString(), 5, listItems.size());

        MbUser userA = UserListTest.getByUserOid(listItems, TestSuite.CONVERSATION_MEMBER_USER_OID);

        assertTrue("Invoked Context menu for " + userA, helper.invokeContextMenuAction4ListItemId(
                method, userA.userId, UserListContextMenuItem.FOLLOWERS));

        FollowersList userList = (FollowersList) helper.waitForNextActivity(method, 15000);
        TestSuite.waitForListLoaded(this, userList, 1);

        List<UserListViewItem> followersItems = userList.getListLoader().getList();
        ListActivityTestHelper<FollowersList> followersHelper = new ListActivityTestHelper<>(this, userList);
        followersHelper.clickListAtPosition(method,
                followersHelper.getPositionOfListItemId(followersItems.get(0).getUserId()));
        Thread.sleep(500);
    }
}
