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

package org.andstatus.app.data;

import android.content.UriMatcher;
import android.net.Uri;

import org.andstatus.app.actor.ActorListType;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;

public class ParsedUriTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithAccounts(this);
    }

    @Test
    public void testActorList() {
        assertOneActorList(demoData.getConversationOrigin().getId());
        assertOneActorList(0);
    }

    private void assertOneActorList(long originId) {
        long actorId = 5;
        long noteId = 2;
        Uri uri = MatchedUri.getActorListUri(actorId, ActorListType.ACTORS_OF_NOTE, originId, noteId, "");
        ParsedUri parsedUri = ParsedUri.fromUri(uri);
        String msgLog = parsedUri.toString();
        assertEquals(TimelineType.UNKNOWN, parsedUri.getTimelineType());
        assertEquals(msgLog, ActorListType.ACTORS_OF_NOTE, parsedUri.getActorListType());
        assertEquals(msgLog, actorId, parsedUri.getAccountActorId());
        assertEquals(msgLog, originId, parsedUri.getOriginId());
        assertEquals(msgLog, noteId, parsedUri.getNoteId());
        assertEquals(msgLog, noteId, parsedUri.getItemId());
        assertEquals(msgLog, 0, parsedUri.getActorId());
    }

    @Test
    public void testUriMatcher() {
        oneUriMatcherSearchQuery("topic");
        oneUriMatcherSearchQuery("%23topic");
    }

    private void oneUriMatcherSearchQuery(String searchQuery) {
        int code1 = 3;
        UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = "timeline.app.andstatus.org";
        matcher.addURI(authority, "note/#/lt/*/origin/#/actor/#/search/*", code1);
        String uriString = "content://" + authority + "/note/0/lt/search/origin/6/actor/0/search/" + searchQuery;
        final Uri uri = Uri.parse(uriString);
        int codeOut = matcher.match(uri);
        assertEquals("String:" + uriString + "\n Uri:" + uri, code1, codeOut);
    }

    @Test
    public void testSearchHashTag() {
        oneSearchQuery("topic");
        oneSearchQuery("#topic");
        oneSearchQuery("%23topic");
    }

    private void oneSearchQuery(String searchQuery) {
        final Timeline timeline = MyContextHolder.get().timelines().get(TimelineType.SEARCH, 0,
                MyContextHolder.get().origins().firstOfType(OriginType.GNUSOCIAL), searchQuery);
        final Uri clickUri = timeline.getClickUri();
        ParsedUri parsedUri = ParsedUri.fromUri(clickUri);
        assertEquals(parsedUri.toString() + "\n" + timeline.toString(), TimelineType.SEARCH, parsedUri.getTimelineType());
        assertEquals(parsedUri.toString() + "\n" + timeline.toString(), searchQuery, parsedUri.getSearchQuery());
    }

}
