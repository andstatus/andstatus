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

package org.andstatus.app.note;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.andstatus.app.R;
import org.andstatus.app.context.ActorInTimeline;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.FileProvider;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.UriUtils;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

public class NoteShare {
    private final Origin origin;
    private final long noteId;
    private final NoteDownloads downloads;
    
    public NoteShare(Origin origin, long noteId, NoteDownloads downloads) {
        this.origin = origin;
        this.noteId = noteId;
        this.downloads = downloads;
        if (origin == null) {
            MyLog.v(this, () -> "Origin not found for noteId=" + noteId);
        }
    }

    public void viewImage(Activity activity) {
        if (downloads.nonEmpty()) {
            activity.startActivity(intentToViewAndShare(false));
        }
    }

    /**
     * @return true if succeeded
     */
    public boolean share(Context context) {
        if (origin == null) {
            return false;
        }
        context.startActivity(
                Intent.createChooser(intentToViewAndShare(true),
                        context.getText(R.string.menu_item_share)));
        return true;
    }

    Intent intentToViewAndShare(boolean share) {
        String noteName = MyQuery.noteIdToStringColumnValue(NoteTable.NAME, noteId);
        String noteSummary = MyQuery.noteIdToStringColumnValue(NoteTable.SUMMARY, noteId);
        String noteContent = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId);

        CharSequence subjectString = noteName;
        if (StringUtil.nonEmpty(noteSummary)) {
            subjectString = subjectString + (StringUtil.nonEmpty(subjectString) ? ". " : "") + noteSummary;
        }
        if (StringUtil.isEmpty(subjectString)) {
            subjectString = I18n.trimTextAt(MyHtml.htmlToCompactPlainText(noteContent), 80);
        }
        subjectString =
                (MyQuery.isSensitive(noteId)
                        ? "(" + myContextHolder.getNow().context().getText(R.string.sensitive) + ") "
                        : "") +
                myContextHolder.getNow().context().getText(origin.alternativeTermForResourceId(R.string.message)) +
                " - " + subjectString;

        Intent intent = new Intent(share ? android.content.Intent.ACTION_SEND : Intent.ACTION_VIEW);
        DownloadData downloadData = downloads.getFirstToShare();
        final Uri mediaFileUri = downloadData.getFile().existsNow()
                ? FileProvider.downloadFilenameToUri(downloadData.getFilename())
                : downloadData.getUri();
        if (share || UriUtils.isEmpty(mediaFileUri)) {
            intent.setType("text/*");
        } else {
            intent.setDataAndType(mediaFileUri, downloadData.getMimeType());
        }
        if (UriUtils.nonEmpty(mediaFileUri)) {
            intent.putExtra(Intent.EXTRA_STREAM, mediaFileUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, subjectString);
        intent.putExtra(Intent.EXTRA_TEXT, buildBody(origin, MyHtml.htmlToPlainText(noteContent), false));
        intent.putExtra(Intent.EXTRA_HTML_TEXT, buildBody(origin, noteContent, true));
        return intent;
    }

    private static String SIGNATURE_FORMAT_HTML = "<p>-- <br />\n%s<br />\nURL: %s</p>";
    private static String SIGNATURE_PLAIN_TEXT = "\n-- \n%s\n URL: %s";

    private String buildBody(Origin origin, String noteContent, boolean isHtml) {
        return new StringBuilder()
                .append(noteContent)
                .append(
                        StringUtil.format(
                                isHtml ? SIGNATURE_FORMAT_HTML
                                        : SIGNATURE_PLAIN_TEXT,
                                MyQuery.noteIdToUsername(
                                        NoteTable.AUTHOR_ID,
                                        noteId,
                                        origin.isMentionAsWebFingerId() ? ActorInTimeline.WEBFINGER_ID
                                                : ActorInTimeline.USERNAME),
                                origin.getNotePermalink(noteId)
                                )).toString();
    }

    /**
     * @return true if succeeded
     */
    public boolean openPermalink(Context context) {
        return origin == null ? false : openLink(context, origin.getNotePermalink(noteId));
    }

    public static boolean openLink(Context context, String urlString) {
        if (StringUtil.isEmpty(urlString)) {
            return false;
        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(urlString));
            context.startActivity(intent);
            return true;
        }
    }

}
