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
import android.widget.Button;
import android.widget.TextView;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.context.ActivityTest;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MySettingsActivity;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.origin.PersistentOriginList;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author yvolk@yurivolkov.com
 */
public class AccountSettingsActivityTest extends ActivityTest<AccountSettingsActivity> {
    private MyAccount ma = null;

    @Override
    protected Class<AccountSettingsActivity> getActivityClass() {
        return AccountSettingsActivity.class;
    }

    @Override
    protected Intent getActivityIntent() {
        TestSuite.initializeWithData(this);

        ma = MyContextHolder.get().accounts().getCurrentAccount();
        if (ma.nonValid()) fail("No persistent accounts yet");

        return new Intent().putExtra(IntentExtra.ACCOUNT_NAME.key, ma.getAccountName());
    }

    @Test
    public void test() throws InterruptedException {
        final String method = "test";
        Button addAccountOrVerifyCredentials = (Button) getActivity().findViewById(R.id.add_account);
        assertTrue(addAccountOrVerifyCredentials != null);
        assertUsernameTextField(R.id.username);
        assertUsernameTextField(R.id.username_readonly);
        DbUtils.waitMs(method, 500);
        assertEquals("MyService is available", false, MyServiceManager.isServiceAvailable());
        openingOriginList();
        DbUtils.waitMs(method, 500);
        getActivity().finish();
        DbUtils.waitMs(method, 500);
        MySettingsActivity.closeAllActivities(getInstrumentation().getTargetContext());
        DbUtils.waitMs(method, 500);
    }

    private void assertUsernameTextField(int viewId) {
        TextView usernameText = (TextView) getActivity().findViewById(viewId);
        assertTrue(usernameText != null);
        assertEquals("Selected username", ma.getUsername(), usernameText.getText().toString());
    }
    
    private void openingOriginList() throws InterruptedException {
        final String method = "testOpeningOriginList";
        ActivityMonitor activityMonitor = getInstrumentation().addMonitor(PersistentOriginList.class.getName(), null, false);
        Runnable clicker = new Runnable() {
            @Override
            public void run() {
                MyLog.v(this, method + "-Log before click");
                getActivity().selectOrigin(OriginType.GNUSOCIAL);
            }
          };
    
        MyLog.v(this, method + "-Log before run clicker 1");
        getActivity().runOnUiThread(clicker);
          
        Activity nextActivity = getInstrumentation().waitForMonitorWithTimeout(activityMonitor, 15000);
        MyLog.v(this, method + "-Log after waitForMonitor: " 
                + nextActivity);
        assertNotNull("Next activity is opened and captured", nextActivity);
        TestSuite.waitForListLoaded(nextActivity, 6);
        DbUtils.waitMs(method, 500);
        nextActivity.finish();
    }
}
