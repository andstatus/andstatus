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

package org.andstatus.app.net.social;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.net.http.HttpConnectionData;
import org.andstatus.app.net.http.OAuthClientKeys;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.util.MyHtmlTest;
import org.andstatus.app.util.TriState;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.andstatus.app.util.MyHtmlTest.twitterBodyHtml;
import static org.andstatus.app.util.MyHtmlTest.twitterBodyToPost;
import static org.andstatus.app.util.UriUtilsTest.assertEndpoint;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class ConnectionTwitterTest {
    private Connection connection;
    private ConnectionMock mock;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithAccounts(this);

        mock = ConnectionMock.newFor(demoData.twitterTestAccountName);
        connection = mock.connection;

        HttpConnectionData data = mock.getHttp().data;
        data.oauthClientKeys = OAuthClientKeys.fromConnectionData(data);
        if (!data.oauthClientKeys.areKeysPresent()) {
            data.oauthClientKeys.setConsumerKeyAndSecret("keyForGetTimelineForTw", "thisIsASecret341232");
        }
    }

    @Test
    public void testGetTimeline() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.twitter_home_timeline);

        InputTimelinePage timeline = connection.getTimeline(true, ApiRoutineEnum.HOME_TIMELINE,
                TimelinePosition.of("380925803053449216") , TimelinePosition.EMPTY, 20,
                connection.getData().getAccountActor()).get();
        assertNotNull("timeline returned", timeline);
        int size = 4;
        assertEquals("Number of items in the Timeline", size, timeline.size());

        int ind = 0;
        AActivity activity = timeline.get(ind);
        String hostName = demoData.twitterTestHostWithoutApiDot;
        assertEquals("Posting note", AObjectType.NOTE, activity.getObjectType());
        assertEquals("Activity oid", "381172771428257792", activity.getOid());
        assertEquals("Note Oid", "381172771428257792", activity.getNote().oid);
        assertEquals("MyAccount", connection.getData().getAccountActor(), activity.accountActor);
        assertEquals("Favorited " + activity, TriState.TRUE, activity.getNote().getFavoritedBy(activity.accountActor));
        Actor author = activity.getAuthor();
        assertEquals("Oid", "221452291", author.oid);
        assertEquals("Username", "Know", author.getUsername());
        assertEquals("WebFinger ID", "know@" + hostName, author.getWebFingerId());
        assertEquals("Display name", "Just so you Know", author.getRealName());
        assertEquals("Description", "Unimportant facts you'll never need to know. Legally responsible publisher: @FUN", author.getSummary());
        assertEquals("Location", "Library of Congress", author.location);
        assertEquals("Profile URL", "https://" + hostName + "/Know", author.getProfileUrl());
        assertEquals("Homepage", "http://t.co/4TzphfU9qt", author.getHomepage());
        assertEquals("Avatar URL", "https://si0.twimg.com/profile_images/378800000411110038/a8b7eced4dc43374e7ae21112ff749b6_normal.jpeg", author.getAvatarUrl());
        assertEndpoint(ActorEndpointType.BANNER, "https://pbs.twimg.com/profile_banners/221452291/1377270845", author);
        assertEquals("Notes count", 1592, author.notesCount);
        assertEquals("Favorites count", 163, author.favoritesCount);
        assertEquals("Following (friends) count", 151, author.followingCount);
        assertEquals("Followers count", 1878136, author.followersCount);
        assertEquals("Created at", connection.parseDate("Tue Nov 30 18:17:25 +0000 2010"), author.getCreatedDate());
        assertEquals("Updated at", 0, author.getUpdatedDate());
        assertEquals("Actor is author", author.oid, activity.getActor().oid);

        ind++;
        activity = timeline.get(ind);
        Note note = activity.getNote();
        assertTrue("Note is loaded", note.getStatus() == DownloadStatus.LOADED);
        assertEquals("Should have a recipient " + activity, 1, note.audience().getNonSpecialActors().size());
        assertNotEquals("Is a Reblog " + activity, ActivityType.ANNOUNCE, activity.type);
        assertTrue("Is a reply", note.getInReplyTo().nonEmpty());
        assertEquals("Reply to the note id", "17176774678", note.getInReplyTo().getNote().oid);
        assertEquals("Reply to the note by actorOid", demoData.twitterTestAccountActorOid, note.getInReplyTo().getAuthor().oid);
        assertTrue("Reply status is unknown", note.getInReplyTo().getNote().getStatus() == DownloadStatus.UNKNOWN);
        assertEquals("Favorited by me " + activity, TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor));
        String startsWith = "@t131t";
        assertEquals("Body of this note starts with", startsWith, note.getContent().substring(0, startsWith.length()));

        ind++;
        activity = timeline.get(ind);
        note = activity.getNote();
        assertThat("Should not have non special recipients", note.audience().getNonSpecialActors(), Matchers.is(Matchers.empty()));
        assertEquals("Is not a Reblog " + activity, ActivityType.ANNOUNCE, activity.type);
        assertTrue("Is not a reply", note.getInReplyTo().isEmpty());
        assertEquals("Reblog of the note id", "315088751183409153", note.oid);
        assertEquals("Author of reblogged note oid", "442756884", activity.getAuthor().oid);
        assertEquals("Reblog id", "383295679507869696", activity.getOid());
        assertEquals("Reblogger oid", "111911542", activity.getActor().oid);
        assertEquals("Favorited by me " + activity, TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor));
        startsWith = "This AndStatus application";
        assertEquals("Body of reblogged note starts with", startsWith,
                note.getContent().substring(0, startsWith.length()));
        Date date = TestSuite.utcTime(2013, Calendar.SEPTEMBER, 26, 18, 23, 5);
        assertEquals("Reblogged at Thu Sep 26 18:23:05 +0000 2013 (" + date + ") " + activity, date,
                TestSuite.utcTime(activity.getUpdatedDate()));
        date = TestSuite.utcTime(2013, Calendar.MARCH, 22, 13, 13, 7);
        assertEquals("Reblogged note created at Fri Mar 22 13:13:07 +0000 2013 (" + date + ")" + note,
                date, TestSuite.utcTime(note.getUpdatedDate()));

        ind++;
        activity = timeline.get(ind);
        note = activity.getNote();
        assertThat("Should not have non special recipients", note.audience().getNonSpecialActors(), Matchers.is(Matchers.empty()));
        assertNotEquals("Is a Reblog " + activity, ActivityType.ANNOUNCE, activity.type);
        assertTrue("Is not a reply", note.getInReplyTo().isEmpty());
        assertEquals("Favorited by me " + activity, TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor));
        assertEquals("Author's oid is actor oid of this account",
                connection.getData().getAccountActor().oid, activity.getAuthor().oid);
        startsWith = "And this is";
        assertEquals("Body of this note starts with", startsWith, note.getContent().substring(0, startsWith.length()));
    }

    @Test
    public void getNoteWithAttachment() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.twitter_note_with_media);

        Note note = connection.getNote("503799441900314624").get().getNote();
        assertFalse("note returned", note.isEmpty());
        assertEquals("has attachment", 1, note.attachments.size());
        assertEquals("attachment",  Attachment.fromUri("https://pbs.twimg.com/media/Bv3a7EsCAAIgigY.jpg"),
                note.attachments.list.get(0));
        assertNotSame("attachment", Attachment.fromUri("https://pbs.twimg.com/media/Bv4a7EsCAAIgigY.jpg"),
                note.attachments.list.get(0));
    }

    @Test
    public void getNoteWithTwoAttachments() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.twitter_note_with_two_attachments);

        Note note = connection.getNote("1198619196260790272").get().getNote();
        assertFalse("note returned " + note, note.isEmpty());
        assertEquals("Body of this note " + note, "Test uploading two images via #AndStatus https://t.co/lJn9QBpWyn",
                note.getContent());
        assertEquals("has two attachments", 2, note.attachments.size());
        assertEquals("attachment",  Attachment.fromUri("https://pbs.twimg.com/media/EKJZzZPWoAICygS.jpg"),
                note.attachments.list.get(0));
        assertEquals("attachment",  Attachment.fromUri("https://pbs.twimg.com/media/EKJZzkYWsAELO-o.jpg"),
                note.attachments.list.get(1));
    }

    @Test
    public void getNoteWithEscapedHtmlTag() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.twitter_note_with_escaped_html_tag);

        String body = "Update: Streckensperrung zw. Berliner Tor &lt;&gt; Bergedorf. Ersatzverkehr mit Bussen und Taxis " +
                "Störungsdauer bis ca. 10 Uhr. #hvv #sbahnhh";
        AActivity activity = connection.getNote("834306097003581440").get();
        assertEquals("No note returned " + activity, AObjectType.NOTE, activity.getObjectType());
        Note note = activity.getNote();
        assertEquals("Body of this note", body, note.getContent());
        assertEquals("Body of this note", ",update,streckensperrung,zw,berliner,tor,bergedorf,ersatzverkehr,mit,bussen," +
                "und,taxis,störungsdauer,bis,ca,10,uhr,hvv,#hvv,sbahnhh,#sbahnhh,", note.getContentToSearch());

        MyAccount ma = demoData.getMyAccount(connection.getData().getAccountName().toString());
        CommandExecutionContext executionContext = new CommandExecutionContext(
                myContextHolder.getNow(), CommandData.newAccountCommand(CommandEnum.GET_NOTE, ma));
        new DataUpdater(executionContext).onActivity(activity);
        assertNotEquals("Note was not added " + activity, 0, note.noteId);
        assertNotEquals("Activity was not added " + activity, 0, activity.getId());
    }

    @Test
    public void getNoteWithEscapedChars() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.twitter_note_with_escaped_chars);

        String contentToSearch = ",testing,if,and,what,is,escaped,in,a,tweet," +
                "1,less-than,sign,and,escaped,&lt," +
                "2,greater-than,sign,and,escaped,&gt," +
                "3,ampersand,&,and,escaped,&amp," +
                "4,apostrophe," +
                "5,br,html,tag,br,/,and,without,/,br,";

        AActivity activity = connection.getNote("1070738478198071296").get();
        assertEquals("No note returned " + activity, AObjectType.NOTE, activity.getObjectType());
        Note note = activity.getNote();
        assertEquals("Body of this note", twitterBodyHtml, note.getContent());
        assertEquals("Body to post is wrong. Try to type:\n" + MyHtmlTest.twitterBodyTypedPlain + "\n",
                twitterBodyToPost, note.getContentToPost());
        assertEquals("Content to Search of this note", contentToSearch, note.getContentToSearch());

        MyAccount ma = demoData.getMyAccount(connection.getData().getAccountName().toString());
        CommandExecutionContext executionContext = new CommandExecutionContext(
                myContextHolder.getNow(), CommandData.newAccountCommand(CommandEnum.GET_NOTE, ma));
        new DataUpdater(executionContext).onActivity(activity);
        assertNotEquals("Note was not added " + activity, 0, note.noteId);
        assertNotEquals("Activity was not added " + activity, 0, activity.getId());
    }

    @Test
    public void follow() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.twitter_follow);

        String actorOid = "96340134";
        AActivity activity = connection.follow(actorOid, true).get();
        assertEquals("No actor returned " + activity, AObjectType.ACTOR, activity.getObjectType());
        Actor friend = activity.getObjActor();
        assertEquals("Wrong username returned " + activity, "LPirro93", friend.getUsername());

        MyAccount ma = demoData.getMyAccount(connection.getData().getAccountName().toString());
        Actor friend2 = Actor.fromId(ma.getOrigin(), 123);
        CommandExecutionContext executionContext = new CommandExecutionContext(
                myContextHolder.getNow(), CommandData.actOnActorCommand(CommandEnum.FOLLOW, ma, friend2, ""));
        new DataUpdater(executionContext).onActivity(activity);

        long friendId = MyQuery.oidToId(myContextHolder.getNow(), OidEnum.ACTOR_OID, ma.getOriginId(), actorOid);

        assertNotEquals("Followed Actor was not added " + activity, 0, friendId);
        assertNotEquals("Activity was not added " + activity, 0, activity.getId());
    }


}
