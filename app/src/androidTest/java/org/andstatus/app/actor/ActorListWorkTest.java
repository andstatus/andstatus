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

package org.andstatus.app.actor;

import android.content.Intent;

import org.andstatus.app.MyAction;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.ActivityTest;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.timeline.ListActivityTestHelper;
import org.andstatus.app.util.MyLog;
import org.junit.Test;

import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ActorListWorkTest extends ActivityTest<ActorList> {

    @Override
    protected Class<ActorList> getActivityClass() {
        return ActorList.class;
    }

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        MyAccount ma = demoData.getMyAccount(demoData.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);

        long msgId = MyQuery.oidToId(OidEnum.MSG_OID, demoData.getConversationOriginId(),
                demoData.CONVERSATION_MENTIONS_NOTE_OID);
        assertTrue(msgId > 0);
        MyLog.i(this, "setUp ended");

        return new Intent(MyAction.VIEW_USERS.getAction(),
                MatchedUri.getActorListUri(ma.getActorId(), ActorListType.USERS_OF_MESSAGE, ma.getOriginId(), msgId, ""));
    }

    @Test
    public void testFollowersList() throws InterruptedException {
        final String method = "testFollowersList";
        TestSuite.waitForListLoaded(getActivity(), 2);
        ListActivityTestHelper<ActorList> helper = new ListActivityTestHelper<>(getActivity(), FollowersList.class);

        List<ActorViewItem> listItems = getActivity().getListLoader().getList();
        assertEquals(listItems.toString(), 5, listItems.size());

        Actor userA = ActorListTest.getByActorOid(listItems, demoData.CONVERSATION_AUTHOR_THIRD_ACTOR_OID);

        assertTrue("Invoked Context menu for " + userA, helper.invokeContextMenuAction4ListItemId(
                method, userA.actorId, ActorContextMenuItem.FOLLOWERS, 0));

        FollowersList userList = (FollowersList) helper.waitForNextActivity(method, 15000);
        TestSuite.waitForListLoaded(userList, 1);

        List<ActorViewItem> followersItems = userList.getListLoader().getList();
        ListActivityTestHelper<FollowersList> followersHelper = new ListActivityTestHelper<>(userList);
        followersHelper.clickListAtPosition(method,
                followersHelper.getPositionOfListItemId(followersItems.get(0).getActorId()));
        DbUtils.waitMs(method, 500);
    }
}
