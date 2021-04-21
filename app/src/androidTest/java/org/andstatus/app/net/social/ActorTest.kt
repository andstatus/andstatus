/*
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.net.social

import org.andstatus.app.actor.GroupType
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DemoNoteInserter
import org.andstatus.app.origin.Origin
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ActorTest {
    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initializeWithAccounts(this)
    }

    @Test
    fun testFromBodyText1() {
        val origin: Origin =  MyContextHolder.myContextHolder.getNow().origins().fromName(DemoData.demoData.gnusocialTestOriginName)
        val anotherUser2 = "anotherUser@somedomain.org"
        val shortUsername3 = "shortusername"
        val groupname1 = "gnusocial"
        val body = """@${DemoData.demoData.gnusocialTestAccountUsername} @${DemoData.demoData.gnusocialTestAccount2Username} Please take this into account
@$anotherUser2 @${DemoData.demoData.gnusocialTestAccount2Username} And we also send this to the group !$groupname1 And let me mention: @$shortUsername3"""
        val actors: List<Actor> = Actor.Companion.newUnknown(origin, GroupType.UNKNOWN).extractActorsFromContent(body, Actor.EMPTY)
        val msgLog = "$body ->\n$actors"
        Assert.assertEquals(msgLog, 6, actors.size.toLong())
        Assert.assertEquals(msgLog, DemoData.demoData.gnusocialTestAccountUsername, actors[0].getUsername())
        Assert.assertEquals(msgLog, DemoData.demoData.gnusocialTestAccount2Username, actors[1].getUsername())
        Assert.assertEquals(msgLog, anotherUser2.toLowerCase(), actors[2].getWebFingerId())
        Assert.assertEquals(msgLog, GroupType.UNKNOWN, actors[3].groupType)
        Assert.assertEquals(msgLog, groupname1, actors[4].getUsername())
        Assert.assertEquals(msgLog, GroupType.GENERIC, actors[4].groupType)
        Assert.assertEquals(msgLog, shortUsername3, actors[5].getUsername())
    }

    @Test
    fun testFromBodyText2() {
        val USERNAME1 = "FontSelinstin"
        val SKIPPED_USERNAME2 = "rocuhdjekrt"
        val SKIPPED_USERNAME3 = "kauiwoeieurt"
        val USERNAME4 = "djjerekwerwewer"
        val origin: Origin =  MyContextHolder.myContextHolder.getNow().origins().fromName(DemoData.demoData.twitterTestOriginName)
        val body = "Starting post @ #ThisIsTagofsome-event-and entertainment by @" +
                USERNAME1 + " @@" + SKIPPED_USERNAME2 + " @#" + SKIPPED_USERNAME3 +
                " &amp; @" + USERNAME4 +
                " No reference !skippedGroupName" +
                " https://t.co/djkdfeowefPh"
        val actors: List<Actor> = Actor.Companion.newUnknown(origin, GroupType.UNKNOWN).extractActorsFromContent(body, Actor.EMPTY)
        val msgLog = "$body -> $actors"
        Assert.assertEquals(msgLog, 2, actors.size.toLong())
        val actor0 = actors[0]
        Assert.assertEquals(msgLog, USERNAME1, actor0.getUsername())
        Assert.assertFalse(msgLog, actor0.isOidReal())
        Assert.assertFalse("$msgLog\nusername:${actor0.getUsername()}\ntempOid: ${actor0.toTempOid()}" +
                "\naltOid:  ${actor0.toAltTempOid()}", actor0.hasAltTempOid())
        Assert.assertEquals(msgLog, USERNAME4, actors[1].getUsername())
    }

    @Test
    fun testIsWebFingerIdValid() {
        checkWebFingerId("", false)
        checkWebFingerId("someUser.", false)
        checkWebFingerId("someUser ", false)
        checkWebFingerId("some.user", false)
        checkWebFingerId("some.user@example.com", true)
        checkWebFingerId("so+me.user@example.com", true)
        checkWebFingerId("some.us+er@example.com", false)
        checkWebFingerId("t131t@identi.ca/PumpIo", false)
        checkWebFingerId("some@example.com.", false)
        checkWebFingerId("some@user", false)
        checkWebFingerId("someuser@gs.kawa-kun.com", true)
        checkWebFingerId("AndStatus@datamost.com", true)
    }

    private fun checkWebFingerId(username: String?, valid: Boolean) {
        Assert.assertEquals("Username '" + username + "' " + if (valid) "is valid" else "invalid", valid,
                Actor.Companion.isWebFingerIdValid(username))
    }

    @Test
    fun testEquals() {
        val origin: Origin =  MyContextHolder.myContextHolder.getNow().origins().fromId(18)
        val actor1: Actor = Actor.Companion.fromOid(origin, "acct:fourthWithoutAvatar@pump.example.com")
        actor1.actorId = 11
        actor1.setUsername( "fourthWithoutAvatar@pump.example.com")
        actor1.setRealName("Real Fourth")
        actor1.setProfileUrl("http://pump.example.com/fourthWithoutAvatar")
        actor1.build()
        val actor2: Actor = Actor.Companion.fromId(origin, 11)
        actor2.setUsername( "fourthWithoutAvatar@pump.example.com")
        actor2.build()
        Assert.assertEquals(actor1, actor2)
        Assert.assertEquals("$actor1 vs $actor2", actor1.hashCode().toLong(), actor2.hashCode().toLong())
    }

    @Test
    fun extractActorsFromContent() {
        val content = "<a href=\"https://loadaverage.org/andstatus\">AndStatus</a> started following" +
                " <a href=\"https://gnusocial.no/mcscx2\">ex mcscx2@quitter.no</a>."
        val actors: List<Actor> = Actor.Companion.newUnknown(DemoData.demoData.getPumpioConversationAccount().origin, GroupType.UNKNOWN)
                .extractActorsFromContent(content, Actor.EMPTY)
        Assert.assertEquals("Actors: $actors", 1, actors.size.toLong())
        Assert.assertEquals("Actors: $actors", "mcscx2@quitter.no", actors[0].getWebFingerId())
    }

    @Test
    fun extractActorsFromContentActivityPub() {
        val actorUniqueName = "me" + DemoData.demoData.testRunUid + "@mastodon.example.com"
        val content = "Sending note to the unknown yet Actor @$actorUniqueName"
        val actors: List<Actor> = DemoData.demoData.getMyAccount(DemoData.demoData.activityPubTestAccountName).actor
                .extractActorsFromContent(content, Actor.EMPTY)
        Assert.assertEquals("Actors: $actors", 1, actors.size.toLong())
        Assert.assertEquals("Actors: $actors", actorUniqueName, actors[0].uniqueName)
    }

    @Test
    fun extractActorsByUsername() {
        extractOneUsername("peter")
        extractOneUsername("AndStatus")
        extractOneUsername("Jet")
    }

    @Test
    fun testWebFingerIdCreation() {
        val accountActor = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName).actor
        val author2 = DemoNoteInserter(accountActor).buildActorFromOid(DemoData.demoData.conversationAuthorSecondActorOid)
        author2.setAvatarUrl("http://png.findicons.com/files/icons/1780/black_and_orange/300/android_orange.png")
        assertEquals("$author2", "second@pump1.example.com", author2.getWebFingerId())
    }

    fun extractOneUsername(username: String?) {
        val content = "Sending note to the unknown yet Actor @$username from the Fediverse"
        val actors: List<Actor> = DemoData.demoData.getMyAccount(DemoData.demoData.activityPubTestAccountName).actor
                .extractActorsFromContent(content, Actor.EMPTY)
        Assert.assertEquals("Actors from '$content': \n$actors", 1, actors.size.toLong())
        Assert.assertEquals("Actors from '$content': \n$actors", username, actors[0].getUsername())
    }
}