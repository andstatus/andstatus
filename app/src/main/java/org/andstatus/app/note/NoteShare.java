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
import android.text.TextUtils;

import org.andstatus.app.R;
import org.andstatus.app.context.ActorInTimeline;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.FileProvider;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UriUtils;

public class NoteShare {
    private final Origin origin;
    private final long noteId;
    private final String imageFilename;
    
    public NoteShare(Origin origin, long noteId, String imageFilename) {
        this.origin = origin;
        this.noteId = noteId;
        this.imageFilename = imageFilename;
        if (origin == null) {
            MyLog.v(this, "Origin not found for noteId=" + noteId);
        }
    }

    public void viewImage(Activity activity) {
        activity.startActivity(intentToViewAndShare(false));
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
        String noteContent = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId);
        String noteContentPlainText = origin.isHtmlContentAllowed() ? MyHtml.fromHtml(noteContent) : noteContent;
        StringBuilder subject = new StringBuilder(
                MyContextHolder.get().context().getText(origin.alternativeTermForResourceId(R.string.message)));
        subject.append(" - " + (StringUtils.nonEmpty(noteName) ? noteName : noteContentPlainText));

        Intent intent = new Intent(share ? android.content.Intent.ACTION_SEND : Intent.ACTION_VIEW);
        final Uri imageFileUri = FileProvider.downloadFilenameToUri(imageFilename);
        if (share || UriUtils.isEmpty(imageFileUri)) {
            intent.setType("text/*");
        } else {
            intent.setDataAndType(imageFileUri, MyContentType.uri2MimeType(
                    MyContextHolder.get().context().getContentResolver(), imageFileUri, "image/*"
            ));
        }
        if (UriUtils.nonEmpty(imageFileUri)) {
            intent.putExtra(Intent.EXTRA_STREAM, imageFileUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, I18n.trimTextAt(subject.toString(), 80));
        intent.putExtra(Intent.EXTRA_TEXT, buildBody(origin, noteContentPlainText, false));
        if (origin.isHtmlContentAllowed() && MyHtml.hasHtmlMarkup(noteContent) ) {
            intent.putExtra(Intent.EXTRA_HTML_TEXT, buildBody(origin, noteContent, true));
        }
        return intent;
    }

    private static String SIGNATURE_FORMAT_HTML = "<p>-- <br />\n%s<br />\nURL: %s</p>";
    private static String SIGNATURE_PLAIN_TEXT = "\n-- \n%s\n URL: %s";

    private String buildBody(Origin origin, String msgBodyText, boolean html) {
        return new StringBuilder()
                .append(msgBodyText)
                .append(
                        String.format(
                                html ? SIGNATURE_FORMAT_HTML
                                        : SIGNATURE_PLAIN_TEXT,
                                MyQuery.noteIdToUsername(
                                        NoteTable.AUTHOR_ID,
                                        noteId,
                                        origin.isMentionAsWebFingerId() ? ActorInTimeline.WEBFINGER_ID
                                                : ActorInTimeline.USERNAME),
                                origin.notePermalink(noteId)
                                )).toString();
    }

    /**
     * @return true if succeeded
     */
    public boolean openPermalink(Context context) {
        return origin == null ? false : openLink(context, origin.notePermalink(noteId));
    }

    public static boolean openLink(Context context, String urlString) {
        if (StringUtils.isEmpty(urlString)) {
            return false;
        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(urlString));
            context.startActivity(intent);
            return true;
        }
    }

}
