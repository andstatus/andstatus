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
package org.andstatus.app.data

import org.andstatus.app.account.MyAccount
import org.andstatus.app.actor.GroupType
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.net.social.Audience
import org.andstatus.app.net.social.Note
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.net.social.pumpio.ConnectionPumpio
import org.andstatus.app.note.NoteEditorData
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginType
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.CommandExecutionContext
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UrlUtils
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class DemoNoteInserter(val accountActor: Actor) {
    private val origin: Origin = accountActor.origin

    constructor(ma: MyAccount) : this(ma.actor) {}

    fun buildActor(): Actor {
        return buildActorFromOid(nextActorUid())
    }

    fun buildActorFromOid(actorOid: String): Actor {
        require(actorOid.isNotEmpty()) { "Actor oid cannot be empty" }
        val actor: Actor = Actor.fromOid(origin, actorOid)
        val username: String
        val profileUrl: String
        if (origin.originType === OriginType.PUMPIO) {
            val connection = ConnectionPumpio()
            username = connection.actorOidToUsername(actorOid)
            profileUrl = "http://" + connection.actorOidToHost(actorOid) + "/" + username
            actor.setCreatedDate(MyLog.uniqueCurrentTimeMS)
        } else {
            username = "actorOf" + origin.name + actorOid
            profileUrl = ("https://" + DemoData.demoData.gnusocialTestOriginName
                + ".example.com/profiles/" + username)
            actor.setUpdatedDate(MyLog.uniqueCurrentTimeMS)
        }
        actor.setUsername(username)
        actor.setProfileUrl(profileUrl)
        actor.setRealName("Real $username")
        actor.setSummary("This is about $username")
        actor.setHomepage("https://example.com/home/$username/start/")
        actor.location = "Faraway place #" + DemoData.demoData.testRunUid
        actor.setAvatarUrl(actor.getHomepage() + "avatar.jpg")
        actor.endpoints.add(ActorEndpointType.BANNER, actor.getHomepage() + "banner.png")
        val rand = InstanceId.next()
        actor.notesCount = rand * 2 + 3
        actor.favoritesCount = rand + 11
        actor.followingCount = rand + 17
        actor.followersCount = rand
        return actor.build()
    }

    private fun nextActorUid(): String {
        return if (origin.originType === OriginType.PUMPIO) {
            "acct:actorOf" + origin.name + DemoData.demoData.testRunUid + InstanceId.next()
        } else DemoData.demoData.testRunUid + InstanceId.next()
    }

    fun buildActivity(
        author: Actor, name: String?, content: String?, inReplyToActivity: AActivity?,
        noteOidIn: String?, noteStatus: DownloadStatus
    ): AActivity {
        val method = "buildActivity"
        var noteOid = noteOidIn
        if (noteOid.isNullOrEmpty() && noteStatus != DownloadStatus.SENDING) {
            noteOid = if (origin.originType === OriginType.PUMPIO) {
                ((if (UrlUtils.hasHost(UrlUtils.fromString(author.getProfileUrl()))) author.getProfileUrl()
                else "http://pumpiotest" + origin.id + ".example.com/actor/" + author.oid)
                    + "/" + (if (inReplyToActivity == null) "note" else "comment")
                    + "/thisisfakeuri" + System.nanoTime())
            } else {
                MyLog.uniqueDateTimeFormatted()
            }
        }
        val activity = buildActivity(author, ActivityType.UPDATE, noteOid)
        val note: Note = Note.fromOriginAndOid(origin, noteOid, noteStatus)
        activity.setNote(note)
        note.updatedDate = activity.getUpdatedDate()
        note.setName(name)
        note.setContentPosted(content)
        note.via = "AndStatus"
        val rand = InstanceId.next()
        note.setLikesCount(rand - 15)
        note.setReblogsCount(rand - 3)
        note.setRepliesCount(rand + 12)
        note.setInReplyTo(inReplyToActivity)
        if (origin.originType === OriginType.PUMPIO) {
            note.url = note.oid
        }
        activity.initializePublicAndFollowers()
        DbUtils.waitMs(method, 10)
        return activity
    }

    fun buildActivity(actor: Actor, type: ActivityType, noteOid: String?): AActivity {
        val activity: AActivity = AActivity.from(accountActor, type)
        activity.setOid(
            ((if (noteOid.isNullOrEmpty()) MyLog.uniqueDateTimeFormatted() else noteOid))
                + "-" + activity.type.name.toLowerCase()
        )
        activity.setActor(actor)
        activity.setUpdatedDate(System.currentTimeMillis())
        return activity
    }

    fun onActivity(activity: AActivity) {
        NoteEditorData.recreateKnownAudience(activity)
        val ma = origin.myContext.accounts.fromActorId(accountActor.actorId)
        assertTrue("Persistent account exists for $accountActor $activity", ma.isValid)
        val timelineType =
            if (activity.getNote().audience().visibility.isPrivate) TimelineType.PRIVATE else TimelineType.HOME
        val execContext = CommandExecutionContext(
            origin.myContext,
            CommandData.newTimelineCommand(CommandEnum.EMPTY, ma, timelineType)
        )
        activity.audience().assertContext()
        DataUpdater(execContext).onActivity(activity)
        checkActivityRecursively(activity, 1)
    }

    private fun checkActivityRecursively(activity: AActivity, level: Int) {
        val note = activity.getNote()
        if (level == 1 && note.nonEmpty) {
            Assert.assertNotEquals("Activity was not added: $activity", 0, activity.id)
        }
        if (level > DataUpdater.MAX_RECURSING || activity.id == 0L) return
        Assert.assertNotEquals("Account is unknown: $activity", 0, activity.accountActor.actorId)
        val actor = activity.getActor()
        if (actor.nonEmpty) {
            Assert.assertNotEquals("Level $level, Actor id not set for $actor in $activity", 0, actor.actorId)
            Assert.assertNotEquals("Level $level, User id not set for $actor in $activity", 0, actor.user.userId)
        }
        checkStoredActor(actor)
        if (note.nonEmpty) {
            Assert.assertNotEquals("Note was not added at level $level $activity", 0, note.noteId)
            val permalink = origin.getNotePermalink(note.noteId)
            val urlPermalink = UrlUtils.fromString(permalink)
            Assert.assertNotNull(
                "Note permalink is a valid URL '$permalink', " +
                    "$note origin: $origin author: ${activity.getAuthor()}", urlPermalink
            )
            origin.url?.takeIf { origin.originType !== OriginType.TWITTER }?.let { url ->
                assertEquals(
                    "Note permalink has the same host as origin, $note",
                    url.host, urlPermalink?.host
                )
            }
            if (note.getName().isNotEmpty()) {
                assertEquals(
                    "Note name $activity", note.getName(),
                    MyQuery.noteIdToStringColumnValue(NoteTable.NAME, note.noteId)
                )
            }
            if (note.summary.isNotEmpty()) {
                assertEquals(
                    "Note summary $activity", note.summary,
                    MyQuery.noteIdToStringColumnValue(NoteTable.SUMMARY, note.noteId)
                )
            }
            if (note.content.isNotEmpty()) {
                assertEquals(
                    "Note content $activity", note.content,
                    MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, note.noteId)
                )
            }
            if (note.url.isNotEmpty()) {
                assertEquals("Note permalink", note.url, origin.getNotePermalink(note.noteId))
            }
            val author = activity.getAuthor()
            if (author.nonEmpty) {
                Assert.assertNotEquals(
                    "Author id for $author not set in note $note in $activity", 0,
                    MyQuery.noteIdToActorId(NoteTable.AUTHOR_ID, note.noteId)
                )
            }
            checkStoredActor(author)
        }
        when (activity.type) {
            ActivityType.LIKE -> {
                val stargazers = MyQuery.getStargazers(origin.myContext.database, accountActor.origin, note.noteId)
                val found = stargazers.stream().anyMatch { stargazer: Actor -> stargazer.actorId == actor.actorId }
                assertTrue(
                    "Actor, who favorited, is not found among stargazers: $activity" +
                        "\nstargazers: $stargazers", found
                )
            }
            ActivityType.ANNOUNCE -> {
                val rebloggers = MyQuery.getRebloggers(origin.myContext.database, accountActor.origin, note.noteId)
                assertTrue("Reblogger is not found among rebloggers: $activity rebloggers: $rebloggers",
                    rebloggers.stream().anyMatch { a: Actor -> a.actorId == actor.actorId })
            }
            ActivityType.FOLLOW -> assertTrue(
                "Friend not found: $activity",
                GroupMembership.isSingleGroupMember(actor, GroupType.FRIENDS, activity.getObjActor().actorId)
            )
            ActivityType.UNDO_FOLLOW -> Assert.assertFalse(
                "Friend found: $activity",
                GroupMembership.isSingleGroupMember(actor, GroupType.FRIENDS, activity.getObjActor().actorId)
            )
            else -> {
            }
        }
        if (note.replies.isNotEmpty()) {
            for (replyActivity in note.replies) {
                if (replyActivity.nonEmpty) {
                    Assert.assertNotEquals("Reply added at level $level $replyActivity", 0, replyActivity.id)
                    checkActivityRecursively(replyActivity, level + 1)
                }
            }
        }
        note.audience().evaluateAndGetActorsToSave(activity.getAuthor())
            .forEach(::checkStoredActor)
        if (activity.getObjActor().nonEmpty) {
            Assert.assertNotEquals("Actor was not added: " + activity.getObjActor(), 0, activity.getObjActor().actorId)
        }
        if (activity.getActivity().nonEmpty) {
            checkActivityRecursively(activity.getActivity(), level + 1)
        }
    }

    companion object {
        fun onActivityS(activity: AActivity) {
            DemoNoteInserter(activity.accountActor).onActivity(activity)
        }

        fun increaseUpdateDate(activity: AActivity): AActivity {
            // In order for a note not to be ignored
            activity.setUpdatedDate(activity.getUpdatedDate() + 1)
            activity.getNote().updatedDate = activity.getNote().updatedDate + 1
            return activity
        }

        fun checkStoredActor(actor: Actor) {
            if (actor.dontStore()) return
            if (actor.oid.isNotEmpty()) {
                assertEquals(
                    "oid $actor", actor.oid,
                    MyQuery.actorIdToStringColumnValue(ActorTable.ACTOR_OID, actor.actorId)
                )
            }
            if (actor.getUsername().isNotEmpty()) {
                assertEquals(
                    "Username $actor", actor.getUsername(),
                    MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actor.actorId)
                )
            }
            val webFingerIdActual = MyQuery.actorIdToStringColumnValue(ActorTable.WEBFINGER_ID, actor.actorId)
            if (actor.getWebFingerId().isEmpty()) {
                assertTrue(
                    "WebFingerID=$webFingerIdActual for $actor", webFingerIdActual.isEmpty()
                        || Actor.isWebFingerIdValid(webFingerIdActual)
                )
            } else {
                assertEquals("WebFingerID=$webFingerIdActual for $actor", actor.getWebFingerId(), webFingerIdActual)
                assertTrue("Invalid WebFingerID $actor", Actor.isWebFingerIdValid(webFingerIdActual))
            }
            if (actor.getRealName().isNotEmpty()) {
                assertEquals(
                    "Display name $actor", actor.getRealName(),
                    MyQuery.actorIdToStringColumnValue(ActorTable.REAL_NAME, actor.actorId)
                )
            }
            if (actor.groupType.hasParentActor) {
                assertTrue("Should have parent $actor", actor.getParentActorId() != 0L)
                assertEquals(
                    "Parent of $actor", actor.getParentActorId(),
                    MyQuery.actorIdToLongColumnValue(ActorTable.PARENT_ACTOR_ID, actor.actorId)
                )
            }
        }

        fun deleteOldNote(origin: Origin, noteOid: String?) {
            val noteIdOld = MyQuery.oidToId(OidEnum.NOTE_OID, origin.id, noteOid)
            if (noteIdOld != 0L) {
                val deleted: Int = MyProvider.deleteNoteAndItsActivities(origin.myContext, noteIdOld)
                assertTrue("Activities of Old note id=$noteIdOld deleted: $deleted", deleted > 0)
            }
        }

        fun addNoteForAccount(ma: MyAccount, body: String?, noteOid: String?, noteStatus: DownloadStatus): AActivity {
            assertTrue("Is not valid: $ma", ma.isValid)
            val accountActor = ma.actor
            val mi = DemoNoteInserter(accountActor)
            val activity = mi.buildActivity(accountActor, "", body, null, noteOid, noteStatus)
            mi.onActivity(activity)
            return activity
        }

        fun assertInteraction(activity: AActivity, eventType: NotificationEventType, notified: TriState) {
            assertEquals(
                "Notification event type\n$activity\n",
                eventType,
                NotificationEventType.fromId(
                    MyQuery.activityIdToLongColumnValue(ActivityTable.INTERACTION_EVENT, activity.id)
                )
            )
            assertEquals(
                "Interacted TriState\n$activity\n",
                TriState.fromBoolean(
                    eventType != NotificationEventType.EMPTY &&
                        eventType != NotificationEventType.HOME
                ),
                MyQuery.activityIdToTriState(ActivityTable.INTERACTED, activity.id)
            )
            val notifiedActorId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTIFIED_ACTOR_ID, activity.id)
            val message = "Notified actor ID\n$activity\n"
            if (eventType == NotificationEventType.EMPTY) {
                assertEquals(message, 0, notifiedActorId)
            } else {
                Assert.assertNotEquals(message, 0, notifiedActorId)
            }
            if (notified.known) {
                assertEquals(
                    "Notified TriState $activity",
                    notified,
                    MyQuery.activityIdToTriState(ActivityTable.NOTIFIED, activity.id)
                )
            }
        }

        fun assertStoredVisibility(activity: AActivity, expected: Visibility) {
            assertEquals(
                "Visibility of\n$activity\n",
                expected, Visibility.fromNoteId(activity.getNote().noteId)
            )
        }

        fun assertVisibility(audience: Audience, visibility: Visibility) {
            assertEquals("Visibility check $audience\n", visibility, audience.visibility)
        }

        fun sendingCreateNoteActivity(ma: MyAccount, content: String): AActivity {
            val activity = AActivity.newPartialNote(ma.actor, ma.actor, null, status = DownloadStatus.SENDING)
            activity.audience().addVisibility(Visibility.PUBLIC_AND_TO_FOLLOWERS)
            activity.getNote().setContent(content, TextMediaType.PLAIN)
            val myContext: MyContext = MyContextHolder.myContextHolder.getNow()
            val executionContext = CommandExecutionContext(
                myContext, CommandData.newItemCommand(CommandEnum.UPDATE_NOTE, ma, activity.id)
            )
            return DataUpdater(executionContext).onActivity(activity).also {
                assertTrue("Activity wasn't saved: $it", it.id != 0L)
                assertTrue("Note wasn't saved: $it", it.getNote().noteId != 0L)
            }
        }
    }

    init {
        assertTrue("Origin exists for $accountActor", origin.isValid)
    }
}
