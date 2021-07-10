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

import android.net.Uri
import org.andstatus.app.account.CredentialsVerificationStatus
import org.andstatus.app.account.MyAccount
import org.andstatus.app.backup.ProgressLogger
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DemoConversationInserter
import org.andstatus.app.data.checker.CheckConversations
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginPumpio
import org.andstatus.app.origin.OriginType
import org.andstatus.app.os.AsyncRunnable
import org.andstatus.app.os.ExceptionsCounter
import org.andstatus.app.util.MyLog
import org.junit.Assert
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
    val localImageTestUri: Uri = Uri.parse("android.resource://org.andstatus.app.test/drawable/icon")
    val localImageTestUri2: Uri = Uri.parse("android.resource://org.andstatus.app/drawable/splash_logo")
    val localVideoTestUri: Uri = Uri.parse("android.resource://org.andstatus.app.test/raw/video320_mp4")
    val localGifTestUri: Uri = Uri.parse("android.resource://org.andstatus.app.test/raw/sample_gif")
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
            MyLog.v(this, "$method; " + (if (asyncTask.isFinished) "Task completed " else "Waiting for task completion ") + count)
            if (asyncTask.isFinished || DbUtils.waitMs(method, 5000)) {
                break
            }
            count--
        }
        if (ExceptionsCounter.firstError.get() != null) {
            Assert.fail("Error during Demo data creation: " + ExceptionsCounter.firstError.get())
        }
        Assert.assertEquals("Demo data creation failed, count=" + count +
                ", $asyncTask", true, asyncTask.noMoreBackgroundWork
        )
        Assert.assertTrue("Error during Demo data creation: " + asyncTask.firstError + ", $asyncTask",
                asyncTask.firstError.isEmpty())
        MyLog.v(TAG, "$method; ended")
    }

    fun addAsync(myContext: MyContext, progressListener: ProgressLogger.ProgressListener): AsyncRunnable {
        val asyncTask = GenerateDemoData(progressListener, myContext, this)
        asyncTask.execute(this, Unit)
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

    fun setSuccessfulAccountAsCurrent() {
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

        fun assertOriginsContext() {
            MyContextHolder.myContextHolder.getNow().origins.collection().forEach(Consumer { obj: Origin -> obj.assertContext() })
        }
    }
}
