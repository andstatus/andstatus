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
import org.andstatus.app.context.ActivityTest;
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

        long noteId = MyQuery.oidToId(OidEnum.NOTE_OID, demoData.getConversationOrigin().getId(),
                demoData.conversationMentionsNoteOid);
        assertTrue(noteId > 0);
        MyLog.i(this, "setUp ended");

        return new Intent(MyAction.VIEW_ACTORS.getAction(),
                MatchedUri.getActorListUri(ActorListType.ACTORS_OF_NOTE, demoData.getConversationOrigin().getId(),
                        noteId, ""));
    }

    @Test
    public void testFollowersList() throws InterruptedException {
        final String method = "testFollowersList";
        TestSuite.waitForListLoaded(getActivity(), 2);
        ListActivityTestHelper<ActorList> helper = new ListActivityTestHelper<>(getActivity(), FollowersList.class);

        List<ActorViewItem> listItems = getActivity().getListLoader().getList();
        assertEquals(listItems.toString(), 5, listItems.size());

        Actor actor = ActorListTest.getByActorOid(listItems, demoData.conversationAuthorThirdActorOid);

        assertTrue("Invoked Context menu for " + actor, helper.invokeContextMenuAction4ListItemId(
                method, actor.actorId, ActorContextMenuItem.FOLLOWERS, 0));

        FollowersList actorList = (FollowersList) helper.waitForNextActivity(method, 15000);
        TestSuite.waitForListLoaded(actorList, 1);

        List<ActorViewItem> followersItems = actorList.getListLoader().getList();
        ListActivityTestHelper<FollowersList> followersHelper = new ListActivityTestHelper<>(actorList);
        followersHelper.clickListAtPosition(method,
                followersHelper.getPositionOfListItemId(followersItems.get(0).getActorId()));
        DbUtils.waitMs(method, 500);
    }
}
