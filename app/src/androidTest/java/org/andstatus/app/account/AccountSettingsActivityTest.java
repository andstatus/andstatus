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
import android.test.ActivityInstrumentationTestCase2;
import android.widget.Button;
import android.widget.TextView;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MySettingsActivity;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.origin.PersistentOriginList;
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
        if (!ma.isValid()) {
            fail("No persistent accounts yet");
        }
        
        Intent intent = new Intent();
        intent.putExtra(IntentExtra.ACCOUNT_NAME.key, ma.getAccountName());
        setActivityIntent(intent);
        
        mActivity = getActivity();
    }
    
    public void test() throws InterruptedException {
        final String method = "test";
        Button addAccountOrVerifyCredentials = (Button) mActivity.findViewById(R.id.add_account);
        assertTrue(addAccountOrVerifyCredentials != null);
        assertUsernameTextField(R.id.username);
        assertUsernameTextField(R.id.username_readonly);
        DbUtils.waitMs(method, 500);
        assertFalse("MyService is not available", MyServiceManager.isServiceAvailable());
        openingOriginList();
        DbUtils.waitMs(method, 500);
        mActivity.finish();
        DbUtils.waitMs(method, 500);
        MySettingsActivity.closeAllActivities(getInstrumentation().getTargetContext());
        DbUtils.waitMs(method, 500);
    }

    private void assertUsernameTextField(int viewId) {
        TextView usernameText = (TextView) mActivity.findViewById(viewId);
        assertTrue(usernameText != null);
        assertEquals("Selected Username", ma.getUsername(), usernameText.getText().toString());
    }
    
    private void openingOriginList() throws InterruptedException {
        final String method = "testOpeningOriginList";
        ActivityMonitor activityMonitor = getInstrumentation().addMonitor(PersistentOriginList.class.getName(), null, false);
        Runnable clicker = new Runnable() {
            @Override
            public void run() {
                MyLog.v(this, method + "-Log before click");
                mActivity.selectOrigin();
            }
          };
    
        MyLog.v(this, method + "-Log before run clicker 1");
        mActivity.runOnUiThread(clicker);
          
        Activity nextActivity = getInstrumentation().waitForMonitorWithTimeout(activityMonitor, 15000);
        MyLog.v(this, method + "-Log after waitForMonitor: " 
                + nextActivity);
        assertNotNull("Next activity is opened and captured", nextActivity);
        TestSuite.waitForListLoaded(this, nextActivity, 8);
        DbUtils.waitMs(method, 500);
        nextActivity.finish();
    }
    
}
