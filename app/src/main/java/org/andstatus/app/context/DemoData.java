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
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.account.DemoAccountInserter;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DemoConversationInserter;
import org.andstatus.app.data.DemoGnuSocialConversationInserter;
import org.andstatus.app.data.checker.CheckConversations;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.DemoOriginInserter;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import java.util.Locale;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class DemoData {
    public static volatile DemoData demoData = new DemoData();
    private static final String TAG = DemoData.class.getSimpleName();

    public final String TESTRUN_UID = String.valueOf(System.currentTimeMillis());

    public final String TEST_ORIGIN_PARENT_HOST = "example.com";
    public final String PUMPIO_ORIGIN_NAME = "PumpioTest";
    public final String PUMPIO_MAIN_HOST = "pump1." + TEST_ORIGIN_PARENT_HOST;
    public final String PUMPIO_TEST_ACCOUNT_USERNAME = "t131t@" + PUMPIO_MAIN_HOST;
    public final String PUMPIO_TEST_ACCOUNT_NAME = PUMPIO_TEST_ACCOUNT_USERNAME + "/" + PUMPIO_ORIGIN_NAME;
    public final String PUMPIO_TEST_ACCOUNT_USER_OID = "acct:" + PUMPIO_TEST_ACCOUNT_USERNAME;

    public final String GNUSOCIAL_TEST_ORIGIN_NAME = "GNUsocialTest";
    public final String GNUSOCIAL_TEST_ACCOUNT_USERNAME = "t131t";
    public final String GNUSOCIAL_TEST_ACCOUNT_NAME = GNUSOCIAL_TEST_ACCOUNT_USERNAME + "/" + GNUSOCIAL_TEST_ORIGIN_NAME;
    public final String GNUSOCIAL_TEST_ACCOUNT_USER_OID = "115391";
    public final String GNUSOCIAL_TEST_ACCOUNT_AVATAR_URL = "https://quitter.se/avatar/115686-48-20150106084830.jpeg";
    public final String GNUSOCIAL_TEST_ACCOUNT2_USERNAME = "gtester2";
    public final String GNUSOCIAL_TEST_ACCOUNT2_NAME = GNUSOCIAL_TEST_ACCOUNT2_USERNAME + "/" + GNUSOCIAL_TEST_ORIGIN_NAME;
    public final String GNUSOCIAL_TEST_ACCOUNT2_USER_OID = "8902454";

    public final String TWITTER_TEST_ORIGIN_NAME = "TwitterTest";
    public final String TWITTER_TEST_ACCOUNT_USERNAME = "t131t";
    public final String TWITTER_TEST_ACCOUNT_USER_OID = "144771645";
    public final String TWITTER_TEST_ACCOUNT_NAME = TWITTER_TEST_ACCOUNT_USERNAME + "/" + TWITTER_TEST_ORIGIN_NAME;

    public final String MASTODON_TEST_ORIGIN_NAME = "MastodonTest";
    public final String MASTODON_TEST_ACCOUNT_USERNAME = "t131t1";
    public final String MASTODON_TEST_ACCOUNT_NAME = MASTODON_TEST_ACCOUNT_USERNAME + "/" + MASTODON_TEST_ORIGIN_NAME;
    public final String MASTODON_TEST_ACCOUNT_USER_OID = "37";

    public final OriginType CONVERSATION_ORIGIN_TYPE = OriginType.PUMPIO;
    public final String CONVERSATION_ORIGIN_NAME = PUMPIO_ORIGIN_NAME;
    private final String CONVERSATION_ACCOUNT_USERNAME = "testerofandstatus@" + PUMPIO_MAIN_HOST;
    public final String CONVERSATION_ACCOUNT_NAME = CONVERSATION_ACCOUNT_USERNAME + "/" + CONVERSATION_ORIGIN_NAME;
    public final String CONVERSATION_ACCOUNT_USER_OID = "acct:" + CONVERSATION_ACCOUNT_USERNAME;
    public final String CONVERSATION_ACCOUNT_AVATAR_URL = "http://andstatus.org/images/andstatus-logo.png";
    public final String CONVERSATION_ENTRY_MESSAGE_OID = "http://" + PUMPIO_MAIN_HOST + "/testerofandstatus/comment/thisisfakeuri" + TESTRUN_UID;
    public final String CONVERSATION_ENTRY_AUTHOR_OID = "acct:first@pumpentry.example.com";
    public final String CONVERSATION_AUTHOR_SECOND_USERNAME = "second@" + PUMPIO_MAIN_HOST;
    public final String CONVERSATION_AUTHOR_SECOND_USER_OID = "acct:" + CONVERSATION_AUTHOR_SECOND_USERNAME;
    public final String CONVERSATION_AUTHOR_THIRD_USERNAME = "third@pump3.example.com";
    public final String CONVERSATION_AUTHOR_THIRD_USER_OID = "acct:" + CONVERSATION_AUTHOR_THIRD_USERNAME;
    public final String CONVERSATION_MENTIONS_MESSAGE_OID = "http://" + PUMPIO_MAIN_HOST + "/second/comment/replywithmentions" + TESTRUN_UID;
    public final String CONVERSATION_MENTION_OF_AUTHOR3_OID = "http://" + PUMPIO_MAIN_HOST + "/second/comment/mention3" + TESTRUN_UID;
    public final String HTML_MESSAGE_OID = "http://" + PUMPIO_MAIN_HOST + "/testerofandstatus/comment/htmlfakeuri" + TESTRUN_UID;
    public final String CONVERSATION_ACCOUNT2_USERNAME = "tester2ofandstatus@" + PUMPIO_MAIN_HOST;
    public final String CONVERSATION_ACCOUNT2_NAME = CONVERSATION_ACCOUNT2_USERNAME + "/" + CONVERSATION_ORIGIN_NAME;
    public final String CONVERSATION_ACCOUNT2_USER_OID = "acct:" + CONVERSATION_ACCOUNT2_USERNAME;

    public final String PLAIN_TEXT_MESSAGE_OID = "2167283" + TESTRUN_UID;
    public final String PUBLIC_MESSAGE_TEXT = "UniqueText" + TESTRUN_UID;
    public final String GLOBAL_PUBLIC_MESSAGE_TEXT = "Public_in_AndStatus_" + TESTRUN_UID;
    /** See http://stackoverflow.com/questions/6602417/get-the-uri-of-an-image-stored-in-drawable */
    public final Uri LOCAL_IMAGE_TEST_URI = Uri.parse("android.resource://org.andstatus.app.tests/drawable/icon");
    public final Uri LOCAL_IMAGE_TEST_URI2 = Uri.parse("android.resource://org.andstatus.app/drawable/splash_logo");
    public final Uri IMAGE1_URL = Uri.parse("https://raw.githubusercontent.com/andstatus/andstatus/master/app/src/main/res/drawable/splash_logo.png");

    private volatile String dataPath = "";

    private DemoData() {
        DemoConversationInserter.onNewDemoData();
    }

    void createNewInstance() {
        demoData = new DemoData();
    }

    public String getTestOriginHost(String testOriginName) {
        String host = testOriginName.toLowerCase(Locale.US) + "." + TEST_ORIGIN_PARENT_HOST;
        if (testOriginName.equalsIgnoreCase(TWITTER_TEST_ORIGIN_NAME)) {
            host = "api." + host;
        }
        return host;
    }

    public void add(final MyContext myContext, String dataPathIn) {
        final String method = "add";
        dataPath = dataPathIn;
        MyLog.v(TAG, method + ": started");
        final MyAsyncTask<Void, Void, Void> asyncTask = addAsync(method, myContext, null);
        long count = 50;
        while (count > 0) {
            boolean completedWork = asyncTask.completedBackgroundWork();
            MyLog.v(method, (completedWork ? "Task completed " : "Waiting for task completion ") + count + " "
                    + asyncTask.getStatus());
            if (completedWork) {
                break;
            }
            count--;
            if (DbUtils.waitMs(method, 5000)) {
                break;
            }
        }
        assertEquals("Demo data inserters failed to complete, count=" + count + ", status=" + asyncTask.getStatus()
                + ", " + asyncTask.toString(), true, asyncTask.completedBackgroundWork());
        assertTrue("Error during Demo data creation: " + asyncTask.getFirstError(), asyncTask.getFirstError().isEmpty());
        MyLog.v(TAG, method + ": ended");
    }

    @NonNull
    public MyAsyncTask<Void, Void, Void> addAsync(final String method, final MyContext myContext,
                                                          final ProgressLogger.ProgressCallback progressCallback) {
        final MyAsyncTask<Void, Void, Void> asyncTask
                = new MyAsyncTask<Void, Void, Void>(method, MyAsyncTask.PoolEnum.QUICK_UI) {
            @Override
            protected Void doInBackground2(Void... params) {
                MyLog.i(TAG + "Async", method + ": started");
                if (progressCallback != null) {
                    DbUtils.waitMs(TAG, 3000);
                    progressCallback.onProgressMessage("Generating demo data...");
                    DbUtils.waitMs(TAG, 1000);
                }
                MyLog.v(TAG + "Async", "Before initialize 1");
                MyContextHolder.initialize(myContext.context(), method);
                MyLog.v(TAG + "Async", "After initialize 1");
                MyServiceManager.setServiceUnavailable();
                DemoOriginInserter originInserter = new DemoOriginInserter(myContext);
                originInserter.insert();
                final DemoAccountInserter accountInserter = new DemoAccountInserter(myContext);
                accountInserter.insert();
                myContext.persistentTimelines().saveChanged();

                MyPreferences.onPreferencesChanged();
                MyContextHolder.setExpiredIfConfigChanged();
                MyLog.v(TAG + "Async", "Before initialize 2");
                MyContextHolder.initialize(myContext.context(), method);
                MyLog.v(TAG + "Async", "After initialize 2");
                MyServiceManager.setServiceUnavailable();
                if (progressCallback != null) {
                    progressCallback.onProgressMessage("Demo accounts added...");
                    DbUtils.waitMs(TAG, 1000);
                }
                assertTrue("Context is not ready", MyContextHolder.get().isReady());
                checkDataPath();
                int size = MyContextHolder.get().persistentAccounts().size();
                assertTrue("Only " + size + " accounts added: " + MyContextHolder.get().persistentAccounts(), size > 5);
                originInserter.checkDefaultTimelinesForOrigins();
                accountInserter.checkDefaultTimelinesForAccounts();

                new DemoConversationInserter().insertConversation("");
                new DemoGnuSocialConversationInserter().insertConversation();
                if (progressCallback != null) {
                    progressCallback.onProgressMessage("Demo messages added...");
                    DbUtils.waitMs(TAG, 1000);
                }
                if (MyContextHolder.get().persistentAccounts().size() == 0) {
                    fail("No persistent accounts");
                }
                setSuccessfulAccountAsCurrent();
                Timeline defaultTimeline = MyContextHolder.get().persistentTimelines().getFiltered(
                        false, TriState.TRUE, TimelineType.EVERYTHING, null,
                        MyContextHolder.get().persistentAccounts().getCurrentAccount().getOrigin()).get(0);
                assertThat(defaultTimeline.getTimelineType(), is(TimelineType.EVERYTHING));
                MyContextHolder.get().persistentTimelines().setDefault(defaultTimeline);
                MyLog.v(TAG + "Async", "Before initialize 3");
                MyContextHolder.initialize(myContext.context(), method);
                assertConversations();
                MyLog.v(TAG + "Async", "After initialize 3");
                if (progressCallback != null) {
                    progressCallback.onProgressMessage("Demo data is ready");
                    DbUtils.waitMs(TAG, 1000);
                }
                MyLog.i(TAG + "Async", method + ": ended");
                return null;
            }

            @Override
            protected void onFinish(Void aVoid, boolean success) {
                if (progressCallback != null) {
                    progressCallback.onComplete(success);
                }
            }
        };
        AsyncTaskLauncher.execute(method, true, asyncTask);
        return asyncTask;
    }

    public void assertConversations() {
        assertEquals("Conversations need fixes", 0, new CheckConversations()
                        .setMyContext(MyContextHolder.get()).setLogger(ProgressLogger.getEmpty()).countChanges());
    }

    private void setSuccessfulAccountAsCurrent() {
        MyLog.i(TAG, "Persistent accounts: " + MyContextHolder.get().persistentAccounts().size());
        boolean found = (MyContextHolder.get().persistentAccounts().getCurrentAccount().getCredentialsVerified()
                == MyAccount.CredentialsVerificationStatus.SUCCEEDED);
        if (!found) {
            for (MyAccount ma : MyContextHolder.get().persistentAccounts().list()) {
                MyLog.i(TAG, ma.toString());
                if (ma.getCredentialsVerified()
                == MyAccount.CredentialsVerificationStatus.SUCCEEDED) {
                    found = true;
                    MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
                    break;
                }
            }
        }
        assertTrue("Found account, which is successfully verified", found);
        assertTrue("Current account is successfully verified",
                MyContextHolder.get().persistentAccounts().getCurrentAccount().getCredentialsVerified()
                == MyAccount.CredentialsVerificationStatus.SUCCEEDED);
    }

    public void checkDataPath() {
        if (!TextUtils.isEmpty(dataPath)) {
            assertEquals("Data path. " + MyContextHolder.get(), dataPath,
                    MyContextHolder.get().context().getDatabasePath("andstatus").getPath());
        }
    }

    public MyAccount getConversationMyAccount() {
        return getMyAccount(CONVERSATION_ACCOUNT_NAME);
    }

    @NonNull
    public MyAccount getMyAccount(String accountName) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(accountName);
        assertTrue(accountName + " exists", ma.isValid());
        Origin origin = MyContextHolder.get().persistentOrigins().fromId(ma.getOriginId());
        assertTrue("Origin for " + accountName + " doesn't exist", origin.isValid());
        return ma;
    }

    @NonNull
    public MbUser getAccountUserByOid(String userOid) {
        for (MyAccount ma : MyContextHolder.get().persistentAccounts().list()) {
            if (ma.getUserOid().equals(userOid)) {
                return ma.toPartialUser();
            }
        }
        return MbUser.EMPTY;
    }

    public long getConversationOriginId() {
        return getConversationMyAccount().getOriginId();
    }
}
