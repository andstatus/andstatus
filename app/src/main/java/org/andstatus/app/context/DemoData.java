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

package org.andstatus.app.context;

import android.net.Uri;

import androidx.annotation.NonNull;

import org.andstatus.app.FirstActivity;
import org.andstatus.app.account.CredentialsVerificationStatus;
import org.andstatus.app.account.DemoAccountInserter;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DemoConversationInserter;
import org.andstatus.app.data.DemoGnuSocialConversationInserter;
import org.andstatus.app.data.checker.CheckConversations;
import org.andstatus.app.data.checker.DataChecker;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.DemoOriginInserter;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginPumpio;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.ExceptionsCounter;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class DemoData {
    public static volatile DemoData demoData = new DemoData();
    private static final String TAG = DemoData.class.getSimpleName();
    private static final String HTTP = "http://";

    public final String testRunUid = MyLog.uniqueDateTimeFormatted();
    public final AtomicInteger conversationIterationCounter = new AtomicInteger(0);

    public final String t131tUsername = "t131t";
    public final String testOriginParentHost = "example.com";

    public final String activityPubUsername = "apTestUser";
    public final String activityPubTestOriginName = "activityPubTest";
    public final String activityPubMainHost = "ap1." + testOriginParentHost;
    public final String activityPubTestAccountUniqueName = activityPubUsername + "@" + activityPubMainHost;
    public final String activityPubTestAccountName = activityPubTestAccountUniqueName + "/" + activityPubTestOriginName;
    public final String activityPubTestAccountActorOid = "https://" + activityPubMainHost + "/users/" + activityPubUsername;

    public final String pumpioOriginName = "PumpioTest";
    public final String pumpioMainHost = "pump1." + testOriginParentHost;
    public final String pumpioSecondHost = "pump2." + testOriginParentHost;
    public final String pumpioTestAccountUniqueName = t131tUsername + "@" + pumpioMainHost;
    public final String pumpioTestAccountName = pumpioTestAccountUniqueName + "/" + pumpioOriginName;
    public final String pumpioTestAccountActorOid = OriginPumpio.ACCOUNT_PREFIX + pumpioTestAccountUniqueName;

    public final String gnusocialTestOriginName = "GNUsocialTest";
    public final String gnusocialTestHost = "gnusocial." + testOriginParentHost;
    public final String gnusocialTestAccountUsername = t131tUsername;
    public final String gnusocialTestAccountName = gnusocialTestAccountUsername + "@" + gnusocialTestHost +
            "/" + OriginType.GNUSOCIAL.getTitle();
    public final String gnusocialTestAccountActorOid = "115391";
    public final String gnusocialTestAccountAvatarUrl = "https://findicons.com/files/icons/2036/farm/48/rabbit.png";
    public final String gnusocialTestAccount2Username = "gtester2";
    public final String gnusocialTestAccount2Name = gnusocialTestAccount2Username  + "@" + gnusocialTestHost +
            "/" + OriginType.GNUSOCIAL.getTitle();
    public final String gnusocialTestAccount2ActorOid = "8902454";

    public final String twitterTestOriginName = "TwitterTest";
    public final String twitterTestHostWithoutApiDot = "twitter." + testOriginParentHost;
    public final String twitterTestHost = "api." + twitterTestHostWithoutApiDot;
    public final String twitterTestAccountUsername = t131tUsername;
    public final String twitterTestAccountActorOid = "144771645";
    public final String twitterTestAccountName = twitterTestAccountUsername + "@" + twitterTestHostWithoutApiDot +
            "/" + OriginType.TWITTER.getTitle();

    public final String mastodonTestOriginName = "MastodonTest";
    public final String mastodonTestHost = "mastodon." + testOriginParentHost;
    public final String mastodonTestAccountUsername = "t131t1";
    public final String mastodonTestAccountName = mastodonTestAccountUsername + "@" + mastodonTestHost +
            "/" + OriginType.MASTODON.getTitle();
    public final String mastodonTestAccountActorOid = "37";

    public final OriginType conversationOriginType = OriginType.PUMPIO;
    public final String conversationOriginName = pumpioOriginName;
    private final String conversationAccountUniqueName = "testerofandstatus@" + pumpioMainHost;
    public final String conversationAccountName = conversationAccountUniqueName + "/" + conversationOriginName;
    public final String conversationAccountActorOid = OriginPumpio.ACCOUNT_PREFIX + conversationAccountUniqueName;
    public final String conversationAccountAvatarUrl = "http://andstatus.org/images/andstatus-logo.png";
    public final String conversationEntryNoteOid = HTTP + pumpioMainHost
            + "/testerofandstatus/comment/thisisfakeuri" + testRunUid;
    public final String conversationEntryAuthorOid = "acct:first@pumpentry.example.com";
    public final String conversationAuthorSecondUniqueName = "second@" + pumpioMainHost;
    public final String conversationAuthorSecondActorOid = OriginPumpio.ACCOUNT_PREFIX + conversationAuthorSecondUniqueName;
    public final String conversationAuthorThirdUniqueName = "third@pump3.example.com";
    public final String conversationAuthorThirdActorOid = OriginPumpio.ACCOUNT_PREFIX + conversationAuthorThirdUniqueName;
    public final String conversationMentionsNoteOid = HTTP + pumpioMainHost + "/second/comment/replywithmentions" + testRunUid;
    public final String conversationMentionOfAuthor3Oid = HTTP + pumpioMainHost + "/second/comment/mention3" + testRunUid;
    public final String htmlNoteOid = HTTP + pumpioMainHost + "/testerofandstatus/comment/htmlfakeuri" + testRunUid;
    public final String conversationAccountSecondUniqueName = "tester2ofandstatus@" + pumpioSecondHost;
    public final String conversationAccountSecondName = conversationAccountSecondUniqueName + "/" + conversationOriginName;
    public final String conversationAccountSecondActorOid = OriginPumpio.ACCOUNT_PREFIX + conversationAccountSecondUniqueName;

    public final String plainTextNoteOid = "2167283" + testRunUid;
    public final String publicNoteText = "UniqueText" + testRunUid;
    public final String globalPublicNoteText = "Public_in_AndStatus_" + testRunUid;
    /** See http://stackoverflow.com/questions/6602417/get-the-uri-of-an-image-stored-in-drawable */
    public final Uri localImageTestUri = Uri.parse("android.resource://org.andstatus.app.tests/drawable/icon");
    public final Uri localImageTestUri2 = Uri.parse("android.resource://org.andstatus.app/drawable/splash_logo");
    public final Uri localVideoTestUri = Uri.parse("android.resource://org.andstatus.app.tests/drawable/video320");
    public final Uri image1Url = Uri.parse("https://raw.githubusercontent.com/andstatus/andstatus/master/app/src/main/res/drawable/splash_logo.png");

    private volatile String dataPath = "";

    void createNewInstance() {
        demoData = new DemoData();
    }

    public void add(final MyContext myContext, String dataPathIn) {
        final String method = "add";
        dataPath = dataPathIn;
        MyLog.v(TAG, method + ": started");
        final MyAsyncTask<Void, Void, Void> asyncTask = addAsync(myContext, ProgressLogger.EMPTY_LISTENER);
        long count = 200;
        while (count > 0) {
            boolean completedWork = asyncTask.completedBackgroundWork();
            MyLog.v(method, (completedWork ? "Task completed " : "Waiting for task completion ") + count + " "
                    + asyncTask.getStatus());
            if (completedWork || DbUtils.waitMs(method, 5000)) {
                break;
            }
            count--;
        }
        if (ExceptionsCounter.firstError.get() != null) {
            fail("Error during Demo data creation: " + ExceptionsCounter.firstError.get());
        }
        assertEquals("Demo data creation failed, count=" + count + ", status=" + asyncTask.getStatus()
                + ", " + asyncTask.toString(), true, asyncTask.completedBackgroundWork());
        assertTrue("Error during Demo data creation: " + asyncTask.getFirstError(), asyncTask.getFirstError().isEmpty());
        MyLog.v(TAG, method + ": ended");
    }

    private static class MyAsyncTaskDemoData extends MyAsyncTask<Void, Void, Void> {
        final ProgressLogger.ProgressListener progressListener;
        final String logTag;
        final MyContext myContext;
        final DemoData demoData;

        private MyAsyncTaskDemoData(ProgressLogger.ProgressListener progressListener, MyContext myContext, DemoData demoData) {
            super(progressListener.getLogTag(), MyAsyncTask.PoolEnum.thatCannotBeShutDown());
            this.progressListener = progressListener;
            this.logTag = progressListener.getLogTag();
            this.myContext = myContext;
            this.demoData = demoData;
        }

        @Override
        protected Void doInBackground2(Void aVoid) {
            MyLog.i(logTag, logTag + ": started");

            DbUtils.waitMs(logTag, 1000);
            progressListener.onProgressMessage("Generating demo data...");
            DbUtils.waitMs(logTag, 500);

            MyLog.v(logTag, "Before initialize 1");
            myContextHolder.getInitialized(myContext.context(), logTag);
            MyLog.v(logTag, "After initialize 1");
            MyServiceManager.setServiceUnavailable();
            DemoOriginInserter originInserter = new DemoOriginInserter(myContext);
            originInserter.insert();
            final DemoAccountInserter accountInserter = new DemoAccountInserter(myContext);
            accountInserter.insert();
            myContext.timelines().saveChanged();

            MyPreferences.onPreferencesChanged();
            myContextHolder.setExpiredIfConfigChanged();
            MyLog.v(logTag, "Before initialize 2");
            myContextHolder.getInitialized(myContext.context(), logTag);
            MyLog.v(logTag, "After initialize 2");
            MyServiceManager.setServiceUnavailable();

            progressListener.onProgressMessage("Demo accounts added...");
            DbUtils.waitMs(logTag, 500);

            assertTrue("Context is not ready " + myContextHolder.getNow(), myContextHolder.getNow().isReady());
            demoData.checkDataPath();
            int size = myContextHolder.getNow().accounts().size();
            assertTrue("Only " + size + " accounts added: " + myContextHolder.getNow().accounts(),
                    size > 5);
            assertEquals("No WebfingerId", Optional.empty(), myContextHolder.getNow().accounts()
                    .get().stream().filter(ma -> !ma.getActor().isWebFingerIdValid()).findFirst());
            int size2 = myContextHolder.getNow().users().size();
            assertTrue("Only " + size2 + " users added: " + myContextHolder.getNow().users()
                            + "\nAccounts: " + myContextHolder.getNow().accounts(),
                    size2 >= size);

            assertOriginsContext();
            DemoOriginInserter.assertDefaultTimelinesForOrigins();
            DemoAccountInserter.assertDefaultTimelinesForAccounts();

            demoData.insertPumpIoConversation("");
            new DemoGnuSocialConversationInserter().insertConversation();

            progressListener.onProgressMessage("Demo notes added...");
            DbUtils.waitMs(logTag, 500);

            if (myContextHolder.getNow().accounts().size() == 0) {
                fail("No persistent accounts");
            }
            demoData.setSuccessfulAccountAsCurrent();
            Timeline defaultTimeline = myContextHolder.getNow().timelines().filter(
                    false, TriState.TRUE, TimelineType.EVERYTHING, Actor.EMPTY,
                    myContextHolder.getNow().accounts().getCurrentAccount().getOrigin())
                    .findFirst().orElse(Timeline.EMPTY);
            assertThat(defaultTimeline.getTimelineType(), is(TimelineType.EVERYTHING));
            myContextHolder.getNow().timelines().setDefault(defaultTimeline);

            MyLog.v(logTag, "Before initialize 3");
            myContextHolder.getInitialized(myContext.context(), logTag);
            MyLog.v(logTag, "After initialize 3");

            assertOriginsContext();
            DemoOriginInserter.assertDefaultTimelinesForOrigins();
            DemoAccountInserter.assertDefaultTimelinesForAccounts();

            assertEquals("Data errors exist", 0 , DataChecker.fixData(
                    new ProgressLogger(progressListener), true, true));
            MyLog.v(logTag, "After data checker");

            progressListener.onProgressMessage("Demo data is ready");
            DbUtils.waitMs(logTag, 500);

            MyLog.i(logTag, logTag + ": ended");
            return null;
        }

        @Override
        protected void onFinish(Void aVoid, boolean success) {
            FirstActivity.checkAndUpdateLastOpenedAppVersion(myContext.context(), true);
            if (progressListener != null) progressListener.onComplete(success);
        }
    }

    @NonNull
    public MyAsyncTask<Void, Void, Void> addAsync(final MyContext myContext,
                                                  final ProgressLogger.ProgressListener progressListener) {
        MyAsyncTaskDemoData asyncTask = new MyAsyncTaskDemoData(progressListener, myContext, this);
        AsyncTaskLauncher.execute(progressListener.getLogTag(), asyncTask);
        return asyncTask;
    }

    public void insertPumpIoConversation(String bodySuffix) {
        new DemoConversationInserter().insertConversation(bodySuffix);
    }

    public void assertConversations() {
        assertEquals("Conversations need fixes", 0,
                new CheckConversations()
                        .setMyContext(myContextHolder.getNow()).setLogger(ProgressLogger.getEmpty("CheckConversations"))
                        .setCountOnly(true)
                        .fix());
    }

    private void setSuccessfulAccountAsCurrent() {
        MyLog.i(TAG, "Persistent accounts: " + myContextHolder.getNow().accounts().size());
        boolean found = (myContextHolder.getNow().accounts().getCurrentAccount().getCredentialsVerified()
                == CredentialsVerificationStatus.SUCCEEDED);
        if (!found) {
            for (MyAccount ma : myContextHolder.getNow().accounts().get()) {
                MyLog.i(TAG, ma.toString());
                if (ma.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED) {
                    found = true;
                    myContextHolder.getNow().accounts().setCurrentAccount(ma);
                    break;
                }
            }
        }
        assertTrue("Found account, which is successfully verified", found);
        assertTrue("Current account is successfully verified",
                myContextHolder.getNow().accounts().getCurrentAccount().getCredentialsVerified()
                == CredentialsVerificationStatus.SUCCEEDED);
    }

    public void checkDataPath() {
        if (!StringUtil.isEmpty(dataPath)) {
            assertEquals("Data path. " + myContextHolder.getNow(), dataPath,
                    myContextHolder.getNow().context().getDatabasePath("andstatus").getPath());
        }
    }

    public MyAccount getGnuSocialAccount() {
        return getMyAccount(gnusocialTestAccountName);
    }

    public MyAccount getPumpioConversationAccount() {
        return getMyAccount(conversationAccountName);
    }

    @NonNull
    public MyAccount getMyAccount(String accountName) {
        MyAccount ma = myContextHolder.getNow().accounts().fromAccountName(accountName);
        assertTrue(accountName + " exists", ma.isValid());
        assertTrue("Origin for " + accountName + " doesn't exist", ma.getOrigin().isValid());
        return ma;
    }

    @NonNull
    public Actor getAccountActorByOid(String actorOid) {
        for (MyAccount ma : myContextHolder.getNow().accounts().get()) {
            if (ma.getActorOid().equals(actorOid)) {
                return ma.getActor();
            }
        }
        return Actor.EMPTY;
    }

    public Origin getPumpioConversationOrigin() {
        return getPumpioConversationAccount().getOrigin();
    }

    public Origin getGnuSocialOrigin() {
        return getGnuSocialAccount().getOrigin();
    }

    public static void crashTest(BooleanSupplier supplier) {
        if (MyLog.isVerboseEnabled() && supplier.getAsBoolean()) {
            MyLog.e(supplier, "Initiating crash test exception");
            throw new NullPointerException("This is a test crash event");
        }
    }

    private static void assertOriginsContext() {
        myContextHolder.getNow().origins().collection().forEach(Origin::assertContext);
    }
}
