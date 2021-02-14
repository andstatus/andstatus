/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.net.Uri
import org.andstatus.app.FirstActivity
import org.andstatus.app.account.CredentialsVerificationStatus
import org.andstatus.app.account.DemoAccountInserter
import org.andstatus.app.account.MyAccount
import org.andstatus.app.backup.ProgressLogger
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DemoConversationInserter
import org.andstatus.app.data.DemoGnuSocialConversationInserter
import org.andstatus.app.data.checker.CheckConversations
import org.andstatus.app.data.checker.DataChecker
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.DemoOriginInserter
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginPumpio
import org.andstatus.app.origin.OriginType
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.ExceptionsCounter
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Predicate

class DemoData {
    val testRunUid = MyLog.uniqueDateTimeFormatted()
    val conversationIterationCounter: AtomicInteger? = AtomicInteger(0)
    val t131tUsername: String? = "t131t"
    val testOriginParentHost: String? = "example.com"
    val activityPubUsername: String? = "apTestUser"
    val activityPubTestOriginName: String? = "activityPubTest"
    val activityPubMainHost: String? = "ap1.$testOriginParentHost"
    val activityPubTestAccountUniqueName: String? = "$activityPubUsername@$activityPubMainHost"
    val activityPubTestAccountName: String? = "$activityPubTestAccountUniqueName/$activityPubTestOriginName"
    val activityPubTestAccountActorOid: String? = "https://$activityPubMainHost/users/$activityPubUsername"
    val pumpioOriginName: String? = "PumpioTest"
    val pumpioMainHost: String? = "pump1.$testOriginParentHost"
    val pumpioSecondHost: String? = "pump2.$testOriginParentHost"
    val pumpioTestAccountUniqueName: String? = "$t131tUsername@$pumpioMainHost"
    val pumpioTestAccountName: String? = "$pumpioTestAccountUniqueName/$pumpioOriginName"
    val pumpioTestAccountActorOid: String? = OriginPumpio.Companion.ACCOUNT_PREFIX + pumpioTestAccountUniqueName
    val gnusocialTestOriginName: String? = "GNUsocialTest"
    val gnusocialTestHost: String? = "gnusocial.$testOriginParentHost"
    val gnusocialTestAccountUsername = t131tUsername
    val gnusocialTestAccountName: String? = gnusocialTestAccountUsername + "@" + gnusocialTestHost +
            "/" + OriginType.GNUSOCIAL.title
    val gnusocialTestAccountActorOid: String? = "115391"
    val gnusocialTestAccountAvatarUrl: String? = "https://findicons.com/files/icons/2036/farm/48/rabbit.png"
    val gnusocialTestAccount2Username: String? = "gtester2"
    val gnusocialTestAccount2Name: String? = gnusocialTestAccount2Username + "@" + gnusocialTestHost +
            "/" + OriginType.GNUSOCIAL.title
    val gnusocialTestAccount2ActorOid: String? = "8902454"
    val twitterTestOriginName: String? = "TwitterTest"
    val twitterTestHostWithoutApiDot: String? = "twitter.$testOriginParentHost"
    val twitterTestHost: String? = "api.$twitterTestHostWithoutApiDot"
    val twitterTestAccountUsername = t131tUsername
    val twitterTestAccountActorOid: String? = "144771645"
    val twitterTestAccountName: String? = twitterTestAccountUsername + "@" + twitterTestHostWithoutApiDot +
            "/" + OriginType.TWITTER.title
    val mastodonTestOriginName: String? = "MastodonTest"
    val mastodonTestHost: String? = "mastodon.$testOriginParentHost"
    val mastodonTestAccountUsername: String? = "t131t1"
    val mastodonTestAccountName: String? = mastodonTestAccountUsername + "@" + mastodonTestHost +
            "/" + OriginType.MASTODON.title
    val mastodonTestAccountActorOid: String? = "37"
    val conversationOriginType: OriginType? = OriginType.PUMPIO
    val conversationOriginName = pumpioOriginName
    private val conversationAccountUniqueName: String? = "testerofandstatus@$pumpioMainHost"
    val conversationAccountName: String? = "$conversationAccountUniqueName/$conversationOriginName"
    val conversationAccountActorOid: String? = OriginPumpio.Companion.ACCOUNT_PREFIX + conversationAccountUniqueName
    val conversationAccountAvatarUrl: String? = "http://andstatus.org/images/andstatus-logo.png"
    val conversationEntryNoteOid: String? = (HTTP + pumpioMainHost
            + "/testerofandstatus/comment/thisisfakeuri" + testRunUid)
    val conversationEntryAuthorOid: String? = "acct:first@pumpentry.example.com"
    val conversationAuthorSecondUniqueName: String? = "second@$pumpioMainHost"
    val conversationAuthorSecondActorOid: String? = OriginPumpio.Companion.ACCOUNT_PREFIX + conversationAuthorSecondUniqueName
    val conversationAuthorThirdUniqueName: String? = "third@pump3.example.com"
    val conversationAuthorThirdActorOid: String? = OriginPumpio.Companion.ACCOUNT_PREFIX + conversationAuthorThirdUniqueName
    val conversationMentionsNoteOid: String? = HTTP + pumpioMainHost + "/second/comment/replywithmentions" + testRunUid
    val conversationMentionOfAuthor3Oid: String? = HTTP + pumpioMainHost + "/second/comment/mention3" + testRunUid
    val htmlNoteOid: String? = HTTP + pumpioMainHost + "/testerofandstatus/comment/htmlfakeuri" + testRunUid
    val conversationAccountSecondUniqueName: String? = "tester2ofandstatus@$pumpioSecondHost"
    val conversationAccountSecondName: String? = "$conversationAccountSecondUniqueName/$conversationOriginName"
    val conversationAccountSecondActorOid: String? = OriginPumpio.Companion.ACCOUNT_PREFIX + conversationAccountSecondUniqueName
    val plainTextNoteOid: String? = "2167283$testRunUid"
    val publicNoteText: String? = "UniqueText$testRunUid"
    val globalPublicNoteText: String? = "Public_in_AndStatus_$testRunUid"

    /** See http://stackoverflow.com/questions/6602417/get-the-uri-of-an-image-stored-in-drawable  */
    val localImageTestUri = Uri.parse("android.resource://org.andstatus.app.tests/drawable/icon")
    val localImageTestUri2 = Uri.parse("android.resource://org.andstatus.app/drawable/splash_logo")
    val localVideoTestUri = Uri.parse("android.resource://org.andstatus.app.tests/raw/video320_mp4")
    val localGifTestUri = Uri.parse("android.resource://org.andstatus.app.tests/raw/sample_gif")
    val image1Url = Uri.parse("https://raw.githubusercontent.com/andstatus/andstatus/master/app/src/main/res/drawable/splash_logo.png")

    @Volatile
    private var dataPath: String? = ""
    fun createNewInstance() {
        demoData = DemoData()
    }

    fun add(myContext: MyContext?, dataPathIn: String?) {
        val method = "add"
        dataPath = dataPathIn
        MyLog.v(TAG, "$method: started")
        val asyncTask = addAsync(myContext, ProgressLogger.Companion.EMPTY_LISTENER)
        var count: Long = 200
        while (count > 0) {
            val completedWork = asyncTask.completedBackgroundWork()
            MyLog.v(method, (if (completedWork) "Task completed " else "Waiting for task completion ") + count + " "
                    + asyncTask.status)
            if (completedWork || DbUtils.waitMs(method, 5000)) {
                break
            }
            count--
        }
        if (ExceptionsCounter.firstError.get() != null) {
            Assert.fail("Error during Demo data creation: " + ExceptionsCounter.firstError.get())
        }
        Assert.assertEquals("Demo data creation failed, count=" + count + ", status=" + asyncTask.status
                + ", " + asyncTask.toString(), true, asyncTask.completedBackgroundWork())
        Assert.assertTrue("Error during Demo data creation: " + asyncTask.firstError, asyncTask.firstError.isEmpty())
        MyLog.v(TAG, "$method: ended")
    }

    private class MyAsyncTaskDemoData private constructor(val progressListener: ProgressLogger.ProgressListener?, myContext: MyContext?, demoData: DemoData?) : MyAsyncTask<Void?, Void?, Void?>(progressListener.getLogTag(), PoolEnum.Companion.thatCannotBeShutDown()) {
        val logTag: String?
        val myContext: MyContext?
        val demoData: DemoData?
        override fun doInBackground2(aVoid: Void?): Void? {
            MyLog.i(logTag, "$logTag: started")
            DbUtils.waitMs(logTag, 1000)
            progressListener.onProgressMessage("Generating demo data...")
            DbUtils.waitMs(logTag, 500)
            MyLog.v(logTag, "Before initialize 1")
            MyContextHolder.Companion.myContextHolder.initialize(null, logTag).getBlocking()
            MyLog.v(logTag, "After initialize 1")
            MyServiceManager.Companion.setServiceUnavailable()
            val originInserter = DemoOriginInserter(myContext)
            originInserter.insert()
            val accountInserter = DemoAccountInserter(myContext)
            accountInserter.insert()
            myContext.timelines().saveChanged()
            MyLog.v(logTag, "Before initialize 2")
            MyContextHolder.Companion.myContextHolder.initialize(null, logTag).getBlocking()
            MyLog.v(logTag, "After initialize 2")
            MyServiceManager.Companion.setServiceUnavailable()
            progressListener.onProgressMessage("Demo accounts added...")
            DbUtils.waitMs(logTag, 500)
            Assert.assertTrue("Context is not ready " + MyContextHolder.Companion.myContextHolder.getNow(), MyContextHolder.Companion.myContextHolder.getNow().isReady())
            demoData.checkDataPath()
            val size: Int = MyContextHolder.Companion.myContextHolder.getNow().accounts().size()
            Assert.assertTrue("Only " + size + " accounts added: " + MyContextHolder.Companion.myContextHolder.getNow().accounts(),
                    size > 5)
            Assert.assertEquals("No WebfingerId", Optional.empty<Any?>(), MyContextHolder.Companion.myContextHolder.getNow().accounts()
                    .get().stream().filter(Predicate { ma: MyAccount? -> !ma.getActor().isWebFingerIdValid }).findFirst())
            val size2: Int = MyContextHolder.Companion.myContextHolder.getNow().users().size()
            Assert.assertTrue("""Only $size2 users added: ${MyContextHolder.Companion.myContextHolder.getNow().users()}
Accounts: ${MyContextHolder.Companion.myContextHolder.getNow().accounts()}""",
                    size2 >= size)
            assertOriginsContext()
            DemoOriginInserter.Companion.assertDefaultTimelinesForOrigins()
            DemoAccountInserter.Companion.assertDefaultTimelinesForAccounts()
            demoData.insertPumpIoConversation("")
            DemoGnuSocialConversationInserter().insertConversation()
            progressListener.onProgressMessage("Demo notes added...")
            DbUtils.waitMs(logTag, 500)
            if (MyContextHolder.Companion.myContextHolder.getNow().accounts().size() == 0) {
                Assert.fail("No persistent accounts")
            }
            demoData.setSuccessfulAccountAsCurrent()
            val defaultTimeline: Timeline = MyContextHolder.Companion.myContextHolder.getNow().timelines().filter(
                    false, TriState.TRUE, TimelineType.EVERYTHING, Actor.Companion.EMPTY,
                    MyContextHolder.Companion.myContextHolder.getNow().accounts().getCurrentAccount().getOrigin())
                    .findFirst().orElse(Timeline.Companion.EMPTY)
            MatcherAssert.assertThat(defaultTimeline.timelineType, CoreMatchers.`is`(TimelineType.EVERYTHING))
            MyContextHolder.Companion.myContextHolder.getNow().timelines().setDefault(defaultTimeline)
            MyLog.v(logTag, "Before initialize 3")
            MyContextHolder.Companion.myContextHolder.initialize(null, logTag).getBlocking()
            MyLog.v(logTag, "After initialize 3")
            assertOriginsContext()
            DemoOriginInserter.Companion.assertDefaultTimelinesForOrigins()
            DemoAccountInserter.Companion.assertDefaultTimelinesForAccounts()
            Assert.assertEquals("Data errors exist", 0, DataChecker.Companion.fixData(
                    ProgressLogger(progressListener), true, true))
            MyLog.v(logTag, "After data checker")
            progressListener.onProgressMessage("Demo data is ready")
            DbUtils.waitMs(logTag, 500)
            MyLog.i(logTag, "$logTag: ended")
            return null
        }

        override fun onFinish(aVoid: Void?, success: Boolean) {
            FirstActivity.Companion.checkAndUpdateLastOpenedAppVersion(myContext.context(), true)
            progressListener?.onComplete(success)
        }

        init {
            logTag = progressListener.getLogTag()
            this.myContext = myContext
            this.demoData = demoData
        }
    }

    fun addAsync(myContext: MyContext?,
                 progressListener: ProgressLogger.ProgressListener?): MyAsyncTask<Void?, Void?, Void?> {
        val asyncTask = MyAsyncTaskDemoData(progressListener, myContext, this)
        AsyncTaskLauncher.Companion.execute(progressListener.getLogTag(), asyncTask)
        return asyncTask
    }

    fun insertPumpIoConversation(bodySuffix: String?) {
        DemoConversationInserter().insertConversation(bodySuffix)
    }

    fun assertConversations() {
        Assert.assertEquals("Conversations need fixes", 0,
                CheckConversations()
                        .setMyContext(MyContextHolder.Companion.myContextHolder.getNow()).setLogger(ProgressLogger.Companion.getEmpty("CheckConversations"))
                        .setCountOnly(true)
                        .fix())
    }

    private fun setSuccessfulAccountAsCurrent() {
        MyLog.i(TAG, "Persistent accounts: " + MyContextHolder.Companion.myContextHolder.getNow().accounts().size())
        var found = (MyContextHolder.Companion.myContextHolder.getNow().accounts().getCurrentAccount().getCredentialsVerified()
                == CredentialsVerificationStatus.SUCCEEDED)
        if (!found) {
            for (ma in MyContextHolder.Companion.myContextHolder.getNow().accounts().get()) {
                MyLog.i(TAG, ma.toString())
                if (ma.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED) {
                    found = true
                    MyContextHolder.Companion.myContextHolder.getNow().accounts().setCurrentAccount(ma)
                    break
                }
            }
        }
        Assert.assertTrue("Found account, which is successfully verified", found)
        Assert.assertTrue("Current account is successfully verified", MyContextHolder.Companion.myContextHolder.getNow().accounts().getCurrentAccount().getCredentialsVerified()
                == CredentialsVerificationStatus.SUCCEEDED)
    }

    fun checkDataPath() {
        if (!StringUtil.isEmpty(dataPath)) {
            Assert.assertEquals("Data path. " + MyContextHolder.Companion.myContextHolder.getNow(), dataPath,
                    MyContextHolder.Companion.myContextHolder.getNow().context().getDatabasePath("andstatus").getPath())
        }
    }

    fun getGnuSocialAccount(): MyAccount? {
        return getMyAccount(gnusocialTestAccountName)
    }

    fun getPumpioConversationAccount(): MyAccount? {
        return getMyAccount(conversationAccountName)
    }

    fun getMyAccount(accountName: String?): MyAccount {
        val ma: MyAccount = MyContextHolder.Companion.myContextHolder.getBlocking().accounts().fromAccountName(accountName)
        Assert.assertTrue("$accountName exists", ma.isValid)
        Assert.assertTrue("Origin for $accountName doesn't exist", ma.origin.isValid)
        return ma
    }

    fun getAccountActorByOid(actorOid: String?): Actor {
        for (ma in MyContextHolder.Companion.myContextHolder.getBlocking().accounts().get()) {
            if (ma.getActorOid() == actorOid) {
                return ma.getActor()
            }
        }
        return Actor.Companion.EMPTY
    }

    fun getPumpioConversationOrigin(): Origin? {
        return getPumpioConversationAccount().getOrigin()
    }

    fun getGnuSocialOrigin(): Origin? {
        return getGnuSocialAccount().getOrigin()
    }

    companion object {
        @Volatile
        var demoData: DemoData? = DemoData()
        private val TAG: String? = DemoData::class.java.simpleName
        private val HTTP: String? = "http://"
        fun crashTest(supplier: BooleanSupplier?) {
            if (MyLog.isVerboseEnabled() && supplier.getAsBoolean()) {
                MyLog.e(supplier, "Initiating crash test exception")
                throw NullPointerException("This is a test crash event")
            }
        }

        private fun assertOriginsContext() {
            MyContextHolder.Companion.myContextHolder.getNow().origins().collection().forEach(Consumer { obj: Origin? -> obj.assertContext() })
        }
    }
}