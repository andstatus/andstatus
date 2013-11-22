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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.MyService.CommandEnum;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;

/**
 * One selected message and, optionally, the whole conversation
 * 
 * @author yvolk@yurivolkov.com
 */
public class ConversationActivity extends Activity implements MyServiceListener, ActionableMessageList {

    private static final String TAG = ConversationActivity.class.getSimpleName();

    private static final String[] PROJECTION = new String[] {
            Msg._ID,
            Msg.IN_REPLY_TO_MSG_ID,
            Msg.AUTHOR_ID,
            User.AUTHOR_NAME,
            Msg.SENDER_ID,
            Msg.BODY,
            Msg.VIA,
            User.IN_REPLY_TO_NAME,
            Msg.IN_REPLY_TO_MSG_ID,
            User.RECIPIENT_NAME,
            Msg.CREATED_DATE,
            User.LINKED_USER_ID,
            MsgOfUser.REBLOGGED,
            MsgOfUser.FAVORITED
    };

    /**
     * Id of current Message, which is sort of a "center" of the conversation view
     */
    protected long mCurrentId = 0;
    /**
     * We use this for message requests
     */
    protected MyAccount ma;

    protected int instanceId;
    MyServiceReceiver myServiceReceiver;

    private MessageContextMenu contextMenu;
    /** 
     * Controls of the TweetEditor
     */
    private MessageEditor messageEditor;
    
    private Object messagesLock = new Object(); 
    @GuardedBy("messagesLock")
    private List<OneMessage> messages = new ArrayList<OneMessage>();

    /**
     * One message row
     */
    private class OneMessage {
        long id;
        long inReplyToMsgId = 0;
        long createdDate = 0;
        long linkedUserId = 0;
        boolean favorited = false;
        String author = "";
        
        /**
         * Comma separated list of the names of all known rebloggers of the message
         */
        String rebloggersString = "";
        String body = "";
        String via = "";
        String inReplyToName = "";
        String recipientName = "";

        public OneMessage(long id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof OneMessage)) {
                return false;
            }
            OneMessage row = (OneMessage) o;
            return (id == row.id);
        }

        @Override
        public int hashCode() {
            return Long.valueOf(id).hashCode();
        }

    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);    // Before loading the content view
        super.onCreate(savedInstanceState);

        if (instanceId == 0) {
            instanceId = InstanceId.next();
            MyLog.v(this, "onCreate instanceId=" + instanceId);
        } else {
            MyLog.v(this, "onCreate reuse the same instanceId=" + instanceId);
        }
        MyServiceManager.setServiceAvailable();
        myServiceReceiver = new MyServiceReceiver(this);

        MyPreferences.loadTheme(TAG, this);

        final Intent intent = getIntent();
        Uri uri = intent.getData();

        mCurrentId = MyProvider.uriToMessageId(uri);
        ma = MyContextHolder.get().persistentAccounts().getAccountWhichMayBeLinkedToThisMessage(mCurrentId, 0, MyProvider.uriToAccountUserId(uri));

        setContentView(R.layout.conversation);

        ViewGroup messageListParent = (ViewGroup) findViewById(R.id.messageListParent);
        LayoutInflater inflater = LayoutInflater.from(this);
        ViewGroup actionsView = (ViewGroup) inflater.inflate(R.layout.conversation_actions, null);
        messageListParent.addView(actionsView, 0);


        OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConversationActivity.this.finish();
            }
        };
        findViewById(R.id.upNavigation).setOnClickListener(listener);
        findViewById(R.id.actionsIcon).setOnClickListener(listener);
        
        messageEditor = new MessageEditor(this);
        messageEditor.hide();
        contextMenu = new MessageContextMenu(this);
        
        if (ma != null) {
            showConversation();
        }
    }

    protected void showConversation() {
        MyLog.v(this, "showConversation, instanceId=" + instanceId);
        if (mCurrentId != 0) {
            new ContentLoader().execute();
        }
    }

    private class ContentLoader extends AsyncTask<Void, Void, Void> {
        List<OneMessage> rows = new ArrayList<OneMessage>();
        List<Long> idsOfTheMessagesToFind = new ArrayList<Long>();

        @Override
        protected Void doInBackground(Void... params) {

            // Recursively show all previous messages
            findMessage(mCurrentId);
            return null;
        }

        private void findMessage(long msgId) {
            if (idsOfTheMessagesToFind.contains(msgId)) {
                MyLog.v(this, "findMessage cycled on the msgId=" + msgId);
                return;
            }
            idsOfTheMessagesToFind.add(msgId);
            MyLog.v(this, "findMessage " + msgId);
            Uri uri = MyProvider.getTimelineMsgUri(ma.getUserId(), TimelineTypeEnum.HOME, true, msgId);
            boolean skip = true;
            Cursor msg = null;
            OneMessage row = new OneMessage(msgId);
            if (msgId != 0) {
                msg = getContentResolver().query(uri, PROJECTION, null, null, null);
            }
            if (msg != null) {
                if (msg.moveToFirst()) {
                    /**
                     * IDs of all known senders of this message except for the Author
                     * These "senders" reblogged the message
                     */
                    Set<Long> rebloggers = new HashSet<Long>();
                    int ind=0;
                    do {
                        long senderId = msg.getLong(msg.getColumnIndex(Msg.SENDER_ID));
                        long authorId = msg.getLong(msg.getColumnIndex(Msg.AUTHOR_ID));
                        long linkedUserId = msg.getLong(msg.getColumnIndex(User.LINKED_USER_ID));

                        if (ind == 0) {
                            // This is the same for all retrieved rows
                            row.inReplyToMsgId = msg.getLong(msg.getColumnIndex(Msg.IN_REPLY_TO_MSG_ID));
                            row.createdDate = msg.getLong(msg.getColumnIndex(Msg.CREATED_DATE));
                            row.author = msg.getString(msg.getColumnIndex(User.AUTHOR_NAME));
                            row.body = msg.getString(msg.getColumnIndex(Msg.BODY));
                            row.via = Html.fromHtml(msg.getString(msg.getColumnIndex(Msg.VIA))).toString().trim();
                            int colIndex = msg.getColumnIndex(User.IN_REPLY_TO_NAME);
                            if (colIndex > -1) {
                                row.inReplyToName = msg.getString(colIndex);
                                if (TextUtils.isEmpty(row.inReplyToName)) {
                                    row.inReplyToName = "";
                                }
                            }
                            colIndex = msg.getColumnIndex(User.RECIPIENT_NAME);
                            if (colIndex > -1) {
                                row.recipientName = msg.getString(colIndex);
                                if (TextUtils.isEmpty(row.recipientName)) {
                                    row.recipientName = "";
                                }
                            }
                        }

                        if (senderId != authorId) {
                            rebloggers.add(senderId);
                        }
                        if (linkedUserId != 0) {
                            if (row.linkedUserId == 0) {
                                row.linkedUserId = linkedUserId;
                            }
                            if (msg.getInt(msg.getColumnIndex(MsgOfUser.REBLOGGED)) == 1
                                    && linkedUserId != authorId) {
                                rebloggers.add(linkedUserId);
                            }
                            if (msg.getInt(msg.getColumnIndex(MsgOfUser.FAVORITED)) == 1) {
                                row.favorited = true;
                            }
                        }
                        
                        ind++;
                    } while (msg.moveToNext());

                    for (long rebloggerId : rebloggers) {
                        if (!TextUtils.isEmpty(row.rebloggersString)) {
                            row.rebloggersString += ", ";
                        }
                        row.rebloggersString += MyProvider.userIdToName(rebloggerId);
                    }
                    if (rows.contains(row)) {
                        MyLog.v(this, "Message " + msgId + " is in the list already");
                    } else {
                        rows.add(row);
                        skip = false;
                    }
                }
                msg.close();

                if (!skip) {
                    if (row.createdDate == 0) {
                        MyLog.v(this, "Message " + msgId + " should be retrieved from the Internet");
                        MyServiceManager.sendCommand(new CommandData(CommandEnum.GET_STATUS, ma
                                .getAccountName(), msgId));
                    } else {
                        if (row.inReplyToMsgId != 0) {
                            findMessage(row.inReplyToMsgId);
                        } else if (!SharedPreferencesUtil.isEmpty(row.inReplyToName)) {
                            MyLog.v(this, "Message " + msgId + " has reply to name ("
                                    + row.inReplyToName
                                    + ") but no reply to message id");
                            // Don't try to retrieve this message again. It
                            // looks like there really are such messages.
                            OneMessage row2 = new OneMessage(0);
                            row2.author = row.inReplyToName;
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
            recreateTheConversationView(rows);
        }
    }

    private void recreateTheConversationView(List<OneMessage> rows) {
        TextView titleText = (TextView) findViewById(R.id.titleText);
        titleText.setText( rows.size() > 1 ? R.string.label_conversation : R.string.message);
        ViewGroup list = (ViewGroup) findViewById(android.R.id.list);
        list.removeAllViews();
        for (int ind = 0; ind < rows.size(); ind++) {
            list.addView(rowToView(rows.get(ind)));
        }
        synchronized(messagesLock) {
            messages = rows;
        }
    }

    /**
     * Formats message as a View (LinearLayout) suitable for a conversation
     * list
     * 
     * @param message row to add to the conversation list
     */
    private LinearLayout rowToView(OneMessage row) {
        LayoutInflater inflater = LayoutInflater.from(ConversationActivity.this);
        LinearLayout messageView = (LinearLayout) inflater.inflate(R.layout.message_basic,
                null);
        messageView.setOnCreateContextMenuListener(contextMenu);

        TextView id = (TextView) messageView.findViewById(R.id.id);
        id.setText(Long.toString(row.id));
        TextView linkedUserId = (TextView) messageView.findViewById(R.id.linked_user_id);
        linkedUserId.setText(Long.toString(row.linkedUserId));

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
        if (!SharedPreferencesUtil.isEmpty(row.via)) {
            messageDetails += " " + String.format(
                    Locale.getDefault(),
                    getText(R.string.message_source_from).toString(),
                    row.via);
        }
        if (row.inReplyToMsgId !=0) {
            String inReplyToName = row.inReplyToName;
            if (SharedPreferencesUtil.isEmpty(inReplyToName)) {
                inReplyToName = "...";
            }
            messageDetails += " "
                    + String.format(Locale.getDefault(),
                            getText(R.string.message_source_in_reply_to).toString(),
                            row.inReplyToName);
        }
        if (!SharedPreferencesUtil.isEmpty(row.rebloggersString)) {
            if (!row.rebloggersString.equals(row.author)) {
                if (!SharedPreferencesUtil.isEmpty(row.inReplyToName)) {
                    messageDetails += ";";
                }
                messageDetails += " "
                        + String.format(Locale.getDefault(), getText(ma.alternativeTermForResourceId(R.string.reblogged_by))
                                .toString(), row.rebloggersString);
            }
        }
        if (!SharedPreferencesUtil.isEmpty(row.recipientName)) {
            messageDetails += " "
                    + String.format(Locale.getDefault(), getText(R.string.message_source_to)
                            .toString(), row.recipientName);
        }
        details.setText(messageDetails);
        ImageView favorited = (ImageView) messageView.findViewById(R.id.message_favorited);
        favorited.setImageResource(row.favorited ? android.R.drawable.star_on : android.R.drawable.star_off);
        return messageView;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (ActivityRequestCode.fromId(requestCode)) {
            case SELECT_ACCOUNT_TO_ACT_AS:
                if (resultCode == RESULT_OK) {
                    MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(data.getStringExtra(IntentExtra.EXTRA_ACCOUNT_NAME.key));
                    if (ma != null) {
                        contextMenu.setAccountUserIdToActAs(ma.getUserId());
                        contextMenu.showContextMenu();
                    }
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        myServiceReceiver.registerReceiver(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        myServiceReceiver.unregisterReceiver(this);
    }

    @Override
    public void onReceive(CommandData commandData) {
        switch(commandData.command) {
            case GET_STATUS:
                if (!commandData.commandResult.hasError()) {
                    showConversation();
                }
                break;
            default:
                break;
        }
        
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        contextMenu.onContextItemSelected(item);
        return super.onContextItemSelected(item);
    }

    @Override
    public Activity getActivity() {
        return this;
    }

    @Override
    public MessageEditor getMessageEditor() {
        return messageEditor;
    }

    @Override
    public long getLinkedUserIdFromCursor(int position) {
        synchronized(messagesLock) {
            if (position < 0 || position >= messages.size() ) {
                return 0;
            } else {
                return messages.get(position).linkedUserId;
            }
        }
    }

    @Override
    public long getCurrentMyAccountUserId() {
        return ma.getUserId();
    }

    @Override
    public long getSelectedUserId() {
        return 0;
    }

    @Override
    public TimelineTypeEnum getTimelineType() {
        return TimelineTypeEnum.MESSAGESTOACT;
    }

    @Override
    public boolean isTimelineCombined() {
        return true;
    }
}
