/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.UriMatcher
import android.net.Uri
import org.andstatus.app.actor.ActorsScreenType
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.OriginType
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ParsedUriTest {
    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initializeWithAccounts(this)
    }

    @Test
    fun testActorsScreen() {
        assertOneActorsScreen(DemoData.demoData.getPumpioConversationOrigin().id)
        assertOneActorsScreen(0)
    }

    private fun assertOneActorsScreen(originId: Long) {
        val noteId: Long = 2
        val uri: Uri = MatchedUri.Companion.getActorsScreenUri(ActorsScreenType.ACTORS_OF_NOTE, originId, noteId, "")
        val parsedUri: ParsedUri = ParsedUri.Companion.fromUri(uri)
        val msgLog = parsedUri.toString()
        Assert.assertEquals(TimelineType.UNKNOWN, parsedUri.getTimelineType())
        Assert.assertEquals(msgLog, ActorsScreenType.ACTORS_OF_NOTE, parsedUri.getActorsScreenType())
        Assert.assertEquals(msgLog, originId, parsedUri.getOriginId())
        Assert.assertEquals(msgLog, noteId, parsedUri.getNoteId())
        Assert.assertEquals(msgLog, noteId, parsedUri.getItemId())
        Assert.assertEquals(msgLog, 0, parsedUri.getActorId())
    }

    @Test
    fun testUriMatcher() {
        oneUriMatcherSearchQuery("topic")
        oneUriMatcherSearchQuery("%23topic")
    }

    private fun oneUriMatcherSearchQuery(searchQuery: String?) {
        val code1 = 3
        val matcher = UriMatcher(UriMatcher.NO_MATCH)
        val authority = "timeline.app.andstatus.org"
        matcher.addURI(authority, "note/#/lt/*/origin/#/actor/#/search/*", code1)
        val uriString = "content://$authority/note/0/lt/search/origin/6/actor/0/search/$searchQuery"
        val uri = Uri.parse(uriString)
        val codeOut = matcher.match(uri)
        Assert.assertEquals("String:$uriString\n Uri:$uri", code1.toLong(), codeOut.toLong())
    }

    @Test
    fun testSearchHashTag() {
        oneSearchQuery("topic")
        oneSearchQuery("#topic")
        oneSearchQuery("%23topic")
    }

    private fun oneSearchQuery(searchQuery: String?) {
        val timeline: Timeline =  MyContextHolder.myContextHolder.getNow().timelines().get(TimelineType.SEARCH, Actor.EMPTY,
                 MyContextHolder.myContextHolder.getNow().origins().firstOfType(OriginType.GNUSOCIAL), searchQuery)
        val clickUri = timeline.getClickUri()
        val parsedUri: ParsedUri = ParsedUri.Companion.fromUri(clickUri)
        Assert.assertEquals("$parsedUri\n$timeline", TimelineType.SEARCH, parsedUri.getTimelineType())
        Assert.assertEquals("$parsedUri\n$timeline", searchQuery, parsedUri.searchQuery)
    }
}
