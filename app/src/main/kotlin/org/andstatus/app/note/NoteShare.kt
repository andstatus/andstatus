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
package org.andstatus.app.note

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.andstatus.app.R
import org.andstatus.app.context.ActorInTimeline
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.data.FileProvider
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.UriUtils

class NoteShare(private val origin: Origin, private val noteId: Long, private val downloads: NoteDownloads) {
    fun viewImage(activity: Activity) {
        if (downloads.nonEmpty) {
            activity.startActivity(intentToViewAndShare(false))
        }
    }

    /**
     * @return true if succeeded
     */
    fun share(context: Context): Boolean {
        if (!origin.isValid) {
            return false
        }
        context.startActivity(
            Intent.createChooser(intentToViewAndShare(true), context.getText(R.string.menu_item_share))
        )
        return true
    }

    fun intentToViewAndShare(share: Boolean): Intent {
        val noteName = MyQuery.noteIdToStringColumnValue(NoteTable.NAME, noteId)
        val noteSummary = MyQuery.noteIdToStringColumnValue(NoteTable.SUMMARY, noteId)
        val noteContent = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId)
        var subjectString: CharSequence = noteName
        if (noteSummary.isNotEmpty()) {
            subjectString = subjectString.toString() + (if (subjectString.isNotEmpty()) ". " else "") + noteSummary
        }
        if (subjectString.isEmpty()) {
            subjectString = I18n.trimTextAt(MyHtml.htmlToCompactPlainText(noteContent), 80)
        }
        subjectString =
            (if (MyQuery.isSensitive(noteId)) "(" + myContextHolder.getNow().context.getText(R.string.sensitive) + ") " else "") +
                myContextHolder.getNow().context.getText(origin.alternativeTermForResourceId(R.string.message)) +
                " - " + subjectString
        val intent = Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW)
        val downloadData = downloads.getFirstToShare()
        val mediaFileUri = if (downloadData.file.existsNow())
            FileProvider.downloadFilenameToUri(downloadData.filename)
        else downloadData.getUri()
        if (share || UriUtils.isEmpty(mediaFileUri)) {
            intent.type = "text/*"
        } else {
            intent.setDataAndType(mediaFileUri, downloadData.getMimeType())
        }
        if (UriUtils.nonEmpty(mediaFileUri)) {
            intent.putExtra(Intent.EXTRA_STREAM, mediaFileUri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, subjectString)
        intent.putExtra(Intent.EXTRA_TEXT, buildBody(origin, MyHtml.htmlToPlainText(noteContent), false))
        intent.putExtra(Intent.EXTRA_HTML_TEXT, buildBody(origin, noteContent, true))
        return intent
    }

    private fun buildBody(origin: Origin, noteContent: String?, isHtml: Boolean): String {
        return StringBuilder()
            .append(noteContent)
            .append(
                StringUtil.format(
                    if (isHtml) SIGNATURE_FORMAT_HTML else SIGNATURE_PLAIN_TEXT,
                    MyQuery.noteIdToUsername(
                        NoteTable.AUTHOR_ID,
                        noteId,
                        if (origin.isMentionAsWebFingerId()) ActorInTimeline.WEBFINGER_ID else ActorInTimeline.USERNAME
                    ),
                    origin.getNotePermalink(noteId)
                )
            ).toString()
    }

    /**
     * @return true if succeeded
     */
    fun openPermalink(context: Context): Boolean {
        return if (!origin.isValid) false else openLink(context, origin.getNotePermalink(noteId))
    }

    companion object {
        private val SIGNATURE_FORMAT_HTML: String = "<p>-- <br />\n%s<br />\nURL: %s</p>"
        private val SIGNATURE_PLAIN_TEXT: String = "\n-- \n%s\n URL: %s"

        fun openLink(context: Context, urlString: String?): Boolean {
            return if (urlString.isNullOrEmpty()) {
                false
            } else {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(urlString)
                context.startActivity(intent)
                true
            }
        }
    }

    init {
        if (!origin.isValid) {
            MyLog.v(this) { "Origin not found for noteId=$noteId" }
        }
    }
}
