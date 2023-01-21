package org.andstatus.app.database

import kotlinx.coroutines.delay
import org.andstatus.app.FirstActivity
import org.andstatus.app.HelpActivityTest.Companion.closeAllActivities
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.MyContextState
import org.andstatus.app.context.MyStorage
import org.andstatus.app.data.ApplicationDataUtil.deleteApplicationData
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RawResourceUtils
import org.andstatus.app.util.TryUtils.getOrElseRecover
import org.andstatus.app.util.TryUtils.onSuccessS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.File
import java.io.IOException

object DatabaseUpgradeTest {
    val TAG = "databaseUpgradeTest"

    suspend fun databaseUpgradeTest() {
        try {
            MyLog.i(TAG, "$TAG started")

            val context = myContextHolder.getNow().context
            deleteApplicationData()
            MyLog.i(TAG, "after deleteApplicationData 1")

            FirstActivity.setDefaultValues(context)
            MyLog.i(TAG, "after setDefaultValues")
            val file: File = MyStorage.getDatabasePath(DatabaseHolder.DATABASE_NAME)
                ?: throw IOException("Failed to open target database file")
            RawResourceUtils.rawResourceToFile(org.andstatus.app.test.R.raw.database_old, file)
                .onFailure {
                    fail("Failed to create database. " + it.message)
                }.onSuccessS { dbFile ->
                    assertEquals("Created ${dbFile.absolutePath}", 147456, dbFile.length())
                    MyServiceManager.setServiceUnavailable()
                    MyLog.i(TAG, "before FirstActivity.startApp")
                    FirstActivity.restartApp(context, this).tryCompleted().getOrElseRecover {
                        throw it
                    }
                    MyLog.i(TAG, "before waiting for database upgrade completion")
                    for (i in 1..120) {
                        delay(500)
                        val myContext = myContextHolder.future.getNow()
                        if (myContext.isReady) break;
                        when (myContext.state) {
                            MyContextState.ERROR -> fail("Error $myContext")
                            MyContextState.DATABASE_UNAVAILABLE -> fail("Database unavailable $myContext")
                            else -> {
                            }
                        }
                    }
                    myContextHolder.future.getNow().let {
                        if (!it.isReady) {
                            fail("MyContext is not ready $it")
                        }
                    }

                    MyLog.v(TAG, "before waiting for isServiceAvailable")
                    while (!MyServiceManager.isServiceAvailable()) {
                        delay(1000)
                    }

                    MyLog.i(TAG, "before myContextHolder.initialize")
                    myContextHolder.initialize(context).getCompleted().let {
                        assertTrue(it.toString(), it.isReady)
                    }
                    MyLog.i(TAG, "before FirstActivity.closeAllActivities")
                    closeAllActivities(context)
                    delay(1000)
                    MyLog.i(TAG, "after FirstActivity.closeAllActivities")
                }
        } finally {
            MyLog.i(TAG, "before deleteApplicationData 2")
            deleteApplicationData()
            MyLog.i(TAG, "$TAG ended")
        }
    }
}
