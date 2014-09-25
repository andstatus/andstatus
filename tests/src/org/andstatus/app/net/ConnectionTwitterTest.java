/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.test.InstrumentationTestCase;

import org.andstatus.app.account.AccountDataReaderEmpty;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyContentType;
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

public class ConnectionTwitterTest extends InstrumentationTestCase {
    private Connection connection;
    private HttpConnectionMock httpConnection;
    private OriginConnectionData connectionData;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);

        Origin origin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.TWITTER_TEST_ORIGIN_NAME);
        
        connectionData = origin.getConnectionData(TriState.UNKNOWN);
        connectionData.setAccountUserOid(TestSuite.TWITTER_TEST_ACCOUNT_USER_OID);
        connectionData.setAccountUsername(TestSuite.TWITTER_TEST_ACCOUNT_USERNAME);
        connectionData.setDataReader(new AccountDataReaderEmpty());
        connection = connectionData.getConnectionClass().newInstance();
        connection.enrichConnectionData(connectionData);
        connectionData.setHttpConnectionClass(HttpConnectionMock.class);
        connection.setAccountData(connectionData);
        httpConnection = (HttpConnectionMock) connection.http;

        httpConnection.data.originUrl = origin.getUrl();
        httpConnection.data.oauthClientKeys = OAuthClientKeys.fromConnectionData(httpConnection.data);

        if (!httpConnection.data.oauthClientKeys.areKeysPresent()) {
            httpConnection.data.oauthClientKeys.setConsumerKeyAndSecret("keyForGetTimelineForTw", "thisIsASecret341232");
        }
    }

    public void testGetTimeline() throws ConnectionException {
        JSONObject jso = RawResourceUtils.getJSONObject(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.twitter_home_timeline);
        httpConnection.setResponse(jso);
        
        List<MbTimelineItem> timeline = connection.getTimeline(ApiRoutineEnum.STATUSES_HOME_TIMELINE, 
                new TimelinePosition("380925803053449216") , 20, connectionData.getAccountUserOid());
        assertNotNull("timeline returned", timeline);
        int size = 4;
        assertEquals("Number of items in the Timeline", size, timeline.size());

        int ind = 0;
        assertEquals("Posting message", MbTimelineItem.ItemType.MESSAGE, timeline.get(ind).getType());
        MbMessage mbMessage = timeline.get(ind).mbMessage;
        assertTrue("Favorited", mbMessage.favoritedByActor.toBoolean(false));
        assertEquals("Actor", connectionData.getAccountUserOid(), mbMessage.actor.oid);
        assertEquals("Author's oid", "221452291", mbMessage.sender.oid);
        assertEquals("Author's username", "Know", mbMessage.sender.getUserName());
        assertEquals("Author's Display name", "Just so you Know", mbMessage.sender.realName);
        assertEquals("WebFinger ID", "Know@" + TestSuite.getTestOriginHost(TestSuite.TWITTER_TEST_ORIGIN_NAME), mbMessage.sender.getWebFingerId());

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

        ind++;
        mbMessage = timeline.get(ind).mbMessage;
        assertTrue("Does not have a recipient", mbMessage.recipient == null);
        assertTrue("Is not a reblog", mbMessage.rebloggedMessage == null);
        assertTrue("Is not a reply", mbMessage.inReplyToMessage == null);
        assertTrue("Is not Favorited", !mbMessage.favoritedByActor.toBoolean(true));
        assertEquals("Author's oid is user oid of this account", connectionData.getAccountUserOid(), mbMessage.sender.oid);
        startsWith = "And this is";
        assertEquals("Body of this message starts with", startsWith, mbMessage.getBody().substring(0, startsWith.length()));
    }
    
    public void testParseDate() {
        String stringDate = "Wed Nov 27 09:27:01 -0300 2013";
        assertEquals("Bad date shouldn't throw (" + stringDate + ")", 0, connection.parseDate(stringDate) );
        Date date = TestSuite.utcTime(2013, Calendar.SEPTEMBER, 26, 18, 23, 05);
        stringDate = "Thu Sep 26 22:23:05 GMT+04:00 2013";   // date.toString gives wrong value!!!
        long parsed = connection.parseDate(stringDate);
        assertEquals("Testing the date: Thu Sep 26 18:23:05 +0000 2013 (" + stringDate + " vs " + new Date(parsed).toString() + ")", date.getTime(), parsed);
    }

    public void testGetMessageWithAttachment() throws ConnectionException, MalformedURLException {
        JSONObject jso = RawResourceUtils.getJSONObject(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.twitter_message_with_media);
        httpConnection.setResponse(jso);

        MbMessage msg = connection.getMessage("503799441900314624");
        assertNotNull("message returned", msg);
        assertEquals("has attachment", msg.attachments.size(), 1);
        MbAttachment attachment = MbAttachment.fromUrlAndContentType(new URL(
                "https://pbs.twimg.com/media/Bv3a7EsCAAIgigY.jpg"), MyContentType.IMAGE);
        assertEquals("attachment", attachment, msg.attachments.get(0));
        attachment.url = new URL("https://pbs.twimg.com/media/Bv4a7EsCAAIgigY.jpg");
        assertNotSame("attachment", attachment, msg.attachments.get(0));
    }
    
}
