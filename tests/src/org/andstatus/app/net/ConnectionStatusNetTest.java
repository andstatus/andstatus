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

package org.andstatus.app.net;

import android.content.Context;
import android.test.InstrumentationTestCase;

import org.andstatus.app.account.AccountDataReaderEmpty;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.ContentType;
import org.andstatus.app.net.Connection.ApiRoutineEnum;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.RawResourceUtils;
import org.andstatus.app.util.TriState;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ConnectionStatusNetTest extends InstrumentationTestCase {
    private Connection connection;
    private HttpConnectionMock httpConnectionMock;
    private OriginConnectionData connectionData;
    
    public static MbMessage getMessageWithAttachment(Context context) throws Exception {
        ConnectionStatusNetTest test = new ConnectionStatusNetTest();
        test.setUp();
        return test.privateGetMessageWithAttachment(context);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initialize(this);

        Origin origin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.STATUSNET_TEST_ORIGIN_NAME);
        
        connectionData = origin.getConnectionData(TriState.UNKNOWN);
        connectionData.setAccountUserOid(TestSuite.STATUSNET_TEST_ACCOUNT_USER_OID);
        connectionData.setAccountUsername(TestSuite.STATUSNET_TEST_ACCOUNT_USERNAME);
        connectionData.setDataReader(new AccountDataReaderEmpty());
        connection = connectionData.getConnectionClass().newInstance();
        connection.enrichConnectionData(connectionData);
        connectionData.setHttpConnectionClass(HttpConnectionMock.class);
        connection.setAccountData(connectionData);
        httpConnectionMock = (HttpConnectionMock) connection.http;

        httpConnectionMock.data.originUrl = origin.getUrl();
    }

    public void testGetPublicTimeline() throws ConnectionException {
        JSONObject jso = RawResourceUtils.getJSONObject(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.home_timeline);
        httpConnectionMock.setResponse(jso);
        
        List<MbTimelineItem> timeline = connection.getTimeline(ApiRoutineEnum.PUBLIC_TIMELINE, 
                new TimelinePosition("380925803053449216") , 20, connectionData.getAccountUserOid());
        assertNotNull("timeline returned", timeline);
        int size = 4;
        assertEquals("Number of items in the Timeline", size, timeline.size());

        int ind = 0;
        assertEquals("Posting message", MbTimelineItem.ItemType.MESSAGE, timeline.get(ind).getType());
        MbMessage mbMessage = timeline.get(ind).mbMessage;
        assertTrue("Message is public", mbMessage.isPublic());
        assertTrue("Favorited", mbMessage.favoritedByActor.toBoolean(false));
        assertEquals("Actor", connectionData.getAccountUserOid(), mbMessage.actor.oid);
        assertEquals("Author's oid", "221452291", mbMessage.sender.oid);
        assertEquals("Author's username", "Know", mbMessage.sender.userName);
        assertEquals("Author's Display name", "Just so you Know", mbMessage.sender.realName);

        ind++;
        mbMessage = timeline.get(ind).mbMessage;
        assertTrue("Does not have a recipient", mbMessage.recipient == null);
        assertTrue("Is not a reblog", mbMessage.rebloggedMessage == null);
        assertTrue("Is a reply", mbMessage.inReplyToMessage != null);
        assertEquals("Reply to the message id", "17176774678", mbMessage.inReplyToMessage.oid);
        assertEquals("Reply to the message by userOid", "144771645", mbMessage.inReplyToMessage.sender.oid);
        assertTrue("Is not Favorited", !mbMessage.favoritedByActor.toBoolean(true));
        String startsWith = "@t131t";
        assertEquals("Body of this message starts with", startsWith, mbMessage.getBody().substring(0, startsWith.length()));

        ind++;
        mbMessage = timeline.get(ind).mbMessage;
        assertTrue("Does not have a recipient", mbMessage.recipient == null);
        assertTrue("Is a reblog", mbMessage.rebloggedMessage != null);
        assertTrue("Is not a reply", mbMessage.inReplyToMessage == null);
        assertEquals("Reblog of the message id", "315088751183409153", mbMessage.rebloggedMessage.oid);
        assertEquals("Reblog of the message by userOid", "442756884", mbMessage.rebloggedMessage.sender.oid);
        assertTrue("Is not Favorited", !mbMessage.favoritedByActor.toBoolean(true));
        startsWith = "RT @AndStatus1: This AndStatus application";
        assertEquals("Body of this message starts with", startsWith, mbMessage.getBody().substring(0, startsWith.length()));
        startsWith = "This AndStatus application";
        assertEquals("Body of reblogged message starts with", startsWith, mbMessage.rebloggedMessage.getBody().substring(0, startsWith.length()));
        Date date = TestSuite.utcTime(2013, Calendar.SEPTEMBER, 26, 18, 23, 05);
        assertEquals("This message created at Thu Sep 26 18:23:05 +0000 2013 (" + date.toString() + ")", date.getTime(), mbMessage.sentDate);
        date = TestSuite.utcTime(2013, Calendar.MARCH, 22, 13, 13, 7);
        assertEquals("Reblogged message created at Fri Mar 22 13:13:07 +0000 2013 (" + date.toString() + ")", date.getTime(), mbMessage.rebloggedMessage.sentDate);
    }

    public void testSearch() throws ConnectionException {
        JSONObject jso = RawResourceUtils.getJSONObject(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.home_timeline);
        httpConnectionMock.setResponse(jso);
        
        List<MbTimelineItem> timeline = connection.search(TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT , 20);
        assertNotNull("timeline returned", timeline);
        int size = 4;
        assertEquals("Number of items in the Timeline", size, timeline.size());
    }
    
    public void testGetMessageWithAttachment() throws ConnectionException, MalformedURLException {
        privateGetMessageWithAttachment(this.getInstrumentation().getContext());
    }

    private MbMessage privateGetMessageWithAttachment(Context context) throws ConnectionException, MalformedURLException {
        // Originally downloaded from https://quitter.se/api/statuses/show.json?id=2215662
        JSONObject jso = RawResourceUtils.getJSONObject(context, 
                org.andstatus.app.tests.R.raw.quitter_message_with_attachment);
        httpConnectionMock.setResponse(jso);
        MbMessage msg = connection.getMessage("2215662");
        msg.oid += "_" + TestSuite.TESTRUN_UID;
        assertNotNull("message returned", msg);
        assertEquals("has attachment", msg.attachments.size(), 1);
        MbAttachment attachment = MbAttachment.fromUrlAndContentType(new URL(
                "https://quitter.se/file/mcscx-20131110T222250-427wlgn.png")
                , ContentType.IMAGE);
        assertEquals("attachment", attachment, msg.attachments.get(0));
        return msg;
    }
}
