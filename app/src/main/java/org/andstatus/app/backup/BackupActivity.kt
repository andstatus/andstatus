/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.backup

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.TextView
import androidx.documentfile.provider.DocumentFile
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.backup.BackupActivity
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.util.Permissions
import org.andstatus.app.util.Permissions.PermissionType

class BackupActivity : MyActivity(), ProgressLogger.ProgressListener {
    var backupFolder: DocumentFile? = null
    var asyncTask: BackupTask? = null
    private var progressCounter = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        mLayoutId = R.layout.backup
        super.onCreate(savedInstanceState)
        Permissions.checkPermissionAndRequestIt(this, PermissionType.WRITE_EXTERNAL_STORAGE)
        findViewById<View?>(R.id.button_backup).setOnClickListener { v: View? -> doBackup(v) }
        findViewById<View?>(R.id.backup_folder).setOnClickListener { v: View? -> selectBackupFolder(v) }
        findViewById<View?>(R.id.button_select_backup_folder).setOnClickListener { v: View? -> selectBackupFolder(v) }
        showBackupFolder()
    }

    private fun doBackup(v: View?) {
        if (asyncTask == null || asyncTask.completedBackgroundWork()) {
            resetProgress()
            asyncTask = BackupTask(this@BackupActivity)
            AsyncTaskLauncher<DocumentFile?>().execute(this, asyncTask, getBackupFolder())
        }
    }

    private fun selectBackupFolder(v: View?) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getBackupFolder().uri)
        }
        startActivityForResult(intent, ActivityRequestCode.SELECT_BACKUP_FOLDER.id)
    }

    private fun getBackupFolder(): DocumentFile {
        return if (backupFolder != null && backupFolder.exists()) {
            backupFolder
        } else MyBackupManager.Companion.getDefaultBackupFolder(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (ActivityRequestCode.Companion.fromId(requestCode)) {
            ActivityRequestCode.SELECT_BACKUP_FOLDER -> if (resultCode == RESULT_OK) {
                setBackupFolder(DocumentFile.fromTreeUri(this, data.getData()))
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun setBackupFolder(backupFolder: DocumentFile?) {
        resetProgress()
        if (backupFolder == null) {
            addProgressMessage("No backup folder selected")
            return
        } else if (backupFolder.exists()) {
            if (!backupFolder.isDirectory) {
                addProgressMessage("Is not a folder '" + backupFolder.uri + "'")
                return
            }
        } else {
            addProgressMessage("The folder doesn't exist: '" + backupFolder.uri + "'")
            return
        }
        this.backupFolder = backupFolder
        MyPreferences.setLastBackupUri(backupFolder.uri)
        showBackupFolder()
        resetProgress()
    }

    private fun showBackupFolder() {
        val view = findViewById<TextView?>(R.id.backup_folder)
        if (view != null) {
            view.text = getBackupFolder().uri.path
        }
    }

    private class BackupTask internal constructor(private val activity: BackupActivity?) : MyAsyncTask<DocumentFile?, CharSequence?, Void?>(PoolEnum.LONG_UI) {
        override fun doInBackground2(file: DocumentFile?): Void? {
            MyBackupManager.Companion.backupInteractively(file, activity, activity)
            return null
        }
    }

    private fun resetProgress() {
        progressCounter = 0
        val progressLog = findViewById<TextView?>(R.id.progress_log)
        progressLog.text = ""
    }

    private fun addProgressMessage(message: CharSequence?) {
        progressCounter++
        val progressLog = findViewById<TextView?>(R.id.progress_log)
        val log = """
            $progressCounter. $message
            ${progressLog.text}
            """.trimIndent()
        progressLog.text = log
    }

    override fun onResume() {
        MyContextHolder.Companion.myContextHolder.getNow().setInForeground(true)
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        MyContextHolder.Companion.myContextHolder.getNow().setInForeground(false)
    }

    override fun onProgressMessage(message: CharSequence?) {
        runOnUiThread { addProgressMessage(message) }
    }
}