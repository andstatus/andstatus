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
package org.andstatus.app.service

import org.andstatus.app.data.DemoNoteInserter
import org.andstatus.app.net.social.Actor
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TriState
import org.junit.Assert
import org.junit.Test
import java.net.MalformedURLException

class RepeatingFailingCommandTest : MyServiceTest() {
    @Test
    @Throws(MalformedURLException::class)
    fun repeatingFailingCommand() {
        for (iteration in 0 until ITERATIONS_NUMBER) oneIteration(iteration)
    }

    private fun oneIteration(iteration: Int) {
        val method = "repeatingFailingCommand$iteration"
        MyLog.i(this, "$method started")
        val inserter = DemoNoteInserter(ma)
        val actor = inserter.buildActor()
        inserter.onActivity(ma.actor.update(actor))
        val urlString = "http://andstatus.org/nonexistent2_avatar_" + System.currentTimeMillis() + ".png"
        AvatarDownloaderTest.changeAvatarUrl(actor, urlString)
        val startCount = mService.executionStartCount
        val endCount = mService.executionEndCount
        val requestsCounter0 = mService.getHttp()?.getRequestsCounter() ?: 0
        setAndSendGetAvatarCommand(actor, false)
        mService.assertCommandExecutionStarted("First command $actor", startCount, TriState.TRUE)
        setAndSendGetAvatarCommand(actor, false)
        Assert.assertTrue("First command didn't end $actor", mService.waitForCommandExecutionEnded(endCount))
        Assert.assertTrue("""
    Request for the command wasn't sent: ${mService.getListenedCommand()}
    ${mService.getHttp()}
    """.trimIndent(), mService.getHttp()?.getRequestsCounter() ?: 0 > requestsCounter0)
        setAndSendGetAvatarCommand(actor, false)
        mService.assertCommandExecutionStarted("""
    Duplicated command started ${mService.getListenedCommand()}
    $actor
    """.trimIndent(), startCount + 1, TriState.FALSE)
        setAndSendGetAvatarCommand(actor, true)
        mService.assertCommandExecutionStarted("""
    Manually launched duplicated command didn't start ${mService.getListenedCommand()}
    $actor
    """.trimIndent(), startCount + 1, TriState.TRUE)
        Assert.assertTrue("""
    The third command didn't end ${mService.getListenedCommand()}
    $actor
    """.trimIndent(),
                mService.waitForCommandExecutionEnded(endCount + 1))
        Assert.assertTrue("Service didn't stop", mService.waitForServiceStopped(true))
        MyLog.i(this, "$method ended, $actor")
    }

    // We need to generate new command in order to have new unique ID for it. This is how it works in app itself
    private fun setAndSendGetAvatarCommand(actor: Actor, manuallyLaunched: Boolean) {
        val command: CommandData = CommandData.Companion.newActorCommand(CommandEnum.GET_AVATAR, actor, "")
        if (manuallyLaunched) {
            command.setManuallyLaunched(true)
        }
        mService.setListenedCommand(command)
        mService.sendListenedCommand()
    }

    companion object {
        private const val ITERATIONS_NUMBER = 2
    }
}