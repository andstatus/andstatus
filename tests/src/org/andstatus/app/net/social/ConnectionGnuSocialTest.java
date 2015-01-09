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
import android.test.InstrumentationTestCase;

import org.andstatus.app.account.AccountDataReaderEmpty;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.HttpConnectionMock;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.RawResourceUtils;
import org.andstatus.app.util.TriState;

import java.io.IOException;
import java.net.URL;
import java.util.List;

public class ConnectionGnuSocialTest extends InstrumentationTestCase {
    private Connection connection;
    private HttpConnectionMock httpConnectionMock;
    private OriginConnectionData connectionData;
    
    public static MbMessage getMessageWithAttachment(Context context) throws Exception {
        ConnectionGnuSocialTest test = new ConnectionGnuSocialTest();
        test.setUp();
        return test.privateGetMessageWithAttachment(context, true);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);

        Origin origin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.GNUSOCIAL_TEST_ORIGIN_NAME);
        
        connectionData = origin.getConnectionData(TriState.UNKNOWN);
        connectionData.setAccountUserOid(TestSuite.GNUSOCIAL_TEST_ACCOUNT_USER_OID);
        connectionData.setAccountUsername(TestSuite.GNUSOCIAL_TEST_ACCOUNT_USERNAME);
        connectionData.setDataReader(new AccountDataReaderEmpty());
        connection = connectionData.getConnectionClass().newInstance();
        connection.enrichConnectionData(connectionData);
        connectionData.setHttpConnectionClass(HttpConnectionMock.class);
        connection.setAccountData(connectionData);
        httpConnectionMock = (HttpConnectionMock) connection.http;

        httpConnectionMock.data.originUrl = origin.getUrl();
    }

    public void testGetPublicTimeline() throws IOException {
        String jso = RawResourceUtils.getString(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.quitter_home);
        httpConnectionMock.setResponse(jso);
        
        List<MbTimelineItem> timeline = connection.getTimeline(ApiRoutineEnum.PUBLIC_TIMELINE, 
                new TimelinePosition("2656388") , 20, connectionData.getAccountUserOid());
        assertNotNull("timeline returned", timeline);
        int size = 3;
        assertEquals("Number of items in the Timeline", size, timeline.size());

        int ind = 0;
        assertEquals("Posting message", MbTimelineItem.ItemType.MESSAGE, timeline.get(ind).getType());
        MbMessage mbMessage = timeline.get(ind).mbMessage;
        assertTrue("Favorited", mbMessage.favoritedByActor.toBoolean(false));

        ind++;
        mbMessage = timeline.get(ind).mbMessage;
        assertTrue("Does not have a recipient", mbMessage.recipient == null);
        assertTrue("Is not a reblog", mbMessage.rebloggedMessage == null);
        assertTrue("Is a reply", mbMessage.inReplyToMessage != null);
        assertEquals("Reply to the message id", "2663833", mbMessage.inReplyToMessage.oid);
        assertEquals("Reply to the message by userOid", "114973", mbMessage.inReplyToMessage.sender.oid);
        assertFalse("Is not Favorited", mbMessage.favoritedByActor.toBoolean(true));
        String startsWith = "@<span class=\"vcard\">";
        assertEquals("Body of this message starts with", startsWith, mbMessage.getBody().substring(0, startsWith.length()));
        
        ind++;
        mbMessage = timeline.get(ind).mbMessage;
        assertTrue("Message is public", mbMessage.isPublic());
        assertFalse("Not Favorited", mbMessage.favoritedByActor.toBoolean(false));
        assertEquals("Actor", connectionData.getAccountUserOid(), mbMessage.actor.oid);
        assertEquals("Sender's oid", "114973", mbMessage.sender.oid);
        assertEquals("Sender's username", "mmn", mbMessage.sender.getUserName());
        assertEquals("Sender's Display name", "mmn", mbMessage.sender.realName);
        assertEquals("Sender's URL", "https://social.umeahackerspace.se/mmn", mbMessage.sender.getUrl());
        assertEquals("Sender's WebFinger ID", "mmn@social.umeahackerspace.se", mbMessage.sender.getWebFingerId());
    }

    public void testSearch() throws IOException {
        String jso = RawResourceUtils.getString(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.twitter_home_timeline);
        httpConnectionMock.setResponse(jso);
        
        List<MbTimelineItem> timeline = connection.search(TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT , 20);
        assertNotNull("timeline returned", timeline);
        int size = 4;
        assertEquals("Number of items in the Timeline", size, timeline.size());
    }

    public void testPostWithMedia() throws IOException {
        String jso = RawResourceUtils.getString(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.quitter_message_with_attachment);
        httpConnectionMock.setResponse(jso);
        
        MbMessage message2 = connection.updateStatus("Test post message with media", "", TestSuite.LOCAL_IMAGE_TEST_URI);
        message2.setPublic(true); 
        assertEquals("Message returned", privateGetMessageWithAttachment(this.getInstrumentation().getContext(), false), message2);
    }
    
    public void testGetMessageWithAttachment() throws IOException {
        privateGetMessageWithAttachment(this.getInstrumentation().getContext(), true);
    }

    private MbMessage privateGetMessageWithAttachment(Context context, boolean uniqueUid) throws IOException {
        // Originally downloaded from https://quitter.se/api/statuses/show.json?id=2215662
        String jso = RawResourceUtils.getString(context, 
                org.andstatus.app.tests.R.raw.quitter_message_with_attachment);
        httpConnectionMock.setResponse(jso);
        MbMessage msg = connection.getMessage("2215662");
        if (uniqueUid) {
            msg.oid += "_" + TestSuite.TESTRUN_UID;
        }
        assertNotNull("message returned", msg);
        assertEquals("has attachment", msg.attachments.size(), 1);
        MbAttachment attachment = MbAttachment.fromUrlAndContentType(new URL(
                "https://quitter.se/file/mcscx-20131110T222250-427wlgn.png")
                , MyContentType.IMAGE);
        assertEquals("attachment", attachment, msg.attachments.get(0));
        return msg;
    }
}
