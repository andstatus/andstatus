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
import org.andstatus.app.data.DataUpdater
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
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UrlUtils
import org.junit.Assert
import java.util.function.Consumer

class DemoNoteInserter(val accountActor: Actor?) {
    private val origin: Origin?

    constructor(ma: MyAccount?) : this(ma.getActor()) {}

    fun buildActor(): Actor? {
        return buildActorFromOid(nextActorUid())
    }

    fun buildActorFromOid(actorOid: String?): Actor? {
        require(!StringUtil.isEmpty(actorOid)) { "Actor oid cannot be empty" }
        val actor: Actor = Actor.Companion.fromOid(origin, actorOid)
        val username: String
        val profileUrl: String
        if (origin.getOriginType() === OriginType.PUMPIO) {
            val connection = ConnectionPumpio()
            username = connection.actorOidToUsername(actorOid)
            profileUrl = "http://" + connection.actorOidToHost(actorOid) + "/" + username
            actor.createdDate = MyLog.uniqueCurrentTimeMS()
        } else {
            username = "actorOf" + origin.getName() + actorOid
            profileUrl = ("https://" + DemoData.Companion.demoData.gnusocialTestOriginName
                    + ".example.com/profiles/" + username)
            actor.updatedDate = MyLog.uniqueCurrentTimeMS()
        }
        actor.username = username
        actor.profileUrl = profileUrl
        actor.realName = "Real $username"
        actor.summary = "This is about $username"
        actor.homepage = "https://example.com/home/$username/start/"
        actor.location = "Faraway place #" + DemoData.Companion.demoData.testRunUid
        actor.avatarUrl = actor.homepage + "avatar.jpg"
        actor.endpoints.add(ActorEndpointType.BANNER, actor.homepage + "banner.png")
        val rand = InstanceId.next()
        actor.notesCount = rand * 2 + 3
        actor.favoritesCount = rand + 11
        actor.followingCount = rand + 17
        actor.followersCount = rand
        return actor.build()
    }

    private fun nextActorUid(): String? {
        return if (origin.getOriginType() === OriginType.PUMPIO) {
            "acct:actorOf" + origin.getName() + DemoData.Companion.demoData.testRunUid + InstanceId.next()
        } else DemoData.Companion.demoData.testRunUid.toString() + InstanceId.next()
    }

    fun buildActivity(author: Actor?, name: String?, content: String?, inReplyToActivity: AActivity?,
                      noteOidIn: String?, noteStatus: DownloadStatus?): AActivity? {
        val method = "buildActivity"
        var noteOid = noteOidIn
        if (StringUtil.isEmpty(noteOid) && noteStatus != DownloadStatus.SENDING) {
            noteOid = if (origin.getOriginType() === OriginType.PUMPIO) {
                ((if (UrlUtils.hasHost(UrlUtils.fromString(author.getProfileUrl()))) author.getProfileUrl() else "http://pumpiotest" + origin.getId() + ".example.com/actor/" + author.oid)
                        + "/" + (if (inReplyToActivity == null) "note" else "comment")
                        + "/thisisfakeuri" + System.nanoTime())
            } else {
                MyLog.uniqueDateTimeFormatted()
            }
        }
        val activity = buildActivity(author, ActivityType.UPDATE, noteOid)
        val note: Note = Note.Companion.fromOriginAndOid(origin, noteOid, noteStatus)
        activity.setNote(note)
        note.updatedDate = activity.getUpdatedDate()
        note.name = name
        note.setContentPosted(content)
        note.via = "AndStatus"
        val rand = InstanceId.next()
        note.likesCount = rand - 15
        note.reblogsCount = rand - 3
        note.repliesCount = rand + 12
        note.setInReplyTo(inReplyToActivity)
        if (origin.getOriginType() === OriginType.PUMPIO) {
            note.url = note.oid
        }
        activity.initializePublicAndFollowers()
        DbUtils.waitMs(method, 10)
        return activity
    }

    fun buildActivity(actor: Actor, type: ActivityType, noteOid: String?): AActivity? {
        val activity: AActivity = AActivity.Companion.from(accountActor, type)
        activity.oid = ((if (StringUtil.isEmpty(noteOid)) MyLog.uniqueDateTimeFormatted() else noteOid)
                + "-" + activity.type.name.toLowerCase())
        activity.setActor(actor)
        activity.updatedDate = System.currentTimeMillis()
        return activity
    }

    fun onActivity(activity: AActivity?) {
        NoteEditorData.Companion.recreateKnownAudience(activity)
        val ma = origin.myContext.accounts().fromActorId(accountActor.actorId)
        Assert.assertTrue("Persistent account exists for $accountActor $activity", ma.isValid)
        val timelineType = if (activity.getNote().audience().visibility.isPrivate) TimelineType.PRIVATE else TimelineType.HOME
        val execContext = CommandExecutionContext(origin.myContext,
                CommandData.Companion.newTimelineCommand(CommandEnum.EMPTY, ma, timelineType))
        activity.audience().assertContext()
        DataUpdater(execContext).onActivity(activity)
        checkActivityRecursively(activity, 1)
    }

    private fun checkActivityRecursively(activity: AActivity?, level: Int) {
        val note = activity.getNote()
        if (level == 1 && note.nonEmpty()) {
            Assert.assertNotEquals("Activity was not added: $activity", 0, activity.getId())
        }
        if (level > DataUpdater.Companion.MAX_RECURSING || activity.getId() == 0L) return
        Assert.assertNotEquals("Account is unknown: $activity", 0, activity.accountActor.actorId)
        val actor = activity.getActor()
        if (actor.nonEmpty()) {
            Assert.assertNotEquals("Level $level, Actor id not set for $actor in $activity", 0, actor.actorId)
            Assert.assertNotEquals("Level $level, User id not set for $actor in $activity", 0, actor.user.userId)
        }
        checkStoredActor(actor)
        if (note.nonEmpty()) {
            Assert.assertNotEquals("Note was not added at level $level $activity", 0, note.noteId)
            val permalink = origin.getNotePermalink(note.noteId)
            val urlPermalink = UrlUtils.fromString(permalink)
            Assert.assertNotNull("""Note permalink is a valid URL '$permalink',
$note
 origin: $origin
 author: ${activity.getAuthor()}""", urlPermalink)
            if (origin.getUrl() != null && origin.getOriginType() !== OriginType.TWITTER) {
                Assert.assertEquals("Note permalink has the same host as origin, $note",
                        origin.getUrl().host, urlPermalink.host)
            }
            if (StringUtil.nonEmpty(note.name)) {
                Assert.assertEquals("Note name $activity", note.name,
                        MyQuery.noteIdToStringColumnValue(NoteTable.NAME, note.noteId))
            }
            if (StringUtil.nonEmpty(note.summary)) {
                Assert.assertEquals("Note summary $activity", note.summary,
                        MyQuery.noteIdToStringColumnValue(NoteTable.SUMMARY, note.noteId))
            }
            if (StringUtil.nonEmpty(note.content)) {
                Assert.assertEquals("Note content $activity", note.content,
                        MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, note.noteId))
            }
            if (StringUtil.nonEmpty(note.url)) {
                Assert.assertEquals("Note permalink", note.url, origin.getNotePermalink(note.noteId))
            }
            val author = activity.getAuthor()
            if (author.nonEmpty()) {
                Assert.assertNotEquals("Author id for $author not set in note $note in $activity", 0,
                        MyQuery.noteIdToActorId(NoteTable.AUTHOR_ID, note.noteId))
            }
            checkStoredActor(author)
        }
        when (activity.type) {
            ActivityType.LIKE -> {
                val stargazers = MyQuery.getStargazers(origin.myContext.database, accountActor.origin, note.noteId)
                val found = stargazers.stream().anyMatch { stargazer: Actor? -> stargazer.actorId == actor.actorId }
                Assert.assertTrue("""
    Actor, who favorited, is not found among stargazers: $activity
    stargazers: $stargazers
    """.trimIndent(), found)
            }
            ActivityType.ANNOUNCE -> {
                val rebloggers = MyQuery.getRebloggers(origin.myContext.database, accountActor.origin, note.noteId)
                Assert.assertTrue("""
    Reblogger is not found among rebloggers: $activity
    rebloggers: $rebloggers
    """.trimIndent(), rebloggers.stream().anyMatch { a: Actor? -> a.actorId == actor.actorId })
            }
            ActivityType.FOLLOW -> Assert.assertTrue("Friend not found: $activity",
                    GroupMembership.Companion.isGroupMember(actor, GroupType.FRIENDS, activity.getObjActor().actorId))
            ActivityType.UNDO_FOLLOW -> Assert.assertFalse("Friend found: $activity",
                    GroupMembership.Companion.isGroupMember(actor, GroupType.FRIENDS, activity.getObjActor().actorId))
            else -> {
            }
        }
        if (!note.replies.isEmpty()) {
            for (replyActivity in note.replies) {
                if (replyActivity.nonEmpty()) {
                    Assert.assertNotEquals("Reply added at level $level $replyActivity", 0, replyActivity.id)
                    checkActivityRecursively(replyActivity, level + 1)
                }
            }
        }
        note.audience().evaluateAndGetActorsToSave(activity.getAuthor()).forEach(Consumer { actor: Actor? -> checkStoredActor(actor) })
        if (activity.getObjActor().nonEmpty()) {
            Assert.assertNotEquals("Actor was not added: " + activity.getObjActor(), 0, activity.getObjActor().actorId)
        }
        if (activity.getActivity().nonEmpty()) {
            checkActivityRecursively(activity.getActivity(), level + 1)
        }
    }

    companion object {
        fun onActivityS(activity: AActivity?) {
            DemoNoteInserter(activity.accountActor).onActivity(activity)
        }

        fun increaseUpdateDate(activity: AActivity?): AActivity? {
            // In order for a note not to be ignored
            activity.setUpdatedDate(activity.getUpdatedDate() + 1)
            activity.getNote().updatedDate = activity.getNote().updatedDate + 1
            return activity
        }

        fun checkStoredActor(actor: Actor?) {
            if (actor.dontStore()) return
            val id = actor.actorId
            if (StringUtil.nonEmpty(actor.oid)) {
                Assert.assertEquals("oid $actor", actor.oid,
                        MyQuery.actorIdToStringColumnValue(ActorTable.ACTOR_OID, id))
            }
            if (!actor.getUsername().isEmpty()) {
                Assert.assertEquals("Username $actor", actor.getUsername(),
                        MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, id))
            }
            val webFingerIdActual = MyQuery.actorIdToStringColumnValue(ActorTable.WEBFINGER_ID, id)
            if (actor.getWebFingerId().isEmpty()) {
                Assert.assertTrue("WebFingerID=$webFingerIdActual for $actor", StringUtil.isEmpty(webFingerIdActual)
                        || Actor.Companion.isWebFingerIdValid(webFingerIdActual))
            } else {
                Assert.assertEquals("WebFingerID=$webFingerIdActual for $actor", actor.getWebFingerId(), webFingerIdActual)
                Assert.assertTrue("Invalid WebFingerID $actor", Actor.Companion.isWebFingerIdValid(webFingerIdActual))
            }
            if (StringUtil.nonEmpty(actor.getRealName())) {
                Assert.assertEquals("Display name $actor", actor.getRealName(),
                        MyQuery.actorIdToStringColumnValue(ActorTable.REAL_NAME, id))
            }
        }

        fun deleteOldNote(origin: Origin, noteOid: String?) {
            val noteIdOld = MyQuery.oidToId(OidEnum.NOTE_OID, origin.id, noteOid)
            if (noteIdOld != 0L) {
                val deleted: Int = MyProvider.Companion.deleteNoteAndItsActivities(origin.myContext, noteIdOld)
                Assert.assertTrue("Activities of Old note id=$noteIdOld deleted: $deleted", deleted > 0)
            }
        }

        fun addNoteForAccount(ma: MyAccount?, body: String?, noteOid: String?, noteStatus: DownloadStatus?): AActivity? {
            Assert.assertTrue("Is not valid: $ma", ma.isValid())
            val accountActor = ma.getActor()
            val mi = DemoNoteInserter(accountActor)
            val activity = mi.buildActivity(accountActor, "", body, null, noteOid, noteStatus)
            mi.onActivity(activity)
            return activity
        }

        fun assertInteraction(activity: AActivity?, eventType: NotificationEventType?, notified: TriState?) {
            Assert.assertEquals("Notification event type\n$activity\n",
                    eventType,
                    NotificationEventType.Companion.fromId(
                            MyQuery.activityIdToLongColumnValue(ActivityTable.INTERACTION_EVENT, activity.getId())))
            Assert.assertEquals("Interacted TriState\n$activity\n",
                    TriState.Companion.fromBoolean(eventType != NotificationEventType.EMPTY &&
                            eventType != NotificationEventType.HOME),
                    MyQuery.activityIdToTriState(ActivityTable.INTERACTED, activity.getId()))
            val notifiedActorId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTIFIED_ACTOR_ID, activity.getId())
            val message = "Notified actor ID\n$activity\n"
            if (eventType == NotificationEventType.EMPTY) {
                Assert.assertEquals(message, 0, notifiedActorId)
            } else {
                Assert.assertNotEquals(message, 0, notifiedActorId)
            }
            if (notified.known) {
                Assert.assertEquals("""
    Notified TriState
    $activity
    
    """.trimIndent(),
                        notified,
                        MyQuery.activityIdToTriState(ActivityTable.NOTIFIED, activity.getId()))
            }
        }

        fun assertStoredVisibility(activity: AActivity?, expected: Visibility?) {
            Assert.assertEquals("Visibility of\n$activity\n",
                    expected, Visibility.Companion.fromNoteId(activity.getNote().noteId))
        }

        fun assertVisibility(audience: Audience?, visibility: Visibility?) {
            Assert.assertEquals("Visibility check $audience\n", visibility, audience.getVisibility())
        }
    }

    init {
        Assert.assertTrue(accountActor != null)
        origin = accountActor.origin
        Assert.assertTrue("Origin exists for $accountActor", origin.isValid())
    }
}