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

import org.andstatus.app.TestSuite;
import org.andstatus.app.account.AccountDataReaderEmpty;
import org.andstatus.app.net.Connection.ApiRoutineEnum;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.origin.Origin.OriginEnum;
import org.andstatus.app.util.TriState;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

public class ConnectionPumpioTest extends InstrumentationTestCase {
    Context context;
    ConnectionPumpio connection;
    String host = "identi.ca";
    HttpConnectionMock httpConnection;
    OriginConnectionData connectionData;

    String keyStored;
    String secretStored;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        context = TestSuite.initialize(this);

        Origin origin = OriginEnum.PUMPIO.newOrigin();
        connectionData = origin.getConnectionData(TriState.UNKNOWN);
        connectionData.dataReader = new AccountDataReaderEmpty();
        connection = (ConnectionPumpio) connectionData.connectionClass.newInstance();
        connection.enrichConnectionData(connectionData);
        connectionData.httpConnectionClass = HttpConnectionMock.class;
        connection.setAccountData(connectionData);
        httpConnection = (HttpConnectionMock) connection.http;

        httpConnection.data.host = host;
        httpConnection.data.oauthClientKeys = OAuthClientKeys.fromConnectionData(httpConnection.data);
        keyStored = httpConnection.data.oauthClientKeys.getConsumerKey();
        secretStored = httpConnection.data.oauthClientKeys.getConsumerSecret();

        if (!httpConnection.data.oauthClientKeys.areKeysPresent()) {
            httpConnection.data.oauthClientKeys.setConsumerKeyAndSecret("keyForThetestGetTimeline", "thisIsASecret02341");
        }
    }
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (!TextUtils.isEmpty(keyStored)) {
            httpConnection.data.oauthClientKeys.setConsumerKeyAndSecret(keyStored, secretStored);        
        }
    }

    public void testOidToObjectType() {
        String oids[] = {"https://identi.ca/api/activity/L4v5OL93RrabouQc9_QGfg", 
                "https://identi.ca/api/comment/ibpUqhU1TGCE2yHNbUv54g",
                "https://identi.ca/api/note/nlF5jl1HQciIs_zP85EeYg",
                "https://identi.ca/obj/ibpcomment",
                "http://identi.ca/notice/95772390",
                "acct:t131t@identi.ca",
                "http://identi.ca/user/46155"};
        String objectTypes[] = {"activity", 
                "comment", 
                "note",
                "unknown object type: https://identi.ca/obj/ibpcomment",
                "note",
                "person",
                "person"};
        for (int ind=0; ind < oids.length; ind++) {
            String oid = oids[ind];
            String objectType = objectTypes[ind];
            assertEquals("Expecting '" + objectType + "'", objectType, connection.oidToObjectType(oid));
        }
    }

    public void testUsernameToHost() {
        String usernames[] = {"t131t@identi.ca", 
                "somebody@example.com",
                "https://identi.ca/api/note/nlF5jl1HQciIs_zP85EeYg",
                "example.com",
                "@somewhere.com"};
        String hosts[] = {"identi.ca", 
                "example.com", 
                "",
                "",
                "somewhere.com"};
        for (int ind=0; ind < usernames.length; ind++) {
            assertEquals("Expecting '" + hosts[ind] + "'", hosts[ind], connection.usernameToHost(usernames[ind]));
        }
    }
    
    public void testGetTimeline() throws ConnectionException {
        String sinceId = "http://" + host + "/activity/frefq3232sf";

        JSONObject jso = RawResourceReader.getJSONObjectResource(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.user_t131t_inbox);
        httpConnection.setResponse(jso);
        
        List<MbTimelineItem> timeline = connection.getTimeline(ApiRoutineEnum.STATUSES_HOME_TIMELINE, 
                new TimelinePosition(sinceId) , 20, "acct:t131t@" + host);
        assertNotNull("timeline returned", timeline);
        int size = 6;
        assertEquals("Number of items in the Timeline", size, timeline.size());

        int ind = 0;
        assertEquals("Posting image", MbTimelineItem.ItemType.MESSAGE, timeline.get(ind).getType());
        MbMessage mbMessage = timeline.get(ind).mbMessage;
        assertTrue("trailing linebreaks trimmed: '" + mbMessage.getBody() + "'", mbMessage.getBody().endsWith("Link"));
        assertEquals("Message sent date: " + mbMessage.sentDate, TestSuite.gmtTime(2013, Calendar.SEPTEMBER, 13, 1, 8, 32).getTime(), mbMessage.sentDate);

        ind++;
        assertEquals("Other User", MbTimelineItem.ItemType.USER, timeline.get(ind).getType());
        MbUser mbUser = timeline.get(ind).mbUser;
        assertEquals("Other actor", "acct:jpope@io.jpope.org", mbUser.actor.oid);
        assertEquals("Following", TriState.TRUE, mbUser.followedByActor);

        ind++;
        assertEquals("User", MbTimelineItem.ItemType.USER, timeline.get(ind).getType());
        mbUser = timeline.get(ind).mbUser;
        assertEquals("Url of the actor", "https://identi.ca/t131t", mbUser.actor.url);
        assertEquals("Following", TriState.TRUE, mbUser.followedByActor);
        assertEquals("Url of the user", "https://fmrl.me/grdryn", mbUser.url);

        ind++;
        mbMessage = timeline.get(ind).mbMessage;
        assertEquals("Favorited by someone else", TriState.TRUE, mbMessage.favoritedByActor);
        assertEquals("Actor -someone else", "acct:jpope@io.jpope.org" , mbMessage.actor.oid);
        assertTrue("Does not have a recipient", mbMessage.recipient == null);
        assertEquals("Url of the message", "https://fmrl.me/lostson/note/Dp-njbPQSiOfdclSOuAuFw", mbMessage.url);

        ind++;
        mbMessage = timeline.get(ind).mbMessage;
        assertTrue("Have a recipient", mbMessage.recipient != null);
        assertEquals("Directed to yvolk", "acct:yvolk@identi.ca" , mbMessage.recipient.oid);

        ind++;
        mbMessage = timeline.get(ind).mbMessage;
        assertTrue("Is a reply", mbMessage.inReplyToMessage != null);
        assertEquals("Is a reply to this user", mbMessage.inReplyToMessage.sender.userName, "jankusanagi@identi.ca");
    }

    public void testGetUsersFollowedBy() throws ConnectionException {
        JSONObject jso = RawResourceReader.getJSONObjectResource(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.user_t131t_following);
        httpConnection.setResponse(jso);
        
        assertTrue(connection.isApiSupported(ApiRoutineEnum.GET_FRIENDS));        
        assertTrue(connection.isApiSupported(ApiRoutineEnum.GET_FRIENDS_IDS));        
        
        List<MbUser> users = connection.getUsersFollowedBy("acct:t131t@" + host);
        assertNotNull("List of users returned", users);
        int size = 5;
        assertEquals("Response for t131t", size, users.size());

        assertEquals("Does the Pope shit in the woods?", users.get(1).description);
        assertEquals("gitorious@identi.ca", users.get(2).userName);
        assertEquals("acct:ken@coding.example", users.get(3).oid);
        assertEquals("Yuri Volkov", users.get(4).realName);
    }
    
    public void testUpdateStatus() throws ConnectionException, JSONException {
        String body = "@peter Do you think it's true?";
        String inReplyToId = "https://identi.ca/api/note/94893FsdsdfFdgtjuk38ErKv";
        httpConnection.setResponse(new JSONObject());
        connection.data.accountUserOid = "acct:mytester@" + host;
        connection.updateStatus(body, inReplyToId);
        JSONObject activity = httpConnection.getPostedJSONObject();
        assertTrue("Object present", activity.has("object"));
        JSONObject obj = activity.getJSONObject("object");
        assertEquals("Message content", body, obj.getString("content"));
        assertEquals("Reply is comment", PumpioObjectType.COMMENT.id(), obj.getString("objectType"));
        
        assertTrue("InReplyTo is present", obj.has("inReplyTo"));
        JSONObject inReplyToObject = obj.getJSONObject("inReplyTo");
        assertEquals("Id of the in reply to object", inReplyToId, inReplyToObject.getString("id"));

        body = "Testing the application...";
        inReplyToId = "";
        connection.updateStatus(body, inReplyToId);
        activity = httpConnection.getPostedJSONObject();
        assertTrue("Object present", activity.has("object"));
        obj = activity.getJSONObject("object");
        assertEquals("Message content", body, obj.getString("content"));
        assertEquals("Message without reply is a note", PumpioObjectType.NOTE.id(), obj.getString("objectType"));
        
        assertTrue("InReplyTo is not present", !obj.has("inReplyTo"));
    }
    
    public void testUnfollowUser() throws ConnectionException {
        JSONObject jso = RawResourceReader.getJSONObjectResource(this.getInstrumentation().getContext(), 
                org.andstatus.app.tests.R.raw.unfollow_pumpio);
        httpConnection.setResponse(jso);
        connection.data.accountUserOid = "acct:t131t@" + host;
        String userOid = "acct:evan@e14n.com";
        MbUser user = connection.followUser(userOid, false);
        assertTrue("User is present", !user.isEmpty());
        assertEquals("Our account acted", connection.data.accountUserOid, user.actor.oid);
        assertEquals("Object of action", userOid, user.oid);
        assertEquals("Unfollowed", TriState.FALSE, user.followedByActor);
    }

    public void testParseDate() {
        String stringDate = "Wed Nov 27 09:27:01 -0300 2013";
        assertEquals("Bad date shouldn't throw (" + stringDate + ")", 0, connection.parseDate(stringDate) );
    }
}
