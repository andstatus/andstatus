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
package org.andstatus.app.context

import android.app.ProgressDialog
import android.content.Context
import android.widget.Toast
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.R
import org.andstatus.app.data.DbUtils
import org.andstatus.app.database.DatabaseHolder
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.service.MyServiceState
import org.andstatus.app.util.DialogFactory
import org.andstatus.app.util.FileUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import java.io.File

class StorageSwitch(private val parentFragment: MySettingsFragment) {
    private val mContext: Context get() = parentFragment.getActivity()
            ?: throw IllegalStateException("No Activity in the parent fragment")
    private var mUseExternalStorageNew = false

    fun showSwitchStorageDialog(requestCode: ActivityRequestCode, useExternalStorageNew: Boolean) {
        mUseExternalStorageNew = useExternalStorageNew
        DialogFactory.showOkCancelDialog(parentFragment, R.string.dialog_title_external_storage,
                if (useExternalStorageNew) R.string.summary_preference_storage_external_on else R.string.summary_preference_storage_external_off,
                requestCode)
    }

    fun move() {
        AsyncTaskLauncher.execute(this, MoveDataBetweenStoragesTask())
    }

    private fun checkAndSetDataBeingMoved(): Boolean {
        synchronized(moveLock) {
            if (mDataBeingMoved) {
                return false
            }
            mDataBeingMoved = true
            return true
        }
    }

    fun isDataBeingMoved(): Boolean {
        synchronized(moveLock) { return mDataBeingMoved }
    }

    private class TaskResult {
        var success = false
        var moved = false
        var messageBuilder: StringBuilder = StringBuilder()
        fun getMessage(): String {
            return messageBuilder.toString()
        }
    }

    /**
     * Move Data to/from External Storage
     *
     * @author yvolk@yurivolkov.com
     */
    private inner class MoveDataBetweenStoragesTask : MyAsyncTask<Void?, Void?, TaskResult?>(PoolEnum.LONG_UI) {
        // indeterminate duration, not cancelable
        private val dlg: ProgressDialog = ProgressDialog.show(mContext,
                mContext.getText(R.string.dialog_title_external_storage),
                mContext.getText(R.string.dialog_summary_external_storage),
                true,
                false)

        override fun doInBackground2(params: Void?): TaskResult {
            val result = TaskResult()
            MyContextHolder.myContextHolder.getBlocking()
            MyServiceManager.setServiceUnavailable()
            MyServiceManager.stopService()

            do {
                DbUtils.waitMs(this, 500)
            } while (MyServiceManager.getServiceState() == MyServiceState.UNKNOWN)
            if (MyServiceManager.getServiceState() != MyServiceState.STOPPED) {
                result.messageBuilder.append(mContext.getText(R.string.system_is_busy_try_later))
                return result
            }
            if (!checkAndSetDataBeingMoved()) {
                return result
            }
            try {
                moveAll(result)
            } finally {
                synchronized(moveLock) { mDataBeingMoved = false }
            }
            result.messageBuilder.insert(0, " Move " + strSucceeded(result.success))
            MyLog.v(this) { result.getMessage() }
            return result
        }

        private fun moveAll(result: TaskResult) {
            val useExternalStorageOld = MyStorage.isStorageExternal()
            if (mUseExternalStorageNew
                    && !MyStorage.isWritableExternalStorageAvailable(result.messageBuilder)) {
                mUseExternalStorageNew = false
            }
            MyLog.d(this, "About to move data from " + useExternalStorageOld + " to "
                    + mUseExternalStorageNew)
            if (mUseExternalStorageNew == useExternalStorageOld) {
                result.messageBuilder.append(" Nothing to do.")
                result.success = true
                return
            }
            try {
                result.success = moveDatabase(mUseExternalStorageNew, result.messageBuilder, DatabaseHolder.DATABASE_NAME)
                if (result.success) {
                    result.moved = true
                    moveFolder(mUseExternalStorageNew, result.messageBuilder, MyStorage.DIRECTORY_DOWNLOADS)
                    moveFolder(mUseExternalStorageNew, result.messageBuilder, MyStorage.DIRECTORY_LOGS)
                }
            } finally {
                if (result.success) {
                    saveNewSettings(mUseExternalStorageNew, result.messageBuilder)
                }
            }
        }

        private fun moveDatabase(useExternalStorageNew: Boolean, messageToAppend: StringBuilder, databaseName: String): Boolean {
            val method = "moveDatabase"
            var succeeded = false
            var done = false

            /** Did we actually copy database? */
            var copied = false
            var dbFileOld: File? = null
            var dbFileNew: File? = null
            try {
                dbFileOld = MyContextHolder.myContextHolder.getNow().baseContext().getDatabasePath(
                        databaseName)
                dbFileNew = MyStorage.getDatabasePath(databaseName, TriState.fromBoolean(useExternalStorageNew))
                if (dbFileOld == null) {
                    messageToAppend.append(" No old database $databaseName")
                    done = true
                }
                if (!done && dbFileOld != null) {
                    if (dbFileNew == null) {
                        messageToAppend.append(" No new database $databaseName")
                        done = true
                    } else {
                        if (!dbFileOld.exists()) {
                            messageToAppend.append(" No old database $databaseName")
                            done = true
                            succeeded = true
                        } else if (dbFileNew.exists()) {
                            messageToAppend.insert(0, " Database already exists $databaseName")
                            if (!dbFileNew.delete()) {
                                messageToAppend
                                        .insert(0, " Couldn't delete already existed files. ")
                                done = true
                            }
                        }
                    }
                }
                if (!done && dbFileNew != null && dbFileOld != null) {
                    if (MyLog.isVerboseEnabled()) {
                        MyLog.v(this, method + " from: " + dbFileOld.path)
                        MyLog.v(this, method + " to: " + dbFileNew.path)
                    }
                    try {
                        MyContextHolder.myContextHolder.release { "moveDatabase" }
                        if (FileUtils.copyFile(this, dbFileOld, dbFileNew)) {
                            copied = true
                            succeeded = true
                        }
                    } catch (e: Exception) {
                        MyLog.v(this, "Copy database $databaseName", e)
                        messageToAppend.insert(0, " Couldn't copy database "
                                + databaseName + ": " + getErrorInfo(e) + ". ")
                    }
                }
            } catch (e: Exception) {
                MyLog.v(this, e)
                messageToAppend.append(method + " error: " + getErrorInfo(e) + ". ")
                succeeded = false
            } finally {
                // Delete unnecessary files
                try {
                    if (succeeded) {
                        if (copied && dbFileOld != null && dbFileOld.exists()
                                && !dbFileOld.delete()) {
                            messageToAppend.append("$method couldn't delete old files. ")
                        }
                    } else {
                        if (dbFileNew != null && dbFileNew.exists()
                                && !dbFileNew.delete()) {
                            messageToAppend.append("$method couldn't delete new files. ")
                        }
                    }
                } catch (e: Exception) {
                    MyLog.v(this, "$method Delete old file", e)
                    messageToAppend.append(method + " couldn't delete old files. " + getErrorInfo(e)
                            + ". ")
                }
            }
            MyLog.d(this, method + "; " + databaseName + " " + strSucceeded(succeeded))
            return succeeded
        }

        private fun moveFolder(useExternalStorageNew: Boolean, messageToAppend: StringBuilder, folderType: String) {
            val method = "moveFolder $folderType"
            var succeeded = false
            var done = false
            var didWeCopyAnything = false
            var dirOld: File? = null
            var dirNew: File? = null
            try {
                if (!done) {
                    dirOld = MyStorage.getDataFilesDir(folderType)
                    dirNew = MyStorage.getDataFilesDir(folderType,
                            TriState.fromBoolean(useExternalStorageNew))
                    if (dirOld == null || !dirOld.exists()) {
                        messageToAppend.append(" No old folder. ")
                        done = true
                        succeeded = true
                    }
                    if (dirNew == null) {
                        messageToAppend.append(" No new folder?! ")
                        done = true
                    }
                }
                if (!done && dirOld != null && dirNew != null) {
                    if (MyLog.isVerboseEnabled()) {
                        MyLog.v(this, method + " from: " + dirOld.getPath())
                        MyLog.v(this, method + " to: " + dirNew.getPath())
                    }
                    var filename = ""
                    try {
                        for (fileOld in dirOld.listFiles()) {
                            if (fileOld.isFile) {
                                filename = fileOld.name
                                val fileNew = File(dirNew, filename)
                                if (FileUtils.copyFile(this, fileOld, fileNew)) {
                                    didWeCopyAnything = true
                                }
                            }
                        }
                        succeeded = true
                    } catch (e: Exception) {
                        val logMsg = "$method couldn't copy'$filename'"
                        MyLog.v(this, logMsg, e)
                        messageToAppend.insert(0, " " + logMsg + ": " + e.message)
                    }
                    done = true
                }
            } catch (e: Exception) {
                MyLog.v(this, e)
                messageToAppend.append(method + " error: " + getErrorInfo(e) + ". ")
                succeeded = false
            } finally {
                // Delete unnecessary files
                try {
                    if (succeeded) {
                        if (didWeCopyAnything && dirOld != null) {
                            for (fileOld in dirOld.listFiles()) {
                                if (fileOld.isFile && !fileOld.delete()) {
                                    messageToAppend.append(method + " couldn't delete old file "
                                            + fileOld.name)
                                }
                            }
                        }
                    } else {
                        if (dirNew != null && dirNew.exists()) {
                            for (fileNew in dirNew.listFiles()) {
                                if (fileNew.isFile && !fileNew.delete()) {
                                    messageToAppend.append(method + " couldn't delete new file "
                                            + fileNew.name)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    val logMsg = "$method deleting unnecessary files"
                    MyLog.v(this, logMsg, e)
                    messageToAppend.append(logMsg + ": " + getErrorInfo(e))
                }
            }
            MyLog.d(this, method + " " + strSucceeded(succeeded))
        }

        private fun saveNewSettings(useExternalStorageNew: Boolean, messageToAppend: StringBuilder) {
            try {
                SharedPreferencesUtil.putBoolean(MyPreferences.KEY_USE_EXTERNAL_STORAGE, useExternalStorageNew)
                MyPreferences.onPreferencesChanged()
            } catch (e: Exception) {
                MyLog.v(this, "Save new settings", e)
                messageToAppend.append("Couldn't save new settings. " + getErrorInfo(e))
            }
        }

        // This is in the UI thread, so we can mess with the UI
        override fun onFinish(result: TaskResult?, success: Boolean) {
            DialogFactory.dismissSafely(dlg)
            if (result == null) {
                MyLog.w(this, "Result is Null")
                Toast.makeText(mContext, mContext.getString(R.string.error), Toast.LENGTH_LONG).show()
                return
            }
            MyLog.d(this, this.javaClass.simpleName + " ended, "
                    + if (result.success) if (result.moved) "moved" else "didn't move" else "failed")
            if (!result.success) {
                result.messageBuilder.insert(0, mContext.getString(R.string.error) + ": ")
            }
            Toast.makeText(mContext, result.getMessage(), Toast.LENGTH_LONG).show()
            parentFragment.showUseExternalStorage()
        }

        override fun onCancelled2(result: TaskResult?) {
            DialogFactory.dismissSafely(dlg)
        }
    }

    companion object {

        // TODO: Should be one object for atomic updates. start ---
        private val moveLock: Any = Any()
        /**
         * This semaphore helps to avoid ripple effect: changes in MyAccount cause
         * changes in this activity ...
         */
        @Volatile
        private var mDataBeingMoved = false
        // end ---

        fun getErrorInfo(e: Throwable): String {
            return (StringUtil.notEmpty(e.message, "(no error message)")
                    + " (" + e.javaClass.canonicalName + ")")
        }

        private fun strSucceeded(succeeded: Boolean): String {
            return if (succeeded) "succeeded" else "failed"
        }
    }
}
