package org.andstatus.app.database

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.andstatus.app.FirstActivity
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyContextState
import org.andstatus.app.context.MyStorage
import org.andstatus.app.data.ApplicationDataUtil.deleteApplicationData
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.RawResourceUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.File
import java.io.IOException

fun databaseUpgradeTest() {
    try {
        val context = MyContextHolder.myContextHolder.getNow().context
        deleteApplicationData()

        FirstActivity.setDefaultValues(context)
        val file: File = MyStorage.getDatabasePath(DatabaseHolder.DATABASE_NAME)
            ?: throw IOException("Failed to open target database file")
        RawResourceUtils.rawResourceToFile(org.andstatus.app.tests.R.raw.database_old, file)
            .onFailure {
                fail("Failed to create database. " + it.message)
            }.onSuccess { dbFile ->
                assertEquals("Created ${dbFile.absolutePath}", 147456, dbFile.length())
                FirstActivity.startApp(MyContextHolder.myContextHolder.initialize(context).getBlocking())
                runBlocking {
                    repeat(120) {
                        delay(500)
                        val myContext = MyContextHolder.myContextHolder.initialize(context).getNow()
                        when (myContext.state) {
                            MyContextState.ERROR -> fail("Error $myContext")
                            MyContextState.DATABASE_UNAVAILABLE -> fail("Database unavailable $myContext")
                            else -> {
                            }
                        }
                        if (myContext.isReady && MyServiceManager.isServiceAvailable()) return@repeat
                    }

                    val myContext = MyContextHolder.myContextHolder.initialize(context).getBlocking()
                    assertTrue(myContext.toString(), myContext.isReady)
                    FirstActivity.closeAllActivities(context)
                    delay(1000)
                }
            }
    } finally {
        deleteApplicationData()
    }
}
