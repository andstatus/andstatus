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

package org.andstatus.app;

import junit.framework.TestCase;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.DataInserterTest;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.origin.Origin.OriginEnum;
import org.andstatus.app.util.MyLog;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * @author yvolk@yurivolkov.com
 */
public class TestSuite extends TestCase {
    private static final String TAG = TestSuite.class.getSimpleName();
    private static volatile boolean initialized = false;
    private static volatile Context context;
    private static volatile String dataPath;
    
    public static boolean isInitialized() {
        return initialized;
    }
    
    public static synchronized Context initialize(InstrumentationTestCase testCase) throws NameNotFoundException {
        if (initialized) {
            MyLog.d(TAG, "Already initialized");
            return context;
        }
        for (int iter=0; iter<5; iter++) {
            MyLog.d(TAG, "Initializing Test Suite");
            context = testCase.getInstrumentation().getTargetContext();
            if (context == null) {
                MyLog.e(TAG, "targetContext is null.");
                throw new IllegalArgumentException("this.getInstrumentation().getTargetContext() returned null");
            }
            MyLog.d(TAG, "Before MyContextHolder.initialize " + iter);
            try {
                MyContextForTest myContextForTest = new MyContextForTest();
                myContextForTest.setContext(MyContextHolder.replaceCreator(myContextForTest));
                MyContextHolder.initialize(context, testCase);
                MyLog.d(TAG, "After MyContextHolder.initialize " + iter);
                break;
            } catch (IllegalStateException e) {
                MyLog.w(TAG, "Error caught " + iter);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertTrue("MyContext state=" + MyContextHolder.get().state(), MyContextHolder.get().state() != MyContextState.EMPTY);
        
        if (MyPreferences.shouldSetDefaultValues()) {
            MyLog.d(TAG, "Before setting default preferences");
            // Default values for the preferences will be set only once
            // and in one place: here
            MyPreferences.setDefaultValues(R.xml.preferences_test, false);
            if (MyPreferences.shouldSetDefaultValues()) {
                MyLog.e(TAG, "Default values were not set?!");   
            } else {
                MyLog.i(TAG, "Default values has been set");   
            }
        }
        MyPreferences.getDefaultSharedPreferences().edit().putString(MyPreferences.KEY_MIN_LOG_LEVEL, Integer.toString(MyLog.VERBOSE)).commit();
        MyLog.forget();
        assertTrue("Log level set to verbose", MyLog.isLoggable(TAG, MyLog.VERBOSE));
        MyServiceManager.setServiceUnavailable();

        if (MyContextHolder.get().state() == MyContextState.UPGRADING) {
            MyLog.d(TAG, "Upgrade is needed");
            MyContextHolder.upgradeIfNeeded(TAG);
            waitTillUpgradeEnded();
        }
        MyLog.d(TAG, "Before check isReady " + MyContextHolder.get());
        initialized =  MyContextHolder.get().isReady();
        assertTrue("Test Suite initialized, MyContext state=" + MyContextHolder.get().state(), initialized);
        dataPath = MyContextHolder.get().context().getDatabasePath("andstatus").getPath();
        MyLog.v("TestSuite", "Test Suite initialized, MyContext state=" + MyContextHolder.get().state() 
                + "; databasePath=" + dataPath);
        
        if (MyPreferences.checkAndUpdateLastOpenedAppVersion(MyContextHolder.get().context())) {
            MyLog.i(TAG, "New version of application is running");
        }
        
        return context;
    }

    public static String checkDataPath(Object objTag) {
        String message = "";
        String dataPath2 = MyContextHolder.get().context().getDatabasePath("andstatus").getPath();
        if (dataPath.equalsIgnoreCase(dataPath2)) {
            return "ok";
        }
        message =  (MyContextHolder.get().isReady() ? "" : "not ready; ") + dataPath2 + " instead of " + dataPath;
        MyLog.e(objTag, message);
        return message;
    }
    
    public static synchronized void forget() {
        context = null;
        MyLog.d(TAG, "Before forget");
        MyContextHolder.release();
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
    
    private static volatile boolean dataAdded = false;
    /**
     * This method mimics execution of one test case before another
     * @throws Exception 
     */
    public static void enshureDataAdded() throws Exception {
        MyLog.v(TAG, "enshureDataAdded started");
        if (!dataAdded) {
            dataAdded = true;
            DataInserterTest dataInserter = new DataInserterTest();
            dataInserter.setUp();
            dataInserter.testFollowingUser();
            dataInserter.testMessageFavoritedByOtherUser();
            dataInserter.testMessageFavoritedByAccountUser();
            dataInserter.testDirectMessageToMyAccount();
            dataInserter.testConversation();
        }

        if (MyContextHolder.get().persistentAccounts().size() == 0) {
            fail("No persistent accounts");
        }
        setSuccessfulAccountAsCurrent();
        
        MyLog.v(TAG, "enshureDataAdded ended");
    }
    
    public static final OriginEnum CONVERSATION_ACCOUNT_ORIGIN = OriginEnum.PUMPIO;
    public static final String CONVERSATION_ACCOUNT_NAME = "testerofandstatus@identi.ca/pump.io";
    public static final String CONVERSATION_ACCOUNT_AVATAR_URL = "http://andstatus.org/andstatus/images/AndStatus_logo.png";
    public static final String CONVERSATION_ENTRY_MESSAGE_OID = "http://identi.ca/testerofandstatus/comment/thisisfakeuri" + System.nanoTime();
    
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
    
    public static Date gmtTime(int year, int month, int day, int hour, int minute, int second) {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
        cal.set(year, month, day, hour, minute, second);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();        
    }
}
