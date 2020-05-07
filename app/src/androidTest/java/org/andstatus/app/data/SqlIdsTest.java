/*
 * Copyright (c) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.TriState;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SqlIdsTest {
    @Before
    public void setUp() {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void fromTimeline() {
        MyAccount myAccount = myContextHolder.getNow().accounts().fromAccountName(demoData.conversationAccountName);
        assertTrue("account is not valid: " + demoData.conversationAccountName, myAccount.isValid());

        Timeline timeline = myContextHolder.getNow().timelines().filter(false, TriState.FALSE,
                TimelineType.SENT, myAccount.getActor(), myAccount.getOrigin()).findFirst().orElse(Timeline.EMPTY);
        assertNotEquals(0, SqlIds.actorIdsOfTimelineActor(timeline).size());

        Timeline timelineCombined = myContextHolder.getNow().timelines().filter(false, TriState.TRUE,
                TimelineType.SENT, Actor.EMPTY, Origin.EMPTY).findFirst().orElse(Timeline.EMPTY);
        assertNotEquals("No actors for " + timelineCombined,0, SqlIds.actorIdsOfTimelineActor(timelineCombined).size());

        long actorId = MyQuery.oidToId(OidEnum.ACTOR_OID, myAccount.getOriginId(),  demoData.conversationAuthorSecondActorOid);
        Actor actor = Actor.load(myContextHolder.getNow(),actorId);
        assertNotEquals("No actor for " + demoData.conversationAuthorSecondActorOid,0, actorId);
        Timeline timelineUser = myContextHolder.getNow().timelines().get(TimelineType.SENT, actor, myAccount.getOrigin());
        assertNotEquals("No actors for " + timelineUser,0, SqlIds.actorIdsOfTimelineActor(timelineCombined).size());
    }
}
