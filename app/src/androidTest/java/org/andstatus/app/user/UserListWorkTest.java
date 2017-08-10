/*
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

import org.andstatus.app.ListActivityTestHelper;
import org.andstatus.app.MyAction;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.ActivityTest;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.context.DemoData;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.util.MyLog;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UserListWorkTest extends ActivityTest<UserList> {

    @Override
    protected Class<UserList> getActivityClass() {
        return UserList.class;
    }

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        MyAccount ma = DemoData.getMyAccount(DemoData.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);

        long msgId = MyQuery.oidToId(OidEnum.MSG_OID, DemoData.getConversationOriginId(),
                DemoData.CONVERSATION_MENTIONS_MESSAGE_OID);
        assertTrue(msgId > 0);
        MyLog.i(this, "setUp ended");

        return new Intent(MyAction.VIEW_USERS.getAction(),
                MatchedUri.getUserListUri(ma.getUserId(), UserListType.USERS_OF_MESSAGE, ma.getOriginId(), msgId, ""));
    }

    @Test
    public void testFollowersList() throws InterruptedException {
        final String method = "testFollowersList";
        TestSuite.waitForListLoaded(getActivity(), 2);
        ListActivityTestHelper<UserList> helper = new ListActivityTestHelper<>(getActivity(), FollowersList.class);

        List<UserViewItem> listItems = getActivity().getListLoader().getList();
        assertEquals(listItems.toString(), 6, listItems.size());

        MbUser userA = UserListTest.getByUserOid(listItems, DemoData.CONVERSATION_MEMBER_USER_OID);

        assertTrue("Invoked Context menu for " + userA, helper.invokeContextMenuAction4ListItemId(
                method, userA.userId, UserListContextMenuItem.FOLLOWERS));

        FollowersList userList = (FollowersList) helper.waitForNextActivity(method, 15000);
        TestSuite.waitForListLoaded(userList, 1);

        List<UserViewItem> followersItems = userList.getListLoader().getList();
        ListActivityTestHelper<FollowersList> followersHelper = new ListActivityTestHelper<>(userList);
        followersHelper.clickListAtPosition(method,
                followersHelper.getPositionOfListItemId(followersItems.get(0).getUserId()));
        DbUtils.waitMs(method, 500);
    }
}
