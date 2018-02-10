/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social;

import android.support.test.InstrumentationRegistry;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.util.RawResourceUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class ConnectionMastodonTest {
    private ConnectionMastodonMock connection;
    private String accountActorOid;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
        accountActorOid = demoData.mastodonTestAccountActorOid;
        connection = new ConnectionMastodonMock();
    }

    @Test
    public void testGetHomeTimeline() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.mastodon_home_timeline);
        connection.getHttpMock().setResponse(jso);

        List<AActivity> timeline = connection.getTimeline(Connection.ApiRoutineEnum.HOME_TIMELINE,
                new TimelinePosition("2656388"), TimelinePosition.EMPTY, 20, accountActorOid);
        assertNotNull("timeline returned", timeline);
        int size = 1;
        assertEquals("Number of items in the Timeline", size, timeline.size());

        int ind = 0;
        AActivity activity = timeline.get(ind);
        assertEquals("Timeline position", "22", activity.getTimelinePosition().getPosition());
        assertEquals("Note Oid", "22", activity.getNote().oid);
        assertEquals("Account unknown " + activity, true, MyContextHolder.get().accounts()
                .fromActorOfSameOrigin(activity.accountActor).isValid());
        Note note = activity.getNote();
        assertEquals("Is not a note " + activity, AObjectType.NOTE, activity.getObjectType());
        assertEquals("Favorited " + activity, TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor));
        Actor actor = activity.getActor();

        String stringDate = "2017-04-16T11:13:12.133Z";
        long parsedDate = connection.parseDate(stringDate);
        assertEquals("Parsing " + stringDate, 4, new Date(parsedDate).getMonth() + 1);
        assertEquals("Created at", parsedDate, actor.getCreatedDate());

        assertEquals("Actor is partially defined " + actor, false, actor.isPartiallyDefined());
        assertEquals("Actor Oid", "37", actor.oid);
        assertEquals("Username", "t131t1", actor.getUsername());

        assertEquals("Note Oid " + activity, "22", note.oid);
        assertEquals("Note url" + activity, "https://neumastodon.com/@t131t1/22", note.url);
        assertEquals("Body", "<p>I'm figuring out how to work with Mastodon</p>", note.getBody());
        assertEquals("Note application", "Web", note.via);

        assertEquals("Media attachments", 1, note.attachments.size());
        Attachment attachment = note.attachments.get(0);
        assertEquals("Content type", MyContentType.IMAGE, attachment.contentType);
        assertEquals("Content type", UriUtils.fromString("https://files.neumastodon.com/media_attachments/files/000/306/223/original/e678f956970a585b.png?1492832537"),
                attachment.getUri());
    }

    @Test
    public void testGetConversation() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.mastodon_get_conversation);
        connection.getHttpMock().setResponse(jso);

        List<AActivity> timeline = connection.getConversation("5596683");
        assertNotNull("timeline returned", timeline);
        assertEquals("Number of items in the Timeline", 5, timeline.size());
    }

    @Test
    public void testGetNotifications() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.mastodon_notifications);
        connection.getHttpMock().setResponse(jso);

        List<AActivity> timeline = connection.getTimeline(Connection.ApiRoutineEnum.NOTIFICATIONS_TIMELINE,
                new TimelinePosition(""), TimelinePosition.EMPTY, 20, accountActorOid);
        assertNotNull("timeline returned", timeline);
        assertEquals("Number of items in the Timeline", 20, timeline.size());

        int ind = 0;
        AActivity activity = timeline.get(ind);
        assertEquals("Timeline position", "2667058", activity.getTimelinePosition().getPosition());
        assertEquals("Note Oid", "4729037", activity.getNote().oid);
        assertEquals("Is not a Reblog " + activity, ActivityType.ANNOUNCE, activity.type);
        assertEquals("Is not an activity", AObjectType.ACTIVITY, activity.getObjectType());
        Actor actor = activity.getActor();
        assertEquals("Actor's Oid", "15451", actor.oid);
        assertEquals("Actor's username", "Chaosphere", actor.getUsername());
        assertEquals("WebfingerId", "Chaosphere@mastodon.social", actor.getWebFingerId());
        assertEquals("Author's username" + activity, "AndStatus", activity.getAuthor().getUsername());
        assertEquals("Favorited " + activity, TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor));

        ind = 2;
        activity = timeline.get(ind);
        assertEquals("Timeline position", "2674022", activity.getTimelinePosition().getPosition());
        assertEquals("Note Oid", "4729037", activity.getNote().oid);
        assertEquals("Is not an activity " + activity, AObjectType.ACTIVITY, activity.getObjectType());
        assertEquals("Is not LIKE " + activity, ActivityType.LIKE, activity.type);
        assertThat(activity.getNote().getBody(), is("<p>IT infrastructure of modern church</p>"));
        assertEquals("Favorited " + activity, TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor));
        assertEquals("Author's username", "AndStatus", activity.getAuthor().getUsername());
        actor = activity.getActor();
        assertEquals("Actor's Oid", "48790", actor.oid);
        assertEquals("Actor's Username", "vfrmedia", actor.getUsername());
        assertEquals("WebfingerId", "vfrmedia@social.tchncs.de", actor.getWebFingerId());

        ind = 17;
        activity = timeline.get(ind);
        assertEquals("Is not FOLLOW " + activity, ActivityType.FOLLOW, activity.type);
        assertEquals("Is not an ACTOR", AObjectType.ACTOR, activity.getObjectType());
        actor = activity.getActor();
        assertEquals("Actor's Oid", "24853", actor.oid);
        assertEquals("Username", "resir014", actor.getUsername());
        assertEquals("WebfingerId", "resir014@icosahedron.website", actor.getWebFingerId());
        Actor objActor = activity.getObjActor();
        assertEquals("Not following me" + activity, accountActorOid, objActor.oid);

        ind = 19;
        activity = timeline.get(ind);
        assertEquals("Is not UPDATE " + activity, ActivityType.UPDATE, activity.type);
        assertEquals("Is not a note", AObjectType.NOTE, activity.getObjectType());
        assertThat(activity.getNote().getBody(), containsString("universe of Mastodon"));
        actor = activity.getActor();
        assertEquals("Actor's Oid", "119218", actor.oid);
        assertEquals("Username", "izwx6502", actor.getUsername());
        assertEquals("WebfingerId", "izwx6502@mstdn.jp", actor.getWebFingerId());
    }
}
