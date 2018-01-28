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

import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.pumpio.ConnectionPumpio;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;

import java.net.URL;
import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
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

    public Actor buildUser() {
        if (origin.getOriginType() == OriginType.PUMPIO) {
            return buildActorFromOid("acct:userOf" + origin.getName() + demoData.TESTRUN_UID);
        }
        return buildActorFromOid(demoData.TESTRUN_UID);
    }

    public Actor buildActorFromOidAndAvatar(String actorOid, String avatarUrlString) {
        Actor actor = buildActorFromOid(actorOid);
        actor.avatarUrl = avatarUrlString;
        return actor;
    }
    
    final Actor buildActorFromOid(String actorOid) {
        Actor actor = Actor.fromOriginAndActorOid(origin, actorOid);
        String username;
        String profileUrl;
        if (origin.getOriginType() == OriginType.PUMPIO) {
            ConnectionPumpio connection = new ConnectionPumpio();
            username = connection.actorOidToUsername(actorOid);
            profileUrl = "http://" + connection.usernameToHost(username) + "/"
                    + connection.usernameToNickname(username);
        } else {
            username = "userOf" + origin.getName() + actorOid;
            profileUrl = "https://" + demoData.GNUSOCIAL_TEST_ORIGIN_NAME
                    + ".example.com/profiles/" + username;
        }
        actor.setUsername(username);
        actor.setProfileUrl(profileUrl);
        actor.setRealName("Real " + username);
        actor.setDescription("This is about " + username);
        actor.setHomepage("https://example.com/home/" + username + "/start/");
        actor.location = "Faraway place #" + demoData.TESTRUN_UID;
        actor.avatarUrl = actor.getHomepage() + "avatar.jpg";
        actor.bannerUrl = actor.getHomepage() + "banner.png";
        long rand = InstanceId.next();
        actor.notesCount = rand * 2 + 3;
        actor.favoritesCount = rand + 11;
        actor.followingCount = rand + 17;
        actor.followersCount = rand;
        return actor;
    }

    public AActivity buildActivity(Actor author, String body, AActivity inReplyToActivity, String noteOidIn,
                                   DownloadStatus noteStatus) {
        final String method = "buildActivity";
        String noteOid = noteOidIn;
        if (TextUtils.isEmpty(noteOid) && noteStatus != DownloadStatus.SENDING) {
            if (origin.getOriginType() == OriginType.PUMPIO) {
                noteOid = (UrlUtils.hasHost(UrlUtils.fromString(author.getProfileUrl()))
                          ? author.getProfileUrl()
                          : "http://pumpiotest" + origin.getId() + ".example.com/user/" + author.oid)
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
        note.setBody(body);
        note.via = "AndStatus";
        note.setInReplyTo(inReplyToActivity);
        if (origin.getOriginType() == OriginType.PUMPIO) {
            note.url = note.oid;
        }
        DbUtils.waitMs(method, 10);
        return activity;
    }

    public AActivity buildActivity(@NonNull Actor actor, @NonNull ActivityType type, String noteOid) {
        AActivity activity = AActivity.from(accountActor, type);
        activity.setTimelinePosition(
                (TextUtils.isEmpty(noteOid) ?  MyLog.uniqueDateTimeFormatted() : noteOid)
                + "-" + activity.type.name().toLowerCase());
        activity.setActor(actor);
        activity.setUpdatedDate(System.currentTimeMillis());
        return activity;
    }

    static void onActivityS(AActivity activity) {
        new DemoNoteInserter(activity.accountActor).onActivity(activity);
    }

    static void increaseUpdateDate(AActivity activity) {
        // In order for a note not to be ignored
        activity.setUpdatedDate(activity.getUpdatedDate() + 1);
        activity.getNote().setUpdatedDate(activity.getNote().getUpdatedDate() + 1);
    }

    public void onActivity(final AActivity activity) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromActorId(accountActor.actorId);
        assertTrue("Persistent account exists for " + accountActor + " " + activity, ma.isValid());
        final TimelineType timelineType = activity.getNote().isPrivate() ? TimelineType.PRIVATE : TimelineType.HOME;
        DataUpdater di = new DataUpdater(new CommandExecutionContext(
                        CommandData.newTimelineCommand(CommandEnum.EMPTY, ma, timelineType)));
        di.onActivity(activity);
        checkActivityRecursively(activity, 1);
    }

    private void checkActivityRecursively(AActivity activity, int level) {
        if (level == 1) {
            assertNotEquals( "Activity was not added: " + activity, 0, activity.getId());
        }
        if (level > 10 || activity.getId() == 0) {
            return;
        }
        assertNotEquals( "Account is unknown: " + activity, 0, activity.accountActor.actorId);

        Actor actor = activity.getActor();
        if (actor.nonEmpty()) {
            assertNotEquals( "Actor id not set for " + actor + " in activity " + activity, 0, actor.actorId);
        }

        Note note = activity.getNote();
        if (note.nonEmpty()) {
            assertNotEquals( "Note was not added at level " + level + " " + activity, 0, note.noteId);

            String permalink = origin.notePermalink(note.noteId);
            URL urlPermalink = UrlUtils.fromString(permalink);
            assertNotNull("Note permalink is a valid URL '" + permalink + "',\n" + note.toString()
                    + "\n origin: " + origin
                    + "\n author: " + activity.getAuthor().toString(), urlPermalink);
            if (origin.getUrl() != null && origin.getOriginType() != OriginType.TWITTER) {
                assertEquals("Note permalink has the same host as origin, " + note.toString(),
                        origin.getUrl().getHost(), urlPermalink.getHost());
            }
            if (!TextUtils.isEmpty(note.url)) {
                assertEquals("Note permalink", note.url, origin.notePermalink(note.noteId));
            }

            Actor author = activity.getAuthor();
            if (author.nonEmpty()) {
                assertNotEquals( "Author id for " + author + " not set in note " + note + " in activity " + activity, 0,
                        MyQuery.noteIdToActorId(NoteTable.AUTHOR_ID, note.noteId));
            }
        }

        if (activity.type == ActivityType.LIKE) {
            List<Actor> stargazers = MyQuery.getStargazers(MyContextHolder.get().getDatabase(), accountActor.origin, note.noteId);
            boolean found = false;
            for (Actor stargazer : stargazers) {
                if (stargazer.actorId == actor.actorId) {
                    found = true;
                    break;
                }
            }
            assertTrue("Actor, who favorited, is not found among stargazers: " + activity
                    + "\nstargazers: " + stargazers, found);
        }

        if (activity.type == ActivityType.ANNOUNCE) {
            List<Actor> rebloggers = MyQuery.getRebloggers(MyContextHolder.get().getDatabase(), accountActor.origin, note.noteId);
            boolean found = false;
            for (Actor stargazer : rebloggers) {
                if (stargazer.actorId == actor.actorId) {
                    found = true;
                    break;
                }
            }
            assertTrue("Reblogger is not found among rebloggers: " + activity
                    + "\nrebloggers: " + rebloggers, found);
        }

        if (!note.replies.isEmpty()) {
            for (AActivity replyActivity : note.replies) {
                if (replyActivity.nonEmpty()) {
                    assertNotEquals("Reply added at level " + level + " " + replyActivity, 0, replyActivity.getId());
                    checkActivityRecursively(replyActivity, level + 1);
                }
            }
        }

        if (activity.getObjActor().nonEmpty()) {
            assertNotEquals( "Actor was not added: " + activity.getObjActor(), 0, activity.getObjActor().actorId);
        }
        if (activity.getActivity().nonEmpty()) {
            checkActivityRecursively(activity.getActivity(), level + 1);
        }
    }

    static void deleteOldNote(@NonNull Origin origin, String messageOid) {
        long noteIdOld = MyQuery.oidToId(OidEnum.NOTE_OID, origin.getId(), messageOid);
        if (noteIdOld != 0) {
            int deleted = MyProvider.deleteNote(MyContextHolder.get().context(), noteIdOld);
            assertTrue( "Activities of Old note id=" + noteIdOld + " deleted: " + deleted, deleted > 0);
        }
    }
    
    public static AActivity addNoteForAccount(MyAccount ma, String body, String noteOid, DownloadStatus noteStatus) {
        assertTrue("Is not valid: " + ma, ma.isValid());
        Actor accountActor = ma.getActor();
        DemoNoteInserter mi = new DemoNoteInserter(accountActor);
        AActivity activity = mi.buildActivity(accountActor, body, null, noteOid, noteStatus);
        mi.onActivity(activity);
        return activity;
    }

    public static void assertNotified(AActivity activity, TriState notified) {
        assertEquals("Should" + (notified == TriState.FALSE ? " not" : "") + " be notified " + activity,
                notified,
                MyQuery.activityIdToTriState(ActivityTable.NOTIFIED, activity.getId()));
    }
}
