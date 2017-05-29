/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.view.ViewGroup;
import android.widget.ListView;

import org.andstatus.app.HelpActivity;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.OriginsAndAccountsInserter;
import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.data.ConversationInserter;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.GnuSocialMessagesInserter;
import org.andstatus.app.data.MessageInserter;
import org.andstatus.app.data.MyDataCheckerConversations;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.ExceptionsCounter;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.PersistentTimelinesTest;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.Permissions;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author yvolk@yurivolkov.com
 */
public class TestSuite {
    private static final String TAG = TestSuite.class.getSimpleName();
    private static volatile boolean initialized = false;
    private static volatile Context context;
    private static volatile String dataPath;
    
    public static Context initializeWithData(Object testCase) {
        initialize(testCase);
        ensureDataAdded();
        return getMyContextForTest().context();
    }
    
    public static synchronized Context initialize(Object testCase) {
        final String method = "initialize";
        if (initialized) {
            return context;
        }
        for (int iter=1; iter<6; iter++) {
            MyLog.d(TAG, "Initializing Test Suite, iteration=" + iter);
            if (testCase == null) {
                MyLog.e(TAG, "testCase is null.");
                throw new IllegalArgumentException("testCase is null");
            }
            Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            if (instrumentation == null) {
                MyLog.e(TAG, "testCase.getInstrumentation() is null.");
                throw new IllegalArgumentException("testCase.getInstrumentation() returned null");
            }
            context = instrumentation.getTargetContext();
            if (context == null) {
                MyLog.e(TAG, "targetContext is null.");
                throw new IllegalArgumentException("testCase.getInstrumentation().getTargetContext() returned null");
            }
            MyLog.d(TAG, "Before MyContextHolder.initialize " + iter);
            try {
                MyContextForTest myContextForTest = new MyContextForTest();
                myContextForTest.setContext(MyContextHolder.replaceCreator(myContextForTest));
                MyContextHolder.initialize(context, testCase);
                MyLog.d(TAG, "After MyContextHolder.initialize " + iter);
                break;
            } catch (IllegalStateException e) {
                MyLog.i(TAG, "Error caught, iteration=" + iter, e);
            }
            DbUtils.waitMs(method, 100);
        }
        MyLog.d(TAG, "After Initializing Test Suite loop");
        MyContextHolder.setExecutionMode(
                ExecutionMode.load(InstrumentationRegistry.getArguments().getString("executionMode")));
        assertTrue("MyContext state=" + MyContextHolder.get().state(), MyContextHolder.get().state() != MyContextState.EMPTY);

        SharedPreferencesUtil.putString(MyPreferences.KEY_MIN_LOG_LEVEL, Integer.toString(MyLog.VERBOSE));
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_DOWNLOAD_AND_DISPLAY_ATTACHED_IMAGES, true);
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_ATTACH_IMAGES_TO_MY_MESSAGES, true);
        AsyncTaskLauncher.forget();
        ExceptionsCounter.forget();
        MyLog.forget();
        assertTrue("Log level set to verbose", MyLog.isLoggable(TAG, MyLog.VERBOSE));
        MyServiceManager.setServiceUnavailable();

        if (MyContextHolder.get().state() != MyContextState.READY) {
            MyLog.d(TAG, "MyContext is not ready: " + MyContextHolder.get().state());
            if (MyContextHolder.get().state() == MyContextState.NO_PERMISSIONS) {
                Permissions.setAllGranted(true);
            }
            waitUntilContextIsReady();
        }

        MyLog.d(TAG, "Before check isReady " + MyContextHolder.get());
        initialized =  MyContextHolder.get().isReady();
        assertTrue("Test Suite initialized, MyContext state=" + MyContextHolder.get().state(), initialized);
        dataPath = MyContextHolder.get().context().getDatabasePath("andstatus").getPath();
        MyLog.v("TestSuite", "Test Suite initialized, MyContext state=" + MyContextHolder.get().state() 
                + "; databasePath=" + dataPath);
        
        if (HelpActivity.checkAndUpdateLastOpenedAppVersion(MyContextHolder.get().context(), true)) {
            MyLog.i(TAG, "New version of application is running");
        }
        return context;
    }

    public static String checkDataPath(Object objTag) {
        String dataPath2 = MyContextHolder.get().context().getDatabasePath("andstatus").getPath();
        if (dataPath.equalsIgnoreCase(dataPath2)) {
            return "ok";
        }
        String message =  (MyContextHolder.get().isReady() ? "" : "not ready; ") + dataPath2 + " instead of " + dataPath;
        MyLog.e(objTag, message);
        return message;
    }
    
    public static synchronized void forget() {
        MyLog.d(TAG, "Before forget");
        MyContextHolder.release();
        context = null;
        initialized = false;
    }
    
    public static void waitUntilContextIsReady() {
        final String method = "waitUntilContextIsReady";
        Intent intent = new Intent(MyContextHolder.get().context(), HelpActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        MyContextHolder.get().context().startActivity(intent);
        for (int i=100; i > 0; i--) {
            DbUtils.waitMs(method, 2000);
            MyLog.d(TAG, "Waiting for context " + i + " " + MyContextHolder.get().state());
            switch (MyContextHolder.get().state()) {
                case READY:
                case ERROR:
                    i = 0;
                    break;
                default:
                    break;
            }
        }
        assertEquals("Is Not ready", MyContextState.READY, MyContextHolder.get().state());

        intent = new Intent(MyContextHolder.get().context(), HelpActivity.class);
        intent.putExtra(HelpActivity.EXTRA_CLOSE_ME, true);
        MyContextHolder.get().context().startActivity(intent);
        DbUtils.waitMs(method, 2000);
    }

    public static void clearAssertionData() {
        getMyContextForTest().getData().clear();
    }
    
    public static MyContextForTest getMyContextForTest() {
        if (!(MyContextHolder.get() instanceof MyContextForTest)) {
            fail("Wrong type of current context");
        }
        return (MyContextForTest) MyContextHolder.get();
    }
    
    public static void setHttpConnectionMockClass(Class<? extends HttpConnection> httpConnectionMockClass) {
        getMyContextForTest().setHttpConnectionMockClass(httpConnectionMockClass);
    }

    public static void setHttpConnectionMockInstance(HttpConnection httpConnectionMockInstance) {
        getMyContextForTest().setHttpConnectionMockInstance(httpConnectionMockInstance);
    }

    private static volatile boolean dataAdded = false;
    public static void onDataDeleted() {
        dataAdded = false;
    }

    public static void ensureDataAdded() {
        final String method = "ensureDataAdded";
        MyLog.v(method, method + ": started");
        if (!dataAdded) {
            dataAdded = true;
            MyContextHolder.initialize(context, method);

            final MyAsyncTask<Void, Void, Void> asyncTask
                    = new MyAsyncTask<Void, Void, Void>(method, MyAsyncTask.PoolEnum.QUICK_UI) {
                @Override
                protected Void doInBackground2(Void... params) {
                    MyServiceManager.setServiceUnavailable();
                    new OriginsAndAccountsInserter(getMyContextForTest()).insert();
                    return null;
                }
            };
            AsyncTaskLauncher.execute(method, true, asyncTask);
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
            assertFalse("OriginsAndAccountsInserter failed to complete, count=" + count +
                    ", status=" + asyncTask.getStatus() + ", " + asyncTask.toString(), asyncTask.needsBackgroundWork());

            MyPreferences.onPreferencesChanged();
            MyContextHolder.setExpiredIfConfigChanged();
            MyContextHolder.initialize(context, method);
            MyServiceManager.setServiceUnavailable();
            assertTrue("Context is not ready", MyContextHolder.get().isReady());
            assertEquals("Data path", "ok", TestSuite.checkDataPath(method));
            int size = MyContextHolder.get().persistentAccounts().size();
            assertTrue("Only " + size + " accounts added: " + MyContextHolder.get().persistentAccounts(), size > 5);
            PersistentTimelinesTest.checkDefaultTimelinesForOrigins();
            PersistentTimelinesTest.checkDefaultTimelinesForAccounts();

            new ConversationInserter().insertConversation("");
            new GnuSocialMessagesInserter().insertData();
            assertEquals("Conversations need fixes", 0, new MyDataCheckerConversations(MyContextHolder.get(),
                    ProgressLogger.getEmpty()).countChanges());
            if (MyContextHolder.get().persistentAccounts().size() == 0) {
                fail("No persistent accounts");
            }
            setSuccessfulAccountAsCurrent();
            MyContextHolder.initialize(context, method);
        }
        MyLog.v(method, method + ": ended");
    }

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
    
    public static String getTestOriginHost(String testOriginName) {
        String host = testOriginName.toLowerCase(Locale.US) + "." + TEST_ORIGIN_PARENT_HOST;
        if (testOriginName.equalsIgnoreCase(TWITTER_TEST_ORIGIN_NAME)) {
            host = "api." + host;
        }
        return host;
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
    
    public static Date utcTime(int year, int month, int day, int hour, int minute, int second) {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.set(year, month, day, hour, minute, second);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();        
    }

    public static Date utcTime(long millis) {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(millis);
        return cal.getTime();
    }

    public static int waitForListLoaded(Activity activity, int minCount) throws InterruptedException {
        final String method = "waitForListLoaded";
        final ViewGroup list = (ViewGroup) activity.findViewById(android.R.id.list);
        assertTrue(list != null);
        int itemsCount = 0;
        for (int ind=0; ind<60; ind++) {
            DbUtils.waitMs(method, 2000);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            int itemsCountNew = list.getChildCount();
            if (ListView.class.isInstance(list)) {
                itemsCountNew = ((ListView) list).getCount();
            }
            MyLog.v(TAG, "waitForListLoaded; countNew=" + itemsCountNew + ", prev=" + itemsCount + ", min=" + minCount);
            if (itemsCountNew >= minCount && itemsCount == itemsCountNew) {
                break;
            }
            itemsCount = itemsCountNew;
        }
        assertTrue("There are " + itemsCount + " items (min=" + minCount + ") in the list of " + activity.getClass().getSimpleName(),
                itemsCount >= minCount);
        return itemsCount;
    }

    public static void waitForIdleSync() throws InterruptedException {
        final String method = "waitForIdleSync";
        DbUtils.waitMs(method, 200);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        DbUtils.waitMs(method, 2000);
    }
    
    public static boolean isScreenLocked(Context context) {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return km == null || km.inKeyguardRestrictedInputMode();
    }

    public static boolean setAndWaitForIsInForeground(boolean isInForeground) {
        final String method = "setAndWaitForIsInForeground";
        MyContextHolder.get().setInForeground(isInForeground);
        for (int pass = 0; pass < 300; pass++) {
            if (MyContextHolder.get().isInForeground() == isInForeground) {
                return true;
            }
            if (DbUtils.waitMs(method, 100)) {
                return false;
            }
        }
        return false;
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
        return new MessageInserter(getConversationMyAccount()).buildUserFromOidAndAvatar(
                CONVERSATION_ACCOUNT_USER_OID, CONVERSATION_ACCOUNT_AVATAR_URL);
    }

    public static long getConversationOriginId() {
        return getConversationMyAccount().getOriginId();
    }
}
