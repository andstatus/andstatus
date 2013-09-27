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
import android.util.Log;

import org.andstatus.app.account.AccountDataReaderEmpty;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.net.Connection.ApiRoutineEnum;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.origin.Origin.OriginEnum;
import org.andstatus.app.util.TriState;
import org.json.JSONObject;

import java.util.Date;
import java.util.List;

public class ConnectionTwitterTest extends InstrumentationTestCase {
    private static final String TAG = ConnectionTwitterTest.class.getSimpleName();
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
        context = this.getInstrumentation().getTargetContext();
        if (context == null) {
            Log.e(TAG, "targetContext is null.");
            throw new IllegalArgumentException("this.getInstrumentation().getTargetContext() returned null");
        }
        MyPreferences.initialize(context, this);

        Origin origin = OriginEnum.TWITTER.newOrigin();
        connectionData = origin.getConnectionData(TriState.UNKNOWN);
        connectionData.accountUserOid = "144771645";
        connectionData.accountUsername = "t131t";
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
        assertTrue("Favorited", timeline.get(ind).mbMessage.favoritedByActor.toBoolean(false));
        assertEquals("Actor", connectionData.accountUserOid, timeline.get(ind).mbMessage.actor.oid);
        assertEquals("Author's oid", "221452291", timeline.get(ind).mbMessage.sender.oid);
        assertEquals("Author's username", "Know", timeline.get(ind).mbMessage.sender.userName);
        assertEquals("Author's Display name", "Just so you Know", timeline.get(ind).mbMessage.sender.realName);

        ind++;
        assertTrue("Does not have a recipient", timeline.get(ind).mbMessage.recipient == null);
        assertTrue("Is not a reblog", timeline.get(ind).mbMessage.rebloggedMessage == null);
        assertTrue("Is a reply", timeline.get(ind).mbMessage.inReplyToMessage != null);
        assertEquals("Reply to the message id", "17176774678", timeline.get(ind).mbMessage.inReplyToMessage.oid);
        assertEquals("Reply to the message by userOid", "144771645", timeline.get(ind).mbMessage.inReplyToMessage.sender.oid);
        assertTrue("Is not Favorited", !timeline.get(ind).mbMessage.favoritedByActor.toBoolean(true));
        String startsWith = "@t131t";
        assertEquals("Body of this message starts with", startsWith, timeline.get(ind).mbMessage.body.substring(0, startsWith.length()));

        ind++;
        assertTrue("Does not have a recipient", timeline.get(ind).mbMessage.recipient == null);
        assertTrue("Is a reblog", timeline.get(ind).mbMessage.rebloggedMessage != null);
        assertTrue("Is not a reply", timeline.get(ind).mbMessage.inReplyToMessage == null);
        assertEquals("Reblog of the message id", "315088751183409153", timeline.get(ind).mbMessage.rebloggedMessage.oid);
        assertEquals("Reblog of the message by userOid", "442756884", timeline.get(ind).mbMessage.rebloggedMessage.sender.oid);
        assertTrue("Is not Favorited", !timeline.get(ind).mbMessage.favoritedByActor.toBoolean(true));
        startsWith = "RT @AndStatus1: This AndStatus application";
        assertEquals("Body of this message starts with", startsWith, timeline.get(ind).mbMessage.body.substring(0, startsWith.length()));
        startsWith = "This AndStatus application";
        assertEquals("Body of reblogged message starts with", startsWith, timeline.get(ind).mbMessage.rebloggedMessage.body.substring(0, startsWith.length()));
        // TODO: use Calendar
        Date date = new Date(2013 - 1900, 9 - 1, 26, 18, 23, 05);
        assertEquals("This message created at Thu Sep 26 18:23:05 +0000 2013 (" + date.toString() + ")", date.getTime(), timeline.get(ind).mbMessage.sentDate);
        date = new Date(2013 - 1900, 3 - 1, 22, 13, 13, 7);
        assertEquals("Reblogged message created at Fri Mar 22 13:13:07 +0000 2013 (" + date.toString() + ")", date.getTime(), timeline.get(ind).mbMessage.rebloggedMessage.sentDate);

        ind++;
        assertTrue("Does not have a recipient", timeline.get(ind).mbMessage.recipient == null);
        assertTrue("Is not a reblog", timeline.get(ind).mbMessage.rebloggedMessage == null);
        assertTrue("Is not a reply", timeline.get(ind).mbMessage.inReplyToMessage == null);
        assertTrue("Is not Favorited", !timeline.get(ind).mbMessage.favoritedByActor.toBoolean(true));
        assertEquals("Author's oid is user oid of this account", connectionData.accountUserOid, timeline.get(ind).mbMessage.sender.oid);
        startsWith = "And this is";
        assertEquals("Body of this message starts with", startsWith, timeline.get(ind).mbMessage.body.substring(0, startsWith.length()));
    }
}
