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
package org.andstatus.app.service

import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.util.IgnoredInTravis2
import org.andstatus.app.util.MyLog
import org.junit.After
import org.junit.Assert
import org.junit.Before

abstract class MyServiceTest: IgnoredInTravis2() {
    private var myServiceTestHelper: MyServiceTestHelper? = null
    val mService: MyServiceTestHelper get() = myServiceTestHelper ?: throw IllegalStateException("MyServiceTestHelper is null")

    @Volatile
    private var mMa: MyAccount = MyAccount.EMPTY
    val ma: MyAccount get() = mMa

    @Before
    fun setUp() {
        var ok = false
        MyLog.i(this, "setUp started")
        if (TestSuite.isInitializedWithData()) {
            TestSuite.waitForIdleSync()
        }
        try {
            TestSuite.initializeWithData(this)
            myServiceTestHelper = MyServiceTestHelper().also {
                it.setUp(null)
                mMa =  MyContextHolder.myContextHolder.getNow().accounts.getFirstSucceeded().also { myAccount ->
                    Assert.assertTrue("No successfully verified accounts", myAccount.isValidAndSucceeded())
                }
            }
            ok = true
        } finally {
            if (ok) {
                TestSuite.waitForIdleSync()
            }
            MyLog.i(this, "setUp ended " +
                    (if (ok) "successfully " else "failed") +
                    " instanceId=" + if (myServiceTestHelper == null) "null" else myServiceTestHelper?.connectionInstanceId)
        }
    }

    @After
    fun tearDown() {
        MyLog.i(this, "tearDown started" + if (myServiceTestHelper == null) ", mService:null" else "")
        myServiceTestHelper?.tearDown()
        MyLog.i(this, "tearDown ended")
    }
}
