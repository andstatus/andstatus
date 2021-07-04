/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.actor

import android.content.Intent
import io.vavr.control.Try
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.note.NoteContextMenuItem
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.ListActivityTestHelper
import org.andstatus.app.timeline.TimelineActivity
import org.andstatus.app.timeline.TimelineActivityTest
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import org.junit.Assert
import org.junit.Test

class ActorsScreenTest : TimelineActivityTest<ActivityViewItem>() {
    private var noteId: Long = 0
    override fun getActivityIntent(): Intent {
        MyLog.i(this, "setUp started")
        TestSuite.initializeWithData(this)
        noteId = MyQuery.oidToId(OidEnum.NOTE_OID, DemoData.demoData.getPumpioConversationOrigin().id,
                DemoData.demoData.conversationMentionsNoteOid)
        Assert.assertNotEquals("No note with oid " + DemoData.demoData.conversationMentionsNoteOid, 0, noteId)
        val timeline: Timeline =  MyContextHolder.myContextHolder.getNow().timelines.get(TimelineType.EVERYTHING,
                Actor.EMPTY,  Origin.EMPTY)
        val updatedDate = MyQuery.noteIdToLongColumnValue(NoteTable.UPDATED_DATE, noteId)
        timeline.setVisibleItemId(noteId)
        timeline.setVisibleOldestDate(updatedDate)
        timeline.setVisibleY(0)
        MyLog.i(this, "setUp ended")
        return Intent(Intent.ACTION_VIEW, timeline.getUri())
    }

    @Test
    fun testActorsOfNote() {
        val method = "testActorsOfNote"
        TestSuite.waitForListLoaded(activity, 2)
        val helper = ListActivityTestHelper<TimelineActivity<*>>(activity, ActorsScreen::class.java)
        val content = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId)
        val logMsg = MyQuery.noteInfoForLog(activity.myContext, noteId)
        val actors: List<Actor> = Actor.Companion.newUnknown(DemoData.demoData.getPumpioConversationAccount().origin, GroupType.UNKNOWN)
                .extractActorsFromContent(content, Actor.EMPTY)
        Assert.assertEquals(logMsg, 3, actors.size.toLong())
        Assert.assertEquals(logMsg, "unknownUser", actors[2].getUsername())
        Assert.assertEquals(logMsg, "unknownUser@example.com", actors[2].uniqueName)
        Assert.assertEquals(logMsg, "unknownuser@example.com", actors[2].getWebFingerId())
        val actorsScreen = Try.of { tryToOpenActorsScreen(method, helper, logMsg) }
                .recover(java.lang.AssertionError::class.java) { e: java.lang.AssertionError? -> tryToOpenActorsScreen(method, helper, logMsg) }
                .getOrElseThrow { detailMessage: Throwable? -> AssertionError(detailMessage) }
        val listItems = actorsScreen.getListLoader().getList()
        Assert.assertEquals(listItems.toString(), 5, listItems.size.toLong())
        val actorE: Actor =  MyContextHolder.myContextHolder.getNow().users.actors.values.stream()
                .filter { actor: Actor -> actor.oid == DemoData.demoData.conversationAuthorThirdActorOid }
                .findAny().orElse(Actor.EMPTY)
        Assert.assertTrue("Found " + DemoData.demoData.conversationAuthorThirdActorOid
                + " cached " +  MyContextHolder.myContextHolder.getNow().users.actors, actorE.nonEmpty)
        val actorA: Actor = getByActorOid(listItems, DemoData.demoData.conversationAuthorThirdActorOid)
        Assert.assertTrue("Not found " + DemoData.demoData.conversationAuthorThirdActorOid + ", " + logMsg, actorA.nonEmpty)
        compareAttributes(actorE, actorA, false)
        val actorsScreenHelper = ListActivityTestHelper(actorsScreen)
        actorsScreenHelper.clickListAtPosition(method, actorsScreenHelper.getPositionOfListItemId(listItems[if (listItems.size > 2) 2 else 0].getActorId()))
        DbUtils.waitMs(method, 500)
    }

    private fun tryToOpenActorsScreen(method: String, helper: ListActivityTestHelper<TimelineActivity<*>>, logMsg: String): ActorsScreen {
        var item: ActivityViewItem = ActivityViewItem.Companion.EMPTY
        val timelineData = activity.getListData()
        for (position in 0 until timelineData.size()) {
            val item2 = timelineData.getItem(position)
            if (item2.noteViewItem.getId() == noteId) {
                item = item2
                break
            }
        }
        Assert.assertTrue("No view item. $logMsg\nThe note was not found in the timeline $timelineData", item.nonEmpty)
        Assert.assertTrue("Invoked Context menu for $logMsg", helper.invokeContextMenuAction4ListItemId(method,
                item.getId(), NoteContextMenuItem.ACTORS_OF_NOTE, org.andstatus.app.R.id.note_wrapper))
        val actorsScreen = helper.waitForNextActivity(method, 25000) as ActorsScreen
        TestSuite.waitForListLoaded(actorsScreen, 1)
        return actorsScreen
    }

    private fun compareAttributes(expected: Actor, actual: Actor, forActorsScreen: Boolean) {
        Assert.assertEquals("Oid", expected.oid, actual.oid)
        Assert.assertEquals("Username", expected.getUsername(), actual.getUsername())
        Assert.assertEquals("WebFinger ID", expected.getWebFingerId(), actual.getWebFingerId())
        Assert.assertEquals("Display name", expected.getRealName(), actual.getRealName())
        Assert.assertEquals("Description", expected.getSummary(), actual.getSummary())
        Assert.assertEquals("Location", expected.location, actual.location)
        Assert.assertEquals("Profile URL", expected.getProfileUrl(), actual.getProfileUrl())
        Assert.assertEquals("Homepage", expected.getHomepage(), actual.getHomepage())
        if (!forActorsScreen) {
            Assert.assertEquals("Avatar URL", expected.getAvatarUrl(), actual.getAvatarUrl())
            Assert.assertEquals("Endpoints", expected.endpoints, actual.endpoints)
        }
        Assert.assertEquals("Notes count", expected.notesCount, actual.notesCount)
        Assert.assertEquals("Favorites count", expected.favoritesCount, actual.favoritesCount)
        Assert.assertEquals("Following (friends) count", expected.followingCount, actual.followingCount)
        Assert.assertEquals("Followers count", expected.followersCount, actual.followersCount)
        Assert.assertEquals("Created at", expected.getCreatedDate(), actual.getCreatedDate())
        Assert.assertEquals("Updated at",
                if (expected.getUpdatedDate() == RelativeTime.DATETIME_MILLIS_NEVER) RelativeTime.SOME_TIME_AGO else expected.getUpdatedDate(),
                actual.getUpdatedDate())
    }

    companion object {
        fun getByActorOid(listItems: MutableList<ActorViewItem>, oid: String?): Actor {
            for (item in listItems) {
                if (item.actor.oid == oid) {
                    return item.actor
                }
            }
            return Actor.EMPTY
        }
    }
}
