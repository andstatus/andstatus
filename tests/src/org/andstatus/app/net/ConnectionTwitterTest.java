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

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import org.andstatus.app.MyContextHolder;
import org.andstatus.app.TestSuite;
import org.andstatus.app.account.AccountDataReaderEmpty;
import org.andstatus.app.net.Connection.ApiRoutineEnum;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.origin.OriginTest;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.TriState;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ConnectionTwitterTest extends InstrumentationTestCase {
    Context context;
    Connection connection;
    String host = "twitter.com";
    HttpConnectionMock httpConnection;
    OriginConnectionData connectionData;

    String keyStored;
    String secretStored;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = TestSuite.initialize(this);

        Origin origin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.TWITTER_TEST_ORIGIN_NAME);
        
        connectionData = origin.getConnectionData(TriState.UNKNOWN);
        connectionData.accountUserOid = TestSuite.TWITTER_TEST_ACCOUNT_USER_OID;
        connectionData.accountUsername = TestSuite.TWITTER_TEST_ACCOUNT_USERNAME;
        connectionData.dataReader = new AccountDataReaderEmpty();
        connection = connectionData.connectionClass.newInstance();
        connection.enrichConnectionData(connectionData);
        connectionData.httpConnectionClass = HttpConnectionMock.class;
        connection.setAccountData(connectionData);
        httpConnection = (HttpConnectionMock) connection.http;

        httpConnection.data.host = host;
        httpConnection.data.oauthClientKeys = OAuthClientKeys.fromConnectionData(httpConnection.data);
        keyStored = httpConnection.data.oauthClientKeys.getConsumerKey();
        secretStored = httpConnection.data.oauthClientKeys.getConsumerSecret();

        if (!httpConnection.data.oauthClientKeys.areKeysPresent()) {
            httpConnection.data.oauthClientKeys.setConsumerKeyAndSecret("keyForGetTimelineForTw", "thisIsASecret341232");
        }
    }
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (!TextUtils.isEmpty(keyStored)) {
            httpConnection.data.oauthClientKeys.setConsumerKeyAndSecret(keyStored, secretStored);        
        }
    }

    public void testGetTimeline() throws ConnectionException {
        JSONObject jso = RawResourceReader.getJSONObjectResource(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.home_timeline);
        httpConnection.setResponse(jso);
        
        List<MbTimelineItem> timeline = connection.getTimeline(ApiRoutineEnum.STATUSES_HOME_TIMELINE, 
                new TimelinePosition("380925803053449216") , 20, connectionData.accountUserOid);
        assertNotNull("timeline returned", timeline);
        int size = 4;
        assertEquals("Number of items in the Timeline", size, timeline.size());

        int ind = 0;
        assertEquals("Posting message", MbTimelineItem.ItemType.MESSAGE, timeline.get(ind).getType());
        MbMessage mbMessage = timeline.get(ind).mbMessage;
        assertTrue("Favorited", mbMessage.favoritedByActor.toBoolean(false));
        assertEquals("Actor", connectionData.accountUserOid, mbMessage.actor.oid);
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

        ind++;
        mbMessage = timeline.get(ind).mbMessage;
        assertTrue("Does not have a recipient", mbMessage.recipient == null);
        assertTrue("Is not a reblog", mbMessage.rebloggedMessage == null);
        assertTrue("Is not a reply", mbMessage.inReplyToMessage == null);
        assertTrue("Is not Favorited", !mbMessage.favoritedByActor.toBoolean(true));
        assertEquals("Author's oid is user oid of this account", connectionData.accountUserOid, mbMessage.sender.oid);
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
    
}
