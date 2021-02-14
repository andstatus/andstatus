/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.service;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.util.MyLog;
import org.junit.After;
import org.junit.Before;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.junit.Assert.assertTrue;

abstract class MyServiceTest {
    MyServiceTestHelper mService;
    protected volatile MyAccount ma;

    @Before
    public void setUp() throws Exception {
        boolean ok = false;
        MyLog.i(this, "setUp started");
        if (TestSuite.isInitializedWithData()) {
            TestSuite.waitForIdleSync();
        }
        try {
            TestSuite.initializeWithData(this);
            mService = new MyServiceTestHelper();
            mService.setUp(null);
            ma = myContextHolder.getNow().accounts().getFirstSucceeded();
            assertTrue("No successfully verified accounts", ma.isValidAndSucceeded());
            mService.waitForServiceStopped(true);
            ok = true;
        } finally {
            if (ok) {
                TestSuite.waitForIdleSync();
            }
            MyLog.i(this, "setUp ended " +
                    (ok ? "successfully " : "failed") +
                    " instanceId=" + (mService == null ? "null" : mService.connectionInstanceId));
        }
    }

    @After
    public void tearDown() throws Exception {
        MyLog.i(this, "tearDown started" + (mService == null ? ", mService:null" : ""));
        if (mService != null) {
            mService.tearDown();
        }
        MyLog.i(this, "tearDown ended");
    }
}
