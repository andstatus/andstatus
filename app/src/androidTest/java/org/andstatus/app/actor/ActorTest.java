/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.actor;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.social.Actor;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;

public class ActorTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void extractActorsFromContent() {
        String content = "<a href=\"https://loadaverage.org/andstatus\">AndStatus</a> started following" +
                " <a href=\"https://gnusocial.no/mcscx2\">ex mcscx2@quitter.no</a>.";
        List<Actor> actors = Actor.fromOriginAndActorOid(demoData.getConversationMyAccount().getOrigin(), "")
                .extractActorsFromContent(content, false, Actor.EMPTY);
        assertEquals("Actors: " + actors, 1, actors.size());
        assertEquals("Actors: " + actors, "mcscx2@quitter.no", actors.get(0).getWebFingerId());
    }

}
