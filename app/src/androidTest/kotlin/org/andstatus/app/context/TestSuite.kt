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
package org.andstatus.app.context

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.andstatus.app.FirstActivity
import org.andstatus.app.HelpActivity
import org.andstatus.app.MyAction
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.http.HttpConnection
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.ExceptionsCounter
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.EspressoUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.Permissions
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StopWatch
import org.junit.Assert
import java.util.*

/**
 * @author yvolk@yurivolkov.com
 */
object TestSuite {
    private val TAG: String = TestSuite::class.simpleName!!

    @Volatile
    private var initialized = false

    @Volatile
    private var context: Context? = null

    @Volatile
    private var dataPath: String? = null

    @Volatile
    private var dataAdded = false

    fun initializeWithAccounts(testCase: Any?): MyContextTestImpl {
        initialize(testCase)
        if (myContextHolder.getNow().accounts.fromAccountName(DemoData.demoData.activityPubTestAccountName).isEmpty) {
            ensureDataAdded()
        }
        return getMyContextForTest()
    }

    fun initializeWithData(testCase: Any?): MyContextTestImpl {
        initialize(testCase)
        ensureDataAdded()
        return getMyContextForTest()
    }

    @Synchronized
    fun initialize(testCase: Any?): Context = runBlocking {
        val method = "initialize"
        if (initialized) return@runBlocking context
            ?: throw IllegalStateException("Context is null for initialised TestSuite")

        var creatorSet = false
        MyLog.setMinLogLevel(MyLog.VERBOSE)
        for (iter in 1..5) {
            MyLog.i(TAG, "Initializing Test Suite, iteration=$iter")
            if (testCase == null) {
                MyLog.e(TAG, "testCase is null.")
                throw IllegalArgumentException("testCase is null")
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            if (instrumentation == null) {
                MyLog.e(TAG, "testCase.getInstrumentation() is null.")
                throw IllegalArgumentException("testCase.getInstrumentation() returned null")
            }
            val co = instrumentation.targetContext ?: run {
                MyLog.e(TAG, "targetContext is null.")
                throw IllegalArgumentException("testCase.getInstrumentation().getTargetContext() returned null")
            }
            context = co
            MyLog.i(TAG, "Before myContextHolder.initialize $iter")
            try {
                if (creatorSet || myContextHolder
                        .trySetCreator(MyContextTestImpl(myContextHolder.getNow(), co, testCase))
                ) {
                    creatorSet = true
                    FirstActivity.Companion.startMeAsync(co, MyAction.INITIALIZE_APP)
                    DbUtils.waitMs(method, 3000)
                    if (myContextHolder.getFuture().future.isFinished) {
                        val myContext: MyContext = myContextHolder.getNow()
                        MyLog.i(TAG, "After starting FirstActivity $iter $myContext")
                        if (myContext.state == MyContextState.READY) break
                    } else {
                        MyLog.i(TAG, "After starting FirstActivity $iter is initializing...")
                    }
                }
            } catch (e: IllegalStateException) {
                MyLog.i(TAG, "Error caught, iteration=$iter", e)
            }
            DbUtils.waitMs(method, 3000)
        }
        MyLog.i(TAG, "After Initializing Test Suite loop")
        myContextHolder.executionMode =
            ExecutionMode.Companion.load(InstrumentationRegistry.getArguments().getString("executionMode"))
        val myContext: MyContext = myContextHolder.getCompleted()
        Assert.assertNotEquals("MyContext state $myContext", MyContextState.EMPTY, myContext.state)
        val logLevel = MyLog.VERBOSE
        MyLog.setMinLogLevel(logLevel)
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_DOWNLOAD_AND_DISPLAY_ATTACHED_IMAGES, true)
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_ATTACH_IMAGES_TO_MY_NOTES, true)
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_BACKUP_DOWNLOADS, true)
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_BACKUP_LOG_FILES, true)
        AsyncTaskLauncher.Companion.forget()
        ExceptionsCounter.forget()
        MyLog.forget()
        Assert.assertTrue("Level $logLevel should be loggable", MyLog.isLoggable(TAG, logLevel))
        MyServiceManager.setServiceUnavailable()
        if (myContextHolder.getCompleted().state != MyContextState.READY) {
            MyLog.d(TAG, "MyContext is not ready: " + myContextHolder.getNow().state)
            if (myContextHolder.getNow().state == MyContextState.NO_PERMISSIONS) {
                Permissions.setAllGranted(true)
            }
            waitUntilContextIsReady()
        }
        MyLog.d(TAG, "Before check isReady " + myContextHolder.getNow())
        initialized = myContextHolder.getNow().isReady
        Assert.assertTrue(
            "Test Suite initialized, MyContext state=" + myContextHolder.getNow().state,
            initialized
        )
        dataPath = myContextHolder.getNow().context.getDatabasePath("andstatus").getPath()
        MyLog.v(
            "TestSuite", "Test Suite initialized, MyContext state=" + myContextHolder.getNow().state
                + "; databasePath=" + dataPath
        )
        if (FirstActivity.Companion.checkAndUpdateLastOpenedAppVersion(
                myContextHolder.getNow().context, true
            )
        ) {
            MyLog.i(TAG, "New version of application is running")
        }
        myContextHolder.initialize(null).getCompleted()
        return@runBlocking context ?: throw IllegalStateException("Failed to initialize context")
    }

    @Synchronized
    fun forget() {
        MyLog.d(TAG, "Before forget")
        myContextHolder.releaseBlocking { "forget" }
        context = null
        initialized = false
    }

    fun waitUntilContextIsReady() {
        val method = "waitUntilContextIsReady"
        var intent = Intent(myContextHolder.getNow().context, HelpActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        myContextHolder.getNow().context.startActivity(intent)
        var i = 100
        while (i > 0) {
            DbUtils.waitMs(method, 2000)
            MyLog.d(TAG, "Waiting for context " + i + " " + myContextHolder.getNow().state)
            when (myContextHolder.getNow().state) {
                MyContextState.READY, MyContextState.ERROR -> i = 0
                else -> {
                }
            }
            i--
        }
        Assert.assertEquals("Is Not ready", MyContextState.READY, myContextHolder.getNow().state)
        intent = Intent(myContextHolder.getNow().context, HelpActivity::class.java)
        intent.putExtra(HelpActivity.Companion.EXTRA_CLOSE_ME, true)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        myContextHolder.getNow().context.startActivity(intent)
        DbUtils.waitMs(method, 2000)
    }

    fun clearAssertions() {
        getMyContextForTest().getAssertions().clear()
    }

    fun getMyContextForTest(): MyContextTestImpl = runBlocking {
        val myContext: MyContext = myContextHolder.getCompleted()
        if (myContext !is MyContextTestImpl) {
            Assert.fail("Wrong type of current context: " + (myContext.javaClass.name))
        }
        if (myContext.isExpired) {
            myContextHolder.initialize(myContext.context)
        }
        myContextHolder.getCompleted() as MyContextTestImpl
    }

    fun setHttpConnectionStubClass(httpConnectionStubClass: Class<out HttpConnection?>?) {
        getMyContextForTest().setHttpConnectionStubClass(httpConnectionStubClass)
    }

    fun setHttpConnectionStubInstance(httpConnectionStubInstance: HttpConnection?) {
        getMyContextForTest().setHttpConnectionStubInstance(httpConnectionStubInstance)
    }

    fun onDataDeleted() {
        MyLog.v(TAG, "onDataDeleted")
        dataAdded = false
    }

    private fun ensureDataAdded() {
        if (dataAdded) return
        val method = "ensureDataAdded"
        MyLog.v(method, "$method; started")
        if (!dataAdded) {
            dataAdded = true
            DemoData.demoData.createNewInstance()
            DemoData.demoData.add(getMyContextForTest(), dataPath ?: "")
        }
        MyLog.v(method, "$method; ended")
    }

    fun utcTime(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Date {
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        cal[year, month, day, hour, minute] = second
        cal[Calendar.MILLISECOND] = 0
        return cal.time
    }

    fun utcTime(millis: Long): Date {
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = millis
        return cal.time
    }

    fun waitForListLoaded(activity: Activity, minCount: Int): Int {
        val method = "waitForListLoaded"
        val stopWatch: StopWatch = StopWatch.createStarted()
        val list = activity.findViewById<View?>(android.R.id.list) as ViewGroup?
        Assert.assertTrue(list != null)
        var itemsCount = 0
        do {
            EspressoUtils.waitForIdleSync()
            val itemsCountNew = if (list is ListView) list.count else list?.childCount ?: 0
            MyLog.v(TAG, "waitForListLoaded; countNew=$itemsCountNew, prev=$itemsCount, min=$minCount")
            if (itemsCountNew >= minCount && itemsCount == itemsCountNew) {
                break
            }
            itemsCount = itemsCountNew
            EspressoUtils.waitForIdleSync()
            // TODO: Wait for something else to remove the delay
        } while (stopWatch.notPassedSeconds(40))
        val msgLog = "There are " + itemsCount + " items (min=" + minCount + ")" +
            " in the list of " + activity::class.simpleName + ", ${stopWatch.time} ms"
        Assert.assertTrue(msgLog, itemsCount >= minCount)
        MyLog.v(this, method + " ended, $msgLog")
        return itemsCount
    }

    fun isScreenLocked(context: Context): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
        return km == null || km.inKeyguardRestrictedInputMode()
    }

    fun setAndWaitForIsInForeground(isInForeground: Boolean): Boolean {
        val method = "setAndWaitForIsInForeground"
        myContextHolder.getNow().isInForeground = isInForeground
        for (pass in 0..299) {
            if (myContextHolder.getNow().isInForeground == isInForeground) {
                return true
            }
            if (DbUtils.waitMs(method, 100)) {
                return false
            }
        }
        return false
    }

    fun clearHttpStubs() {
        setHttpConnectionStubClass(null)
        setHttpConnectionStubInstance(null)
        runBlocking {
            myContextHolder.getCompleted()
                .accounts.get()
                .forEach(MyAccount::setConnection)
        }
    }
}
