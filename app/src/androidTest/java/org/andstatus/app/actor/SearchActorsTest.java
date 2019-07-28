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
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.util.MyLog;
import org.junit.Test;

import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SearchActorsTest extends ActivityTest<ActorList> {

    @Override
    protected Class<ActorList> getActivityClass() {
        return ActorList.class;
    }

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithAccounts(this);
        MyLog.i(this, "setUp ended");

        return new Intent(MyAction.VIEW_ACTORS.getAction(),
            MatchedUri.getActorListUri(ActorListType.ACTORS_AT_ORIGIN, 0, 0, demoData.t131tUsername));
    }

    @Test
    public void testSearchActor() throws InterruptedException {
        TestSuite.waitForListLoaded(getActivity(), 2);

        List<ActorViewItem> listItems = getActivity().getListLoader().getList();
        assertTrue("Found only " + listItems.size() + "items\n" + listItems.toString(), listItems.size() > 1);

        Actor actor = ActorListTest.getByActorOid(listItems, demoData.pumpioTestAccountActorOid);
        assertEquals("Actor was not found\n" + listItems.toString(), demoData.pumpioTestAccountActorOid, actor.oid);
    }
}
