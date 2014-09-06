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

package org.andstatus.app.account;

import android.app.Activity;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Intent;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.test.ActivityInstrumentationTestCase2;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.origin.OriginList;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class AccountSettingsActivityTest extends ActivityInstrumentationTestCase2<AccountSettingsActivity> {
    private AccountSettingsActivity mActivity;
    private MyAccount ma = null;

    public AccountSettingsActivityTest() {
        super(AccountSettingsActivity.class);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
        
        ma = MyContextHolder.get().persistentAccounts().getCurrentAccount();
        if (ma == null) {
            fail("No persistent accounts yet");
        }
        
        Intent intent = new Intent();
        intent.putExtra(IntentExtra.EXTRA_ACCOUNT_NAME.key, ma.getAccountName());
        setActivityIntent(intent);
        
        mActivity = getActivity();
    }
    
    public void test() throws InterruptedException {
        Preference addAccountOrVerifyCredentials = mActivity.findPreference(MyPreferences.KEY_VERIFY_CREDENTIALS);
        assertTrue(addAccountOrVerifyCredentials != null);
        EditTextPreference usernameText = (EditTextPreference) mActivity.findPreference(MyAccount.KEY_USERNAME_NEW);
        assertTrue(usernameText != null);
        assertEquals("Selected Username", ma.getUsername(), usernameText.getText());
        Thread.sleep(500);
        assertFalse("MyService is not available", MyServiceManager.isServiceAvailable());
        testOpeningOriginList();
        Thread.sleep(500);
        mActivity.finish();
    }
    
    private void testOpeningOriginList() throws InterruptedException {
        final String method = "testOpeningOriginList";
        ActivityMonitor activityMonitor = getInstrumentation().addMonitor(OriginList.class.getName(), null, false);
        Runnable clicker = new Runnable() {
            @Override
            public void run() {
                MyLog.v(this, method + "-Log before click");
                mActivity.onOriginClick();
            }
          };
    
        MyLog.v(this, method + "-Log before run clicker 1");
        mActivity.runOnUiThread(clicker);
          
        Activity nextActivity = getInstrumentation().waitForMonitorWithTimeout(activityMonitor, 15000);
        MyLog.v(this, method + "-Log after waitForMonitor: " 
                + nextActivity);
        assertNotNull("Next activity is opened and captured", nextActivity);
        TestSuite.waitForListLoaded(this, nextActivity, 8);
        Thread.sleep(500);
        nextActivity.finish();        
    }
    
}
