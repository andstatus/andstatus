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

import android.net.Uri;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.actor.ActorListType;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;

public class ParsedUriTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void testUserList() {
        assertOneUserList(demoData.getConversationOriginId());
        assertOneUserList(0);
    }

    private void assertOneUserList(long originId) {
        long actorId = 5;
        long msgId = 2;
        Uri uri = MatchedUri.getActorListUri(actorId, ActorListType.ACTORS_OF_NOTE, originId, msgId, "");
        ParsedUri parsedUri = ParsedUri.fromUri(uri);
        String msgLog = parsedUri.toString();
        assertEquals(TimelineType.UNKNOWN, parsedUri.getTimelineType());
        assertEquals(msgLog, ActorListType.ACTORS_OF_NOTE, parsedUri.getUserListType());
        assertEquals(msgLog, actorId, parsedUri.getAccountUserId());
        assertEquals(msgLog, originId, parsedUri.getOriginId());
        assertEquals(msgLog, msgId, parsedUri.getNoteId());
        assertEquals(msgLog, msgId, parsedUri.getItemId());
        assertEquals(msgLog, 0, parsedUri.getActorId());
    }
}
