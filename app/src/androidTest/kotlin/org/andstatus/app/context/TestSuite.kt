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
import kotlinx.coroutines.delay
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
import org.andstatus.app.util.TryUtils.ionSuccess
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

    fun initializeWithAccounts(testCase: Any): MyContextTestImpl = runBlocking {
        initializeInner(testCase)
        if (myContextHolder.getCompleted().accounts.fromAccountName(DemoData.demoData.activityPubTestAccountName).isEmpty) {
            ensureDataAdded()
        }
        getMyContextForTest()
    }

    fun initializeWithData(testCase: Any): MyContextTestImpl = runBlocking {
        initializeInner(testCase)
        ensureDataAdded()
        getMyContextForTest()
    }

    fun initialize(testCase: Any, forget: Boolean = false): Context = runBlocking {
        initializeInner(testCase, forget)
    }

    private suspend fun initializeInner(testCase: Any, forget: Boolean = false): Context = runBlocking {
        if (forget) {
            forget()
        }
        if (initialized) return@runBlocking context
            ?: throw IllegalStateException("Context is null for initialised TestSuite")

        MyLog.setMinLogLevel(MyLog.VERBOSE)
        initializeTestSuiteLoop(testCase)
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
            MyLog.d(TAG, "MyContext is not ready: " + myContextHolder.getCompleted().state)
            if (myContextHolder.getCompleted().state == MyContextState.NO_PERMISSIONS) {
                Permissions.setAllGranted(true)
            }
            waitUntilContextCompleted()
        }
        MyLog.d(TAG, "Before check isReady " + myContextHolder.getCompleted())
        initialized = myContextHolder.getCompleted().isReady
        Assert.assertTrue(
            "Test Suite initialized, MyContext state=" + myContextHolder.getCompleted().state,
            initialized
        )
        dataPath = myContextHolder.getCompleted().context.getDatabasePath("andstatus").getPath()
        MyLog.v(
            "TestSuite", "Test Suite initialized, MyContext state=" + myContextHolder.getCompleted().state
                + "; databasePath=" + dataPath
        )
        if (FirstActivity.Companion.checkAndUpdateLastOpenedAppVersion(
                myContextHolder.getCompleted().context, true
            )
        ) {
            MyLog.i(TAG, "New version of application is running")
        }
        myContextHolder.initialize(null).getCompleted()
        return@runBlocking context ?: throw IllegalStateException("Failed to initialize context")
    }

    private suspend fun initializeTestSuiteLoop(testCase: Any) {
        var creatorSet = false
        for (iter in 1..5) {
            MyLog.i(TAG, "Initializing Test Suite, iteration=$iter")
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
                        .trySetCreator(MyContextTestImpl(myContextHolder.getCompleted(), co, testCase))
                ) {
                    creatorSet = true
                    FirstActivity.startMeAsync(co, MyAction.INITIALIZE_APP)
                    delay(3000)
                    myContextHolder.future.tryCurrent.ionSuccess {
                        val myContext: MyContext = myContextHolder.getCompleted()
                        MyLog.i(TAG, "After starting FirstActivity $iter $myContext")
                        if (myContext.state == MyContextState.READY) return
                    }.onFailure {
                        MyLog.i(TAG, "After starting FirstActivity $iter is initializing...")
                    }
                }
            } catch (e: IllegalStateException) {
                MyLog.i(TAG, "Error caught, iteration=$iter", e)
            }
            delay(3000)
        }
    }

    suspend fun forget() {
        MyLog.d(TAG, "Before forget")
        myContextHolder.release { "forget" }
        context = null
        initialized = false
    }

    private suspend fun waitUntilContextCompleted() {
        val method = "waitUntilContextIsReady"
        var intent = Intent(myContextHolder.getCompleted().context, HelpActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        myContextHolder.getCompleted().context.startActivity(intent)
        val tryContext = myContextHolder.future.tryCompleted()
        Assert.assertEquals("Is Not ready $tryContext", MyContextState.READY, myContextHolder.getCompleted().state)
        intent = Intent(myContextHolder.getCompleted().context, HelpActivity::class.java)
        intent.putExtra(HelpActivity.Companion.EXTRA_CLOSE_ME, true)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        myContextHolder.getCompleted().context.startActivity(intent)
        delay(2000)
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

    private suspend fun ensureDataAdded() {
        if (dataAdded) return
        val method = "ensureDataAdded"
        MyLog.v(method, "$method; started")
        if (!dataAdded) {
            dataAdded = true
            DemoData.demoData.createNewInstance()
            DemoData.demoData.add(dataPath ?: "")
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

    fun setAndWaitForIsInForeground(isInForeground: Boolean): Boolean = runBlocking {
        val method = "setAndWaitForIsInForeground"
        myContextHolder.getCompleted().isInForeground = isInForeground
        for (pass in 0..299) {
            if (myContextHolder.getCompleted().isInForeground == isInForeground) {
                return@runBlocking true
            }
            if (DbUtils.waitMs(method, 100)) {
                return@runBlocking false
            }
        }
        return@runBlocking false
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
