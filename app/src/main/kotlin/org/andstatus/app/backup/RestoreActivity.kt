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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.TextView
import androidx.documentfile.provider.DocumentFile
import io.vavr.control.Try
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.os.AsyncEffects
import org.andstatus.app.os.AsyncEnum.DEFAULT_POOL
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.UriUtils

class RestoreActivity : MyActivity(RestoreActivity::class), ProgressLogger.ProgressListener {
    private var dataFolder: DocumentFile? = null
    private var asyncTask: AsyncEffects<DocumentFile>? = null
    private var progressCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        mLayoutId = R.layout.restore
        super.onCreate(savedInstanceState)
        findViewById<View?>(R.id.button_restore).setOnClickListener { v: View? -> doRestore(v) }
        findViewById<View?>(R.id.backup_folder).setOnClickListener { v: View? -> selectBackupFolder(v) }
        findViewById<View?>(R.id.button_select_backup_folder).setOnClickListener { v: View? -> selectBackupFolder(v) }
        showDataFolder()
    }

    private fun doRestore(v: View?) {
        if (asyncTask?.isFinished ?: true) {
            resetProgress()
            asyncTask = AsyncEffects<DocumentFile>(this, DEFAULT_POOL, cancelable = false)
                .doInBackground { params ->
                    MyBackupManager.restoreInteractively(params, this@RestoreActivity, this@RestoreActivity)
                    TryUtils.SUCCESS
                }
                .also {
                    it.maxCommandExecutionSeconds = MAX_RESTORE_SECONDS.toLong()
                    it.execute(this, getDataFolder())
                }
        }
    }

    private fun selectBackupFolder(v: View?) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getDataFolder().uri)
        }
        startActivityForResult(intent, ActivityRequestCode.SELECT_BACKUP_FOLDER.id)
    }

    private fun getDataFolder(): DocumentFile {
        return dataFolder?.takeIf { it.exists() } ?: MyBackupManager.getDefaultBackupFolder(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.getData() ?: Uri.EMPTY
        if (UriUtils.nonEmpty(uri)) when (ActivityRequestCode.fromId(requestCode)) {
            ActivityRequestCode.SELECT_BACKUP_FOLDER -> if (resultCode == RESULT_OK) {
                setDataFolder(DocumentFile.fromTreeUri(this, uri))
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun setDataFolder(selectedFolder: DocumentFile?) {
        resetProgress()
        if (selectedFolder == null) {
            addProgressMessage("No backup data folder selected")
            return
        } else if (selectedFolder.exists()) {
            if (!selectedFolder.isDirectory) {
                addProgressMessage("Is not a folder: '" + selectedFolder.uri.path + "'")
                return
            }
        } else {
            addProgressMessage("The folder doesn't exist: '" + selectedFolder.uri.path + "'")
            return
        }
        val descriptorFile: Try<DocumentFile> = MyBackupManager.getExistingDescriptorFile(selectedFolder)
        if (descriptorFile.isFailure) {
            addProgressMessage(
                "This is not an AndStatus backup folder." +
                    " Descriptor file " + MyBackupManager.DESCRIPTOR_FILE_NAME +
                    " doesn't exist in: '" + selectedFolder.uri.path + "'"
            )
            return
        }
        dataFolder = selectedFolder
        showDataFolder()
        resetProgress()
    }

    private fun showDataFolder() {
        val view = findViewById<TextView?>(R.id.backup_folder)
        if (view != null) {
            val folder = getDataFolder()
            view.text = if (MyBackupManager.isDataFolder(folder)) folder.uri.path else getText(R.string.not_set)
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
        val log = "${Integer.toString(progressCounter)}. $message" +
            "\n${progressLog.text}"
        progressLog.text = log
    }

    override fun onResume() {
        myContextHolder.getNow().isInForeground = true
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        myContextHolder.getNow().isInForeground = false
    }

    override fun onProgressMessage(message: CharSequence) {
        runOnUiThread { addProgressMessage(message) }
    }

    companion object {
        private const val MAX_RESTORE_SECONDS = 600
    }
}
