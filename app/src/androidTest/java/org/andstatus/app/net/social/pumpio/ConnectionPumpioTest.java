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
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnectionMock;
import org.andstatus.app.net.http.OAuthClientKeys;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.AObjectType;
import org.andstatus.app.net.social.Actor;
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

import static org.andstatus.app.context.DemoData.demoData;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ConnectionPumpioTest {
    private ConnectionPumpio connection;
    private URL originUrl;
    private HttpConnectionMock httpConnectionMock;

    private String keyStored;
    private String secretStored;
    
    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
        originUrl = UrlUtils.fromString("https://" + demoData.pumpioMainHost);

        TestSuite.setHttpConnectionMockClass(HttpConnectionMock.class);
        OriginConnectionData connectionData = OriginConnectionData.fromAccountName(AccountName.fromOriginAndUsername(
                MyContextHolder.get().origins().fromName(demoData.pumpioOriginName), ""),
                TriState.UNKNOWN);
        connectionData.setAccountActor(demoData.getAccountActorByOid(demoData.pumpioTestAccountActorOid));
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
        String actorOids[] = {"acct:t131t@" + demoData.pumpioMainHost,
                "somebody@" + demoData.pumpioMainHost};
        String urls[] = {"api/user/t131t/profile", 
                "api/user/somebody/profile"};
        String hosts[] = {demoData.pumpioMainHost, demoData.pumpioMainHost};
        for (int ind=0; ind < actorOids.length; ind++) {
            ConnectionAndUrl conu = connection.getConnectionAndUrl(ApiRoutineEnum.GET_ACTOR, actorOids[ind]);
            assertEquals("Expecting '" + urls[ind] + "'", urls[ind], conu.url);
            assertEquals("Expecting '" + hosts[ind] + "'", hosts[ind], conu.httpConnection.data.originUrl.getHost());
        }
    }

    @Test
    public void testGetTimeline() throws IOException {
        String sinceId = "https%3A%2F%2F" + originUrl.getHost() + "%2Fapi%2Factivity%2Ffrefq3232sf";

        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.pumpio_actor_t131t_inbox);
        httpConnectionMock.setResponse(jso);
        
        List<AActivity> timeline = connection.getTimeline(ApiRoutineEnum.HOME_TIMELINE,
                new TimelinePosition(sinceId), TimelinePosition.EMPTY, 20, "acct:t131t@" + originUrl.getHost());
        assertNotNull("timeline returned", timeline);
        int size = 6;
        assertEquals("Number of items in the Timeline", size, timeline.size());

        int ind = 0;
        assertEquals("Posting image", AObjectType.NOTE, timeline.get(ind).getObjectType());
        AActivity activity = timeline.get(ind);
        Note note = activity.getNote();
        assertThat("Note body " + note, note.getBody(), startsWith("Wow! Fantastic wheel stand at #DragWeek2013 today."));
        assertEquals("Note updated at " + TestSuite.utcTime(activity.getUpdatedDate()),
                TestSuite.utcTime(2013, Calendar.SEPTEMBER, 13, 1, 8, 38),
                TestSuite.utcTime(activity.getUpdatedDate()));
        Actor actor = activity.getActor();
        assertEquals("Sender's oid", "acct:jpope@io.jpope.org", actor.oid);
        assertEquals("Sender's username", "jpope@io.jpope.org", actor.getUsername());
        assertEquals("Sender's Display name", "jpope", actor.getRealName());
        assertEquals("Sender's profile image URL", "https://io.jpope.org/uploads/jpope/2013/7/8/LPyLPw_thumb.png", actor.avatarUrl);
        assertEquals("Sender's profile URL", "https://io.jpope.org/jpope", actor.getProfileUrl());
        assertEquals("Sender's Homepage", "https://io.jpope.org/jpope", actor.getHomepage());
        assertEquals("Sender's WebFinger ID", "jpope@io.jpope.org", actor.getWebFingerId());
        assertEquals("Description", "Does the Pope shit in the woods?", actor.getDescription());
        assertEquals("Notes count", 0, actor.notesCount);
        assertEquals("Favorites count", 0, actor.favoritesCount);
        assertEquals("Following (friends) count", 0, actor.followingCount);
        assertEquals("Followers count", 0, actor.followersCount);
        assertEquals("Location", "/dev/null", actor.location);
        assertEquals("Created at", 0, actor.getCreatedDate());
        assertEquals("Updated at", TestSuite.utcTime(2013, Calendar.SEPTEMBER, 12, 17, 10, 44),
                TestSuite.utcTime(actor.getUpdatedDate()));
        assertEquals("Actor is an Author", actor, activity.getAuthor());
        assertNotEquals("Is a Reblog " + activity, ActivityType.ANNOUNCE, activity.type);
        assertEquals("Favorited by me " + activity, TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor));

        ind++;
        activity = timeline.get(ind);
        assertEquals("Is not FOLLOW " + activity, ActivityType.FOLLOW, activity.type);
        assertEquals("Actor", "acct:jpope@io.jpope.org", activity.getActor().oid);
        assertEquals("Actor not followed by me", TriState.TRUE, activity.getActor().followedByMe);
        assertEquals("Activity Object", AObjectType.ACTOR, activity.getObjectType());
        Actor objActor = activity.getObjActor();
        assertEquals("objActor followed", "acct:atalsta@microca.st", objActor.oid);
        assertEquals("WebFinger ID", "atalsta@microca.st", objActor.getWebFingerId());
        assertEquals("Actor followed by me", TriState.FALSE, objActor.followedByMe);

        ind++;
        activity = timeline.get(ind);
        assertEquals("Is not FOLLOW " + activity, ActivityType.FOLLOW, activity.type);
        assertEquals("Actor", AObjectType.ACTOR, activity.getObjectType());
        objActor = activity.getObjActor();
        assertEquals("Url of the actor", "https://identi.ca/t131t", activity.getActor().getProfileUrl());
        assertEquals("WebFinger ID", "t131t@identi.ca", activity.getActor().getWebFingerId());
        assertEquals("Following", TriState.TRUE, objActor.followedByMe);
        assertEquals("Url of objActor", "https://fmrl.me/grdryn", objActor.getProfileUrl());

        ind++;
        activity = timeline.get(ind);
        assertEquals("Is not LIKE " + activity, ActivityType.LIKE, activity.type);
        assertEquals("Actor " + activity, "acct:jpope@io.jpope.org", activity.getActor().oid);
        assertEquals("Activity updated " + activity,
                TestSuite.utcTime(2013, Calendar.SEPTEMBER, 20, 22, 20, 25),
                TestSuite.utcTime(activity.getUpdatedDate()));
        note = activity.getNote();
        assertEquals("Author " + activity, "acct:lostson@fmrl.me", activity.getAuthor().oid);
        assertTrue("Does not have a recipient", note.audience().isEmpty());
        assertEquals("Note oid " + note, "https://fmrl.me/api/note/Dp-njbPQSiOfdclSOuAuFw", note.oid);
        assertEquals("Url of the note " + note, "https://fmrl.me/lostson/note/Dp-njbPQSiOfdclSOuAuFw", note.url);
        assertThat("Note body " + note, note.getBody(), startsWith("My new <b>Firefox</b> OS phone arrived today"));
        assertEquals("Note updated " + note,
                TestSuite.utcTime(2013, Calendar.SEPTEMBER, 20, 20, 4, 22),
                TestSuite.utcTime(note.getUpdatedDate()));

        ind++;
        note = timeline.get(ind).getNote();
        assertTrue("Have a recipient", note.audience().nonEmpty());
        assertEquals("Directed to yvolk", "acct:yvolk@identi.ca" , note.audience().getFirst().oid);

        ind++;
        activity = timeline.get(ind);
        note = activity.getNote();
        assertEquals(activity.isSubscribedByMe(), TriState.UNKNOWN);
        assertTrue("Is a reply", note.getInReplyTo().nonEmpty());
        assertEquals("Is not a reply to this actor " + activity, "jankusanagi@identi.ca", note.getInReplyTo().getAuthor().getUsername());
        assertEquals(TriState.UNKNOWN, note.getInReplyTo().isSubscribedByMe());
    }

    @Test
    public void testGetActorsFollowedBy() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.pumpio_actor_t131t_following);
        httpConnectionMock.setResponse(jso);
        
        assertTrue(connection.isApiSupported(ApiRoutineEnum.GET_FRIENDS));        
        assertTrue(connection.isApiSupported(ApiRoutineEnum.GET_FRIENDS_IDS));        
        
        List<Actor> actors = connection.getFriends("acct:t131t@" + originUrl.getHost());
        assertNotNull("List of actors returned", actors);
        int size = 5;
        assertEquals("Response for t131t", size, actors.size());

        assertEquals("Does the Pope shit in the woods?", actors.get(1).getDescription());
        assertEquals("gitorious@identi.ca", actors.get(2).getUsername());
        assertEquals("acct:ken@coding.example", actors.get(3).oid);
        assertEquals("Yuri Volkov", actors.get(4).getRealName());
    }

    @Test
    public void testUpdateStatus() throws ConnectionException, JSONException {
        String body = "@peter Do you think it's true?";
        String inReplyToId = "https://identi.ca/api/note/94893FsdsdfFdgtjuk38ErKv";
        httpConnectionMock.setResponse("");
        connection.getData().setAccountActor(demoData.getAccountActorByOid(demoData.conversationAccountActorOid));
        connection.updateNote(body, "", inReplyToId, null);
        JSONObject activity = httpConnectionMock.getPostedJSONObject();
        assertTrue("Object present", activity.has("object"));
        JSONObject obj = activity.getJSONObject("object");
        assertEquals("Note content", body, MyHtml.fromHtml(obj.getString("content")));
        assertEquals("Reply is comment", PObjectType.COMMENT.id(), obj.getString("objectType"));
        
        assertTrue("InReplyTo is present", obj.has("inReplyTo"));
        JSONObject inReplyToObject = obj.getJSONObject("inReplyTo");
        assertEquals("Id of the in reply to object", inReplyToId, inReplyToObject.getString("id"));

        body = "Testing the application...";
        inReplyToId = "";
        connection.updateNote(body, "", inReplyToId, null);
        activity = httpConnectionMock.getPostedJSONObject();
        assertTrue("Object present", activity.has("object"));
        obj = activity.getJSONObject("object");
        assertEquals("Note content", body, MyHtml.fromHtml(obj.getString("content")));
        assertEquals("Note without reply is a note", PObjectType.NOTE.id(), obj.getString("objectType"));

        JSONArray recipients = activity.optJSONArray("to");
        assertEquals("To Public collection", ActivitySender.PUBLIC_COLLECTION_ID, ((JSONObject) recipients.get(0)).get("id"));

        assertTrue("InReplyTo is not present", !obj.has("inReplyTo"));
    }

    @Test
    public void testReblog() throws ConnectionException, JSONException {
        String rebloggedId = "https://identi.ca/api/note/94893FsdsdfFdgtjuk38ErKv";
        httpConnectionMock.setResponse("");
        connection.getData().setAccountActor(demoData.getAccountActorByOid(demoData.conversationAccountActorOid));
        connection.announce(rebloggedId);
        JSONObject activity = httpConnectionMock.getPostedJSONObject();
        assertTrue("Object present", activity.has("object"));
        JSONObject obj = activity.getJSONObject("object");
        assertEquals("Sharing a note", PObjectType.NOTE.id(), obj.getString("objectType"));
        assertEquals("Nothing in TO", null, activity.optJSONArray("to"));
        assertEquals("No followers in CC", null, activity.optJSONArray("cc"));
    }

    @Test
    public void testUnfollowActor() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.unfollow_pumpio);
        httpConnectionMock.setResponse(jso);
        connection.getData().setAccountActor(demoData.getAccountActorByOid(demoData.conversationAccountActorOid));
        String actorOid = "acct:evan@e14n.com";
        AActivity activity = connection.follow(actorOid, false);
        assertEquals("Not unfollow action", ActivityType.UNDO_FOLLOW, activity.type);
        Actor objActor = activity.getObjActor();
        assertTrue("objActor is present", !objActor.isEmpty());
        assertEquals("Actor", "acct:t131t@pump1.example.com", activity.getActor().oid);
        assertEquals("Object of action", actorOid, objActor.oid);
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
        connection.getData().setAccountActor(demoData.getAccountActorByOid(demoData.conversationAccountActorOid));
        assertTrue("Success", connection.deleteNote("https://" + demoData.pumpioMainHost
                + "/api/comment/xf0WjLeEQSlyi8jwHJ0ttre"));

        boolean thrown = false;
        try {
            connection.deleteNote("");
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
        
        connection.getData().setAccountActor(demoData.getAccountActorByOid(demoData.conversationAccountActorOid));
        AActivity activity = connection.updateNote("Test post note with media", "", "", demoData.localImageTestUri);
        activity.getNote().setPrivate(TriState.FALSE);
        assertEquals("Note returned", privateGetNoteWithAttachment(
                InstrumentationRegistry.getInstrumentation().getContext(), false), activity.getNote());
    }
    
    private Note privateGetNoteWithAttachment(Context context, boolean uniqueUid) throws IOException {
        String jso = RawResourceUtils.getString(context,
                org.andstatus.app.tests.R.raw.pumpio_activity_with_image);
        httpConnectionMock.setResponse(jso);

        Note msg = connection.getNote("w9wME-JVQw2GQe6POK7FSQ").getNote();
        if (uniqueUid) {
            msg = msg.copy(msg.oid + "_" + demoData.testRunUid);
        }
        assertNotNull("note returned", msg);
        assertEquals("has attachment", msg.attachments.size(), 1);
        Attachment attachment = Attachment.fromUrlAndContentType(new URL(
                "https://io.jpope.org/uploads/jpope/2014/8/18/m1o1bw.jpg"), MyContentType.IMAGE);
        assertEquals("attachment", attachment, msg.attachments.get(0));
        assertEquals("Body text", "<p>Hanging out up in the mountains.</p>", msg.getBody());
        return msg;
    }

    @Test
    public void getNoteWithAttachment() throws IOException {
        privateGetNoteWithAttachment(InstrumentationRegistry.getInstrumentation().getContext(), true);
    }

    @Test
    public void getNoteWithReplies() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.pumpio_note_self);
        httpConnectionMock.setResponse(jso);

        final String noteOid = "https://identi.ca/api/note/Z-x96Q8rTHSxTthYYULRHA";
        final AActivity activity = connection.getNote(noteOid);
        Note note = activity.getNote();
        assertNotNull("note returned", note);
        assertEquals("Note oid", noteOid, note.oid);
        assertEquals("Number of replies", 2, note.replies.size());
        Note reply = note.replies.get(0).getNote();
        assertEquals("Reply oid", "https://identi.ca/api/comment/cJdi4cGWQT-Z9Rn3mjr5Bw", reply.oid);
        assertEquals("Is not a Reply " + activity, noteOid, reply.getInReplyTo().getNote().oid);
    }
}
