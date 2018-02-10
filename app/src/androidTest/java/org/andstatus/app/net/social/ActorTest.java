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

package org.andstatus.app.net.social;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.origin.Origin;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ActorTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void testFromBodyText1() {
        Origin origin = MyContextHolder.get().origins().fromName(demoData.gnusocialTestOriginName);
        String webFingerId2 = "anotherUser@somedomain.org";
        String shortUsername3 = "shortusername";
        String body = "@" + demoData.gnusocialTestAccountUsername
                + " @" + demoData.gnusocialTestAccount2Username
                + " Please take this into account\n@" + webFingerId2
                + " @" + demoData.gnusocialTestAccount2Username
                + " And let me mention: @" + shortUsername3;
        List<Actor> actors = Actor.fromOriginAndActorOid(origin, "").extractActorsFromBodyText(body, false);
        String msgLog = body + " ->\n" + actors;
        assertEquals(msgLog, 4, actors.size());
        assertEquals(msgLog, demoData.gnusocialTestAccountUsername, actors.get(0).getUsername());
        assertEquals(msgLog, demoData.gnusocialTestAccount2Username, actors.get(1).getUsername());
        assertEquals(msgLog, webFingerId2, actors.get(2).getWebFingerId());
        assertEquals(msgLog, shortUsername3, actors.get(3).getUsername());
    }

    @Test
    public void testFromBodyText2() {
        final String USERNAME1 = "FontSelinstin";
        final String SKIPPED_USERNAME2 = "rocuhdjekrt";
        final String SKIPPED_USERNAME3 = "kauiwoeieurt";
        final String USERNAME4 = "djjerekwerwewer";

        Origin origin = MyContextHolder.get().origins().fromName(demoData.twitterTestOriginName);
        String body = "Starting post @ #ThisIsTagofsome-event-and entertainment"
                + " by @" + USERNAME1 + " @@" + SKIPPED_USERNAME2 + " @#" + SKIPPED_USERNAME3
                + " &amp; @" + USERNAME4
                + " https://t.co/djkdfeowefPh";
        List<Actor> actors = Actor.fromOriginAndActorOid(origin, "").extractActorsFromBodyText(body, false);
        String msgLog = body + " -> " + actors;
        assertEquals(msgLog, 2, actors.size());
        assertEquals(msgLog, USERNAME1, actors.get(0).getUsername());
        assertFalse(msgLog, actors.get(0).isOidReal());
        assertFalse(msgLog, actors.get(0).hasAltTempOid());

        assertEquals(msgLog, USERNAME4, actors.get(1).getUsername());
    }

    @Test
    public void testIsWebFingerIdValid() {
        checkWebFingerId("", false);
        checkWebFingerId("someUser.", false);
        checkWebFingerId("someUser ", false);
        checkWebFingerId("some.user", false);
        checkWebFingerId("some.user@example.com", true);
        checkWebFingerId("so+me.user@example.com", true);
        checkWebFingerId("some.us+er@example.com", false);
        checkWebFingerId("t131t@identi.ca/PumpIo", false);
        checkWebFingerId("some@example.com.", false);
        checkWebFingerId("some@user", false);
        checkWebFingerId("someuser@gs.kawa-kun.com", true);
        checkWebFingerId("AndStatus@datamost.com", true);
    }

    private void checkWebFingerId(String username, boolean valid) {
        assertEquals("Username '" + username + "' " + (valid ? "is valid" : "invalid"), valid,
                Actor.isWebFingerIdValid(username));
    }

    @Test
    public void testEquals() {
        Origin origin = MyContextHolder.get().origins().fromId(18);
        Actor actor1 = Actor.fromOriginAndActorOid(origin, "acct:fourthWithoutAvatar@pump.example.com");
        actor1.actorId = 11;
        actor1.setUsername("fourthWithoutAvatar@pump.example.com");
        actor1.setRealName("Real fourthWithoutAvatar@pump.example.com");
        actor1.setProfileUrl("http://pump.example.com/fourthWithoutAvatar");

        Actor actor2 = Actor.fromOriginAndActorId(origin, 11);
        actor2.setUsername("fourthWithoutAvatar@pump.example.com");

        assertEquals(actor1, actor2);
        assertEquals(actor1.toString() + " vs " + actor2, actor1.hashCode(), actor2.hashCode());

    }
}
