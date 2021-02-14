/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.timeline;

import android.content.Intent;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ActorTimelineTest extends TimelineActivityTest<ActivityViewItem> {

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        MyContext myContext = myContextHolder.getBlocking();
        final MyAccount ma = demoData.getMyAccount(demoData.conversationAccountName);
        assertTrue(ma.isValid());
        myContext.accounts().setCurrentAccount(ma);
        long actorId = MyQuery.oidToId(OidEnum.ACTOR_OID, ma.getOriginId(), demoData.conversationAuthorSecondActorOid);
        Actor actor = Actor.fromId(ma.getOrigin(), actorId);
        assertNotEquals("Actor " + demoData.conversationAuthorSecondActorOid + " id=" + actorId + " -> "
                + actor, 0, actor.actorId);
        final Timeline timeline = myContext.timelines().get(TimelineType.SENT, actor, ma.getOrigin());
        assertFalse("Timeline " + timeline, timeline.isCombined());
        timeline.forgetPositionsAndDates();
        MyLog.i(this, "setUp ended, " + timeline);
        return new Intent(Intent.ACTION_VIEW, timeline.getUri());
    }

    @Test
    public void openSecondAuthorTimeline() {
        wrap(this::_openSecondAuthorTimeline);
    }

    private void _openSecondAuthorTimeline() throws InterruptedException {
        final String method = "openSecondAuthorTimeline";
        TestSuite.waitForListLoaded(getActivity(), 10);
        TimelineData<ActivityViewItem> timelineData = getActivity().getListData();
        ActivityViewItem followItem = ActivityViewItem.EMPTY;
        for (int position = 0; position < timelineData.size(); position++) {
            ActivityViewItem item = timelineData.getItem(position);
            if (item.activityType == ActivityType.FOLLOW) {
                followItem = item;
            }
        }
        assertNotEquals("No follow action by " + demoData.conversationAuthorSecondActorOid
                        + " in " + timelineData,
                ActivityViewItem.EMPTY, followItem);
    }
}
