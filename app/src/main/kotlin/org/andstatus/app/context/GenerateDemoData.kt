/*
 * Copyright (C) 2017-2021 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.context

import io.vavr.control.Try
import kotlinx.coroutines.delay
import org.andstatus.app.FirstActivity
import org.andstatus.app.account.DemoAccountInserter
import org.andstatus.app.account.MyAccount
import org.andstatus.app.backup.ProgressLogger
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.data.DemoGnuSocialConversationInserter
import org.andstatus.app.data.checker.DataChecker
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.DemoOriginInserter
import org.andstatus.app.os.AsyncEnum
import org.andstatus.app.os.AsyncRunnable
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TriState
import org.andstatus.app.util.TryUtils
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert
import java.util.*

class GenerateDemoData constructor(
    val progressListener: ProgressLogger.ProgressListener,
    val demoData: DemoData
) :
    AsyncRunnable(GenerateDemoData::class, AsyncEnum.DEFAULT_POOL) {
    private val logTag: String = progressListener.getLogTag()
    override val cancelable: Boolean = false

    override suspend fun doInBackground(params: Unit): Try<Unit> {
        MyLog.i(logTag, "$logTag; started")
        progressListener.onProgressMessage("Generating demo data...")
        delay(500)
        MyLog.v(logTag, "Before initialize 1")
        val myContext: MyContext = myContextHolder.initialize(null, logTag).getCompleted()
        MyLog.v(logTag, "After initialize 1")
        MyServiceManager.setServiceUnavailable()
        val originInserter = DemoOriginInserter(myContext)
        originInserter.insert()
        val accountInserter = DemoAccountInserter(myContext)
        accountInserter.insert()
        myContext.timelines.saveChanged()
        MyLog.v(logTag, "Before initialize 2")
        myContextHolder.initialize(null, logTag).getCompleted()
        MyLog.v(logTag, "After initialize 2")
        MyServiceManager.setServiceUnavailable()
        progressListener.onProgressMessage("Demo accounts added...")
        delay(500)

        val myContext2 = myContextHolder.getCompleted()
        Assert.assertTrue("Context is not ready $myContext2", myContext2.isReady)
        demoData.checkDataPath()
        val size: Int = myContext2.accounts.size()
        Assert.assertTrue(
            "Only " + size + " accounts added: " + myContext2.accounts,
            size > 5
        )
        Assert.assertEquals(
            "No WebfingerId", Optional.empty<Any?>(), myContext2.accounts
                .get().stream().filter { ma: MyAccount -> !ma.actor.isWebFingerIdValid() }.findFirst()
        )
        val size2: Int = myContext2.users.size()
        Assert.assertTrue(
            "Only $size2 users added: ${myContext2.users}\n" +
                "Accounts: ${myContext2.accounts}",
            size2 >= size
        )

        DemoData.assertOriginsContext()
        DemoOriginInserter.assertDefaultTimelinesForOrigins()
        DemoAccountInserter.assertDefaultTimelinesForAccounts()
        demoData.insertPumpIoConversation("")
        DemoGnuSocialConversationInserter().insertConversation()
        progressListener.onProgressMessage("Demo notes added...")
        delay(500)
        if (myContextHolder.getNow().accounts.size() == 0) {
            Assert.fail("No persistent accounts")
        }
        demoData.setSuccessfulAccountAsCurrent()
        val defaultTimeline: Timeline = myContextHolder.getNow().timelines.filter(
            false, TriState.TRUE, TimelineType.EVERYTHING, Actor.EMPTY,
            myContextHolder.getNow().accounts.currentAccount.origin
        )
            .findFirst().orElse(Timeline.EMPTY)
        MatcherAssert.assertThat(defaultTimeline.timelineType, CoreMatchers.`is`(TimelineType.EVERYTHING))
        myContextHolder.getNow().timelines.setDefault(defaultTimeline)
        MyLog.v(logTag, "Before initialize 3")
        myContextHolder.initialize(null, logTag).getCompleted()
        MyLog.v(logTag, "After initialize 3")
        DemoData.assertOriginsContext()
        DemoOriginInserter.assertDefaultTimelinesForOrigins()
        DemoAccountInserter.assertDefaultTimelinesForAccounts()
        Assert.assertEquals(
            "Data errors exist", 0, DataChecker.fixData(
                ProgressLogger(progressListener), includeLong = true, countOnly = true
            )
        )
        MyLog.v(logTag, "After data checker")
        progressListener.onProgressMessage("Demo data is ready")
        delay(500)
        MyLog.i(logTag, "$logTag; ended")
        return TryUtils.SUCCESS
    }

    override suspend fun onPostExecute(result: Try<Unit>) {
        FirstActivity.checkAndUpdateLastOpenedAppVersion(myContextHolder.getNow().context, true)
        progressListener.onComplete(result.isSuccess)
    }
}
