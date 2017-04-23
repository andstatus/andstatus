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

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.util.RawResourceUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public class ConnectionMastodonTest extends InstrumentationTestCase {
    private static final String MESSAGE_OID = "22";
    private ConnectionMastodonMock connection;
    String accountUserOid = TestSuite.MASTODON_TEST_ACCOUNT_USER_OID;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
        connection = new ConnectionMastodonMock();
    }

    public void testGetHomeTimeline() throws IOException {
        String jso = RawResourceUtils.getString(this.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.mastodon_home_timeline);
        connection.getHttpMock().setResponse(jso);

        List<MbTimelineItem> timeline = connection.getTimeline(Connection.ApiRoutineEnum.STATUSES_HOME_TIMELINE,
                new TimelinePosition("2656388"), TimelinePosition.getEmpty(), 20, accountUserOid);
        assertNotNull("timeline returned", timeline);
        int size = 1;
        assertEquals("Number of items in the Timeline", size, timeline.size());

        int ind = 0;
        assertEquals("Is not a message", MbTimelineItem.ItemType.MESSAGE, timeline.get(ind).getType());
        MbMessage mbMessage = timeline.get(ind).mbMessage;
        assertEquals("Favorited", TriState.UNKNOWN, mbMessage.getFavoritedByActor());
        MbUser sender = mbMessage.sender;

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
}
