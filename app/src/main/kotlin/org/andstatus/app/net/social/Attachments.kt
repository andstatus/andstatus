/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.net.social

import org.andstatus.app.context.MyContext
import org.andstatus.app.data.DownloadData
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.note.NoteDownloads
import org.andstatus.app.service.CommandExecutionContext
import org.andstatus.app.service.FileDownloader
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.UriUtils
import java.util.*
import java.util.stream.Collectors

class Attachments private constructor(isEmpty: Boolean) : IsEmpty {
    val list: MutableList<Attachment>

    constructor() : this(false) {}

    fun add(attachment: Attachment): Attachments {
        if (!attachment.isValid() || list.contains(attachment)) return this
        val attachments = Attachments(false)
        attachments.list.addAll(list)
        attachments.list.add(attachment)
        return attachments
    }

    fun save(execContext: CommandExecutionContext, noteId: Long) {
        renumber()
        val downloads: MutableList<DownloadData> = ArrayList()
        for (attachment in list) {
            val dd: DownloadData = DownloadData.fromAttachment(noteId, attachment)
            dd.setDownloadNumber(attachment.getDownloadNumber())
            if (attachment.previewOf.nonEmpty) {
                dd.setPreviewOfDownloadId( downloads.stream().filter { d: DownloadData -> d.getUri() == attachment.previewOf.uri }.findAny()
                        .map { obj: DownloadData -> obj.downloadId }.orElse(0L))
            }
            dd.saveToDatabase()
            downloads.add(dd)
            when (dd.getStatus()) {
                DownloadStatus.LOADED, DownloadStatus.HARD_ERROR -> {
                }
                else -> if (UriUtils.isDownloadable(dd.getUri())) {
                    if (attachment.contentType.getDownloadMediaOfThisType()) {
                        dd.requestDownload(execContext.myContext)
                    }
                } else {
                    FileDownloader.load(dd, execContext.commandData)
                }
            }
        }
        DownloadData.deleteOtherOfThisNote(execContext.myContext, noteId,
                downloads.stream().map { obj: DownloadData -> obj.downloadId }.collect(Collectors.toList()))
    }

    private fun renumber() {
        val copy: MutableList<Attachment> = ArrayList(list)
        Collections.sort(copy)
        for (ind in copy.indices) {
            copy[ind].setDownloadNumber(ind.toLong())
        }
    }

    override val isEmpty: Boolean
        get() {
            return list.isEmpty()
        }

    fun clear() {
        list.clear()
    }

    fun copy(): Attachments {
        val attachments = Attachments()
        attachments.list.addAll(list)
        return attachments
    }

    fun size(): Int {
        return list.size
    }

    override fun toString(): String {
        return this.javaClass.simpleName + "{" +
                list +
                '}'
    }

    fun toUploadCount(): Long {
        return list.stream().filter { a: Attachment -> !UriUtils.isDownloadable(a.uri) }.count()
    }

    fun getFirstToUpload(): Attachment {
        return list.stream().filter { a: Attachment -> !UriUtils.isDownloadable(a.uri) }.findFirst().orElse(Attachment.EMPTY)
    }

    companion object {
        val EMPTY: Attachments = Attachments(true)

        fun load(myContext: MyContext, noteId: Long): Attachments {
            if (myContext.isEmptyOrExpired || noteId == 0L) return EMPTY
            val downloads: NoteDownloads = NoteDownloads.fromNoteId(myContext, noteId)
            if (downloads.isEmpty) return EMPTY
            val map: MutableMap<Long, Attachment> = HashMap()
            for (downloadData in downloads.list) {
                map[downloadData.downloadId] = Attachment(downloadData)
            }
            var attachments = Attachments()
            for (downloadData in downloads.list) {
                map[downloadData.downloadId]?.let { attachment ->
                    if (downloadData.getPreviewOfDownloadId() != 0L) {
                        val target = map[downloadData.getPreviewOfDownloadId()]
                        if (target == null) {
                            MyLog.i(downloadData, "Couldn't find target of preview $downloadData")
                        } else {
                            attachment.setPreviewOf(target)
                        }
                    }
                    attachments = attachments.add(attachment)
                }
            }
            return attachments
        }
    }

    init {
        list = if (isEmpty) mutableListOf() else ArrayList()
    }
}
