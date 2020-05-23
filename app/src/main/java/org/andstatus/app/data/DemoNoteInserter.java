/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

import androidx.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.actor.GroupType;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpointType;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.Visibility;
import org.andstatus.app.net.social.pumpio.ConnectionPumpio;
import org.andstatus.app.note.NoteEditorData;
import org.andstatus.app.notification.NotificationEventType;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;

import java.net.URL;
import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DemoNoteInserter {
    public final Actor accountActor;
    private final Origin origin;

    public DemoNoteInserter(MyAccount ma) {
        this(ma.getActor());
    }

    public DemoNoteInserter(Actor accountActor) {
        this.accountActor = accountActor;
        assertTrue(accountActor != null);
        origin = accountActor.origin;
        assertTrue("Origin exists for " + accountActor, origin.isValid());
    }

    public Actor buildActor() {
        return buildActorFromOid(nextActorUid());
    }

    final Actor buildActorFromOid(String actorOid) {
        if (StringUtil.isEmpty(actorOid)) throw  new IllegalArgumentException("Actor oid cannot be empty");
        Actor actor = Actor.fromOid(origin, actorOid);
        String username;
        String profileUrl;
        if (origin.getOriginType() == OriginType.PUMPIO) {
            ConnectionPumpio connection = new ConnectionPumpio();
            username = connection.actorOidToUsername(actorOid);
            profileUrl = "http://" + connection.actorOidToHost(actorOid) + "/" + username;
            actor.setCreatedDate(MyLog.uniqueCurrentTimeMS());
        } else {
            username = "actorOf" + origin.getName() + actorOid;
            profileUrl = "https://" + demoData.gnusocialTestOriginName
                    + ".example.com/profiles/" + username;
            actor.setUpdatedDate(MyLog.uniqueCurrentTimeMS());
        }
        actor.setUsername(username);
        actor.setProfileUrl(profileUrl);
        actor.setRealName("Real " + username);
        actor.setSummary("This is about " + username);
        actor.setHomepage("https://example.com/home/" + username + "/start/");
        actor.location = "Faraway place #" + demoData.testRunUid;
        actor.setAvatarUrl(actor.getHomepage() + "avatar.jpg");
        actor.endpoints.add(ActorEndpointType.BANNER, actor.getHomepage() + "banner.png");
        long rand = InstanceId.next();
        actor.notesCount = rand * 2 + 3;
        actor.favoritesCount = rand + 11;
        actor.followingCount = rand + 17;
        actor.followersCount = rand;
        return actor.build();
    }

    private String nextActorUid() {
        if (origin.getOriginType() == OriginType.PUMPIO) {
            return "acct:actorOf" + origin.getName() + demoData.testRunUid + InstanceId.next();
        }
        return String.valueOf(demoData.testRunUid) + InstanceId.next();
    }

    public AActivity buildActivity(Actor author, String name, String content, AActivity inReplyToActivity,
                                   String noteOidIn, DownloadStatus noteStatus) {
        final String method = "buildActivity";
        String noteOid = noteOidIn;
        if (StringUtil.isEmpty(noteOid) && noteStatus != DownloadStatus.SENDING) {
            if (origin.getOriginType() == OriginType.PUMPIO) {
                noteOid = (UrlUtils.hasHost(UrlUtils.fromString(author.getProfileUrl()))
                          ? author.getProfileUrl()
                          : "http://pumpiotest" + origin.getId() + ".example.com/actor/" + author.oid)
                        + "/" + (inReplyToActivity == null ? "note" : "comment")
                        + "/thisisfakeuri" + System.nanoTime();
            } else {
                noteOid = MyLog.uniqueDateTimeFormatted();
            }
        }
        AActivity activity = buildActivity(author, ActivityType.UPDATE, noteOid);
        Note note = Note.fromOriginAndOid(origin, noteOid, noteStatus);
        activity.setNote(note);
        note.setUpdatedDate(activity.getUpdatedDate());
        note.setName(name);
        note.setContentPosted(content);
        note.via = "AndStatus";
        long rand = InstanceId.next();
        note.setLikesCount(rand - 15);
        note.setReblogsCount(rand - 3);
        note.setRepliesCount(rand + 12);
        note.setInReplyTo(inReplyToActivity);
        if (origin.getOriginType() == OriginType.PUMPIO) {
            note.url = note.oid;
        }
        activity.initializePublicAndFollowers();
        DbUtils.waitMs(method, 10);
        return activity;
    }

    public AActivity buildActivity(@NonNull Actor actor, @NonNull ActivityType type, String noteOid) {
        AActivity activity = AActivity.from(accountActor, type);
        activity.setOid(
                (StringUtil.isEmpty(noteOid) ?  MyLog.uniqueDateTimeFormatted() : noteOid)
                + "-" + activity.type.name().toLowerCase());
        activity.setActor(actor);
        activity.setUpdatedDate(System.currentTimeMillis());
        return activity;
    }

    static void onActivityS(AActivity activity) {
        new DemoNoteInserter(activity.accountActor).onActivity(activity);
    }

    static AActivity increaseUpdateDate(AActivity activity) {
        // In order for a note not to be ignored
        activity.setUpdatedDate(activity.getUpdatedDate() + 1);
        activity.getNote().setUpdatedDate(activity.getNote().getUpdatedDate() + 1);
        return activity;
    }

    public void onActivity(final AActivity activity) {
        NoteEditorData.recreateKnownAudience(activity);

        MyAccount ma = origin.myContext.accounts().fromActorId(accountActor.actorId);
        assertTrue("Persistent account exists for " + accountActor + " " + activity, ma.isValid());
        final TimelineType timelineType = activity.getNote().audience().getVisibility().isPrivate() ? TimelineType.PRIVATE : TimelineType.HOME;
        CommandExecutionContext execContext = new CommandExecutionContext(origin.myContext,
                CommandData.newTimelineCommand(CommandEnum.EMPTY, ma, timelineType));
        activity.audience().assertContext();
        new DataUpdater(execContext).onActivity(activity);
        checkActivityRecursively(activity, 1);
    }

    private void checkActivityRecursively(AActivity activity, int level) {
        Note note = activity.getNote();
        if (level == 1 && note.nonEmpty()) {
            assertNotEquals( "Activity was not added: " + activity, 0, activity.getId());
        }
        if (level > DataUpdater.MAX_RECURSING || activity.getId() == 0) return;

        assertNotEquals( "Account is unknown: " + activity, 0, activity.accountActor.actorId);
        Actor actor = activity.getActor();
        if (actor.nonEmpty()) {
            assertNotEquals( "Level " + level + ", Actor id not set for " + actor + " in " + activity, 0, actor.actorId);
            assertNotEquals( "Level " + level + ", User id not set for " + actor + " in " + activity, 0, actor.user.userId);
        }
        checkStoredActor(actor);

        if (note.nonEmpty()) {
            assertNotEquals( "Note was not added at level " + level + " " + activity, 0, note.noteId);

            String permalink = origin.getNotePermalink(note.noteId);
            URL urlPermalink = UrlUtils.fromString(permalink);
            assertNotNull("Note permalink is a valid URL '" + permalink + "',\n" + note.toString()
                    + "\n origin: " + origin
                    + "\n author: " + activity.getAuthor().toString(), urlPermalink);
            if (origin.getUrl() != null && origin.getOriginType() != OriginType.TWITTER) {
                assertEquals("Note permalink has the same host as origin, " + note.toString(),
                        origin.getUrl().getHost(), urlPermalink.getHost());
            }
            if (StringUtil.nonEmpty(note.getName())) {
                assertEquals("Note name " + activity, note.getName(),
                        MyQuery.noteIdToStringColumnValue(NoteTable.NAME, note.noteId));
            }
            if (StringUtil.nonEmpty(note.getSummary())) {
                assertEquals("Note summary " + activity, note.getSummary(),
                        MyQuery.noteIdToStringColumnValue(NoteTable.SUMMARY, note.noteId));
            }
            if (StringUtil.nonEmpty(note.getContent())) {
                assertEquals("Note content " + activity, note.getContent(),
                        MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, note.noteId));
            }
            if (StringUtil.nonEmpty(note.url)) {
                assertEquals("Note permalink", note.url, origin.getNotePermalink(note.noteId));
            }

            Actor author = activity.getAuthor();
            if (author.nonEmpty()) {
                assertNotEquals( "Author id for " + author + " not set in note " + note + " in " + activity, 0,
                        MyQuery.noteIdToActorId(NoteTable.AUTHOR_ID, note.noteId));
            }
            checkStoredActor(author);
        }

        switch (activity.type) {
            case LIKE:
                List<Actor> stargazers = MyQuery.getStargazers(origin.myContext.getDatabase(), accountActor.origin, note.noteId);
                boolean found = stargazers.stream().anyMatch(stargazer -> stargazer.actorId == actor.actorId);
                assertTrue("Actor, who favorited, is not found among stargazers: " + activity
                        + "\nstargazers: " + stargazers, found);
                break;
            case ANNOUNCE:
                List<Actor> rebloggers = MyQuery.getRebloggers(origin.myContext.getDatabase(), accountActor.origin, note.noteId);
                assertTrue("Reblogger is not found among rebloggers: " + activity
                        + "\nrebloggers: " + rebloggers, rebloggers.stream().anyMatch(a -> a.actorId == actor.actorId));
                break;
            case FOLLOW:
                assertTrue("Friend not found: " + activity,
                        GroupMembership.isGroupMember(actor, GroupType.FRIENDS, activity.getObjActor().actorId));
                break;
            case UNDO_FOLLOW:
                assertFalse("Friend found: " + activity,
                        GroupMembership.isGroupMember(actor, GroupType.FRIENDS, activity.getObjActor().actorId));
                break;
            default:
                break;
        }

        if (!note.replies.isEmpty()) {
            for (AActivity replyActivity : note.replies) {
                if (replyActivity.nonEmpty()) {
                    assertNotEquals("Reply added at level " + level + " " + replyActivity, 0, replyActivity.getId());
                    checkActivityRecursively(replyActivity, level + 1);
                }
            }
        }
        note.audience().getActorsToSave(activity.getAuthor()).forEach(DemoNoteInserter::checkStoredActor);

        if (activity.getObjActor().nonEmpty()) {
            assertNotEquals( "Actor was not added: " + activity.getObjActor(), 0, activity.getObjActor().actorId);
        }
        if (activity.getActivity().nonEmpty()) {
            checkActivityRecursively(activity.getActivity(), level + 1);
        }
    }

    public static void checkStoredActor(Actor actor) {
        if (actor.dontStore()) return;

        long id = actor.actorId;

        if (StringUtil.nonEmpty(actor.oid)) {
            assertEquals("oid " + actor, actor.oid,
                    MyQuery.actorIdToStringColumnValue(ActorTable.ACTOR_OID, id));
        }

        if (!actor.getUsername().isEmpty()) {
            assertEquals("Username " + actor, actor.getUsername(),
                    MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, id));
        }

        String webFingerIdActual = MyQuery.actorIdToStringColumnValue(ActorTable.WEBFINGER_ID, id);
        if (actor.getWebFingerId().isEmpty()) {
            assertTrue("WebFingerID=" + webFingerIdActual + " for " + actor, StringUtil.isEmpty(webFingerIdActual)
                    || Actor.isWebFingerIdValid(webFingerIdActual));
        } else {
            assertEquals("WebFingerID=" + webFingerIdActual + " for " + actor, actor.getWebFingerId(), webFingerIdActual);
            assertTrue("Invalid WebFingerID " + actor, Actor.isWebFingerIdValid(webFingerIdActual));
        }

        if (StringUtil.nonEmpty(actor.getRealName())) {
            assertEquals("Display name " + actor, actor.getRealName(),
                    MyQuery.actorIdToStringColumnValue(ActorTable.REAL_NAME, id));
        }
    }

    static void deleteOldNote(@NonNull Origin origin, String noteOid) {
        long noteIdOld = MyQuery.oidToId(OidEnum.NOTE_OID, origin.getId(), noteOid);
        if (noteIdOld != 0) {
            int deleted = MyProvider.deleteNoteAndItsActivities(origin.myContext, noteIdOld);
            assertTrue( "Activities of Old note id=" + noteIdOld + " deleted: " + deleted, deleted > 0);
        }
    }
    
    public static AActivity addNoteForAccount(MyAccount ma, String body, String noteOid, DownloadStatus noteStatus) {
        assertTrue("Is not valid: " + ma, ma.isValid());
        Actor accountActor = ma.getActor();
        DemoNoteInserter mi = new DemoNoteInserter(accountActor);
        AActivity activity = mi.buildActivity(accountActor, "", body, null, noteOid, noteStatus);
        mi.onActivity(activity);
        return activity;
    }

    public static void assertInteraction(AActivity activity, NotificationEventType eventType, TriState notified) {
        assertEquals("Notification event type\n" + activity + "\n",
                eventType,
                NotificationEventType.fromId(
                        MyQuery.activityIdToLongColumnValue(ActivityTable.INTERACTION_EVENT, activity.getId())));

        assertEquals("Interacted TriState\n" + activity + "\n",
                TriState.fromBoolean(eventType != NotificationEventType.EMPTY &&
                        eventType != NotificationEventType.HOME),
                MyQuery.activityIdToTriState(ActivityTable.INTERACTED, activity.getId()));

        final long notifiedActorId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTIFIED_ACTOR_ID, activity.getId());
        final String message = "Notified actor ID\n" + activity + "\n";
        if (eventType == NotificationEventType.EMPTY) {
            assertEquals(message, 0, notifiedActorId);
        } else {
            assertNotEquals(message, 0, notifiedActorId);
        }

        if (notified.known) {
            assertEquals("Notified TriState\n"
                            + activity + "\n",
                    notified,
                    MyQuery.activityIdToTriState(ActivityTable.NOTIFIED, activity.getId()));
        }
    }

    public static void assertStoredVisibility(AActivity activity, Visibility expected) {
        assertEquals("Visibility of\n" + activity + "\n",
                expected, Visibility.fromNoteId(activity.getNote().noteId));
    }

    public static void assertVisibility(Audience audience, Visibility visibility) {
        assertEquals("Visibility check " + audience + "\n", visibility, audience.getVisibility());
    }
}
