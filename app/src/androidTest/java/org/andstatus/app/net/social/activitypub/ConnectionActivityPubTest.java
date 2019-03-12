/* 
 * Copyright (c) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social.activitypub;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.http.HttpConnectionMock;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.AObjectType;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpointType;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.TriState;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.net.social.activitypub.VerifyCredentialsActivityPubTest.ACTOR_OID;
import static org.andstatus.app.net.social.activitypub.VerifyCredentialsActivityPubTest.UNIQUE_NAME_IN_ORIGIN;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class ConnectionActivityPubTest {
    private Connection connection;
    private HttpConnectionMock httpConnection;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithAccounts(this);

        TestSuite.setHttpConnectionMockClass(HttpConnectionMock.class);
        OriginConnectionData connectionData = OriginConnectionData.fromMyAccount(
                demoData.getMyAccount(demoData.activityPubTestAccountName), TriState.UNKNOWN);
        connection = connectionData.newConnection();
        httpConnection = (HttpConnectionMock) connection.getHttp();
        TestSuite.setHttpConnectionMockClass(null);
    }

    @Test
    public void getTimeline() throws IOException {
        String sinceId = "";
        httpConnection.addResponse(org.andstatus.app.tests.R.raw.activitypub_inbox_pleroma);
        Actor actorForTimeline = Actor.fromOid(connection.getData().getOrigin(), ACTOR_OID)
                .withUniqueNameInOrigin(UNIQUE_NAME_IN_ORIGIN);
        actorForTimeline.endpoints.add(ActorEndpointType.API_INBOX, "https://pleroma.site/users/AndStatus/inbox");
        List<AActivity> timeline = connection.getTimeline(Connection.ApiRoutineEnum.HOME_TIMELINE,
                new TimelinePosition(sinceId), TimelinePosition.EMPTY, 20, actorForTimeline);
        assertNotNull("timeline returned", timeline);
        assertEquals("Number of items in the Timeline", 5, timeline.size());

        int ind = 0;
        AActivity activity = timeline.get(ind);
        assertEquals("Creating a Note " + activity, AObjectType.NOTE, activity.getObjectType());
        Note note = activity.getNote();
        assertEquals("Note name " + note, "", note.getName());
        assertThat("Note body " + note, note.getContent(), startsWith("We could successfully create an account"));
        assertEquals("Note updated at " + TestSuite.utcTime(activity.getUpdatedDate()),
                TestSuite.utcTime(2019, Calendar.FEBRUARY, 10, 17, 37, 25).toString(),
                TestSuite.utcTime(activity.getUpdatedDate()).toString());
        Actor actor = activity.getActor();
        assertEquals("Actor's oid " + activity, "https://pleroma.site/users/ActivityPubTester", actor.oid);
        assertEquals("Actor's Webfinger " + activity, "", actor.getWebFingerId());

        assertEquals("Actor is an Author", actor, activity.getAuthor());
        assertEquals("Should be Create " + activity, ActivityType.CREATE, activity.type);
        assertEquals("Favorited by me " + activity, TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor));

        ind++;
        activity = timeline.get(ind);
        assertEquals("Is not FOLLOW " + activity, ActivityType.FOLLOW, activity.type);
        assertEquals("Actor", "https://pleroma.site/users/ActivityPubTester", activity.getActor().oid);
        assertEquals("Actor followed by me", TriState.UNKNOWN, activity.getActor().followedByMe);
        assertEquals("Activity Object", AObjectType.ACTOR, activity.getObjectType());
        Actor objActor = activity.getObjActor();
        assertEquals("objActor followed", "https://pleroma.site/users/AndStatus", objActor.oid);
        assertEquals("Actor followed by me", TriState.UNKNOWN, objActor.followedByMe);

        for (ind = 2; ind < 5; ind++) {
            activity = timeline.get(ind);
            assertEquals("Is not UPDATE " + activity, ActivityType.UPDATE, activity.type);
            assertEquals("Actor", AObjectType.ACTOR, activity.getObjectType());
            objActor = activity.getObjActor();
            assertEquals("Following", TriState.UNKNOWN, objActor.followedByMe);
            assertEquals("Url of objActor", "https://pleroma.site/users/AndStatus", objActor.getProfileUrl());
            assertEquals("WebFinger ID", "andstatus@pleroma.site", objActor.getWebFingerId());
        }
    }

    @Test
    public void getNotesByActor() throws IOException {
        String ACTOR_OID2 = "https://pleroma.site/users/kaniini";
        String sinceId = "";
        httpConnection.addResponse(org.andstatus.app.tests.R.raw.activitypub_outbox_pleroma);
        Actor actorForTimeline = Actor.fromOid(connection.getData().getOrigin(), ACTOR_OID2)
                .withUniqueNameInOrigin(UNIQUE_NAME_IN_ORIGIN);
        actorForTimeline.endpoints.add(ActorEndpointType.API_OUTBOX, ACTOR_OID2 + "/outbox");
        List<AActivity> timeline = connection.getTimeline(Connection.ApiRoutineEnum.ACTOR_TIMELINE,
                new TimelinePosition(sinceId), TimelinePosition.EMPTY, 20, actorForTimeline);
        assertNotNull("timeline returned", timeline);
        assertEquals("Number of items in the Timeline", 10, timeline.size());

        AActivity activity = timeline.get(7);
        assertEquals("Announcing " + activity, ActivityType.ANNOUNCE, activity.type);
        assertEquals("Announcing a Note " + activity, AObjectType.NOTE, activity.getObjectType());
        Note note = activity.getNote();
        assertEquals("Note oid " + note, "https://lgbtq.cool/users/abby/statuses/101702144808655868", note.oid);
        Actor actor = activity.getActor();
        assertEquals("Actor's oid " + activity, ACTOR_OID2, actor.oid);

        assertEquals("Author is unknown", Actor.EMPTY, activity.getAuthor());
    }
}
