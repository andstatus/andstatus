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
import org.andstatus.app.data.ConversationInserter;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.msg.MessageListContextMenuItem;
import org.andstatus.app.msg.TimelineActivity;
import org.andstatus.app.msg.TimelineViewItem;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.timeline.TimelineType;
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
                MatchedUri.getTimelineUri(Timeline.getTimeline(TimelineType.HOME, ma, 0, null)));
        setActivityIntent(intent);

        MyLog.i(this, "setUp ended");
    }

    public void testUsersOfMessage() throws InterruptedException {
        final String method = "testUsersOfMessage";
        TestSuite.waitForListLoaded(this, getActivity(), 2);
        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<>(this, UserList.class);
        long msgId = MyQuery.oidToId(OidEnum.MSG_OID, TestSuite.getConversationOriginId(),
                TestSuite.CONVERSATION_MENTIONS_MESSAGE_OID);
        String body = MyQuery.msgIdToStringColumnValue(MsgTable.BODY, msgId);
        String logMsg = MyQuery.msgInfoForLog(msgId);

        List<MbUser> users = MbUser.fromOriginAndUserOid(TestSuite.getConversationMyAccount().getOriginId(), "").extractUsersFromBodyText(body, false);
        assertEquals(logMsg, 3, users.size());
        assertEquals(logMsg, "unknownUser@example.com", users.get(2).getUserName());

        TimelineViewItem item = getActivity().getListData().getById(msgId);
        boolean messageWasFound = item.getMsgId() == msgId;
        if (!messageWasFound) {
            item = getActivity().getListData().getItem(0);
            msgId = item.getMsgId();
            String logMsg1 = "The message was not found in the timeline " + getActivity().getListData() +
                    " new msgId=" + msgId;
            logMsg += "\n" + logMsg1;
            MyLog.i(method, logMsg1);
        }

        assertTrue("Invoked Context menu for " + logMsg, helper.invokeContextMenuAction4ListItemId(method, msgId, MessageListContextMenuItem.USERS_OF_MESSAGE));

        UserList userList = (UserList) helper.waitForNextActivity(method, 15000);
        TestSuite.waitForListLoaded(this, userList, 1);

        List<UserListViewItem> listItems = userList.getListLoader().getList();

        if (messageWasFound) {
            assertEquals(listItems.toString(), 5, listItems.size());

            MbUser userE = ConversationInserter.getUsers().get(TestSuite.CONVERSATION_MEMBER_USER_OID);
            assertTrue("Found " + TestSuite.CONVERSATION_MEMBER_USER_OID + " cached ", userE != null);
            MbUser userA = getByUserOid(listItems, TestSuite.CONVERSATION_MEMBER_USER_OID);
            assertTrue("Found " + TestSuite.CONVERSATION_MEMBER_USER_OID + ", " + logMsg, userA != null);
            compareAttributes(userE, userA, true);
        }

        ListActivityTestHelper<UserList> userListHelper = new ListActivityTestHelper<>(this, userList);
        userListHelper.clickListAtPosition(method, userListHelper.getPositionOfListItemId(listItems.get(
                listItems.size() > 2 ? 2 : 0).getUserId()));
        DbUtils.waitMs(method, 500);
    }

    private void compareAttributes(MbUser expected, MbUser actual, boolean forUserList) {
        assertEquals("Oid", expected.oid, actual.oid);
        assertEquals("Username", expected.getUserName(), actual.getUserName());
        assertEquals("WebFinger ID", expected.getWebFingerId(), actual.getWebFingerId());
        assertEquals("Display name", expected.getRealName(), actual.getRealName());
        assertEquals("Description", expected.getDescription(), actual.getDescription());
        assertEquals("Location", expected.location, actual.location);
        assertEquals("Profile URL", expected.getProfileUrl(), actual.getProfileUrl());
        assertEquals("Homepage", expected.getHomepage(), actual.getHomepage());
        if (!forUserList) {
            assertEquals("Avatar URL", expected.avatarUrl, actual.avatarUrl);
            assertEquals("Banner URL", expected.bannerUrl, actual.bannerUrl);
        }
        assertEquals("Messages count", expected.msgCount, actual.msgCount);
        assertEquals("Favorites count", expected.favoritesCount, actual.favoritesCount);
        assertEquals("Following (friends) count", expected.followingCount, actual.followingCount);
        assertEquals("Followers count", expected.followersCount, actual.followersCount);
        assertEquals("Created at", expected.getCreatedDate(), actual.getCreatedDate());
        assertEquals("Updated at", expected.getUpdatedDate(), actual.getUpdatedDate());
    }

    static MbUser getByUserOid(List<UserListViewItem> listItems, String oid) {
        for (UserListViewItem item : listItems) {
            if (item.mbUser.oid.equals(oid)) {
                return item.mbUser;
            }
        }
        return null;
    }
}
