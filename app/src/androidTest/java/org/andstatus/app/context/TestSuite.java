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
import android.test.InstrumentationTestCase;
import android.view.ViewGroup;
import android.widget.ListView;

import junit.framework.TestCase;

import org.andstatus.app.HelpActivity;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.ConversationInserter;
import org.andstatus.app.data.GnuSocialMessagesInserter;
import org.andstatus.app.data.MessageInserter;
import org.andstatus.app.data.OriginsAndAccountsInserter;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author yvolk@yurivolkov.com
 */
public class TestSuite extends TestCase {
    private static final String TAG = TestSuite.class.getSimpleName();
    private static volatile boolean initialized = false;
    private static volatile Context context;
    private static volatile String dataPath;
    
    public static Context initializeWithData(InstrumentationTestCase testCase) throws Exception {
        initialize(testCase);
        ensureDataAdded();
        return getMyContextForTest().context();
    }
    
    public static synchronized Context initialize(InstrumentationTestCase testCase) throws InterruptedException {
        if (initialized) {
            MyLog.d(TAG, "Already initialized");
            return context;
        }
        for (int iter=1; iter<6; iter++) {
            MyLog.d(TAG, "Initializing Test Suite, iteration=" + iter);
            if (testCase == null) {
                MyLog.e(TAG, "testCase is null.");
                throw new IllegalArgumentException("testCase is null");
            }
            Instrumentation instrumentation = testCase.getInstrumentation();
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
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        MyLog.d(TAG, "After Initializing Test Suite loop");
        assertTrue("MyContext state=" + MyContextHolder.get().state(), MyContextHolder.get().state() != MyContextState.EMPTY);
        
        SharedPreferencesUtil.getDefaultSharedPreferences().edit()
            .putString(MyPreferences.KEY_MIN_LOG_LEVEL, Integer.toString(MyLog.VERBOSE))
            .putBoolean(MyPreferences.KEY_DOWNLOAD_AND_DISPLAY_ATTACHED_IMAGES, true)
            .putBoolean(MyPreferences.KEY_ATTACH_IMAGES_TO_MY_MESSAGES, true)
            .apply();
        AsyncTaskLauncher.forget();
        MyLog.forget();
        assertTrue("Log level set to verbose", MyLog.isLoggable(TAG, MyLog.VERBOSE));
        MyServiceManager.setServiceUnavailable();

        if (MyContextHolder.get().state() == MyContextState.UPGRADING) {
            MyLog.d(TAG, "Upgrade is needed");
            Intent intent = new Intent(MyContextHolder.get().context(), HelpActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            MyContextHolder.get().context().startActivity(intent);
            waitTillUpgradeEnded();
            Thread.sleep(500);
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
    
    public static void waitTillUpgradeEnded() {
        for (int i=1; i < 100; i++) {
            if(MyContextHolder.get().isReady()) {
                break;
            }
            MyLog.d(TAG, "Waiting for upgrade to end " + i);
            try {
                Thread.sleep(2000);
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }            
        }
        assertTrue("Is Ready now", MyContextHolder.get().isReady());
    }

    public static void clearAssertionData() {
        getMyContextForTest().getData().clear();
    }
    
    public static MyContextForTest getMyContextForTest() {
        MyContextForTest myContextForTest = null;
        if (MyContextHolder.get() instanceof MyContextForTest) {
            myContextForTest = (MyContextForTest) MyContextHolder.get(); 
        } else {
            fail("Wrong type of current context");
        }
        return myContextForTest;
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

    /**
     * This method mimics execution of one test case before another
     * @throws Exception 
     */
    public static void ensureDataAdded() throws Exception {
        String method = "ensureDataAdded";
        MyLog.v(TAG, method + ": started");
        if (!dataAdded) {
            dataAdded = true;
            new OriginsAndAccountsInserter(getMyContextForTest()).insert();
            new ConversationInserter().insertConversation("");
            new GnuSocialMessagesInserter().insertData();
        }

        if (MyContextHolder.get().persistentAccounts().size() == 0) {
            fail("No persistent accounts");
        }
        MyContextHolder.initialize(context, TAG);
        setSuccessfulAccountAsCurrent();
        
        MyLog.v(TAG, method + ": ended");
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
            for (MyAccount ma : MyContextHolder.get().persistentAccounts().collection()) {
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

    public static int waitForListLoaded(InstrumentationTestCase instrumentationTestCase, Activity activity, int minCount) throws InterruptedException {
        return waitForListLoaded(instrumentationTestCase.getInstrumentation(), activity, minCount);
    }

    public static int waitForListLoaded(Instrumentation instrumentation, Activity activity, int minCount) throws InterruptedException {
        final ViewGroup list = (ViewGroup) activity.findViewById(android.R.id.list);
        assertTrue(list != null);
        int itemsCount = 0;
        for (int ind=0; ind<60; ind++) {
            Thread.sleep(2000);
            instrumentation.waitForIdleSync();
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

    public static void waitForIdleSync(InstrumentationTestCase instrumentationTestCase) throws InterruptedException {
        waitForIdleSync(instrumentationTestCase.getInstrumentation());
    }

    public static void waitForIdleSync(Instrumentation instrumentation) throws InterruptedException {
        Thread.sleep(200);
        instrumentation.waitForIdleSync();
        Thread.sleep(2000);
    }
    
    public static boolean isScreenLocked(Context context) {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return km == null || km.inKeyguardRestrictedInputMode();
    }

    public static boolean setAndWaitForIsInForeground(boolean isInForeground) {
        MyContextHolder.get().setInForeground(isInForeground);
        for (int pass = 0; pass < 300; pass++) {
            if (MyContextHolder.get().isInForeground() == isInForeground) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    public static MyAccount getConversationMyAccount() {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(CONVERSATION_ACCOUNT_NAME); 
        assertTrue(CONVERSATION_ACCOUNT_NAME + " exists", ma.isValid());
        Origin origin = MyContextHolder.get().persistentOrigins().fromId(ma.getOriginId());
        assertTrue("Origin for " + ma.getAccountName() + " exists", origin != null);
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
