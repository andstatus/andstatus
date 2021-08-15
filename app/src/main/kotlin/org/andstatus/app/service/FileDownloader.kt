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
import org.andstatus.app.net.http.HttpRequest
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.Connection
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.TryUtils.onFailureAsConnectionException
import java.io.File

abstract class FileDownloader protected constructor(val myContext: MyContext, val data: DownloadData) {
    private var connectionStub: Connection? = null
    private var connectionRequired: ConnectionRequired = ConnectionRequired.ANY

    fun load(commandData: CommandData): Try<Boolean> =
        if (data.getStatus() == DownloadStatus.LOADED) { TryUtils.TRUE }
        else doDownloadFile()
            .onFailureAsConnectionException { ce ->
                if (ce.isHardError) {
                    data.hardErrorLogged("load", ce)
                    commandData.getResult().incrementParseExceptions()
                } else {
                    data.softErrorLogged("load", ce)
                    commandData.getResult().incrementNumIoExceptions()
                }
                ce.message?.let { message ->
                    commandData.getResult().setMessage(message)
                }
            }
            .also { data.saveToDatabase() }
            .onSuccess { onSuccessfulLoad() }
            .map { true }

    protected abstract fun onSuccessfulLoad()

    private fun doDownloadFile(): Try<Boolean> {
        val method = this::doDownloadFile.name
        val fileTemp = DownloadFile(
            MyStorage.TEMP_FILENAME_PREFIX + MyLog.uniqueCurrentTimeMS +
                "_" + data.filenameNew
        )
        if (fileTemp.existsNow()) fileTemp.delete()

        data.beforeDownload()
            .flatMap {
                if (fileTemp.existsNow()) {
                    TryUtils.failure("$method; Couldn't delete existing temp file $fileTemp")
                } else TryUtils.TRUE
            }
            .flatMap {
                val ma = findBestAccountForDownload()
                if (ma.isValidAndSucceeded()) {
                    val connection = connectionStub ?: ma.connection
                    MyLog.v(this) {
                        ("About to download " + data.toString() + "; connection"
                            + (if (connectionStub == null) "" else " (stubbed)")
                            + ": " + connection
                            + "; account:" + ma.getAccountName())
                    }
                    newRequest(fileTemp.getFile())
                        .let(connection::execute)
                        .map { true }

                } else {
                    TryUtils.failure("No account to download " + data.toString() + "; account:" + ma.getAccountName())
                }
            }
            .flatMap {
                if (fileTemp.existsNow()) TryUtils.TRUE
                else TryUtils.failure("$method; New temp file doesn't exist $fileTemp")
            }
            .flatMap {
                val fileNew = DownloadFile(data.filenameNew)
                MyLog.v(this) { "$method; Renaming $fileTemp to $fileNew" }
                fileNew.delete()
                if (fileNew.existsNow()) {
                    TryUtils.failure("$method; Couldn't delete existing file $fileNew")
                } else {
                    val file1 = fileTemp.getFile()
                    val file2 = fileNew.getFile()
                    if (file1 == null) {
                        TryUtils.failure("$method; file1 is null ???")
                    } else if (file2 == null) {
                        TryUtils.failure<Boolean>("$method; file2 is null ???")
                    } else {
                        if (file1.renameTo(file2)) {
                            if (!fileNew.existsNow()) {
                                TryUtils.failure<Boolean>(
                                    "$method; After renamingfrom $fileTemp" +
                                        " new file doesn't exist $fileNew"
                                )
                            } else TryUtils.TRUE
                        } else {
                            TryUtils.failure("$method; Couldn't rename file $fileTemp to $fileNew")
                        }
                    }
                }
            }
            .flatMap { data.onDownloaded() }
            .onFailure {
                fileTemp.delete()
                data.onNoFile()
            }
            .let { return it }
    }

    private fun newRequest(file: File?): HttpRequest {
        return HttpRequest.of(ApiRoutineEnum.DOWNLOAD_FILE, data.getUri())
                .withConnectionRequired(connectionRequired)
                .withFile(file)
    }

    fun setConnectionRequired(connectionRequired: ConnectionRequired): FileDownloader {
        this.connectionRequired = connectionRequired
        return this
    }

    fun getStatus(): DownloadStatus {
        return data.getStatus()
    }

    protected abstract fun findBestAccountForDownload(): MyAccount
    fun setConnectionStub(connectionStub: Connection?): FileDownloader {
        this.connectionStub = connectionStub
        return this
    }

    companion object {
        fun newForDownloadData(myContext: MyContext, data: DownloadData): FileDownloader {
            return if (data.actorId != 0L) {
                AvatarDownloader(myContext, data)
            } else {
                AttachmentDownloader(myContext, data)
            }
        }

        fun load(downloadData: DownloadData, commandData: CommandData): Try<Boolean> {
            val downloader = newForDownloadData(commandData.myAccount.myContext, downloadData)
            return downloader.load(commandData)
        }
    }
}
