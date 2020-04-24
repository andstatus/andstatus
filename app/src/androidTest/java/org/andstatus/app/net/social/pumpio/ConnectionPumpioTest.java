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

import android.net.Uri;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnectionData;
import org.andstatus.app.net.http.OAuthClientKeys;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.AObjectType;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpointType;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Attachments;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.ApiRoutineEnum;
import org.andstatus.app.net.social.ConnectionMock;
import org.andstatus.app.net.social.InputTimelinePage;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.net.social.Visibility;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.vavr.control.Try;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;
import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;
import static org.andstatus.app.util.UriUtilsTest.assertEndpoint;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConnectionPumpioTest {
    private ConnectionPumpio connection;
    private URL originUrl;
    private ConnectionMock mock;

    private String keyStored;
    private String secretStored;
    
    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithAccounts(this);
        originUrl = UrlUtils.fromString("https://" + demoData.pumpioMainHost);

        mock = ConnectionMock.newFor(demoData.conversationAccountName);
        connection = (ConnectionPumpio) mock.connection;

        HttpConnectionData data = mock.getHttp().data;
        data.originUrl = originUrl;
        data.oauthClientKeys = OAuthClientKeys.fromConnectionData(data);
        keyStored = data.oauthClientKeys.getConsumerKey();
        secretStored = data.oauthClientKeys.getConsumerSecret();

        if (!data.oauthClientKeys.areKeysPresent()) {
            data.oauthClientKeys.setConsumerKeyAndSecret("keyForThetestGetTimeline", "thisIsASecret02341");
        }
    }
    
    @After
    public void tearDown() {
        if (!StringUtil.isEmpty(keyStored)) {
            mock.getHttp().data.oauthClientKeys.setConsumerKeyAndSecret(keyStored, secretStored);
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
                ConnectionPumpio.PUBLIC_COLLECTION_ID};
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
    public void actorOidToHost() {
        String oids[] = {"t131t@identi.ca",
                "acct:somebody@example.com",
                "https://identi.ca/api/note/nlF5jl1HQciIs_zP85EeYg",
                "example.com",
                "@somewhere.com"};
        String hosts[] = {"identi.ca", 
                "example.com", 
                "",
                "",
                "somewhere.com"};
        for (int ind=0; ind < oids.length; ind++) {
            assertEquals("Expecting '" + hosts[ind] + "'", hosts[ind], connection.actorOidToHost(oids[ind]));
        }
    }

    @Test
    public void testGetConnectionAndUrl() throws ConnectionException {
        Origin origin = connection.getData().getOrigin();
        Actor actors[] = {
                Actor.fromOid(origin,"acct:t131t@" + demoData.pumpioMainHost)
                    .setWebFingerId("t131t@" + demoData.pumpioMainHost),
                Actor.fromOid(origin,"somebody@" + demoData.pumpioMainHost)
                    .setWebFingerId("somebody@" + demoData.pumpioMainHost)
        };
        String urls[] = {originUrl + "/api/user/t131t/profile", originUrl + "/api/user/somebody/profile"};
        String hosts[] = {demoData.pumpioMainHost, demoData.pumpioMainHost};
        for (int ind=0; ind < actors.length; ind++) {
            ConnectionAndUrl conu = ConnectionAndUrl.fromActor(connection, ApiRoutineEnum.GET_ACTOR, actors[ind]).get();
            assertEquals("Expecting '" + urls[ind] + "'", Uri.parse(urls[ind]), conu.uri);
            assertEquals("Expecting '" + hosts[ind] + "'", hosts[ind], conu.httpConnection.data.originUrl.getHost());
        }
    }

    @Test
    public void testGetTimeline() throws IOException {
        String sinceId = "https%3A%2F%2F" + originUrl.getHost() + "%2Fapi%2Factivity%2Ffrefq3232sf";
        mock.addResponse(org.andstatus.app.tests.R.raw.pumpio_actor_t131t_inbox);
        final String webFingerId = "t131t@" + originUrl.getHost();
        Actor actor1 = Actor.fromOid(connection.getData().getOrigin(),"acct:" + webFingerId)
                .setWebFingerId(webFingerId);
        InputTimelinePage timeline = connection.getTimeline(true, ApiRoutineEnum.HOME_TIMELINE,
                TimelinePosition.of(sinceId), TimelinePosition.EMPTY, 20, actor1).get();
        assertNotNull("timeline returned", timeline);
        int size = 6;
        assertEquals("Number of items in the Timeline", size, timeline.size());

        int ind = 0;
        assertEquals("Posting image", AObjectType.NOTE, timeline.get(ind).getObjectType());
        assertActivity0FromTimeline(timeline.get(ind));

        ind++;
        AActivity activity = timeline.get(ind);
        assertEquals("Is not FOLLOW " + activity, ActivityType.FOLLOW, activity.type);
        assertEquals("Actor", "acct:jpope@io.jpope.org", activity.getActor().oid);
        assertEquals("Actor is not my friend", TriState.TRUE, activity.getActor().isMyFriend);
        assertEquals("Activity Object", AObjectType.ACTOR, activity.getObjectType());
        Actor objActor = activity.getObjActor();
        assertEquals("objActor followed", "acct:atalsta@microca.st", objActor.oid);
        assertEquals("WebFinger ID", "atalsta@microca.st", objActor.getWebFingerId());
        assertEquals("Actor is my friend", TriState.FALSE, objActor.isMyFriend);

        ind++;
        activity = timeline.get(ind);
        assertEquals("Is not FOLLOW " + activity, ActivityType.FOLLOW, activity.type);
        assertEquals("Actor", AObjectType.ACTOR, activity.getObjectType());
        objActor = activity.getObjActor();
        assertEquals("Url of the actor", "https://identi.ca/t131t", activity.getActor().getProfileUrl());
        assertEquals("WebFinger ID", "t131t@identi.ca", activity.getActor().getWebFingerId());
        assertEquals("Actor is not my friend", TriState.TRUE, objActor.isMyFriend);
        assertEquals("Url of objActor", "https://fmrl.me/grdryn", objActor.getProfileUrl());

        ind++;
        activity = timeline.get(ind);
        assertEquals("Is not LIKE " + activity, ActivityType.LIKE, activity.type);
        assertEquals("Actor " + activity, "acct:jpope@io.jpope.org", activity.getActor().oid);
        assertEquals("Activity updated " + activity,
                TestSuite.utcTime(2013, Calendar.SEPTEMBER, 20, 22, 20, 25),
                TestSuite.utcTime(activity.getUpdatedDate()));
        Note note = activity.getNote();
        assertEquals("Author " + activity, "acct:lostson@fmrl.me", activity.getAuthor().oid);
        assertTrue("Has a non special recipient " + note.audience().getRecipients(),
                note.audience().getNonSpecialActors().isEmpty());
        assertEquals("Note oid " + note, "https://fmrl.me/api/note/Dp-njbPQSiOfdclSOuAuFw", note.oid);
        assertEquals("Url of the note " + note, "https://fmrl.me/lostson/note/Dp-njbPQSiOfdclSOuAuFw", note.url);
        assertThat("Note body " + note, note.getContent(), startsWith("My new <b>Firefox</b> OS phone arrived today"));
        assertEquals("Note updated " + note,
                TestSuite.utcTime(2013, Calendar.SEPTEMBER, 20, 20, 4, 22),
                TestSuite.utcTime(note.getUpdatedDate()));

        ind++;
        note = timeline.get(ind).getNote();
        assertTrue("Have a recipient", note.audience().hasNonSpecial());
        assertEquals("Directed to yvolk", "acct:yvolk@identi.ca" , note.audience().getFirstNonSpecial().oid);

        ind++;
        activity = timeline.get(ind);
        note = activity.getNote();
        assertEquals(TriState.UNKNOWN, activity.isSubscribedByMe());
        assertTrue("Is a reply", note.getInReplyTo().nonEmpty());
        assertEquals("Is not a reply to this actor " + activity, "jankusanagi@identi.ca",
                note.getInReplyTo().getAuthor().getUniqueName());
        assertEquals(TriState.UNKNOWN, note.getInReplyTo().isSubscribedByMe());
    }

    private void assertActivity0FromTimeline(AActivity activity) {
        Note note = activity.getNote();
        assertEquals("Note name " + note, "Wheel Stand", note.getName());
        assertThat("Note body " + note, note.getContent(), startsWith("Wow! Fantastic wheel stand at #DragWeek2013 today."));
        assertEquals("Note updated at " + TestSuite.utcTime(activity.getUpdatedDate()),
                TestSuite.utcTime(2013, Calendar.SEPTEMBER, 13, 1, 8, 38),
                TestSuite.utcTime(activity.getUpdatedDate()));
        Actor actor = activity.getActor();
        assertJpopeActor(actor, false);

        assertEquals("Actor is an Author", actor, activity.getAuthor());
        assertNotEquals("Is a Reblog " + activity, ActivityType.ANNOUNCE, activity.type);
        assertEquals("Favorited by me " + activity, TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor));

        Audience audience = note.audience();
        assertEquals("Should be Public for now. Followers in cc aren't recognized yet as a Followers collection... " +
                activity + "\n", Visibility.PUBLIC, audience.getVisibility());
        assertFalse("Is to Followers. We shouldn't know this yet?! " + audience, audience.isFollowers());
        assertThat(audience.getRecipients().toString(),
                audience.getNonSpecialActors().stream().map(Actor::getUsername).collect(Collectors.toList()),
                containsInAnyOrder("user/jpope/followers"));

        CommandExecutionContext executionContext = new CommandExecutionContext(
                MyContextHolder.get(),
                CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE, mock.getData().getMyAccount(), TimelineType.HOME));
        new DataUpdater(executionContext).onActivity(activity);

        Actor actorStored = Actor.loadFromDatabase(mock.getData().getOrigin().myContext, actor.actorId,
                () -> Actor.EMPTY, false);
        assertJpopeActor(actorStored, true);

        Note noteStored = Note.loadContentById(mock.getData().getOrigin().myContext, note.noteId);

        Audience audienceStored = noteStored.audience();
        assertEquals("Should be Public with Followers " + audienceStored,
                Visibility.PUBLIC_AND_TO_FOLLOWERS, audienceStored.getVisibility());
        assertTrue("Is not to Followers " + audienceStored, audienceStored.isFollowers());
        assertThat(audienceStored.getRecipients().toString(), audienceStored.getNonSpecialActors(), is(empty()));
    }

    private void assertJpopeActor(Actor actor, boolean stored) {
        assertEquals("Sender's oid", "acct:jpope@io.jpope.org", actor.oid);
        assertEquals("Sender's username", "jpope", actor.getUsername());
        assertEquals("Sender's unique name in Origin", "jpope@io.jpope.org", actor.getUniqueName());
        assertEquals("Sender's Display name", "jpope", actor.getRealName());
        assertEquals("Sender's profile image URL", "https://io.jpope.org/uploads/jpope/2013/7/8/LPyLPw_thumb.png",
                actor.getAvatarUrl());
        assertEquals("Sender's profile URL", "https://io.jpope.org/jpope", actor.getProfileUrl());
        assertEquals("Sender's Homepage", "https://io.jpope.org/jpope", actor.getHomepage());
        assertEquals("Sender's WebFinger ID", "jpope@io.jpope.org", actor.getWebFingerId());
        assertEquals("Description", "Does the Pope shit in the woods?", actor.getSummary());
        assertEquals("Notes count", 0, actor.notesCount);
        assertEquals("Favorites count", 0, actor.favoritesCount);
        assertEquals("Following (friends) count", 0, actor.followingCount);
        assertEquals("Followers count", 0, actor.followersCount);
        assertEquals("Location", "/dev/null", actor.location);
        assertEquals("Created at", stored ? SOME_TIME_AGO : DATETIME_MILLIS_NEVER, actor.getCreatedDate());
        assertEquals("Updated at", TestSuite.utcTime(2013, Calendar.SEPTEMBER, 12, 17, 10, 44),
                TestSuite.utcTime(actor.getUpdatedDate()));

        assertEndpoint(ActorEndpointType.API_INBOX, "https://io.jpope.org/api/user/jpope/inbox", actor);
        assertEndpoint(ActorEndpointType.API_PROFILE, "https://io.jpope.org/api/user/jpope/profile", actor);
        assertEndpoint(ActorEndpointType.API_FOLLOWING, "https://io.jpope.org/api/user/jpope/following", actor);
        assertEndpoint(ActorEndpointType.API_FOLLOWERS, "https://io.jpope.org/api/user/jpope/followers", actor);
        assertEndpoint(ActorEndpointType.API_LIKED, "https://io.jpope.org/api/user/jpope/favorites", actor);
    }

    @Test
    public void testGetFriends() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.pumpio_actor_t131t_following);
        
        assertTrue(connection.hasApiEndpoint(ApiRoutineEnum.GET_FRIENDS));
        assertTrue(connection.hasApiEndpoint(ApiRoutineEnum.GET_FRIENDS_IDS));

        final String webFingerId = "t131t@" + originUrl.getHost();
        Actor actor = Actor.fromOid(connection.getData().getOrigin(),"acct:" + webFingerId)
                .setWebFingerId(webFingerId);
        List<Actor> actors = connection.getFriends(actor).get();
        assertNotNull("List of actors, who " + actor.getUniqueNameWithOrigin() + " is following", actors);
        int size = 5;
        assertEquals("Response for t131t", size, actors.size());

        assertEquals("Does the Pope shit in the woods?", actors.get(1).getSummary());
        assertEquals("gitorious", actors.get(2).getUsername());
        assertEquals("gitorious@identi.ca", actors.get(2).getUniqueName());
        assertEquals("acct:ken@coding.example", actors.get(3).oid);
        assertEquals("Yuri Volkov", actors.get(4).getRealName());
    }

    @Test
    public void testReply() throws JSONException {
        String name = "To Peter";
        String content = "@peter Do you think it's true?";
        String inReplyToId = "https://identi.ca/api/note/94893FsdsdfFdgtjuk38ErKv";
        Note note = Note.fromOriginAndOid(mock.getData().getOrigin(), "", DownloadStatus.SENDING)
                .setName(name).setContentPosted(content);

        connection.updateNote(note, inReplyToId, Attachments.EMPTY);
        JSONObject jsoActivity = mock.getHttpMock().getPostedJSONObject();
        assertTrue("Object present", jsoActivity.has("object"));
        JSONObject jso = jsoActivity.getJSONObject("object");
        assertEquals("Note name", name, MyHtml.htmlToPlainText(jso.getString("displayName")));
        assertEquals("Note content", content, MyHtml.htmlToPlainText(jso.getString("content")));
        assertEquals("Reply is comment", PObjectType.COMMENT.id, jso.getString("objectType"));
        assertTrue("InReplyTo is present", jso.has("inReplyTo"));
        JSONObject inReplyToObject = jso.getJSONObject("inReplyTo");
        assertEquals("Id of the in reply to object", inReplyToId, inReplyToObject.getString("id"));
    }

    @Test
    public void testUpdateStatus() throws JSONException {
        String name = "";
        String content = "Testing the application...";
        Note note = Note.fromOriginAndOid(mock.getData().getOrigin(), "", DownloadStatus.SENDING)
                .setName(name).setContentPosted(content);

        Try<AActivity> tryActivity = connection.updateNote(note, "", Attachments.EMPTY);
        JSONObject jsoActivity = mock.getHttpMock().getPostedJSONObject();
        assertTrue("Object present " + jsoActivity +
                "\nResults: " + mock.getHttpMock().getResults(), jsoActivity.has("object"));
        JSONObject jso = jsoActivity.getJSONObject("object");
        assertEquals("Note name", name, MyHtml.htmlToPlainText(JsonUtils.optString(jso, "displayName")));
        assertEquals("Note content", content, MyHtml.htmlToPlainText(jso.getString("content")));
        assertEquals("Note without reply is a note", PObjectType.NOTE.id, jso.getString("objectType"));
        assertFalse("No explicit recipients expected " + jsoActivity, jsoActivity.has("to"));
        assertFalse("InReplyTo is not present " + jsoActivity, jso.has("inReplyTo"));
    }

    @Test
    public void testReblog() throws JSONException {
        String rebloggedId = "https://identi.ca/api/note/94893FsdsdfFdgtjuk38ErKv";
        connection.announce(rebloggedId);
        JSONObject activity = mock.getHttpMock().getPostedJSONObject();
        assertTrue("Object present", activity.has("object"));
        JSONObject obj = activity.getJSONObject("object");
        assertEquals("Sharing a note", PObjectType.NOTE.id, obj.getString("objectType"));
        assertFalse("Nothing in 'to'", activity.has("to"));
        assertFalse("No followers in CC", activity.has("cc"));
    }

    @Test
    public void testUndoFollowActor() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.unfollow_pumpio);
        String actorOid = "acct:evan@e14n.com";
        AActivity activity = connection.follow(actorOid, false).get();
        assertEquals("Not unfollow action", ActivityType.UNDO_FOLLOW, activity.type);
        Actor objActor = activity.getObjActor();
        assertTrue("objActor is present", objActor.nonEmpty());
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
        mock.addResponse(org.andstatus.app.tests.R.raw.pumpio_delete_comment_response);
        assertTrue("Success", connection.deleteNote("https://" + demoData.pumpioMainHost
                + "/api/comment/xf0WjLeEQSlyi8jwHJ0ttre").get());

        Try<Boolean> tried = connection.deleteNote("");
        assertTrue(tried.isFailure());
        assertThat(tried.getCause(), isA(IllegalArgumentException.class));
    }

    @Test
    public void testPostWithImage() throws IOException {
        // TODO: There should be 3 responses, just like for Video
        mock.addResponse(org.andstatus.app.tests.R.raw.pumpio_activity_with_image);

        Note note = Note.fromOriginAndOid(mock.getData().getOrigin(), "", DownloadStatus.SENDING)
                .setContentPosted("Test post note with media");
        Try<AActivity> activity = connection.updateNote(note, "",
                new Attachments().add(Attachment.fromUriAndMimeType(demoData.localImageTestUri,
                        MyContentType.IMAGE.generalMimeType)));
    }

    @Test
    public void testPostWithVideo() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.pumpio_activity_with_video_response1);
        mock.addResponse(org.andstatus.app.tests.R.raw.pumpio_activity_with_video_response2);
        mock.addResponse(org.andstatus.app.tests.R.raw.pumpio_activity_with_video_response3);

        String name = "Note - Testing Video attachments in #AndStatus";
        String content = "<p dir=\"ltr\">Video attachment is here</p>";

        Note note = Note.fromOriginAndOid(mock.getData().getOrigin(), "", DownloadStatus.SENDING)
                .setName(name).setContentPosted(content);

        AActivity activity = connection.updateNote(note, "",
                new Attachments().add(Attachment.fromUriAndMimeType(demoData.localVideoTestUri,
                        MyContentType.VIDEO.generalMimeType))).get();
        assertEquals("Responses counter " + mock.getHttpMock(), 3, mock.getHttpMock().responsesCounter);
        Note note2 = activity.getNote();
        assertEquals("Note name " + activity, name, note2.getName());
        assertEquals("Note content " + activity, content, note2.getContent());
        assertEquals("Should have an attachment " + activity, false, note2.attachments.isEmpty());
        Attachment attachment = note2.attachments.list.get(0);
        assertEquals("Video attachment " + activity, MyContentType.VIDEO, attachment.contentType);
        assertEquals("Video content type " + activity, "video/mp4", attachment.mimeType);
        assertEquals("Video uri " + activity,
                "https://identi.ca/uploads/andstatus/2018/4/11/7CmQmw.mp4", attachment.uri.toString());
    }

    private Note privateGetNoteWithAttachment(boolean uniqueUid) throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.pumpio_activity_with_image);

        Note note = connection.getNote("https://io.jpope.org/api/activity/w9wME-JVQw2GQe6POK7FSQ").get().getNote();
        if (uniqueUid) {
            note = note.copy(Optional.of(note.oid + "_" + demoData.testRunUid), Optional.empty());
        }
        assertNotNull("note returned", note);
        assertEquals("has attachment", 1, note.attachments.size());
        Attachment attachment = Attachment.fromUri("https://io.jpope.org/uploads/jpope/2014/8/18/m1o1bw.jpg");
        assertEquals("attachment", attachment, note.attachments.list.get(0));
        assertEquals("Body text", "<p>Hanging out up in the mountains.</p>\n", note.getContent());
        return note;
    }

    @Test
    public void getNoteWithAttachment() throws IOException {
        privateGetNoteWithAttachment(true);
    }

    @Test
    public void getNoteWithReplies() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.pumpio_note_self);

        final String noteOid = "https://identi.ca/api/note/Z-x96Q8rTHSxTthYYULRHA";
        final AActivity activity = connection.getNote(noteOid).get();
        Note note = activity.getNote();
        assertNotNull("note returned", note);
        assertEquals("Note oid", noteOid, note.oid);
        assertEquals("Number of replies", 2, note.replies.size());
        Note reply = note.replies.get(0).getNote();
        assertEquals("Reply oid", "https://identi.ca/api/comment/cJdi4cGWQT-Z9Rn3mjr5Bw", reply.oid);
        assertEquals("Is not a Reply " + activity, noteOid, reply.getInReplyTo().getNote().oid);
    }
}
