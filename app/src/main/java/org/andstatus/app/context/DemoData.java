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
import org.andstatus.app.data.DemoConversationInserter;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DemoGnuSocialMessagesInserter;
import org.andstatus.app.data.DemoMessageInserter;
import org.andstatus.app.data.MyDataCheckerConversations;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.DemoOriginInserter;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class DemoData {
    private static final String TAG = DemoData.class.getSimpleName();

    public static final String TESTRUN_UID = String.valueOf(System.currentTimeMillis());

    private static final String TEST_ORIGIN_PARENT_HOST = "example.com";
    public static final String PUMPIO_ORIGIN_NAME = "PumpioTest";
    private static final String PUMPIO_TEST_ACCOUNT_USERNAME = "t131t@identi.ca";
    public static final String PUMPIO_TEST_ACCOUNT_NAME = PUMPIO_TEST_ACCOUNT_USERNAME + "/" + PUMPIO_ORIGIN_NAME;
    public static final String PUMPIO_TEST_ACCOUNT_USER_OID = "acct:" + PUMPIO_TEST_ACCOUNT_USERNAME;

    public static final String GNUSOCIAL_TEST_ORIGIN_NAME = "GNUsocialTest";
    public static final String GNUSOCIAL_TEST_ACCOUNT_USERNAME = "t131t";
    public static final String GNUSOCIAL_TEST_ACCOUNT_NAME = GNUSOCIAL_TEST_ACCOUNT_USERNAME + "/" + GNUSOCIAL_TEST_ORIGIN_NAME;
    public static final String GNUSOCIAL_TEST_ACCOUNT_USER_OID = "115391";
    public static final String GNUSOCIAL_TEST_ACCOUNT_AVATAR_URL = "https://quitter.se/avatar/115686-48-20150106084830.jpeg";
    public static final String GNUSOCIAL_TEST_ACCOUNT2_USERNAME = "gtester2";
    public static final String GNUSOCIAL_TEST_ACCOUNT2_NAME = GNUSOCIAL_TEST_ACCOUNT2_USERNAME + "/" + GNUSOCIAL_TEST_ORIGIN_NAME;
    public static final String GNUSOCIAL_TEST_ACCOUNT2_USER_OID = "8902454";

    public static final String TWITTER_TEST_ORIGIN_NAME = "TwitterTest";
    public static final String TWITTER_TEST_ACCOUNT_USERNAME = "t131t";
    public static final String TWITTER_TEST_ACCOUNT_USER_OID = "144771645";
    public static final String TWITTER_TEST_ACCOUNT_NAME = TWITTER_TEST_ACCOUNT_USERNAME + "/" + TWITTER_TEST_ORIGIN_NAME;

    public static final String MASTODON_TEST_ORIGIN_NAME = "MastodonTest";
    public static final String MASTODON_TEST_ACCOUNT_USERNAME = "t131t1";
    public static final String MASTODON_TEST_ACCOUNT_NAME = MASTODON_TEST_ACCOUNT_USERNAME + "/" + MASTODON_TEST_ORIGIN_NAME;
    public static final String MASTODON_TEST_ACCOUNT_USER_OID = "37";

    public static final OriginType CONVERSATION_ORIGIN_TYPE = OriginType.PUMPIO;
    public static final String CONVERSATION_ORIGIN_NAME = PUMPIO_ORIGIN_NAME;
    private static final String CONVERSATION_ACCOUNT_USERNAME = "testerofandstatus@identi.ca";
    public static final String CONVERSATION_ACCOUNT_NAME = CONVERSATION_ACCOUNT_USERNAME + "/" + CONVERSATION_ORIGIN_NAME;
    public static final String CONVERSATION_ACCOUNT_USER_OID = "acct:" + CONVERSATION_ACCOUNT_USERNAME;
    public static final String CONVERSATION_ACCOUNT_AVATAR_URL = "http://andstatus.org/images/andstatus-logo.png";
    public static final String CONVERSATION_ENTRY_MESSAGE_OID = "http://identi.ca/testerofandstatus/comment/thisisfakeuri" + TESTRUN_UID;
    public static final String CONVERSATION_ENTRY_USER_OID = "acct:first@example.net";
    public static final String CONVERSATION_MEMBER_USERNAME = "third@pump.example.com";
    public static final String CONVERSATION_MEMBER_USER_OID = "acct:" + CONVERSATION_MEMBER_USERNAME;
    public static final String CONVERSATION_MENTIONS_MESSAGE_OID = "http://identi.ca/second/comment/replywithmentions" + TESTRUN_UID;
    public static final String HTML_MESSAGE_OID = "http://identi.ca/testerofandstatus/comment/htmlfakeuri" + TESTRUN_UID;
    private static final String CONVERSATION_ACCOUNT2_USERNAME = "tester2ofandstatus@identi.ca";
    public static final String CONVERSATION_ACCOUNT2_NAME = CONVERSATION_ACCOUNT2_USERNAME + "/" + CONVERSATION_ORIGIN_NAME;
    public static final String CONVERSATION_ACCOUNT2_USER_OID = "acct:" + CONVERSATION_ACCOUNT2_USERNAME;

    public static final String PLAIN_TEXT_MESSAGE_OID = "2167283" + TESTRUN_UID;
    public static final String PUBLIC_MESSAGE_TEXT = "UniqueText" + TESTRUN_UID;
    public static final String GLOBAL_PUBLIC_MESSAGE_TEXT = "Public_in_AndStatus_" + TESTRUN_UID;
    /** See http://stackoverflow.com/questions/6602417/get-the-uri-of-an-image-stored-in-drawable */
    public static final Uri LOCAL_IMAGE_TEST_URI = Uri.parse("android.resource://org.andstatus.app.tests/drawable/icon");
    public static final Uri LOCAL_IMAGE_TEST_URI2 = Uri.parse("android.resource://org.andstatus.app/drawable/splash_logo");
    public static final Uri IMAGE1_URL = Uri.parse("https://raw.githubusercontent.com/andstatus/andstatus/master/app/src/main/res/drawable/splash_logo.png");

    private static volatile String dataPath = "";

    private DemoData() {
        // Empty
    }

    public static String getTestOriginHost(String testOriginName) {
        String host = testOriginName.toLowerCase(Locale.US) + "." + TEST_ORIGIN_PARENT_HOST;
        if (testOriginName.equalsIgnoreCase(TWITTER_TEST_ORIGIN_NAME)) {
            host = "api." + host;
        }
        return host;
    }

    public static void add(final MyContext myContext, String dataPathIn) {
        final String method = "add";
        dataPath = dataPathIn;
        MyLog.v(TAG, method + ": started");
        final MyAsyncTask<Void, Void, Void> asyncTask = addAsync(method, myContext, null);
        long count = 50;
        while (count > 0) {
            boolean needsWork = asyncTask.needsBackgroundWork();
            MyLog.v(method, (needsWork ? "Waiting for task completion " : "Task completed ") + count + " "
                    + asyncTask.getStatus());
            if (!needsWork) {
                break;
            }
            count--;
            if (DbUtils.waitMs(method, 5000)) {
                break;
            }
        }
        assertFalse("Demo data inserters failed to complete, count=" + count +
                ", status=" + asyncTask.getStatus() + ", " + asyncTask.toString(), asyncTask.needsBackgroundWork());
        MyLog.v(TAG, method + ": ended");
    }

    @NonNull
    public static MyAsyncTask<Void, Void, Void> addAsync(final String method, final MyContext myContext,
                                                          final ProgressLogger.ProgressCallback progressCallback) {
        MyContextHolder.initialize(myContext.context(), method);
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
                MyServiceManager.setServiceUnavailable();
                new DemoOriginInserter(myContext).insert();
                new DemoAccountInserter(myContext).insert();
                myContext.persistentTimelines().saveChanged();

                MyPreferences.onPreferencesChanged();
                MyContextHolder.setExpiredIfConfigChanged();
                MyContextHolder.initialize(myContext.context(), method);
                MyServiceManager.setServiceUnavailable();
                if (progressCallback != null) {
                    progressCallback.onProgressMessage("Demo accounts added...");
                    DbUtils.waitMs(TAG, 1000);
                }
                assertTrue("Context is not ready", MyContextHolder.get().isReady());
                checkDataPath();
                int size = MyContextHolder.get().persistentAccounts().size();
                assertTrue("Only " + size + " accounts added: " + MyContextHolder.get().persistentAccounts(), size > 5);
                DemoOriginInserter.checkDefaultTimelinesForOrigins();
                DemoAccountInserter.checkDefaultTimelinesForAccounts();

                new DemoConversationInserter().insertConversation("");
                new DemoGnuSocialMessagesInserter().insertData();
                if (progressCallback != null) {
                    progressCallback.onProgressMessage("Demo messages added...");
                    DbUtils.waitMs(TAG, 1000);
                }
                assertEquals("Conversations need fixes", 0, new MyDataCheckerConversations(MyContextHolder.get(),
                        ProgressLogger.getEmpty()).countChanges());
                if (MyContextHolder.get().persistentAccounts().size() == 0) {
                    fail("No persistent accounts");
                }
                setSuccessfulAccountAsCurrent();
                MyPreferences.setDefaultTimelineId(
                        MyContextHolder.get().persistentTimelines().getFiltered(false, TriState.TRUE,
                                MyContextHolder.get().persistentAccounts().getCurrentAccount(), null).get(0).getId());
                MyContextHolder.initialize(myContext.context(), method);
                if (progressCallback != null) {
                    progressCallback.onProgressMessage("Demo data is ready");
                    DbUtils.waitMs(TAG, 1000);
                }
                MyLog.i(TAG + "Async", method + ": ended");
                return null;
            }

            @Override
            protected void onCancelled() {
                super.onCancelled();
                if (progressCallback != null) {
                    progressCallback.onComplete(false);
                }
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                if (progressCallback != null) {
                    progressCallback.onComplete(true);
                }
            }
        };
        AsyncTaskLauncher.execute(method, true, asyncTask);
        return asyncTask;
    }

    private static void setSuccessfulAccountAsCurrent() {
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

    public static void checkDataPath() {
        if (!TextUtils.isEmpty(dataPath)) {
            assertEquals("Data path. " + MyContextHolder.get(), dataPath,
                    MyContextHolder.get().context().getDatabasePath("andstatus").getPath());
        }
    }

    public static MyAccount getConversationMyAccount() {
        return getMyAccount(CONVERSATION_ACCOUNT_NAME);
    }

    @NonNull
    public static MyAccount getMyAccount(String accountName) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(accountName);
        assertTrue(accountName + " exists", ma.isValid());
        Origin origin = MyContextHolder.get().persistentOrigins().fromId(ma.getOriginId());
        assertTrue("Origin for " + accountName + " doesn't exist", origin != null);
        return ma;
    }

    public static MbUser getConversationMbUser() {
        return new DemoMessageInserter(getConversationMyAccount()).buildUserFromOidAndAvatar(
                CONVERSATION_ACCOUNT_USER_OID, CONVERSATION_ACCOUNT_AVATAR_URL);
    }

    public static long getConversationOriginId() {
        return getConversationMyAccount().getOriginId();
    }
}
