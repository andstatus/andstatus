/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.data.NoteForAnyAccount;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RawResourceUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.data.DemoNoteInserter.assertVisibility;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConnectionMastodonTest {
    private ConnectionMock mock;
    private Actor accountActor;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithAccounts(this);
        mock = ConnectionMock.newFor(demoData.mastodonTestAccountName);
        accountActor = mock.getData().getAccountActor();
    }

    @Test
    public void testGetHomeTimeline() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.mastodon_home_timeline);

        InputTimelinePage timeline = mock.connection.getTimeline(true, Connection.ApiRoutineEnum.HOME_TIMELINE,
                TimelinePosition.of("2656388"), TimelinePosition.EMPTY, 20, accountActor).get();
        assertNotNull("timeline returned", timeline);
        int size = 1;
        assertEquals("Number of items in the Timeline", size, timeline.size());

        int ind = 0;
        AActivity activity = timeline.get(ind);
        Note note = activity.getNote();
        assertEquals("Activity oid", "22", activity.getOid());
        assertEquals("Note Oid", "22", note.oid);
        assertEquals("Account unknown " + activity, true, MyContextHolder.get().accounts()
                .fromActorOfSameOrigin(activity.accountActor).isValid());
        assertEquals("Is not a note " + activity, AObjectType.NOTE, activity.getObjectType());
        assertEquals("Favorited " + activity, TriState.UNKNOWN, note.getFavoritedBy(activity.accountActor));
        assertVisibility(note.audience(), Visibility.PUBLIC_AND_TO_FOLLOWERS);

        Actor actor = activity.getActor();
        String stringDate = "2017-04-16T11:13:12.133Z";
        long parsedDate = mock.connection.parseDate(stringDate);
        assertEquals("Parsing " + stringDate, 4, new Date(parsedDate).getMonth() + 1);
        assertEquals("Created at", parsedDate, actor.getCreatedDate());

        assertTrue("Actor is partially defined " + actor, actor.isFullyDefined());
        assertEquals("Actor Oid", "37", actor.oid);
        assertEquals("Username", "t131t1", actor.getUsername());

        assertEquals("Note Oid " + activity, "22", note.oid);
        assertEquals("Note url" + activity, "https://neumastodon.com/@t131t1/22", note.url);
        assertEquals("Name", "", note.getName());
        assertEquals("Summary", "This is a test spoiler", note.getSummary());
        assertEquals("Body", "<p>I&apos;m figuring out how to work with Mastodon</p>", note.getContent());
        assertEquals("Note application", "Web", note.via);

        assertEquals("Media attachments", 2, note.attachments.size());
        Attachment attachment = note.attachments.list.get(0);
        assertEquals("Content type", MyContentType.IMAGE, attachment.contentType);
        assertEquals("Media URI", UriUtils.fromString("https://files.neumastodon.com/media_attachments/files/000/306/223/original/e678f956970a585b.png?1492832537"),
                attachment.getUri());

        timeline.items.forEach(act -> act.setUpdatedNow(0));
        MyAccount ma = demoData.getMyAccount(demoData.mastodonTestAccountName);
        CommandExecutionContext executionContext = new CommandExecutionContext(
                MyContextHolder.get(), CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE, ma, TimelineType.HOME));
        new DataUpdater(executionContext).onActivity(activity);
    }

    @Test
    public void testIncomingVisibility() throws IOException {
        String response = RawResourceUtils.getString(org.andstatus.app.tests.R.raw.mastodon_home_timeline);
        oneVisibility(response, Visibility.PUBLIC_AND_TO_FOLLOWERS);
        String pattern = "\"visibility\": \"public\"";
        oneVisibility(response.replace(pattern, "\"visibility\": \"unlisted\""), Visibility.PUBLIC_AND_TO_FOLLOWERS);
        oneVisibility(response.replace(pattern, "\"visibility\": \"private\""), Visibility.TO_FOLLOWERS);
        oneVisibility(response.replace(pattern, "\"visibility\": \"direct\""), Visibility.PRIVATE);
    }

    private void oneVisibility(String stringResponse, Visibility visibility) {
        mock.getHttpMock().addResponse(stringResponse);
        InputTimelinePage timeline = mock.connection.getTimeline(true, Connection.ApiRoutineEnum.HOME_TIMELINE,
                TimelinePosition.of("2656388"), TimelinePosition.EMPTY, 20, accountActor).get();
        assertVisibility(timeline.get(0).getNote().audience(), visibility);
    }

    @Test
    public void testGetConversation() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.mastodon_get_conversation);

        List<AActivity> timeline = mock.connection.getConversation("5596683").get();
        assertNotNull("timeline returned", timeline);
        assertEquals("Number of items in the Timeline", 5, timeline.size());
    }

    @Test
    public void testGetNotifications() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.mastodon_notifications);

        InputTimelinePage timeline = mock.connection.getTimeline(true, Connection.ApiRoutineEnum.NOTIFICATIONS_TIMELINE,
                TimelinePosition.EMPTY, TimelinePosition.EMPTY, 20, accountActor).get();
        assertNotNull("timeline returned", timeline);
        assertEquals("Number of items in the Timeline", 20, timeline.size());

        int ind = 0;
        AActivity activity = timeline.get(ind);
        assertEquals("Activity oid", "2667058", activity.getOid());
        assertEquals("Note Oid", "4729037", activity.getNote().oid);
        assertEquals("Is not a Reblog " + activity, ActivityType.ANNOUNCE, activity.type);
        assertEquals("Is not an activity", AObjectType.ACTIVITY, activity.getObjectType());
        Actor actor = activity.getActor();
        assertEquals("Actor's Oid", "15451", actor.oid);
        assertEquals("Actor's username", "Chaosphere", actor.getUsername());
        assertEquals("WebfingerId", "chaosphere@mastodon.social", actor.getWebFingerId());
        assertEquals("Author's username" + activity, "AndStatus", activity.getAuthor().getUsername());
        assertEquals("Favorited " + activity, TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor));

        ind = 2;
        activity = timeline.get(ind);
        assertEquals("Activity oid", "2674022", activity.getOid());
        assertEquals("Note Oid", "4729037", activity.getNote().oid);
        assertEquals("Is not an activity " + activity, AObjectType.ACTIVITY, activity.getObjectType());
        assertEquals("Is not LIKE " + activity, ActivityType.LIKE, activity.type);
        assertThat(activity.getNote().getContent(), is("<p>IT infrastructure of modern church</p>"));
        assertEquals("Favorited " + activity, TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor));
        assertEquals("Author's username", "AndStatus", activity.getAuthor().getUsername());
        actor = activity.getActor();
        assertEquals("Actor's Oid", "48790", actor.oid);
        assertEquals("Actor's Username", "vfrmedia", actor.getUsername());
        assertEquals("WebfingerId", "vfrmedia@social.tchncs.de", actor.getWebFingerId());

        ind = 17;
        activity = timeline.get(ind);
        assertEquals("Is not FOLLOW " + activity, ActivityType.FOLLOW, activity.type);
        assertEquals("Is not an ACTOR", AObjectType.ACTOR, activity.getObjectType());
        actor = activity.getActor();
        assertEquals("Actor's Oid", "24853", actor.oid);
        assertEquals("Username", "resir014", actor.getUsername());
        assertEquals("WebfingerId", "resir014@icosahedron.website", actor.getWebFingerId());
        Actor objActor = activity.getObjActor();
        assertEquals("Not following me" + activity, accountActor.oid, objActor.oid);

        ind = 19;
        activity = timeline.get(ind);
        assertEquals("Is not UPDATE " + activity, ActivityType.UPDATE, activity.type);
        assertEquals("Is not a note", AObjectType.NOTE, activity.getObjectType());
        assertThat(activity.getNote().getContent(), containsString("universe of Mastodon"));
        actor = activity.getActor();
        assertEquals("Actor's Oid", "119218", actor.oid);
        assertEquals("Username", "izwx6502", actor.getUsername());
        assertEquals("WebfingerId", "izwx6502@mstdn.jp", actor.getWebFingerId());
    }

    @Test
    public void testGetActor() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.mastodon_get_actor);

        Actor actor = mock.connection.getActor(Actor.fromOid( accountActor.origin,"5962")).get();
        assertTrue(actor.toString(), actor.nonEmpty());
        assertEquals("Actor's Oid", "5962", actor.oid);
        assertEquals("Username", "AndStatus", actor.getUsername());
        assertEquals("WebfingerId", "andstatus@mastodon.social", actor.getWebFingerId());
        assertThat("Bio", actor.getSummary(), containsString("multiple Social networks"));
        assertThat("Fields appended", actor.getSummary(), containsString("Website: "));
        assertThat("Fields appended", actor.getSummary(), containsString("FAQ: "));
        assertThat("Fields appended", actor.getSummary(), containsString("GitHub: "));
    }

    @Test
    public void mentionsInANote() throws IOException {
        mentionsInANoteOneLoad(1);
        mentionsInANoteOneLoad(2);
        mentionsInANoteOneLoad(3);
    }

    public void mentionsInANoteOneLoad(int iteration) throws IOException {
        MyLog.i("mentionsInANote" + iteration, "started");

        mock.addResponse(org.andstatus.app.tests.R.raw.mastodon_get_note);
        AActivity activity = mock.connection.getNote("101064848262880936").get();
        assertEquals("Is not UPDATE " + activity, ActivityType.UPDATE, activity.type);
        assertEquals("Is not a note", AObjectType.NOTE, activity.getObjectType());

        Actor actor = activity.getActor();
        assertEquals("Actor's Oid", "32", actor.oid);
        assertEquals("Username", "somePettter", actor.getUsername());
        assertEquals("WebfingerId", "somepettter@social.umeahackerspace.se", actor.getWebFingerId());

        Note note = activity.getNote();
        assertThat(note.getContent(), containsString("CW should properly"));

        activity.setUpdatedNow(0);

        MyAccount ma = demoData.getMyAccount(demoData.mastodonTestAccountName);
        CommandExecutionContext executionContext = new CommandExecutionContext(
                MyContextHolder.get(), CommandData.newItemCommand(CommandEnum.GET_NOTE, ma, 123));
        new DataUpdater(executionContext).onActivity(activity);

        assertOneRecipient(activity, "AndStatus", "https://mastodon.example.com/@AndStatus",
                "andstatus@" + accountActor.origin.getHost());
        assertOneRecipient(activity, "qwertystop", "https://wandering.shop/@qwertystop",
                "qwertystop@wandering.shop");

        assertVisibility(activity.getNote().audience(), Visibility.PUBLIC_AND_TO_FOLLOWERS);

        Audience audience = Audience.fromNoteId(accountActor.origin, activity.getNote().noteId);
        assertVisibility(audience, Visibility.PUBLIC_AND_TO_FOLLOWERS);

    }

    private void assertOneRecipient(AActivity activity, String username, String profileUrl, String webFingerId) {
        Audience audience = activity.getNote().audience();
        Actor actor = audience.getNonSpecialActors().stream().filter(a ->
                a.getUsername().equals(username)).findAny().orElse(Actor.EMPTY);
        assertTrue(username + " should be mentioned: " + activity, actor.nonEmpty());
        assertEquals("Mentioned user: " + activity, profileUrl, actor.getProfileUrl());
        assertEquals("Mentioned user: " + activity, webFingerId, actor.getWebFingerId());
    }

    @Test
    public void reblog() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.mastodon_get_reblog);

        AActivity activity = mock.connection.getNote("101100271392454703").get();
        assertEquals("Is not ANNOUNCE " + activity, ActivityType.ANNOUNCE, activity.type);
        assertEquals("Is not an Activity", AObjectType.ACTIVITY, activity.getObjectType());

        Actor actor = activity.getActor();
        assertEquals("Actor's Oid", "153111", actor.oid);
        assertEquals("Username", "ZeniorXV", actor.getUsername());
        assertEquals("WebfingerId", "zeniorxv@mastodon.social", actor.getWebFingerId());

        Note note = activity.getNote();
        assertThat(note.getContent(), containsString("car of the future"));

        Actor author = activity.getAuthor();
        assertEquals("Author's Oid", "159379", author.oid);
        assertEquals("Username", "bjoern", author.getUsername());
        assertEquals("WebfingerId", "bjoern@mastodon.social", author.getWebFingerId());

        activity.setUpdatedNow(0);

        MyAccount ma = demoData.getMyAccount(demoData.mastodonTestAccountName);
        CommandExecutionContext executionContext = new CommandExecutionContext(
                MyContextHolder.get(), CommandData.newItemCommand(CommandEnum.GET_NOTE, ma, 123));
        new DataUpdater(executionContext).onActivity(activity);

        assertNotEquals("Activity wasn't saved " + activity, 0,  activity.getId());
        assertNotEquals("Reblogged note wasn't saved " + activity, 0,  activity.getNote().noteId);
    }

    @Test
    public void tootWithVideoAttachment() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.mastodon_video);
        assertOneTootWithVideo("263975",
                "https://mastodon.social/media_proxy/11640109/original",
                "https://mastodon.social/media_proxy/11640109/small");
    }


    @Test
    public void originalTootWithVideoAttachment() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.mastodon_video_original);
        assertOneTootWithVideo("10496",
                "https://mastodont.cat/system/media_attachments/files/000/684/914/original/7424effb937d991c.mp4?1550739268",
                "https://mastodont.cat/system/media_attachments/files/000/684/914/small/7424effb937d991c.png?1550739268");
    }

    private void assertOneTootWithVideo(String actorOid, String videoUri, String previewUri) throws ConnectionException {
        InputTimelinePage timeline = mock.connection.getTimeline(true, Connection.ApiRoutineEnum.ACTOR_TIMELINE,
                TimelinePosition.EMPTY, TimelinePosition.EMPTY, 20, Actor.fromOid(mock.getData().getOrigin(), actorOid)).get();
        assertNotNull("timeline returned", timeline);
        assertEquals("Number of items in the Timeline", 1, timeline.size());

        AActivity activity = timeline.get(0);
        Note note = activity.getNote();

        assertEquals("Media attachments " + note.attachments, 2, note.attachments.size());
        Attachment video = note.attachments.list.get(0);
        assertEquals("Content type", MyContentType.VIDEO, video.contentType);
        assertEquals("Media URI", UriUtils.fromString(videoUri),
                video.getUri());
        Attachment preview = note.attachments.list.get(1);
        assertEquals("Content type", MyContentType.IMAGE, preview.contentType);
        assertEquals("Media URI", UriUtils.fromString(previewUri),
                preview.getUri());
        assertEquals("Preview of", preview.previewOf, video);

        MyAccount ma = demoData.getMyAccount(demoData.mastodonTestAccountName);
        CommandExecutionContext executionContext = new CommandExecutionContext(
                MyContextHolder.get(), CommandData.newItemCommand(CommandEnum.GET_CONVERSATION, ma, 123));
        new DataUpdater(executionContext).onActivity(activity);

        List<DownloadData> downloads = DownloadData.fromNoteId(MyContextHolder.get(), note.noteId);
        assertEquals("Saved downloads " + downloads, 2, downloads.size());
        DownloadData dPreview = downloads.stream().filter(d -> d.getContentType() == MyContentType.IMAGE).findAny().orElse(DownloadData.EMPTY);
        assertEquals("Preview URL " + downloads, preview.uri, dPreview.getUri());
        assertEquals("Preview " + downloads, 0, dPreview.getDownloadNumber());
        DownloadData dVideo = downloads.stream().filter(d -> d.getContentType() == MyContentType.VIDEO).findAny().orElse(DownloadData.EMPTY);
        assertNotEquals("Video URL not saved " + downloads, 0, dVideo.getDownloadId());
        assertEquals("Preview " + downloads, dVideo.getDownloadId(), dPreview.getPreviewOfDownloadId());
        assertEquals("Video URL " + downloads, video.uri, dVideo.getUri());
        assertEquals("Video " + downloads, 1, dVideo.getDownloadNumber());

        NoteForAnyAccount nfa = new NoteForAnyAccount(MyContextHolder.get(),
                activity.getId(), activity.getNote().noteId);
        assertEquals(preview.uri, nfa.downloads.getFirstForTimeline().getUri());
        assertEquals(MyContentType.IMAGE, nfa.downloads.getFirstForTimeline().getContentType());
        assertEquals(dVideo.getDownloadId(), nfa.downloads.getFirstForTimeline().getPreviewOfDownloadId());
        assertEquals(video.uri, nfa.downloads.getFirstToShare().getUri());
        assertEquals(MyContentType.VIDEO, nfa.downloads.getFirstToShare().getContentType());
    }

}
