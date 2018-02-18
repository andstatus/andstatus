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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.DemoNoteInserter;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.junit.Test;

import java.net.MalformedURLException;

import static org.junit.Assert.assertTrue;

public class RepeatingFailingCommandTest extends MyServiceTest {
    private static final int ITERATIONS_NUMBER = 2;

    @Test
    public void repeatingFailingCommand() throws MalformedURLException {
        for (int iteration = 0; iteration < ITERATIONS_NUMBER; iteration++) oneIteration(iteration);
    }

    private void oneIteration(int iteration) {
        final String method = "repeatingFailingCommand" + iteration;
        MyLog.i(this, method + " started");

        final DemoNoteInserter inserter = new DemoNoteInserter(ma);
        Actor actor = inserter.buildActor();
        inserter.onActivity(actor.update(ma.getActor()));

        String urlString = "http://andstatus.org/nonexistent2_avatar_" + System.currentTimeMillis() +  ".png";
        AvatarDownloaderTest.changeAvatarUrl(actor, urlString);

        long startCount = mService.executionStartCount;
        long endCount = mService.executionEndCount;
        final int requestsCounter0 = mService.getHttp().getRequestsCounter();

        setAndSendGetAvatarCommand(actor, false);
        mService.assertCommandExecutionStarted("First command " + actor, startCount, TriState.TRUE);
        setAndSendGetAvatarCommand(actor, false);
        assertTrue("First command didn't end " + actor, mService.waitForCommandExecutionEnded(endCount));
        assertTrue("Request for the command wasn't sent: " + mService.getListenedCommand() + "\n" +
                mService.getHttp().toString(), mService.getHttp().getRequestsCounter() > requestsCounter0);
        setAndSendGetAvatarCommand(actor, false);
        mService.assertCommandExecutionStarted("Duplicated command started "
                        + mService.getListenedCommand() + "\n" + actor, startCount + 1, TriState.FALSE);
        setAndSendGetAvatarCommand(actor, true);
        mService.assertCommandExecutionStarted("Manually launched duplicated command didn't start "
                        + mService.getListenedCommand() + "\n" + actor, startCount + 1, TriState.TRUE);
        assertTrue("The third command didn't end " + mService.getListenedCommand() + "\n" + actor,
                mService.waitForCommandExecutionEnded(endCount+1));
        assertTrue("Service didn't stop", mService.waitForServiceStopped(true));
        MyLog.i(this, method + " ended, " + actor);
    }

    // We need to generate new command in order to have new unique ID for it. This is how it works in app itself
    private void setAndSendGetAvatarCommand(Actor actor, boolean manuallyLaunched) {
        final CommandData command = CommandData.newActorCommand(
                CommandEnum.GET_AVATAR, MyAccount.EMPTY, actor.origin, actor.actorId, "");
        if (manuallyLaunched) {
            command.setManuallyLaunched(true);
        }
        mService.setListenedCommand(command);
        mService.sendListenedCommand();
    }
}
