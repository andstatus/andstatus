/*
 * Copyright (C) 2021 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.data

import android.accounts.AccountManager
import android.accounts.AuthenticatorException
import android.accounts.OperationCanceledException
import android.content.Context
import org.andstatus.app.account.AccountUtils
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyStorage
import org.andstatus.app.context.TestSuite
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.FileUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.TriState
import org.junit.Assert
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

object ApplicationDataUtil {

    fun deleteApplicationData() {
        MyServiceManager.setServiceUnavailable()
        deleteAccounts()
        val context: Context =  MyContextHolder.myContextHolder.getNow().context
        MyContextHolder.myContextHolder.release { "deleteApplicationData" }
        deleteFiles(context, false)
        deleteFiles(context, true)
        SharedPreferencesUtil.resetHasSetDefaultValues()
        Assert.assertEquals(TriState.FALSE, MyStorage.isApplicationDataCreated())
        TestSuite.onDataDeleted()
    }

    private fun deleteAccounts() {
        val am = AccountManager.get(MyContextHolder.myContextHolder.getNow().context)
        val aa = AccountUtils.getCurrentAccounts(MyContextHolder.myContextHolder.getNow().context)
        for (androidAccount in aa) {
            val logMsg = "Removing old account: " + androidAccount.name
            MyLog.i(this, logMsg)
            val amf = am.removeAccount(androidAccount, null, null)
            try {
                amf.getResult(10, TimeUnit.SECONDS)
            } catch (e: OperationCanceledException) {
                throw Exception(logMsg + ", " + e.message, e)
            } catch (e: AuthenticatorException) {
                throw Exception(logMsg + ", " + e.message, e)
            }
        }
    }

    private fun deleteFiles(context: Context, useExternalStorage: Boolean) {
        FileUtils.deleteFilesRecursively(MyStorage.getDataFilesDir(MyStorage.DIRECTORY_DOWNLOADS, TriState.Companion.fromBoolean(useExternalStorage)))
        FileUtils.deleteFilesRecursively(MyStorage.getDataFilesDir(MyStorage.DIRECTORY_DATABASES, TriState.Companion.fromBoolean(useExternalStorage)))
        FileUtils.deleteFilesRecursively(SharedPreferencesUtil.prefsDirectory(context))
    }

    fun ensureOneFileExistsInDownloads() {
        val downloads = MyStorage.getDataFilesDir(MyStorage.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("No downloads")
        if (Arrays.stream(downloads.listFiles()).noneMatch { obj: File -> obj.isFile }) {
            val dummyFile = File(downloads, "dummy.txt")
            dummyFile.createNewFile()
        }
    }
}
