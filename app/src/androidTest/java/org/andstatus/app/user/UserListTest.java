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

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DemoConversationInserter;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.msg.MessageListContextMenuItem;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.timeline.ListActivityTestHelper;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.timeline.TimelineActivityTest;
import org.andstatus.app.timeline.TimelineData;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.junit.Test;

import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UserListTest extends TimelineActivityTest {

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        final MyAccount ma = demoData.getMyAccount(demoData.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);

        MyLog.i(this, "setUp ended");
        return new Intent(Intent.ACTION_VIEW,
                Timeline.getTimeline(TimelineType.HOME, ma, 0, null).getUri());
    }

    @Test
    public void testUsersOfMessage() throws InterruptedException {
        final String method = "testUsersOfMessage";
        TestSuite.waitForListLoaded(getActivity(), 2);
        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<>(getActivity(), UserList.class);
        long msgId = MyQuery.oidToId(OidEnum.MSG_OID, demoData.getConversationOriginId(),
                demoData.CONVERSATION_MENTIONS_MESSAGE_OID);
        String body = MyQuery.msgIdToStringColumnValue(MsgTable.BODY, msgId);
        String logMsg = MyQuery.msgInfoForLog(msgId);

        List<MbUser> users = MbUser.fromOriginAndUserOid(demoData.getConversationMyAccount().getOrigin(), "").extractUsersFromBodyText(body, false);
        assertEquals(logMsg, 3, users.size());
        assertEquals(logMsg, "unknownUser@example.com", users.get(2).getUserName());

        ActivityViewItem item = ActivityViewItem.EMPTY;
        TimelineData<ActivityViewItem> timelineData = getActivity().getListData();
        for (int position=0; position < timelineData.size(); position++) {
            ActivityViewItem item2 = timelineData.getItem(position);
            if (item2.message.getId() == msgId) {
                item = item2;
                break;
            }
        }
        boolean messageWasFound = !item.equals(ActivityViewItem.EMPTY);
        if (!messageWasFound) {
            item = timelineData.getItem(0);
            String logMsg1 = "The message was not found in the timeline " + timelineData +
                    " new item: " + item;
            logMsg += "\n" + logMsg1;
            MyLog.i(method, logMsg1);
        }

        assertTrue("Invoked Context menu for " + logMsg, helper.invokeContextMenuAction4ListItemId(method,
                item.getId(), MessageListContextMenuItem.USERS_OF_MESSAGE, R.id.message_wrapper));

        UserList userList = (UserList) helper.waitForNextActivity(method, 15000);
        TestSuite.waitForListLoaded(userList, 1);

        List<UserViewItem> listItems = userList.getListLoader().getList();

        if (messageWasFound) {
            assertEquals(listItems.toString(), 5, listItems.size());

            MbUser userE = DemoConversationInserter.getUsers().get(demoData.CONVERSATION_AUTHOR_THIRD_USER_OID);
            assertTrue("Found " + demoData.CONVERSATION_AUTHOR_THIRD_USER_OID + " cached ", userE != null);
            MbUser userA = getByUserOid(listItems, demoData.CONVERSATION_AUTHOR_THIRD_USER_OID);
            assertTrue("Found " + demoData.CONVERSATION_AUTHOR_THIRD_USER_OID + ", " + logMsg, userA != null);
            compareAttributes(userE, userA, true);
        }

        ListActivityTestHelper<UserList> userListHelper = new ListActivityTestHelper<>(userList);
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

    static MbUser getByUserOid(List<UserViewItem> listItems, String oid) {
        for (UserViewItem item : listItems) {
            if (item.mbUser.oid.equals(oid)) {
                return item.mbUser;
            }
        }
        return null;
    }
}
