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

import org.andstatus.app.actor.GroupType;
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
        TestSuite.initializeWithAccounts(this);
    }

    @Test
    public void testFromBodyText1() {
        Origin origin = MyContextHolder.get().origins().fromName(demoData.gnusocialTestOriginName);
        String anotherUser2 = "anotherUser@somedomain.org";
        String shortUsername3 = "shortusername";
        String groupname1 = "gnusocial";
        String body = "@" + demoData.gnusocialTestAccountUsername +
                " @" + demoData.gnusocialTestAccount2Username +
                " Please take this into account\n@" + anotherUser2 +
                " @" + demoData.gnusocialTestAccount2Username +
                " And we also send this to the group !" + groupname1 +
                " And let me mention: @" + shortUsername3;
        List<Actor> actors = Actor.newUnknown(origin, GroupType.UNKNOWN).extractActorsFromContent(body, Actor.EMPTY);
        String msgLog = body + " ->\n" + actors;
        assertEquals(msgLog, 6, actors.size());
        assertEquals(msgLog, demoData.gnusocialTestAccountUsername, actors.get(0).getUsername());
        assertEquals(msgLog, demoData.gnusocialTestAccount2Username, actors.get(1).getUsername());
        assertEquals(msgLog, anotherUser2.toLowerCase(), actors.get(2).getWebFingerId());
        assertEquals(msgLog, GroupType.UNKNOWN, actors.get(3).groupType);
        assertEquals(msgLog, groupname1, actors.get(4).getUsername());
        assertEquals(msgLog, GroupType.GENERIC, actors.get(4).groupType);
        assertEquals(msgLog, shortUsername3, actors.get(5).getUsername());
    }

    @Test
    public void testFromBodyText2() {
        final String USERNAME1 = "FontSelinstin";
        final String SKIPPED_USERNAME2 = "rocuhdjekrt";
        final String SKIPPED_USERNAME3 = "kauiwoeieurt";
        final String USERNAME4 = "djjerekwerwewer";

        Origin origin = MyContextHolder.get().origins().fromName(demoData.twitterTestOriginName);
        String body = "Starting post @ #ThisIsTagofsome-event-and entertainment by @" +
                USERNAME1 + " @@" + SKIPPED_USERNAME2 + " @#" + SKIPPED_USERNAME3 +
                " &amp; @" + USERNAME4 +
                " No reference !skippedGroupName" +
                " https://t.co/djkdfeowefPh";
        List<Actor> actors = Actor.newUnknown(origin, GroupType.UNKNOWN).extractActorsFromContent(body, Actor.EMPTY);
        String msgLog = body + " -> " + actors;
        assertEquals(msgLog, 2, actors.size());
        Actor actor0 = actors.get(0);
        assertEquals(msgLog, USERNAME1, actor0.getUsername());
        assertFalse(msgLog, actor0.isOidReal());
        assertFalse(msgLog +
                "\nusername:" + actor0.getUsername() +
                "\ntempOid: " + actor0.toTempOid() +
                "\naltOid:  " + actor0.toAltTempOid(), actor0.hasAltTempOid());

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
        Actor actor1 = Actor.fromOid(origin, "acct:fourthWithoutAvatar@pump.example.com");
        actor1.actorId = 11;
        actor1.setUsername("fourthWithoutAvatar@pump.example.com");
        actor1.setRealName("Real Fourth");
        actor1.setProfileUrl("http://pump.example.com/fourthWithoutAvatar");
        actor1.build();

        Actor actor2 = Actor.fromId(origin, 11);
        actor2.setUsername("fourthWithoutAvatar@pump.example.com");
        actor2.build();

        assertEquals(actor1, actor2);
        assertEquals(actor1.toString() + " vs " + actor2, actor1.hashCode(), actor2.hashCode());

    }

    @Test
    public void extractActorsFromContent() {
        String content = "<a href=\"https://loadaverage.org/andstatus\">AndStatus</a> started following" +
                " <a href=\"https://gnusocial.no/mcscx2\">ex mcscx2@quitter.no</a>.";
        List<Actor> actors = Actor.newUnknown(demoData.getPumpioConversationAccount().getOrigin(), GroupType.UNKNOWN)
                .extractActorsFromContent(content, Actor.EMPTY);
        assertEquals("Actors: " + actors, 1, actors.size());
        assertEquals("Actors: " + actors, "mcscx2@quitter.no", actors.get(0).getWebFingerId());
    }

    @Test
    public void extractActorsFromContentActivityPub() {
        String actorUniqueName = "me" + demoData.testRunUid + "@mastodon.example.com";
        final String content = "Sending note to the unknown yet Actor @" + actorUniqueName;
        List<Actor> actors = demoData.getMyAccount(demoData.activityPubTestAccountName).getActor()
                .extractActorsFromContent(content, Actor.EMPTY);
        assertEquals("Actors: " + actors, 1, actors.size());
        assertEquals("Actors: " + actors, actorUniqueName, actors.get(0).getUniqueName());
    }

    @Test
    public void extractActorsByUsername() {
        extractOneUsername("peter");
        extractOneUsername("AndStatus");
        extractOneUsername("Jet");
    }

    public void extractOneUsername(String username) {
        final String content = "Sending note to the unknown yet Actor @" + username + " from the Fediverse";
        List<Actor> actors = demoData.getMyAccount(demoData.activityPubTestAccountName).getActor()
                .extractActorsFromContent(content, Actor.EMPTY);
        assertEquals("Actors from '" + content + "': \n" + actors, 1, actors.size());
        assertEquals("Actors from '" + content + "': \n" + actors, username, actors.get(0).getUsername());
    }
}
