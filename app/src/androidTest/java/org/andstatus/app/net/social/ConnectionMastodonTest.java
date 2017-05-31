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

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.context.DemoData;
import org.andstatus.app.util.RawResourceUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ConnectionMastodonTest {
    private ConnectionMastodonMock connection;
    private String accountUserOid = DemoData.MASTODON_TEST_ACCOUNT_USER_OID;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
        connection = new ConnectionMastodonMock();
    }

    @Test
    public void testGetHomeTimeline() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.mastodon_home_timeline);
        connection.getHttpMock().setResponse(jso);

        List<MbActivity> timeline = connection.getTimeline(Connection.ApiRoutineEnum.HOME_TIMELINE,
                new TimelinePosition("2656388"), TimelinePosition.EMPTY, 20, accountUserOid);
        assertNotNull("timeline returned", timeline);
        int size = 1;
        assertEquals("Number of items in the Timeline", size, timeline.size());

        int ind = 0;
        assertEquals("Is not a message", MbObjectType.MESSAGE, timeline.get(ind).getObjectType());
        MbMessage mbMessage = timeline.get(ind).getMessage();
        assertEquals("Favorited", TriState.UNKNOWN, mbMessage.getFavoritedByMe());
        MbUser sender = mbMessage.getAuthor();

        String stringDate = "2017-04-16T11:13:12.133Z";
        long parsedDate = connection.parseDate(stringDate);
        assertEquals("Parsing " + stringDate, 4, new Date(parsedDate).getMonth() + 1);
        assertEquals("Created at", parsedDate, sender.getCreatedDate());

        assertEquals("Sender is partially defined " + sender, false, sender.isPartiallyDefined());
        assertEquals("Sender Oid", "37", sender.oid);
        assertEquals("Username", "t131t1", sender.getUserName());

        assertEquals("Message Oid", "22", mbMessage.oid);
        assertEquals("Message url", "https://neumastodon.com/@t131t1/22", mbMessage.url);
        assertEquals("Body", "<p>I'm figuring out how to work with Mastodon</p>", mbMessage.getBody());
        assertEquals("Message application", "Web", mbMessage.via);

        assertEquals("Media attachments", 1, mbMessage.attachments.size());
        MbAttachment attachment = mbMessage.attachments.get(0);
        assertEquals("Content type", MyContentType.IMAGE, attachment.contentType);
        assertEquals("Content type", UriUtils.fromString("https://files.neumastodon.com/media_attachments/files/000/306/223/original/e678f956970a585b.png?1492832537"),
                attachment.getUri());
    }

    @Test
    public void testGetConversation() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.mastodon_get_conversation);
        connection.getHttpMock().setResponse(jso);

        List<MbActivity> timeline = connection.getConversation("5596683");
        assertNotNull("timeline returned", timeline);
        assertEquals("Number of items in the Timeline", 5, timeline.size());
    }

    @Test
    public void testGetMentions() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.mastodon_notifications);
        connection.getHttpMock().setResponse(jso);

        List<MbActivity> timeline = connection.getTimeline(Connection.ApiRoutineEnum.MENTIONS_TIMELINE,
                new TimelinePosition(""), TimelinePosition.EMPTY, 20, accountUserOid);
        assertNotNull("timeline returned", timeline);
        assertEquals("Number of items in the Timeline", 20, timeline.size());

        int ind = 0;
        assertEquals("Is not a message", MbObjectType.MESSAGE, timeline.get(ind).getObjectType());
        MbMessage mbMessage = timeline.get(ind).getMessage();
        assertEquals("Favorited " + mbMessage, TriState.UNKNOWN, mbMessage.getFavoritedByMe());
        assertEquals("Not reblogged " + mbMessage, true, mbMessage.isReblogged());
        assertEquals("Author's username", "AndStatus", mbMessage.getAuthor().getUserName());
        MbUser actor = mbMessage.getActor();
        assertEquals("Actor's Oid", "15451", actor.oid);
        assertEquals("Username", "Chaosphere", actor.getUserName());
        assertEquals("WebfingerId", "Chaosphere@mastodon.social", actor.getWebFingerId());

        ind = 19;
        assertEquals("Is not a message", MbObjectType.MESSAGE, timeline.get(ind).getObjectType());
        mbMessage = timeline.get(ind).getMessage();
        assertEquals("Favorited " + mbMessage, TriState.UNKNOWN, mbMessage.getFavoritedByMe());
        actor = mbMessage.getActor();
        assertEquals("Actor's Oid", "119218", actor.oid);
        assertEquals("Username", "izwx6502", actor.getUserName());
        assertEquals("WebfingerId", "izwx6502@mstdn.jp", actor.getWebFingerId());

        ind = 17;
        assertEquals("Is a message", MbObjectType.USER, timeline.get(ind).getObjectType());
        MbUser user = timeline.get(ind).getUser();
        actor = user.actor;
        assertEquals("Actor's Oid", "24853", actor.oid);
        assertEquals("Username", "resir014", actor.getUserName());
        assertEquals("WebfingerId", "resir014@icosahedron.website", actor.getWebFingerId());

        assertEquals("Not followed", TriState.TRUE, user.followedByActor);
    }
}
