/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import org.andstatus.app.context.DemoData;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.HttpReadResult;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.util.RawResourceUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConnectionGnuSocialTest {
    private ConnectionTwitterGnuSocialMock connection;

    public static MbMessage getMessageWithAttachment(Context context) throws Exception {
        ConnectionGnuSocialTest test = new ConnectionGnuSocialTest();
        test.setUp();
        return test.privateGetMessageWithAttachment(context, true);
    }
    
    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
        connection = new ConnectionTwitterGnuSocialMock();
    }

    @Test
    public void testGetPublicTimeline() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.quitter_home);
        connection.getHttpMock().setResponse(jso);

        String accountUserOid = DemoData.GNUSOCIAL_TEST_ACCOUNT_USER_OID;
        List<MbActivity> timeline = connection.getTimeline(ApiRoutineEnum.PUBLIC_TIMELINE,
                new TimelinePosition("2656388"), TimelinePosition.EMPTY, 20, accountUserOid);
        assertNotNull("timeline returned", timeline);
        int size = 3;
        assertEquals("Number of items in the Timeline", size, timeline.size());

        int ind = 0;
        assertEquals("Posting message", MbObjectType.MESSAGE, timeline.get(ind).getObjectType());
        MbMessage mbMessage = timeline.get(ind).getMessage();
        assertEquals("conversationOid", "2218650", mbMessage.conversationOid);
        assertTrue("Favorited", mbMessage.getFavoritedByMe().toBoolean(false));
        assertEquals("Oid", "116387", mbMessage.getAuthor().oid);
        assertEquals("Username", "aru", mbMessage.getAuthor().getUserName());
        assertEquals("WebFinger ID", "aru@status.vinilox.eu", mbMessage.getAuthor().getWebFingerId());
        assertEquals("Display name", "aru", mbMessage.getAuthor().getRealName());
        assertEquals("Description", "Manjaro user, student of physics and metalhead. Excuse my english ( ͡° ͜ʖ ͡°)", mbMessage.getAuthor().getDescription());
        assertEquals("Location", "Spain", mbMessage.getAuthor().location);
        assertEquals("Profile URL", "https://status.vinilox.eu/aru", mbMessage.getAuthor().getProfileUrl());
        assertEquals("Homepage", "", mbMessage.getAuthor().getHomepage());
        assertEquals("Avatar URL", "http://quitter.se/avatar/116387-48-20140609172839.png", mbMessage.getAuthor().avatarUrl);
        assertEquals("Banner URL", "", mbMessage.getAuthor().bannerUrl);
        assertEquals("Messages count", 523, mbMessage.getAuthor().msgCount);
        assertEquals("Favorites count", 11, mbMessage.getAuthor().favoritesCount);
        assertEquals("Following (friends) count", 23, mbMessage.getAuthor().followingCount);
        assertEquals("Followers count", 21, mbMessage.getAuthor().followersCount);
        assertEquals("Created at", connection.parseDate("Sun Feb 09 22:33:42 +0100 2014"), mbMessage.getAuthor().getCreatedDate());
        assertEquals("Updated at", 0, mbMessage.getAuthor().getUpdatedDate());

        ind++;
        mbMessage = timeline.get(ind).getMessage();
        assertEquals("conversationOid", "2218650", mbMessage.conversationOid);
        assertTrue("Does not have a recipient", mbMessage.getRecipient().isEmpty());
        assertTrue("Is a reblog", !mbMessage.isReblogged());
        assertTrue("Is a reply", mbMessage.getInReplyTo().nonEmpty());
        assertEquals("Reply to the message id", "2663833", mbMessage.getInReplyTo().oid);
        assertEquals("Reply to the message by userOid", "114973", mbMessage.getInReplyTo().getAuthor().oid);
        assertFalse("Is not Favorited", mbMessage.getFavoritedByMe().toBoolean(true));
        String startsWith = "@<span class=\"vcard\">";
        assertEquals("Body of this message starts with", startsWith, mbMessage.getBody().substring(0, startsWith.length()));
        assertEquals("Username", "andstatus", mbMessage.getAuthor().getUserName());
        assertEquals("Display name", "AndStatus@quitter.se", mbMessage.getAuthor().getRealName());
        assertEquals("Banner URL", "https://quitter.se/file/3fd65c6088ea02dc3a5ded9798a865a8ff5425b13878da35ad894cd084d015fc.png", mbMessage.getAuthor().bannerUrl);

        ind++;
        MbActivity activity = timeline.get(ind);
        mbMessage = activity.getMessage();
        assertEquals("conversationOid", "2218650", mbMessage.conversationOid);
        assertTrue("Message is public", mbMessage.isPublic());
        assertFalse("Not Favorited", mbMessage.getFavoritedByMe().toBoolean(false));
        assertEquals("MyAccount", accountUserOid, activity.accountUser.oid);
        assertEquals("Actor", mbMessage.getAuthor().oid, activity.getActor().oid);
        assertEquals("Oid", "114973", mbMessage.getAuthor().oid);
        assertEquals("Username", "mmn", mbMessage.getAuthor().getUserName());
        assertEquals("WebFinger ID", "mmn@social.umeahackerspace.se", mbMessage.getAuthor().getWebFingerId());
        assertEquals("Display name", "mmn", mbMessage.getAuthor().getRealName());
        assertEquals("Description", "", mbMessage.getAuthor().getDescription());
        assertEquals("Location", "Umeå, Sweden", mbMessage.getAuthor().location);
        assertEquals("Profile URL", "https://social.umeahackerspace.se/mmn", mbMessage.getAuthor().getProfileUrl());
        assertEquals("Homepage", "http://blog.mmn-o.se/", mbMessage.getAuthor().getHomepage());
        assertEquals("Avatar URL", "http://quitter.se/avatar/114973-48-20140702161520.jpeg", mbMessage.getAuthor().avatarUrl);
        assertEquals("Banner URL", "", mbMessage.getAuthor().bannerUrl);
        assertEquals("Messages count", 1889, mbMessage.getAuthor().msgCount);
        assertEquals("Favorites count", 31, mbMessage.getAuthor().favoritesCount);
        assertEquals("Following (friends) count", 17, mbMessage.getAuthor().followingCount);
        assertEquals("Followers count", 31, mbMessage.getAuthor().followersCount);
        assertEquals("Created at", connection.parseDate("Wed Aug 14 10:05:28 +0200 2013"), mbMessage.getAuthor().getCreatedDate());
        assertEquals("Updated at", 0, mbMessage.getAuthor().getUpdatedDate());
    }

    @Test
    public void testSearch() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.twitter_home_timeline);
        connection.getHttpMock().setResponse(jso);
        
        List<MbActivity> timeline = connection.search(new TimelinePosition(""), TimelinePosition.EMPTY, 20,
                DemoData.GLOBAL_PUBLIC_MESSAGE_TEXT);
        assertNotNull("timeline returned", timeline);
        int size = 4;
        assertEquals("Number of items in the Timeline", size, timeline.size());
    }

    @Test
    public void testPostWithMedia() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.quitter_message_with_attachment);
        connection.getHttpMock().setResponse(jso);
        
        MbActivity activity = connection.updateStatus("Test post message with media", "", "", DemoData.LOCAL_IMAGE_TEST_URI);
        activity.getMessage().setPublic(true);
        assertEquals("Message returned", privateGetMessageWithAttachment(
                InstrumentationRegistry.getInstrumentation().getContext(), false), activity.getMessage());
    }

    @Test
    public void testGetMessageWithAttachment() throws IOException {
        privateGetMessageWithAttachment(InstrumentationRegistry.getInstrumentation().getContext(), true);
    }

    private MbMessage privateGetMessageWithAttachment(Context context, boolean uniqueUid) throws IOException {
        final String MESSAGE_OID = "2215662";
        // Originally downloaded from https://quitter.se/api/statuses/show.json?id=2215662
        String jso = RawResourceUtils.getString(context, org.andstatus.app.tests.R.raw.quitter_message_with_attachment);
        connection.getHttpMock().setResponse(jso);
        MbMessage msg = connection.getMessage(MESSAGE_OID).getMessage();
        if (uniqueUid) {
            msg.oid += "_" + DemoData.TESTRUN_UID;
        }
        assertNotNull("message returned", msg);
        assertEquals("conversationOid", "1956322", msg.conversationOid);
        assertEquals("Author", "mcscx", msg.getAuthor().getUserName());
        assertEquals("null Homepage (url) should be treated as blank", "", msg.getAuthor().getHomepage());

        assertEquals("has attachment", msg.attachments.size(), 1);
        MbAttachment attachment = MbAttachment.fromUrlAndContentType(new URL(
                "https://quitter.se/file/mcscx-20131110T222250-427wlgn.png")
                , MyContentType.IMAGE);
        assertEquals("attachment", attachment, msg.attachments.get(0));
        return msg;
    }

    @Test
    public void testReblog() throws IOException {
        final String MESSAGE_OID = "10341561";
        String jString = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.loadaverage_repost_response);
        connection.getHttpMock().setResponse(jString);
        MbActivity activity = connection.postReblog(MESSAGE_OID);
        assertEquals(MbActivityType.ANNOUNCE, activity.type);
        MbMessage message = activity.getMessage();
        assertEquals(message.toString(), MESSAGE_OID, message.oid);
        assertEquals("conversationOid", "9118253", message.conversationOid);
        assertEquals(1, connection.getHttpMock().getRequestsCounter());
        HttpReadResult result = connection.getHttpMock().getResults().get(0);
        assertTrue("URL doesn't contain message oid: " + result.getUrl(), result.getUrl().contains(MESSAGE_OID));
        assertEquals("Activity oid; " + activity, "10341833", activity.getTimelinePosition().getPosition());
        assertEquals("Actor; " + activity, "andstatus@loadaverage.org", activity.getActor().getWebFingerId());
        assertEquals("Author; " + activity, "igor@herds.eu", activity.getMessage().getAuthor().getWebFingerId());
    }
}
