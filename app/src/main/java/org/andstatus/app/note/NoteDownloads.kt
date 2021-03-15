package org.andstatus.app.note

import org.andstatus.app.context.MyContext
import org.andstatus.app.data.DownloadData
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.util.IsEmpty

class NoteDownloads private constructor(val noteId: Long, val list: List<DownloadData>) : IsEmpty {
    override val isEmpty: Boolean
        get() {
            return list.isEmpty()
        }

    fun getFirstForTimeline(): DownloadData {
        return list.stream()
                .filter { d: DownloadData -> d.getStatus() == DownloadStatus.LOADED && d.getContentType().isImage() }
                .findFirst()
                .orElse(
                        list.stream().filter { d: DownloadData -> d.getStatus() == DownloadStatus.LOADED }
                                .findFirst()
                                .orElse(list.stream().findFirst().orElse(DownloadData.EMPTY)))
    }

    fun getFirstToShare(): DownloadData {
        return list.stream().filter { d: DownloadData -> d.getPreviewOfDownloadId() == 0L }.findFirst()
                .orElse(list.stream().findFirst().orElse(DownloadData.EMPTY))
    }

    fun fromId(downloadId: Long): DownloadData {
        return list.stream().filter { d: DownloadData -> d.getDownloadId() == downloadId }.findFirst().orElse(DownloadData.EMPTY)
    }

    companion object {
        fun fromNoteId(myContext: MyContext, noteId: Long): NoteDownloads {
            return NoteDownloads(noteId, DownloadData.fromNoteId(myContext, noteId))
        }
    }
}