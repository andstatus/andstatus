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
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.ActorInTimeline;
import org.andstatus.app.data.FileProvider;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;

public class NoteShare {
    private final Origin origin;
    private final long messageId;
    private final String imageFilename;
    
    public NoteShare(Origin origin, long messageId, String imageFilename) {
        this.origin = origin;
        this.messageId = messageId;
        this.imageFilename = imageFilename;
        if (origin == null) {
            MyLog.v(this, "Origin not found for messageId=" + messageId);
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
        StringBuilder subject = new StringBuilder();
        String msgBody = MyQuery.msgIdToStringColumnValue(NoteTable.BODY, messageId);
        String msgBodyPlainText = msgBody;
        if (origin.isHtmlContentAllowed()) {
            msgBodyPlainText = MyHtml.fromHtml(msgBody);
        }

        subject.append(MyContextHolder.get().context()
                .getText(origin.alternativeTermForResourceId(R.string.message)));
        subject.append(" - " + msgBodyPlainText);

        Intent intent = new Intent(share ? android.content.Intent.ACTION_SEND : Intent.ACTION_VIEW);
        if (share || TextUtils.isEmpty(imageFilename)) {
            intent.setType("text/*");
        } else {
            intent.setDataAndType(FileProvider.downloadFilenameToUri(imageFilename),"image/*");
        }
        if (!TextUtils.isEmpty(imageFilename)) {
            intent.putExtra(Intent.EXTRA_STREAM, FileProvider.downloadFilenameToUri(imageFilename));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, I18n.trimTextAt(subject.toString(), 80));
        intent.putExtra(Intent.EXTRA_TEXT, buildBody(origin, msgBodyPlainText, false));
        if (origin.isHtmlContentAllowed() && MyHtml.hasHtmlMarkup(msgBody) ) {
            intent.putExtra(Intent.EXTRA_HTML_TEXT, buildBody(origin, msgBody, true));
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
                                MyQuery.msgIdToUsername(
                                        NoteTable.AUTHOR_ID,
                                        messageId,
                                        origin.isMentionAsWebFingerId() ? ActorInTimeline.WEBFINGER_ID
                                                : ActorInTimeline.USERNAME),
                                origin.messagePermalink(messageId)
                                )).toString();
    }

    /**
     * @return true if succeeded
     */
    public boolean openPermalink(Context context) {
        return origin == null ? false : openLink(context, origin.messagePermalink(messageId));
    }

    public static boolean openLink(Context context, String urlString) {
        if (TextUtils.isEmpty(urlString)) {
            return false;
        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(urlString));
            context.startActivity(intent);
            return true;
        }
    }

}
