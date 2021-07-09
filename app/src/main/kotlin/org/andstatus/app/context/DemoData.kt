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
import io.vavr.control.Try
import kotlinx.coroutines.delay
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
import org.andstatus.app.os.MyAsyncTask.PoolEnum.DEFAULT_POOL
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BooleanSupplier
import java.util.function.Consumer

class DemoData {
    val testRunUid = MyLog.uniqueDateTimeFormatted()
    val conversationIterationCounter: AtomicInteger = AtomicInteger(0)
    val t131tUsername: String = "t131t"
    val testOriginParentHost: String = "example.com"
    val activityPubUsername: String = "apTestUser"
    val activityPubTestOriginName: String = "activityPubTest"
    val activityPubMainHost: String = "ap1.$testOriginParentHost"
    val activityPubTestAccountUniqueName: String = "$activityPubUsername@$activityPubMainHost"
    val activityPubTestAccountName: String = "$activityPubTestAccountUniqueName/$activityPubTestOriginName"
    val activityPubTestAccountActorOid: String = "https://$activityPubMainHost/users/$activityPubUsername"
    val activityPubTestAccountAvatarUrl: String = "https://cdn.icon-icons.com/icons2/2699/PNG/512/w_activitypub_logo_icon_169246.png"
    val pumpioOriginName: String = "PumpioTest"
    val pumpioMainHost: String = "pump1.$testOriginParentHost"
    val pumpioSecondHost: String = "pump2.$testOriginParentHost"
    val pumpioTestAccountUniqueName: String = "$t131tUsername@$pumpioMainHost"
    val pumpioTestAccountName: String = "$pumpioTestAccountUniqueName/$pumpioOriginName"
    val pumpioTestAccountActorOid: String = OriginPumpio.ACCOUNT_PREFIX + pumpioTestAccountUniqueName
    val gnusocialTestOriginName: String = "GNUsocialTest"
    val gnusocialTestHost: String = "gnusocial.$testOriginParentHost"
    val gnusocialTestAccountUsername = t131tUsername
    val gnusocialTestAccountName: String = gnusocialTestAccountUsername + "@" + gnusocialTestHost +
            "/" + OriginType.GNUSOCIAL.title
    val gnusocialTestAccountActorOid: String = "115391"
    val gnusocialTestAccountAvatarUrl: String = "https://findicons.com/files/icons/2036/farm/48/rabbit.png"
    val gnusocialTestAccount2Username: String = "gtester2"
    val gnusocialTestAccount2Name: String = gnusocialTestAccount2Username + "@" + gnusocialTestHost +
            "/" + OriginType.GNUSOCIAL.title
    val gnusocialTestAccount2ActorOid: String = "8902454"
    val twitterTestOriginName: String = "TwitterTest"
    val twitterTestHostWithoutApiDot: String = "twitter.$testOriginParentHost"
    val twitterTestHost: String = "api.$twitterTestHostWithoutApiDot"
    val twitterTestAccountUsername = t131tUsername
    val twitterTestAccountActorOid: String = "144771645"
    val twitterTestAccountName: String = twitterTestAccountUsername + "@" + twitterTestHostWithoutApiDot +
            "/" + OriginType.TWITTER.title
    val mastodonTestOriginName: String = "MastodonTest"
    val mastodonTestHost: String = "mastodon.$testOriginParentHost"
    val mastodonTestAccountUsername: String = "t131t1"
    val mastodonTestAccountName: String = mastodonTestAccountUsername + "@" + mastodonTestHost +
            "/" + OriginType.MASTODON.title
    val mastodonTestAccountActorOid: String = "37"
    val conversationOriginType: OriginType = OriginType.PUMPIO
    val conversationOriginName = pumpioOriginName
    private val conversationAccountUniqueName: String = "testerofandstatus@$pumpioMainHost"
    val conversationAccountName: String = "$conversationAccountUniqueName/$conversationOriginName"
    val conversationAccountActorOid: String = OriginPumpio.ACCOUNT_PREFIX + conversationAccountUniqueName
    val conversationAccountAvatarUrl: String = "http://andstatus.org/images/andstatus-logo.png"
    val conversationEntryNoteOid: String = (HTTP + pumpioMainHost
            + "/testerofandstatus/comment/thisisfakeuri" + testRunUid)
    val conversationEntryAuthorOid: String = "acct:first@pumpentry.example.com"
    val conversationAuthorSecondUniqueName: String = "second@$pumpioMainHost"
    val conversationAuthorSecondActorOid: String = OriginPumpio.ACCOUNT_PREFIX + conversationAuthorSecondUniqueName
    val conversationAuthorThirdUniqueName: String = "third@pump3.example.com"
    val conversationAuthorThirdActorOid: String = OriginPumpio.ACCOUNT_PREFIX + conversationAuthorThirdUniqueName
    val conversationMentionsNoteOid: String = HTTP + pumpioMainHost + "/second/comment/replywithmentions" + testRunUid
    val conversationMentionOfAuthor3Oid: String = HTTP + pumpioMainHost + "/second/comment/mention3" + testRunUid
    val htmlNoteOid: String = HTTP + pumpioMainHost + "/testerofandstatus/comment/htmlfakeuri" + testRunUid
    val conversationAccountSecondUniqueName: String = "tester2ofandstatus@$pumpioSecondHost"
    val conversationAccountSecondName: String = "$conversationAccountSecondUniqueName/$conversationOriginName"
    val conversationAccountSecondActorOid: String = OriginPumpio.ACCOUNT_PREFIX + conversationAccountSecondUniqueName
    val plainTextNoteOid: String = "2167283$testRunUid"
    val publicNoteText: String = "UniqueText$testRunUid"
    val globalPublicNoteText: String = "Public_in_AndStatus_$testRunUid"

    /** See http://stackoverflow.com/questions/6602417/get-the-uri-of-an-image-stored-in-drawable  */
    val localImageTestUri: Uri = Uri.parse("android.resource://org.andstatus.app.tests/drawable/icon")
    val localImageTestUri2: Uri = Uri.parse("android.resource://org.andstatus.app/drawable/splash_logo")
    val localVideoTestUri: Uri = Uri.parse("android.resource://org.andstatus.app.tests/raw/video320_mp4")
    val localGifTestUri: Uri = Uri.parse("android.resource://org.andstatus.app.tests/raw/sample_gif")
    val image1Url: Uri = Uri.parse("https://raw.githubusercontent.com/andstatus/andstatus/master/app/src/main/res/drawable/splash_logo.png")

    @Volatile
    private var dataPath: String = ""
    fun createNewInstance() {
        demoData = DemoData()
    }

    fun add(myContext: MyContext, dataPathIn: String) {
        val method = "add"
        dataPath = dataPathIn
        MyLog.v(TAG, "$method; started")
        val asyncTask = addAsync(myContext, ProgressLogger.EMPTY_LISTENER)
        var count: Long = 200
        while (count > 0) {
            MyLog.v(this, "$method; " + (if (asyncTask.isFinished) "Task completed " else "Waiting for task completion ") + count + " "
                    + asyncTask.status)
            if (asyncTask.isFinished || DbUtils.waitMs(method, 5000)) {
                break
            }
            count--
        }
        if (ExceptionsCounter.firstError.get() != null) {
            Assert.fail("Error during Demo data creation: " + ExceptionsCounter.firstError.get())
        }
        Assert.assertEquals("Demo data creation failed, count=" + count + ", status=" + asyncTask.status
                + ", $asyncTask", true, asyncTask.noMoreBackgroundWork
        )
        Assert.assertTrue("Error during Demo data creation: " + asyncTask.firstError + ", $asyncTask",
                asyncTask.firstError.isEmpty())
        MyLog.v(TAG, "$method; ended")
    }

    private class GenerateDemoData constructor(val progressListener: ProgressLogger.ProgressListener,
                                               val myContext: MyContext,
                                               val demoData: DemoData) :
        MyAsyncTask<Void?, Void?, Void>(this::class.java, DEFAULT_POOL) {
        val logTag: String = progressListener.getLogTag()
        override val cancelable: Boolean = false

        override suspend fun doInBackground(params: Void?): Try<Void> {
            MyLog.i(logTag, "$logTag; started")
            progressListener.onProgressMessage("Generating demo data...")
            delay(500)
            MyLog.v(logTag, "Before initialize 1")
             MyContextHolder.myContextHolder.initialize(null, logTag).getBlocking()
            MyLog.v(logTag, "After initialize 1")
            MyServiceManager.setServiceUnavailable()
            val originInserter = DemoOriginInserter(myContext)
            originInserter.insert()
            val accountInserter = DemoAccountInserter(myContext)
            accountInserter.insert()
            myContext.timelines.saveChanged()
            MyLog.v(logTag, "Before initialize 2")
            MyContextHolder.myContextHolder.initialize(null, logTag).getBlocking()
            MyLog.v(logTag, "After initialize 2")
            MyServiceManager.setServiceUnavailable()
            progressListener.onProgressMessage("Demo accounts added...")
            delay(500)

            val myContext2 = MyContextHolder.myContextHolder.getBlocking()
            Assert.assertTrue("Context is not ready " + myContext2, myContext2.isReady)
            demoData.checkDataPath()
            val size: Int = myContext2.accounts.size()
            Assert.assertTrue(
                "Only " + size + " accounts added: " + myContext2.accounts,
                size > 5
            )
            Assert.assertEquals("No WebfingerId", Optional.empty<Any?>(), myContext2.accounts
                .get().stream().filter { ma: MyAccount -> !ma.actor.isWebFingerIdValid() }.findFirst()
            )
            val size2: Int = myContext2.users.size()
            Assert.assertTrue(
                "Only $size2 users added: ${myContext2.users}\n" +
                        "Accounts: ${myContext2.accounts}",
                size2 >= size
            )

            assertOriginsContext()
            DemoOriginInserter.assertDefaultTimelinesForOrigins()
            DemoAccountInserter.assertDefaultTimelinesForAccounts()
            demoData.insertPumpIoConversation("")
            DemoGnuSocialConversationInserter().insertConversation()
            progressListener.onProgressMessage("Demo notes added...")
            delay(500)
            if ( MyContextHolder.myContextHolder.getNow().accounts.size() == 0) {
                Assert.fail("No persistent accounts")
            }
            demoData.setSuccessfulAccountAsCurrent()
            val defaultTimeline: Timeline = MyContextHolder.myContextHolder.getNow().timelines.filter(
                    false, TriState.TRUE, TimelineType.EVERYTHING, Actor.EMPTY,
                    MyContextHolder.myContextHolder.getNow().accounts.currentAccount.origin)
                    .findFirst().orElse(Timeline.EMPTY)
            MatcherAssert.assertThat(defaultTimeline.timelineType, CoreMatchers.`is`(TimelineType.EVERYTHING))
            MyContextHolder.myContextHolder.getNow().timelines.setDefault(defaultTimeline)
            MyLog.v(logTag, "Before initialize 3")
            MyContextHolder.myContextHolder.initialize(null, logTag).getBlocking()
            MyLog.v(logTag, "After initialize 3")
            assertOriginsContext()
            DemoOriginInserter.assertDefaultTimelinesForOrigins()
            DemoAccountInserter.assertDefaultTimelinesForAccounts()
            Assert.assertEquals("Data errors exist", 0, DataChecker.fixData(
                    ProgressLogger(progressListener), true, true))
            MyLog.v(logTag, "After data checker")
            progressListener.onProgressMessage("Demo data is ready")
            delay(500)
            MyLog.i(logTag, "$logTag; ended")
            return TryUtils.SUCCESS
        }

        override suspend fun onPostExecute(result: Try<Void>) {
            FirstActivity.checkAndUpdateLastOpenedAppVersion(myContext.context, true)
            progressListener.onComplete(result.isSuccess)
        }
    }

    fun addAsync(myContext: MyContext,
                 progressListener: ProgressLogger.ProgressListener): MyAsyncTask<Void?, Void?, Void> {
        val asyncTask = GenerateDemoData(progressListener, myContext, this)
        AsyncTaskLauncher.execute(this, asyncTask)
        return asyncTask
    }

    fun insertPumpIoConversation(bodySuffix: String?) {
        DemoConversationInserter().insertConversation(bodySuffix)
    }

    fun assertConversations() {
        Assert.assertEquals("Conversations need fixes", 0,
                CheckConversations()
                        .setMyContext( MyContextHolder.myContextHolder.getNow())
                        .setLogger(ProgressLogger.getEmpty("CheckConversations"))
                        .setCountOnly(true)
                        .fix())
    }

    private fun setSuccessfulAccountAsCurrent() {
        MyLog.i(TAG, "Persistent accounts: " +  MyContextHolder.myContextHolder.getNow().accounts.size())
        var found = ( MyContextHolder.myContextHolder.getNow().accounts.currentAccount.getCredentialsVerified()
                == CredentialsVerificationStatus.SUCCEEDED)
        if (!found) {
            for (ma in  MyContextHolder.myContextHolder.getNow().accounts.get()) {
                MyLog.i(TAG, ma.toString())
                if (ma.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED) {
                    found = true
                     MyContextHolder.myContextHolder.getNow().accounts.setCurrentAccount(ma)
                    break
                }
            }
        }
        Assert.assertTrue("Found account, which is successfully verified", found)
        Assert.assertTrue("Current account is successfully verified",  MyContextHolder.myContextHolder.getNow().accounts.currentAccount.getCredentialsVerified()
                == CredentialsVerificationStatus.SUCCEEDED)
    }

    fun checkDataPath() {
        if (dataPath.isNotEmpty()) {
            Assert.assertEquals("Data path. " +  MyContextHolder.myContextHolder.getNow(), dataPath,
                     MyContextHolder.myContextHolder.getNow().context.getDatabasePath("andstatus")?.path
            )
        }
    }

    fun getGnuSocialAccount(): MyAccount {
        return getMyAccount(gnusocialTestAccountName)
    }

    fun getPumpioConversationAccount(): MyAccount {
        return getMyAccount(conversationAccountName)
    }

    fun getMyAccount(accountName: String?): MyAccount {
        val ma: MyAccount =  MyContextHolder.myContextHolder.getBlocking().accounts.fromAccountName(accountName)
        Assert.assertTrue("$accountName exists", ma.isValid)
        Assert.assertTrue("Origin for $accountName doesn't exist", ma.origin.isValid())
        return ma
    }

    fun getAccountActorByOid(actorOid: String?): Actor {
        for (ma in  MyContextHolder.myContextHolder.getBlocking().accounts.get()) {
            if (ma.getActorOid() == actorOid) {
                return ma.actor
            }
        }
        return Actor.EMPTY
    }

    fun getPumpioConversationOrigin(): Origin {
        return getPumpioConversationAccount().origin
    }

    fun getGnuSocialOrigin(): Origin {
        return getGnuSocialAccount().origin
    }

    companion object {
        @Volatile
        var demoData: DemoData = DemoData()
        private val TAG: String = DemoData::class.java.simpleName
        private const val HTTP: String = "http://"
        fun crashTest(supplier: BooleanSupplier) {
            if (MyLog.isVerboseEnabled() && supplier.asBoolean) {
                MyLog.e(supplier, "Initiating crash test exception")
                throw NullPointerException("This is a test crash event")
            }
        }

        private fun assertOriginsContext() {
            MyContextHolder.myContextHolder.getNow().origins.collection().forEach(Consumer { obj: Origin -> obj.assertContext() })
        }
    }
}
