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

import io.vavr.control.Try
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyStorage
import org.andstatus.app.data.DownloadData
import org.andstatus.app.data.DownloadFile
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.net.http.HttpRequest
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.Connection
import org.andstatus.app.util.MyLog
import java.io.File

abstract class FileDownloader protected constructor(val myContext: MyContext?, val data: DownloadData?) {
    private var connectionMock: Connection? = null
    private var connectionRequired: ConnectionRequired? = ConnectionRequired.ANY
    fun load(commandData: CommandData?): Try<Boolean> {
        when (data.getStatus()) {
            DownloadStatus.LOADED -> {
            }
            else -> loadUrl()
        }
        if (data.isError() && !data.getMessage().isNullOrEmpty()) {
            commandData.getResult().message = data.getMessage()
        }
        if (data.isHardError()) {
            commandData.getResult().incrementParseExceptions()
        }
        if (data.isSoftError()) {
            commandData.getResult().incrementNumIoExceptions()
        }
        return if (commandData.getResult().hasError()) Try.failure(ConnectionException.Companion.fromStatusCode(StatusCode.UNKNOWN, commandData.getResult().toSummary())) else Try.success(true)
    }

    private fun loadUrl() {
        data.beforeDownload()
        downloadFile()
        data.saveToDatabase()
        if (!data.isError()) {
            onSuccessfulLoad()
        }
    }

    protected abstract fun onSuccessfulLoad()
    private fun downloadFile() {
        val method = "downloadFile"
        val fileTemp = DownloadFile(MyStorage.TEMP_FILENAME_PREFIX + data.getFilenameNew())
        val file = fileTemp.file
        val ma = findBestAccountForDownload()
        if (ma.isValidAndSucceeded()) {
            val connection = if (connectionMock == null) ma.getConnection() else connectionMock
            MyLog.v(this) {
                ("About to download " + data.toString() + "; connection"
                        + (if (connectionMock == null) "" else " (mocked)")
                        + ": " + connection
                        + "; account:" + ma.getAccountName())
            }
            Try.success(connection)
                    .flatMap { connection1: Connection? -> connection1.execute(newRequest(file)) }
                    .onFailure { e: Throwable? ->
                        val ce: ConnectionException = ConnectionException.Companion.of(e)
                        if (ce.isHardError) {
                            data.hardErrorLogged(method, ce)
                        } else {
                            data.softErrorLogged(method, ce)
                        }
                    }
        } else {
            MyLog.v(this) { "No account to download " + data.toString() + "; account:" + ma.getAccountName() }
            data.hardErrorLogged("$method, No account to download the file", null)
        }
        if (data.isError()) {
            fileTemp.delete()
        }
        val fileNew = DownloadFile(data.getFilenameNew())
        fileNew.delete()
        if (!data.isError() && !fileTemp.file.renameTo(fileNew.file)) {
            data.softErrorLogged("$method; Couldn't rename file $fileTemp to $fileNew", null)
        }
        data.onDownloaded()
    }

    private fun newRequest(file: File?): HttpRequest? {
        return HttpRequest.Companion.of(ApiRoutineEnum.DOWNLOAD_FILE, data.getUri())
                .withConnectionRequired(connectionRequired)
                .withFile(file)
    }

    fun setConnectionRequired(connectionRequired: ConnectionRequired?): FileDownloader? {
        this.connectionRequired = connectionRequired
        return this
    }

    fun getStatus(): DownloadStatus? {
        return data.getStatus()
    }

    protected abstract fun findBestAccountForDownload(): MyAccount?
    fun setConnectionMock(connectionMock: Connection?): FileDownloader? {
        this.connectionMock = connectionMock
        return this
    }

    companion object {
        fun newForDownloadData(myContext: MyContext?, data: DownloadData?): FileDownloader? {
            return if (data.actorId != 0L) {
                AvatarDownloader(myContext, data)
            } else {
                AttachmentDownloader(myContext, data)
            }
        }

        fun load(downloadData: DownloadData?, commandData: CommandData?): Try<Boolean> {
            val downloader = newForDownloadData(commandData.myAccount.origin.myContext, downloadData)
            return downloader.load(commandData)
        }
    }
}