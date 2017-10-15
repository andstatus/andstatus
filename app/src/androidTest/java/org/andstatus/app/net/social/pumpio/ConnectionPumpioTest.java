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

package org.andstatus.app.net.social.pumpio;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.text.TextUtils;

import org.andstatus.app.account.AccountDataReaderEmpty;
import org.andstatus.app.account.AccountName;
import org.andstatus.app.context.DemoData;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnectionMock;
import org.andstatus.app.net.http.OAuthClientKeys;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.net.social.MbActivity;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbObjectType;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.net.social.pumpio.ConnectionPumpio.ConnectionAndUrl;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RawResourceUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Calendar;
import java.util.List;

import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ConnectionPumpioTest {
    private ConnectionPumpio connection;
    private URL originUrl = UrlUtils.fromString("https://" + DemoData.PUMPIO_MAIN_HOST);
    private HttpConnectionMock httpConnectionMock;

    private String keyStored;
    private String secretStored;
    
    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);

        TestSuite.setHttpConnectionMockClass(HttpConnectionMock.class);
        OriginConnectionData connectionData = OriginConnectionData.fromAccountName(AccountName.fromOriginAndUserName(
                MyContextHolder.get().persistentOrigins().fromName(DemoData.PUMPIO_ORIGIN_NAME), ""),
                TriState.UNKNOWN);
        connectionData.setAccountUserOid(DemoData.PUMPIO_TEST_ACCOUNT_USER_OID);
        connectionData.setDataReader(new AccountDataReaderEmpty());
        connection = (ConnectionPumpio) connectionData.newConnection();
        httpConnectionMock = connection.getHttpMock();

        httpConnectionMock.data.originUrl = originUrl;
        httpConnectionMock.data.oauthClientKeys = OAuthClientKeys.fromConnectionData(httpConnectionMock.data);
        keyStored = httpConnectionMock.data.oauthClientKeys.getConsumerKey();
        secretStored = httpConnectionMock.data.oauthClientKeys.getConsumerSecret();

        if (!httpConnectionMock.data.oauthClientKeys.areKeysPresent()) {
            httpConnectionMock.data.oauthClientKeys.setConsumerKeyAndSecret("keyForThetestGetTimeline", "thisIsASecret02341");
        }
        TestSuite.setHttpConnectionMockClass(null);
    }
    
    @After
    public void tearDown() throws Exception {
        if (!TextUtils.isEmpty(keyStored)) {
            httpConnectionMock.data.oauthClientKeys.setConsumerKeyAndSecret(keyStored, secretStored);        
        }
    }

    @Test
    public void testOidToObjectType() {
        String oids[] = {"https://identi.ca/api/activity/L4v5OL93RrabouQc9_QGfg",
                "https://identi.ca/api/comment/ibpUqhU1TGCE2yHNbUv54g",
                "https://identi.ca/api/note/nlF5jl1HQciIs_zP85EeYg",
                "https://identi.ca/obj/ibpcomment",
                "http://identi.ca/notice/95772390",
                "acct:t131t@identi.ca",
                "http://identi.ca/user/46155",
                "https://identi.ca/api/user/andstatus/followers",
                ActivitySender.PUBLIC_COLLECTION_ID};
        String objectTypes[] = {"activity",
                "comment",
                "note",
                "unknown object type: https://identi.ca/obj/ibpcomment",
                "note",
                "person",
                "person",
                "collection",
                "collection"};
        for (int ind=0; ind < oids.length; ind++) {
            String oid = oids[ind];
            String objectType = objectTypes[ind];
            assertEquals("Expecting'" + oid + "' to be '" + objectType + "'", objectType, connection.oidToObjectType(oid));
        }
    }

    @Test
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

    @Test
    public void testGetConnectionAndUrl() throws ConnectionException {
        String userOids[] = {"acct:t131t@" + DemoData.PUMPIO_MAIN_HOST,
                "somebody@" + DemoData.PUMPIO_MAIN_HOST};
        String urls[] = {"api/user/t131t/profile", 
                "api/user/somebody/profile"};
        String hosts[] = {DemoData.PUMPIO_MAIN_HOST, DemoData.PUMPIO_MAIN_HOST};
        for (int ind=0; ind < userOids.length; ind++) {
            ConnectionAndUrl conu = connection.getConnectionAndUrl(ApiRoutineEnum.GET_USER, userOids[ind]);
            assertEquals("Expecting '" + urls[ind] + "'", urls[ind], conu.url);
            assertEquals("Expecting '" + hosts[ind] + "'", hosts[ind], conu.httpConnection.data.originUrl.getHost());
        }
    }

    @Test
    public void testGetTimeline() throws IOException {
        String sinceId = "https%3A%2F%2F" + originUrl.getHost() + "%2Fapi%2Factivity%2Ffrefq3232sf";

        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.pumpio_user_t131t_inbox);
        httpConnectionMock.setResponse(jso);
        
        List<MbActivity> timeline = connection.getTimeline(ApiRoutineEnum.HOME_TIMELINE,
                new TimelinePosition(sinceId), TimelinePosition.EMPTY, 20, "acct:t131t@" + originUrl.getHost());
        assertNotNull("timeline returned", timeline);
        int size = 6;
        assertEquals("Number of items in the Timeline", size, timeline.size());

        int ind = 0;
        assertEquals("Posting image", MbObjectType.MESSAGE, timeline.get(ind).getObjectType());
        MbActivity activity = timeline.get(ind);
        MbMessage mbMessage = activity.getMessage();
        assertThat("Message body " + mbMessage, mbMessage.getBody(), startsWith("Wow! Fantastic wheel stand at #DragWeek2013 today."));
        assertEquals("Message updated at " + TestSuite.utcTime(activity.getUpdatedDate()),
                TestSuite.utcTime(2013, Calendar.SEPTEMBER, 13, 1, 8, 38),
                TestSuite.utcTime(activity.getUpdatedDate()));
        MbUser actor = activity.getActor();
        assertEquals("Sender's oid", "acct:jpope@io.jpope.org", actor.oid);
        assertEquals("Sender's username", "jpope@io.jpope.org", actor.getUserName());
        assertEquals("Sender's Display name", "jpope", actor.getRealName());
        assertEquals("Sender's profile image URL", "https://io.jpope.org/uploads/jpope/2013/7/8/LPyLPw_thumb.png", actor.avatarUrl);
        assertEquals("Sender's profile URL", "https://io.jpope.org/jpope", actor.getProfileUrl());
        assertEquals("Sender's Homepage", "https://io.jpope.org/jpope", actor.getHomepage());
        assertEquals("Sender's WebFinger ID", "jpope@io.jpope.org", actor.getWebFingerId());
        assertEquals("Description", "Does the Pope shit in the woods?", actor.getDescription());
        assertEquals("Messages count", 0, actor.msgCount);
        assertEquals("Favorites count", 0, actor.favoritesCount);
        assertEquals("Following (friends) count", 0, actor.followingCount);
        assertEquals("Followers count", 0, actor.followersCount);
        assertEquals("Location", "/dev/null", actor.location);
        assertEquals("Created at", 0, actor.getCreatedDate());
        assertEquals("Updated at", TestSuite.utcTime(2013, Calendar.SEPTEMBER, 12, 17, 10, 44),
                TestSuite.utcTime(actor.getUpdatedDate()));
        assertEquals("Actor is an Author", actor, activity.getAuthor());
        assertNotEquals("Is a Reblog " + activity, MbActivityType.ANNOUNCE, activity.type);
        assertEquals("Favorited by me " + activity, TriState.UNKNOWN, activity.getMessage().getFavoritedBy(activity.accountUser));

        ind++;
        activity = timeline.get(ind);
        assertEquals("Is not FOLLOW " + activity, MbActivityType.FOLLOW, activity.type);
        assertEquals("Actor", "acct:jpope@io.jpope.org", activity.getActor().oid);
        assertEquals("Actor not followed by me", TriState.TRUE, activity.getActor().followedByMe);
        assertEquals("Activity Object", MbObjectType.USER, activity.getObjectType());
        MbUser mbUser = activity.getUser();
        assertEquals("User followed", "acct:atalsta@microca.st", mbUser.oid);
        assertEquals("WebFinger ID", "atalsta@microca.st", mbUser.getWebFingerId());
        assertEquals("User followed by me", TriState.FALSE, mbUser.followedByMe);

        ind++;
        activity = timeline.get(ind);
        assertEquals("Is not FOLLOW " + activity, MbActivityType.FOLLOW, activity.type);
        assertEquals("User", MbObjectType.USER, activity.getObjectType());
        mbUser = activity.getUser();
        assertEquals("Url of the actor", "https://identi.ca/t131t", activity.getActor().getProfileUrl());
        assertEquals("WebFinger ID", "t131t@identi.ca", activity.getActor().getWebFingerId());
        assertEquals("Following", TriState.TRUE, mbUser.followedByMe);
        assertEquals("Url of the user", "https://fmrl.me/grdryn", mbUser.getProfileUrl());

        ind++;
        activity = timeline.get(ind);
        assertEquals("Is not LIKE " + activity, MbActivityType.LIKE, activity.type);
        assertEquals("Actor " + activity, "acct:jpope@io.jpope.org", activity.getActor().oid);
        assertEquals("Activity updated " + activity,
                TestSuite.utcTime(2013, Calendar.SEPTEMBER, 20, 22, 20, 25),
                TestSuite.utcTime(activity.getUpdatedDate()));
        mbMessage = activity.getMessage();
        assertEquals("Author " + activity, "acct:lostson@fmrl.me", activity.getAuthor().oid);
        assertTrue("Does not have a recipient", mbMessage.audience().isEmpty());
        assertEquals("Message oid " + mbMessage, "https://fmrl.me/api/note/Dp-njbPQSiOfdclSOuAuFw", mbMessage.oid);
        assertEquals("Url of the message " + mbMessage, "https://fmrl.me/lostson/note/Dp-njbPQSiOfdclSOuAuFw", mbMessage.url);
        assertThat("Message body " + mbMessage, mbMessage.getBody(), startsWith("My new <b>Firefox</b> OS phone arrived today"));
        assertEquals("Message updated " + mbMessage,
                TestSuite.utcTime(2013, Calendar.SEPTEMBER, 20, 20, 4, 22),
                TestSuite.utcTime(mbMessage.getUpdatedDate()));

        ind++;
        mbMessage = timeline.get(ind).getMessage();
        assertTrue("Have a recipient", mbMessage.audience().nonEmpty());
        assertEquals("Directed to yvolk", "acct:yvolk@identi.ca" , mbMessage.audience().getFirst().oid);

        ind++;
        mbMessage = timeline.get(ind).getMessage();
        assertEquals(mbMessage.isSubscribedByMe(), TriState.UNKNOWN);
        assertTrue("Is a reply", mbMessage.getInReplyTo().nonEmpty());
        assertEquals("Is a reply to this user", mbMessage.getInReplyTo().getAuthor().getUserName(), "jankusanagi@identi.ca");
        assertEquals(mbMessage.getInReplyTo().getMessage().isSubscribedByMe(), TriState.FALSE);
    }

    @Test
    public void testGetUsersFollowedBy() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.pumpio_user_t131t_following);
        httpConnectionMock.setResponse(jso);
        
        assertTrue(connection.isApiSupported(ApiRoutineEnum.GET_FRIENDS));        
        assertTrue(connection.isApiSupported(ApiRoutineEnum.GET_FRIENDS_IDS));        
        
        List<MbUser> users = connection.getFriends("acct:t131t@" + originUrl.getHost());
        assertNotNull("List of users returned", users);
        int size = 5;
        assertEquals("Response for t131t", size, users.size());

        assertEquals("Does the Pope shit in the woods?", users.get(1).getDescription());
        assertEquals("gitorious@identi.ca", users.get(2).getUserName());
        assertEquals("acct:ken@coding.example", users.get(3).oid);
        assertEquals("Yuri Volkov", users.get(4).getRealName());
    }

    @Test
    public void testUpdateStatus() throws ConnectionException, JSONException {
        String body = "@peter Do you think it's true?";
        String inReplyToId = "https://identi.ca/api/note/94893FsdsdfFdgtjuk38ErKv";
        httpConnectionMock.setResponse("");
        connection.getData().setAccountUserOid("acct:mytester@" + originUrl.getHost());
        connection.updateStatus(body, "", inReplyToId, null);
        JSONObject activity = httpConnectionMock.getPostedJSONObject();
        assertTrue("Object present", activity.has("object"));
        JSONObject obj = activity.getJSONObject("object");
        assertEquals("Message content", body, MyHtml.fromHtml(obj.getString("content")));
        assertEquals("Reply is comment", ObjectType.COMMENT.id(), obj.getString("objectType"));
        
        assertTrue("InReplyTo is present", obj.has("inReplyTo"));
        JSONObject inReplyToObject = obj.getJSONObject("inReplyTo");
        assertEquals("Id of the in reply to object", inReplyToId, inReplyToObject.getString("id"));

        body = "Testing the application...";
        inReplyToId = "";
        connection.updateStatus(body, "", inReplyToId, null);
        activity = httpConnectionMock.getPostedJSONObject();
        assertTrue("Object present", activity.has("object"));
        obj = activity.getJSONObject("object");
        assertEquals("Message content", body, MyHtml.fromHtml(obj.getString("content")));
        assertEquals("Message without reply is a note", ObjectType.NOTE.id(), obj.getString("objectType"));

        JSONArray recipients = activity.optJSONArray("to");
        assertEquals("To Public collection", ActivitySender.PUBLIC_COLLECTION_ID, ((JSONObject) recipients.get(0)).get("id"));

        assertTrue("InReplyTo is not present", !obj.has("inReplyTo"));
    }

    @Test
    public void testReblog() throws ConnectionException, JSONException {
        String rebloggedId = "https://identi.ca/api/note/94893FsdsdfFdgtjuk38ErKv";
        httpConnectionMock.setResponse("");
        connection.getData().setAccountUserOid("acct:mytester@" + originUrl.getHost());
        connection.postReblog(rebloggedId);
        JSONObject activity = httpConnectionMock.getPostedJSONObject();
        assertTrue("Object present", activity.has("object"));
        JSONObject obj = activity.getJSONObject("object");
        assertEquals("Sharing a note", ObjectType.NOTE.id(), obj.getString("objectType"));
        assertEquals("Nothing in TO", null, activity.optJSONArray("to"));
        assertEquals("No followers in CC", null, activity.optJSONArray("cc"));
    }

    @Test
    public void testUnfollowUser() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.unfollow_pumpio);
        httpConnectionMock.setResponse(jso);
        connection.getData().setAccountUserOid("acct:t131t@" + originUrl.getHost());
        String userOid = "acct:evan@e14n.com";
        MbActivity activity = connection.followUser(userOid, false);
        assertEquals("Not unfollow action", MbActivityType.UNDO_FOLLOW, activity.type);
        MbUser user = activity.getUser();
        assertTrue("User is present", !user.isEmpty());
        assertEquals("Actor is not me", connection.getData().getAccountUserOid(), activity.getActor().oid);
        assertEquals("Object of action", userOid, user.oid);
    }

    @Test
    public void testParseDate() {
        String stringDate = "Wed Nov 27 09:27:01 -0300 2013";
        assertEquals("Bad date shouldn't throw (" + stringDate + ")", 0, connection.parseDate(stringDate) );
    }

    @Test
    public void testDestroyStatus() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.pumpio_delete_comment_response);
        httpConnectionMock.setResponse(jso);
        connection.getData().setAccountUserOid(DemoData.CONVERSATION_ACCOUNT_USER_OID);
        assertTrue("Success", connection.destroyStatus("https://" + DemoData.PUMPIO_MAIN_HOST
                + "/api/comment/xf0WjLeEQSlyi8jwHJ0ttre"));

        boolean thrown = false;
        try {
            connection.destroyStatus("");
        } catch (IllegalArgumentException e) {
            MyLog.v(this, e);
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testPostWithMedia() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.pumpio_activity_with_image);
        httpConnectionMock.setResponse(jso);
        
        connection.getData().setAccountUserOid("acct:mymediatester@" + originUrl.getHost());
        MbActivity activity = connection.updateStatus("Test post message with media", "", "", DemoData.LOCAL_IMAGE_TEST_URI);
        activity.getMessage().setPrivate(TriState.FALSE);
        assertEquals("Message returned", privateGetMessageWithAttachment(
                InstrumentationRegistry.getInstrumentation().getContext(), false), activity.getMessage());
    }
    
    private MbMessage privateGetMessageWithAttachment(Context context, boolean uniqueUid) throws IOException {
        String jso = RawResourceUtils.getString(context,
                org.andstatus.app.tests.R.raw.pumpio_activity_with_image);
        httpConnectionMock.setResponse(jso);

        MbMessage msg = connection.getMessage("w9wME-JVQw2GQe6POK7FSQ").getMessage();
        if (uniqueUid) {
            msg.oid += "_" + DemoData.TESTRUN_UID;
        }
        assertNotNull("message returned", msg);
        assertEquals("has attachment", msg.attachments.size(), 1);
        MbAttachment attachment = MbAttachment.fromUrlAndContentType(new URL(
                "https://io.jpope.org/uploads/jpope/2014/8/18/m1o1bw.jpg"), MyContentType.IMAGE);
        assertEquals("attachment", attachment, msg.attachments.get(0));
        assertEquals("Body text", "<p>Hanging out up in the mountains.</p>", msg.getBody());
        return msg;
    }

    @Test
    public void testGetMessageWithAttachment() throws IOException {
        privateGetMessageWithAttachment(InstrumentationRegistry.getInstrumentation().getContext(), true);
    }

    @Test
    public void testGetMessageWithReplies() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.pumpio_note_self);
        httpConnectionMock.setResponse(jso);

        final String msgOid = "https://identi.ca/api/note/Z-x96Q8rTHSxTthYYULRHA";
        final MbActivity activity = connection.getMessage(msgOid);
        MbMessage message = activity.getMessage();
        assertNotNull("message returned", message);
        assertEquals("Message oid", msgOid, message.oid);
        assertEquals("Number of replies", 2, message.replies.size());
        MbMessage reply = message.replies.get(0).getMessage();
        assertEquals("Reply oid", "https://identi.ca/api/comment/cJdi4cGWQT-Z9Rn3mjr5Bw", reply.oid);
        assertEquals("Is not a Reply " + activity, msgOid, reply.getInReplyTo().getMessage().oid);
    }
}
