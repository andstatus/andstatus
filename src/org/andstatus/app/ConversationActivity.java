/* 
 * Copyright (c) 2012 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app;

import java.util.ArrayList;
import java.util.Locale;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.andstatus.app.MyService.CommandData;
import org.andstatus.app.MyService.CommandEnum;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;

/**
 * One selected message and, optionally, whole conversation
 * 
 * @author yvolk
 */
public class ConversationActivity extends Activity {

    private static final String TAG = ConversationActivity.class.getSimpleName();

    private static final String[] PROJECTION = new String[] {
            Msg._ID,
            Msg.IN_REPLY_TO_MSG_ID,
            User.AUTHOR_NAME,
            User.SENDER_NAME,
            Msg.BODY,
            Msg.VIA,
            User.IN_REPLY_TO_NAME,
            Msg.IN_REPLY_TO_MSG_ID,
            User.RECIPIENT_NAME,
            Msg.CREATED_DATE
    };

    /**
     * Id of current Message, which is sort of "center" of the conversation view
     */
    protected long mCurrentId = 0;
    /**
     * We use this for message requests
     */
    protected MyAccount ma;

    protected long instanceId;
    Handler mHandler = new MyHandler();
    MyServiceConnector serviceConnector;

    /**
     * Message handler for messages from threads and from the remote
     * {@link MyService}.
     */
    private class MyHandler extends Handler {
        @Override
        public void handleMessage(android.os.Message msg) {
            MyLog.v(TAG, "handleMessage, what=" + msg.what + ", instance " + instanceId);
            switch (msg.what) {
                case MyServiceConnector.MSG_DATA_LOADING:
                    showConversation();
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (instanceId == 0) {
            instanceId = MyPreferences.nextInstanceId();
            MyLog.v(TAG, "onCreate instance " + instanceId);
        } else {
            MyLog.v(TAG, "onCreate reuse the same instance " + instanceId);
        }
        serviceConnector = new MyServiceConnector(instanceId);

        MyPreferences.loadTheme(TAG, this);

        final Intent intent = getIntent();
        Uri uri = intent.getData();

        long accountId = MyProvider.uriToAccountId(uri);
        if (accountId != 0) {
            ma = MyAccount.getMyAccount(accountId);
        }
        if (ma == null) {
            ma = MyAccount.getCurrentMyAccount();
        }

        mCurrentId = MyProvider.uriToMessageId(uri);
        showConversation();
    }

    protected void showConversation() {
        MyLog.v(TAG, "showConversation instance " + instanceId);
        if (mCurrentId != 0) {
            new ContentLoader().execute();
        }
    }

    private class ContentLoader extends AsyncTask<Void, Void, Void> {
        /**
         * One message row
         */
        private class OneRow {
            long id;
            long prevId = 0;
            long createdDate = 0;
            String author = "";
            String sender = "";
            String body = "";
            String via = "";
            String replyToName = "";
            String recipientName = "";

            public OneRow(long id) {
                this.id = id;
            }

            @Override
            public boolean equals(Object o) {
                boolean equal = false;
                if (o != null && this.getClass().isAssignableFrom(o.getClass())) {
                    equal = (this.id == ((OneRow) o).id);
                }
                return equal;
            }

        }

        /**
         * Rows of the conversation TODO: sort them and maybe format differently
         */
        ArrayList<OneRow> rows = new ArrayList<OneRow>();

        @Override
        protected Void doInBackground(Void... params) {

            // Recursively show all previous messages
            findMessage(mCurrentId);
            return null;
        }

        private void findMessage(long msgId) {
            Uri uri = MyProvider.getTimelineMsgUri(ma.getUserId(), msgId);
            boolean skip = true;
            Cursor msg = null;
            OneRow row = new OneRow(msgId);
            if (msgId != 0) {
                msg = getContentResolver().query(uri, PROJECTION, null, null, null);
            }
            if (msg != null) {
                if (msg.moveToFirst()) {
                    row.prevId = msg.getLong(msg.getColumnIndex(Msg.IN_REPLY_TO_MSG_ID));
                    row.createdDate = msg.getLong(msg.getColumnIndex(Msg.CREATED_DATE));
                    row.author = msg.getString(msg.getColumnIndex(User.AUTHOR_NAME));
                    row.sender = msg.getString(msg.getColumnIndex(User.SENDER_NAME));
                    row.body = msg.getString(msg.getColumnIndex(Msg.BODY));
                    row.via = Html.fromHtml(msg.getString(msg.getColumnIndex(Msg.VIA))).toString();
                    int colIndex = msg.getColumnIndex(User.IN_REPLY_TO_NAME);
                    if (colIndex > -1) {
                        row.replyToName = msg.getString(colIndex);
                    }
                    colIndex = msg.getColumnIndex(User.RECIPIENT_NAME);
                    if (colIndex > -1) {
                        row.recipientName = msg.getString(colIndex);
                    }

                    if (rows.contains(row)) {
                        MyLog.v(TAG, "Message " + msgId + " is in the list already");
                    } else {
                        rows.add(row);
                        skip = false;
                    }
                }
                msg.close();

                if (!skip) {
                    if (row.createdDate == 0) {
                        MyLog.v(TAG, "Message " + msgId + " should be retrieved from the Internet");
                        serviceConnector.sendCommand(new CommandData(CommandEnum.GET_STATUS, ma
                                .getAccountGuid(), msgId));
                    } else {
                        if (row.prevId != 0) {
                            findMessage(row.prevId);
                        } else if (!MyPreferences.isEmpty(row.replyToName)) {
                            MyLog.v(TAG, "Message " + msgId + " has reply to name ("
                                    + row.replyToName
                                    + ") but no reply to message id");
                            // Don't try to retrieve this message again. It
                            // looks like there really are such messages.
                            OneRow row2 = new OneRow(0);
                            row2.author = row.replyToName;
                            row2.body = "("
                                    + ConversationActivity.this
                                            .getText(R.string.id_of_this_message_was_not_specified)
                                    + ")";
                            rows.add(row2);
                            skip = true;
                        }
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            // Recreate conversation list
            if (rows.size() > 1) {
                setTitle(R.string.label_conversation);
            } else {
                setTitle(R.string.label_conversationactivity);
            }
            setContentView(R.layout.conversation);
            LinearLayout list = (LinearLayout) findViewById(android.R.id.list);
            for (int ind = 0; ind < rows.size(); ind++) {
                list.addView(rowToView(rows.get(ind)));
            }
            super.onPostExecute(result);
        }

        /**
         * Formats message as a View (LinearLayout) suitable for a conversation
         * list
         * 
         * @param message row to add to the conversation list
         */
        private LinearLayout rowToView(OneRow row) {
            LayoutInflater inflater = LayoutInflater.from(ConversationActivity.this);
            LinearLayout messageView = (LinearLayout) inflater.inflate(R.layout.tweetlist_item,
                    null);

            TextView author = (TextView) messageView.findViewById(R.id.message_author);
            TextView body = (TextView) messageView.findViewById(R.id.message_body);
            TextView details = (TextView) messageView.findViewById(R.id.message_details);

            author.setText(row.author);
            body.setLinksClickable(true);
            body.setFocusable(true);
            body.setFocusableInTouchMode(true);
            body.setText(row.body);
            Linkify.addLinks(body, Linkify.ALL);

            // Everything else goes to messageDetails
            String messageDetails = RelativeTime.getDifference(ConversationActivity.this,
                    row.createdDate);
            if (!MyPreferences.isEmpty(row.via)) {
                messageDetails += " " + String.format(
                        Locale.getDefault(),
                        getText(R.string.tweet_source_from).toString(),
                        row.via);
            }
            if (!MyPreferences.isEmpty(row.replyToName)) {
                messageDetails += " "
                        + String.format(Locale.getDefault(),
                                getText(R.string.tweet_source_in_reply_to).toString(),
                                row.replyToName);
            }
            if (!MyPreferences.isEmpty(row.sender)) {
                if (!row.sender.equals(row.author)) {
                    if (!MyPreferences.isEmpty(row.replyToName)) {
                        messageDetails += ";";
                    }
                    messageDetails += " "
                            + String.format(Locale.getDefault(), getText(R.string.retweeted_by)
                                    .toString(), row.sender);
                }
            }
            if (!MyPreferences.isEmpty(row.recipientName)) {
                messageDetails += " "
                        + String.format(Locale.getDefault(), getText(R.string.tweet_source_to)
                                .toString(), row.recipientName);
            }
            details.setText(messageDetails);
            return messageView;
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        serviceConnector.bindToService(this, mHandler);
    }

    @Override
    protected void onPause() {
        super.onPause();
        serviceConnector.disconnectService();
    }
}
