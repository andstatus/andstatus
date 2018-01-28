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

package org.andstatus.app.service;

import org.andstatus.app.data.DemoNoteInserter;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.junit.Test;

import java.net.MalformedURLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RepeatingFailingCommandTest extends MyServiceTest {

    @Test
    public void repeatingFailingCommand() throws MalformedURLException {
        final String method = "repeatingFailingCommand";
        MyLog.i(this, method + " started");

        final DemoNoteInserter inserter = new DemoNoteInserter(ma);
        Actor actor = inserter.buildActor();
        inserter.onActivity(actor.update(ma.getActor()));

        String urlString = "http://andstatus.org/nonexistent2_avatar_" + System.currentTimeMillis() +  ".png";
        AvatarDownloaderTest.changeAvatarUrl(ma, urlString);

        mService.setListenedCommand(
                CommandData.newActorCommand(
                        CommandEnum.GET_AVATAR, null, actor.origin, actor.actorId, ""));

        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;

        mService.sendListenedToCommand();
        mService.assertCommandExecutionStarted("First command " + actor, startCount, TriState.TRUE);
        mService.sendListenedToCommand();
        assertTrue("First command didn't end " + actor, mService.waitForCommandExecutionEnded(endCount));
        assertEquals(mService.getHttp().toString(), 1, mService.getHttp().getRequestsCounter());
        mService.sendListenedToCommand();
        mService.assertCommandExecutionStarted("Duplicated command started " + actor, startCount + 1,
                TriState.FALSE);
        mService.getListenedCommand().setManuallyLaunched(true);
        mService.sendListenedToCommand();
        mService.assertCommandExecutionStarted("Manually launched duplicated command didn't start " + actor,
                startCount + 1, TriState.TRUE);
        assertTrue("The third command didn't end " + actor,
                mService.waitForCommandExecutionEnded(endCount+1));
        assertTrue("Service didn't stop", mService.waitForServiceStopped(true));
        MyLog.i(this, method + " ended, " + actor);
    }
}
