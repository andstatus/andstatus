/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.Html;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.andstatus.app.MyService.CommandEnum;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ConversationViewLoader {
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
    
    private Context context;
    private MyAccount ma;
    private long selectedMessageId;
    private MessageContextMenu contextMenu;
    private ReplyLevelComparator replyLevelComparator = new ReplyLevelComparator();
    
    List<ConversationOneMessage> oMsgs = new ArrayList<ConversationOneMessage>();

    public List<ConversationOneMessage> getMsgs() {
        return oMsgs;
    }

    List<Long> idsOfTheMessagesToFind = new ArrayList<Long>();

    public ConversationViewLoader(Context contextIn, MyAccount maIn, long selectedMessageIdIn, MessageContextMenu contextMenuIn) {
        context = contextIn;
        ma = maIn;
        selectedMessageId = selectedMessageIdIn;
        contextMenu = contextMenuIn;
    }
    
    public void load() {
        idsOfTheMessagesToFind.clear();
        oMsgs.clear();
        findPreviousMessagesRecursively(new ConversationOneMessage(selectedMessageId, 0));
        Collections.sort(oMsgs, replyLevelComparator);
        enumerateMessages();
        Collections.sort(oMsgs);
    }

    private void findPreviousMessagesRecursively(ConversationOneMessage oMsg) {
        long msgId = oMsg.id;
        if (checkAndAddMessageToFind(msgId)) {
            return;
        }
        findRepliesRecursively(oMsg);
        MyLog.v(this, "findPreviousMessages " + msgId);
        Uri uri = MyProvider.getTimelineMsgUri(ma.getUserId(), TimelineTypeEnum.HOME, true, msgId);
        boolean skip = true;
        Cursor cursor = null;
        if (msgId != 0) {
            cursor = context.getContentResolver().query(uri, PROJECTION, null, null, null);
        }
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                /**
                 * IDs of all known senders of this message except for the Author
                 * These "senders" reblogged the message
                 */
                Set<Long> rebloggers = new HashSet<Long>();
                int ind=0;
                do {
                    long senderId = cursor.getLong(cursor.getColumnIndex(Msg.SENDER_ID));
                    long authorId = cursor.getLong(cursor.getColumnIndex(Msg.AUTHOR_ID));
                    long linkedUserId = cursor.getLong(cursor.getColumnIndex(User.LINKED_USER_ID));

                    if (ind == 0) {
                        // This is the same for all retrieved rows
                        oMsg.inReplyToMsgId = cursor.getLong(cursor.getColumnIndex(Msg.IN_REPLY_TO_MSG_ID));
                        oMsg.createdDate = cursor.getLong(cursor.getColumnIndex(Msg.CREATED_DATE));
                        oMsg.author = cursor.getString(cursor.getColumnIndex(User.AUTHOR_NAME));
                        oMsg.body = cursor.getString(cursor.getColumnIndex(Msg.BODY));
                        oMsg.via = Html.fromHtml(cursor.getString(cursor.getColumnIndex(Msg.VIA))).toString().trim();
                        int colIndex = cursor.getColumnIndex(User.IN_REPLY_TO_NAME);
                        if (colIndex > -1) {
                            oMsg.inReplyToName = cursor.getString(colIndex);
                            if (TextUtils.isEmpty(oMsg.inReplyToName)) {
                                oMsg.inReplyToName = "";
                            }
                        }
                        colIndex = cursor.getColumnIndex(User.RECIPIENT_NAME);
                        if (colIndex > -1) {
                            oMsg.recipientName = cursor.getString(colIndex);
                            if (TextUtils.isEmpty(oMsg.recipientName)) {
                                oMsg.recipientName = "";
                            }
                        }
                    }

                    if (senderId != authorId) {
                        rebloggers.add(senderId);
                    }
                    if (linkedUserId != 0) {
                        if (oMsg.linkedUserId == 0) {
                            oMsg.linkedUserId = linkedUserId;
                        }
                        if (cursor.getInt(cursor.getColumnIndex(MsgOfUser.REBLOGGED)) == 1
                                && linkedUserId != authorId) {
                            rebloggers.add(linkedUserId);
                        }
                        if (cursor.getInt(cursor.getColumnIndex(MsgOfUser.FAVORITED)) == 1) {
                            oMsg.favorited = true;
                        }
                    }
                    
                    ind++;
                } while (cursor.moveToNext());

                for (long rebloggerId : rebloggers) {
                    if (!TextUtils.isEmpty(oMsg.rebloggersString)) {
                        oMsg.rebloggersString += ", ";
                    }
                    oMsg.rebloggersString += MyProvider.userIdToName(rebloggerId);
                }
                if (oMsgs.contains(oMsg)) {
                    MyLog.v(this, "Message " + msgId + " is in the list already");
                } else {
                    oMsgs.add(oMsg);
                    skip = false;
                }
            }
            cursor.close();

            if (!skip) {
                if (oMsg.createdDate == 0) {
                    MyLog.v(this, "Message " + msgId + " should be retrieved from the Internet");
                    MyServiceManager.sendCommand(new CommandData(CommandEnum.GET_STATUS, ma
                            .getAccountName(), msgId));
                } else {
                    if (oMsg.inReplyToMsgId != 0) {
                        findPreviousMessagesRecursively(new ConversationOneMessage(oMsg.inReplyToMsgId, oMsg.replyLevel-1));
                    } else if (!SharedPreferencesUtil.isEmpty(oMsg.inReplyToName)) {
                        MyLog.v(this, "Message " + msgId + " has reply to name ("
                                + oMsg.inReplyToName
                                + ") but no reply to message id");
                        // Don't try to retrieve this message again. It
                        // looks like there really are such messages.
                        ConversationOneMessage oMsg2 = new ConversationOneMessage(0, oMsg.replyLevel-1);
                        oMsg2.author = oMsg.inReplyToName;
                        oMsg2.body = "("
                                + context.getText(R.string.id_of_this_message_was_not_specified)
                                + ")";
                        oMsgs.add(oMsg2);
                        skip = true;
                    }
                }
            }
        }
    }
    
    private boolean checkAndAddMessageToFind(long msgId) {
        if (idsOfTheMessagesToFind.contains(msgId)) {
            MyLog.v(this, "findMessages cycled on the msgId=" + msgId);
            return true;
        }
        idsOfTheMessagesToFind.add(msgId);
        return false;
    }

    public void findRepliesRecursively(ConversationOneMessage oMsg) {
        MyLog.v(this, "findReplies " + oMsg.id);
        List<Long> replies = MyProvider.getReplyIds(oMsg.id);
        oMsg.nReplies = replies.size();
        for (long replyId : replies) {
            ConversationOneMessage oMsgReply = new ConversationOneMessage(replyId, oMsg.replyLevel + 1);
            findPreviousMessagesRecursively(oMsgReply);
        }
    }

    private class ReplyLevelComparator implements Comparator<ConversationOneMessage> {
        @Override
        public int compare(ConversationOneMessage lhs, ConversationOneMessage rhs) {
            int compared = rhs.replyLevel - lhs.replyLevel;
            if (compared == 0) {
                if (lhs.createdDate == rhs.createdDate) {
                    if ( lhs.id == rhs.id) {
                        compared = 0;
                    } else {
                        compared = (rhs.id - lhs.id > 0 ? 1 : -1);
                    }
                } else {
                    compared = (rhs.createdDate - lhs.createdDate > 0 ? 1 : -1);
                }
            }
            return compared;
        }
    }

    private class OrderCounters {
        int list = -1;
        int history = 1;
    }
    
    private void enumerateMessages() {
        idsOfTheMessagesToFind.clear();
        for (ConversationOneMessage oMsg : oMsgs) {
            oMsg.listOrder = 0;
            oMsg.historyOrder = 0;
        }
        OrderCounters order = new OrderCounters();
        for (int ind = oMsgs.size()-1; ind >= 0; ind--) {
            ConversationOneMessage oMsg = oMsgs.get(ind);
            if (oMsg.listOrder < 0 ) {
                continue;
            }
            enumerateBranch(oMsg, order, 0);
        }
    }

    private void enumerateBranch(ConversationOneMessage oMsg, OrderCounters order, int intent) {
        if (checkAndAddMessageToFind(oMsg.id)) {
            return;
        }
        oMsg.historyOrder = order.history++;
        oMsg.listOrder = order.list--;
        oMsg.indentLevel = intent;
        if (oMsg.nReplies > 1 || oMsg.nParentReplies > 1 ) {
            intent++;
        }
        for (int ind = oMsgs.size() - 1; ind >= 0; ind--) {
           ConversationOneMessage reply = oMsgs.get(ind);
           if (reply.inReplyToMsgId == oMsg.id) {
               reply.nParentReplies = oMsg.nReplies;
               enumerateBranch(reply, order, intent);
           }
        }
    }
    
    /**
     * This better be done in UI thread...
     */
    public void createViews() {
        for (int ind = 0; ind < oMsgs.size(); ind++) {
            oMsgs.get(ind).view = oneMessageToView(oMsgs.get(ind));
        }
    }

    /**
     * Formats message as a View suitable for a conversation list
     */
    private View oneMessageToView(ConversationOneMessage oMsg) {
        LayoutInflater inflater = LayoutInflater.from(context);
        int layoutResource = R.layout.message_conversation;
        if (!Activity.class.isAssignableFrom(context.getClass())) {
            MyLog.w(this, "Context should be from an Activity");
        }
        View messageView = inflater.inflate(layoutResource, null);
        messageView.setOnCreateContextMenuListener(contextMenu);

        int indent0 = 8;
        int indentPixels = indent0 * (2 * oMsg.indentLevel);

        LinearLayout messageIndented = (LinearLayout) messageView.findViewById(R.id.message_indented);
        if (oMsg.id == selectedMessageId && oMsgs.size() > 1) {
            messageIndented.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.message_current_background));
        }
        messageIndented.setPadding(indentPixels, 2, 6, 2);

        if (MyLog.isLoggable(this, MyLog.VERBOSE) && oMsg.indentLevel > 0) {
            ImageView indentView = new ConversationIndentImageView(context, messageIndented, indentPixels);
            ((ViewGroup) messageIndented.getParent()).addView(indentView);
        }
        
        TextView id = (TextView) messageView.findViewById(R.id.id);
        id.setText(Long.toString(oMsg.id));
        TextView linkedUserId = (TextView) messageView.findViewById(R.id.linked_user_id);
        linkedUserId.setText(Long.toString(oMsg.linkedUserId));

        TextView author = (TextView) messageView.findViewById(R.id.message_author);
        TextView body = (TextView) messageView.findViewById(R.id.message_body);
        TextView details = (TextView) messageView.findViewById(R.id.message_details);

        author.setText(oMsg.author);

        TextView number = (TextView) messageView.findViewById(R.id.message_number);
        number.setText(Integer.toString(oMsg.historyOrder));
        
        body.setLinksClickable(true);
        body.setFocusable(true);
        body.setFocusableInTouchMode(true);
        body.setText(oMsg.body);
        Linkify.addLinks(body, Linkify.ALL);

        // Everything else goes to messageDetails
        String messageDetails = RelativeTime.getDifference(context, oMsg.createdDate);
        if (!SharedPreferencesUtil.isEmpty(oMsg.via)) {
            messageDetails += " " + String.format(
                    Locale.getDefault(),
                    context.getText(R.string.message_source_from).toString(),
                    oMsg.via);
        }
        if (oMsg.inReplyToMsgId !=0) {
            String inReplyToName = oMsg.inReplyToName;
            if (SharedPreferencesUtil.isEmpty(inReplyToName)) {
                inReplyToName = "...";
            }
            messageDetails += " "
                    + String.format(Locale.getDefault(),
                            context.getText(R.string.message_source_in_reply_to).toString(),
                            oMsg.inReplyToName)
                    + " (" + msgIdToHistoryOrder(oMsg.inReplyToMsgId) + ")";
        }
        if (!SharedPreferencesUtil.isEmpty(oMsg.rebloggersString)) {
            if (!oMsg.rebloggersString.equals(oMsg.author)) {
                if (!SharedPreferencesUtil.isEmpty(oMsg.inReplyToName)) {
                    messageDetails += ";";
                }
                messageDetails += " "
                        + String.format(Locale.getDefault(), context.getText(ma.alternativeTermForResourceId(R.string.reblogged_by))
                                .toString(), oMsg.rebloggersString);
            }
        }
        if (!SharedPreferencesUtil.isEmpty(oMsg.recipientName)) {
            messageDetails += " "
                    + String.format(Locale.getDefault(), context.getText(R.string.message_source_to)
                            .toString(), oMsg.recipientName);
        }
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            messageDetails = messageDetails + " (i" + oMsg.indentLevel + ",r" + oMsg.replyLevel + ")";
        }
        details.setText(messageDetails);
        ImageView favorited = (ImageView) messageView.findViewById(R.id.message_favorited);
        favorited.setImageResource(oMsg.favorited ? android.R.drawable.star_on : android.R.drawable.star_off);
        return messageView;
    }

    private int msgIdToHistoryOrder(long msgId) {
        for (ConversationOneMessage oMsg : oMsgs) {
            if (oMsg.id == msgId ) {
                return oMsg.historyOrder;
            }
        }
        return 0;
    }
    
}
