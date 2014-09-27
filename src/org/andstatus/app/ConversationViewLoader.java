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
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ImageView.ScaleType;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AttachedImageDrawable;
import org.andstatus.app.data.AvatarDrawable;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyDatabase.Download;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.support.android.v11.widget.MySimpleCursorAdapter;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConversationViewLoader {
    private static final int MAX_INDENT_LEVEL = 19;
    
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

    public ConversationViewLoader(Context context, MyAccount ma, long selectedMessageId, MessageContextMenu contextMenu) {
        this.context = context;
        this.ma = ma;
        this.selectedMessageId = selectedMessageId;
        this.contextMenu = contextMenu;
    }
    
    public void load() {
        idsOfTheMessagesToFind.clear();
        oMsgs.clear();
        findPreviousMessagesRecursively(new ConversationOneMessage(selectedMessageId, 0));
        Collections.sort(oMsgs, replyLevelComparator);
        enumerateMessages();
        if (MyPreferences.getBoolean(
                MyPreferences.KEY_OLD_MESSAGES_FIRST_IN_CONVERSATION, false)) {
            reverseListOrder();
        }
        Collections.sort(oMsgs);
    }

    private void findPreviousMessagesRecursively(ConversationOneMessage oMsg) {
        if (!addMessageIdToFind(oMsg.msgId)) {
            return;
        }
        findRepliesRecursively(oMsg);
        MyLog.v(this, "findPreviousMessages id=" + oMsg.msgId);
        loadMessageFromDatabase(oMsg);
        if (oMsg.isLoaded()) {
            if (addMessageToList(oMsg)) {
                if (oMsg.inReplyToMsgId != 0) {
                    findPreviousMessagesRecursively(new ConversationOneMessage(oMsg.inReplyToMsgId,
                            oMsg.replyLevel - 1));
                } else {
                    checkInReplyToNameOf(oMsg);                    
                }
            }
        } else {
            retrieveFromInternet(oMsg.msgId);
        }
    }
    
    /** Returns true if message was added 
     *          false in a case the message existed already 
     * */
    private boolean addMessageIdToFind(long msgId) {
        if (msgId == 0) {
            return false;
        } else if (idsOfTheMessagesToFind.contains(msgId)) {
            MyLog.v(this, "findMessages cycled on the id=" + msgId);
            return false;
        }
        idsOfTheMessagesToFind.add(msgId);
        return true;
    }

    public void findRepliesRecursively(ConversationOneMessage oMsg) {
        MyLog.v(this, "findReplies for id=" + oMsg.msgId);
        List<Long> replies = MyProvider.getReplyIds(oMsg.msgId);
        oMsg.nReplies = replies.size();
        for (long replyId : replies) {
            ConversationOneMessage oMsgReply = new ConversationOneMessage(replyId, oMsg.replyLevel + 1);
            findPreviousMessagesRecursively(oMsgReply);
        }
    }
    
    private void loadMessageFromDatabase(ConversationOneMessage oMsg) {
        Uri uri = MyProvider.getTimelineMsgUri(ma.getUserId(), TimelineTypeEnum.ALL, true, oMsg.msgId);
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, TimelineSql.getConversationProjection(), null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                loadMessageFromCursor(oMsg, cursor);
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
    }

    private void loadMessageFromCursor(ConversationOneMessage oMsg, Cursor cursor) {
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
                oMsg.author = TimelineSql.userColumnNameToNameAtTimeline(cursor, User.AUTHOR_NAME, false);
                oMsg.body = cursor.getString(cursor.getColumnIndex(Msg.BODY));
                String via = cursor.getString(cursor.getColumnIndex(Msg.VIA));
                if (!TextUtils.isEmpty(via)) {
                    oMsg.via = Html.fromHtml(via).toString().trim();
                }
                if (MyPreferences.showAvatars()) {
                    oMsg.avatarDrawable = new AvatarDrawable(authorId, cursor.getString(cursor.getColumnIndex(Download.AVATAR_FILE_NAME)));
                }
                if (MyPreferences.showAttachedImages()) {
                    oMsg.imageDrawable = AttachedImageDrawable.drawableFromCursor(cursor);
                }
                oMsg.inReplyToName = TimelineSql.userColumnNameToNameAtTimeline(cursor, User.IN_REPLY_TO_NAME, false);
                oMsg.recipientName = TimelineSql.userColumnNameToNameAtTimeline(cursor, User.RECIPIENT_NAME, false);
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
    }

    private boolean addMessageToList(ConversationOneMessage oMsg) {
        boolean added = false;
        if (oMsgs.contains(oMsg)) {
            MyLog.v(this, "Message id=" + oMsg.msgId + " is in the list already");
        } else {
            oMsgs.add(oMsg);
            added = true;
        }
        return added;
    }
    
    private void checkInReplyToNameOf(ConversationOneMessage oMsg) {
        if (!SharedPreferencesUtil.isEmpty(oMsg.inReplyToName)) {
            MyLog.v(this, "Message id=" + oMsg.msgId + " has reply to name ("
                    + oMsg.inReplyToName
                    + ") but no reply to message id");
            // Don't try to retrieve this message again. 
            // It looks like such messages really exist.
            ConversationOneMessage oMsg2 = new ConversationOneMessage(0, oMsg.replyLevel-1);
            // This allows to place the message on the timeline correctly
            oMsg2.createdDate = oMsg.createdDate - 60000;
            oMsg2.author = oMsg.inReplyToName;
            oMsg2.body = "("
                    + context.getText(R.string.id_of_this_message_was_not_specified)
                    + ")";
            addMessageToList(oMsg2);
        }
    }

    private void retrieveFromInternet(long msgId) {
        MyLog.v(this, "Message id=" + msgId + " should be retrieved from the Internet");
        MyServiceManager.sendForegroundCommand(new CommandData(CommandEnum.GET_STATUS, ma
                .getAccountName(), msgId));
    }

    private static class ReplyLevelComparator implements Comparator<ConversationOneMessage>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public int compare(ConversationOneMessage lhs, ConversationOneMessage rhs) {
            int compared = rhs.replyLevel - lhs.replyLevel;
            if (compared == 0) {
                if (lhs.createdDate == rhs.createdDate) {
                    if ( lhs.msgId == rhs.msgId) {
                        compared = 0;
                    } else {
                        compared = (rhs.msgId - lhs.msgId > 0 ? 1 : -1);
                    }
                } else {
                    compared = (rhs.createdDate - lhs.createdDate > 0 ? 1 : -1);
                }
            }
            return compared;
        }
    }

    private static class OrderCounters {
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

    private void enumerateBranch(ConversationOneMessage oMsg, OrderCounters order, int indent) {
        if (!addMessageIdToFind(oMsg.msgId)) {
            return;
        }
        int indentNext = indent;
        oMsg.historyOrder = order.history++;
        oMsg.listOrder = order.list--;
        oMsg.indentLevel = indent;
        if ((oMsg.nReplies > 1 || oMsg.nParentReplies > 1)
                && indentNext < MAX_INDENT_LEVEL) {
            indentNext++;
        }
        for (int ind = oMsgs.size() - 1; ind >= 0; ind--) {
           ConversationOneMessage reply = oMsgs.get(ind);
           if (reply.inReplyToMsgId == oMsg.msgId) {
               reply.nParentReplies = oMsg.nReplies;
               enumerateBranch(reply, order, indentNext);
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
        final String method = "oneMessageToView";
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, method
                    + ": msgId=" + oMsg.msgId
                    + (oMsg.avatarDrawable != null ? ", avatar="
                            + oMsg.avatarDrawable : ""));
        }
        LayoutInflater inflater = LayoutInflater.from(context);
        int layoutResource = R.layout.message_conversation;
        if (!Activity.class.isAssignableFrom(context.getClass())) {
            MyLog.w(this, "Context should be from an Activity");
        }
        View messageView = inflater.inflate(layoutResource, null);
        messageView.setOnCreateContextMenuListener(contextMenu);

        float displayDensity = context.getResources().getDisplayMetrics().density;
        // See  http://stackoverflow.com/questions/2238883/what-is-the-correct-way-to-specify-dimensions-in-dip-from-java-code
        int indent0 = (int)( 10 * displayDensity);
        int indentPixels = indent0 * oMsg.indentLevel;

        LinearLayout messageIndented = (LinearLayout) messageView.findViewById(R.id.message_indented);
        if (oMsg.msgId == selectedMessageId && oMsgs.size() > 1) {
            MySimpleCursorAdapter.setBackgroundCompat(messageIndented, context.getResources().getDrawable(R.drawable.message_current_background));
        }

        AttachedImageView imageView = (AttachedImageView) messageView.findViewById(R.id.attached_image);
        if (oMsg.imageDrawable != null) {
            imageView.setImageDrawable(oMsg.imageDrawable);
            imageView.setVisibility(View.VISIBLE);
        } else {
            imageView.setVisibility(View.GONE);
        }
        
        int viewToTheLeftId = 0;
        if (oMsg.indentLevel > 0) {
            View divider = messageView.findViewById(R.id.divider);
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
            layoutParams.leftMargin = indentPixels - 4;
            divider.setLayoutParams(layoutParams);
            
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this,"density=" + displayDensity);
            }
            viewToTheLeftId = 2;
            ImageView indentView = new ConversationIndentImageView(context, messageIndented, indentPixels);
            indentView.setId(viewToTheLeftId);
            ((ViewGroup) messageIndented.getParent()).addView(indentView);
        }

        if (MyPreferences.showAvatars()) {
            ImageView avatarView = new ImageView(context);
            int size = Math.round(AvatarDrawable.AVATAR_SIZE_DIP * displayDensity);
            avatarView.setScaleType(ScaleType.FIT_CENTER);
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(size, size);
            layoutParams.topMargin = 3;
            if (oMsg.indentLevel > 0) {
                layoutParams.leftMargin = 1;
            }
            if (viewToTheLeftId == 0) {
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
            } else {
                layoutParams.addRule(RelativeLayout.RIGHT_OF, viewToTheLeftId);
            }
            avatarView.setLayoutParams(layoutParams);
            avatarView.setImageDrawable(oMsg.avatarDrawable.getDrawable());
            indentPixels += size;
            ((ViewGroup) messageIndented.getParent()).addView(avatarView);
        }
        messageIndented.setPadding(indentPixels + 6, 2, 6, 2);
        
        TextView id = (TextView) messageView.findViewById(R.id.id);
        id.setText(Long.toString(oMsg.msgId));
        TextView linkedUserId = (TextView) messageView.findViewById(R.id.linked_user_id);
        linkedUserId.setText(Long.toString(oMsg.linkedUserId));

        TextView author = (TextView) messageView.findViewById(R.id.message_author);
        TextView body = (TextView) messageView.findViewById(R.id.message_body);
        TextView details = (TextView) messageView.findViewById(R.id.message_details);

        author.setText(oMsg.author);

        TextView number = (TextView) messageView.findViewById(R.id.message_number);
        number.setText(Integer.toString(oMsg.historyOrder));
        
        if (!TextUtils.isEmpty(oMsg.body)) {
            body.setLinksClickable(true);
            body.setMovementMethod(LinkMovementMethod.getInstance());                
            body.setFocusable(true);
            body.setFocusableInTouchMode(true);
            Spanned spanned = Html.fromHtml(oMsg.body);
            body.setText(spanned);
            if (!MyHtml.hasUrlSpans(spanned)) {
                Linkify.addLinks(body, Linkify.ALL);
            }
        }

        // Everything else goes to messageDetails
        String messageDetails = RelativeTime.getDifference(context, oMsg.createdDate);
        if (!SharedPreferencesUtil.isEmpty(oMsg.via)) {
            messageDetails += " " + String.format(
                    MyContextHolder.get().getLocale(),
                    context.getText(R.string.message_source_from).toString(),
                    oMsg.via);
        }
        if (oMsg.inReplyToMsgId !=0) {
            String inReplyToName = oMsg.inReplyToName;
            if (SharedPreferencesUtil.isEmpty(inReplyToName)) {
                inReplyToName = "...";
            }
            messageDetails += " "
                    + String.format(MyContextHolder.get().getLocale(),
                            context.getText(R.string.message_source_in_reply_to).toString(),
                            oMsg.inReplyToName)
                    + " (" + msgIdToHistoryOrder(oMsg.inReplyToMsgId) + ")";
        }
        if (!SharedPreferencesUtil.isEmpty(oMsg.rebloggersString)
                && !oMsg.rebloggersString.equals(oMsg.author)) {
            if (!SharedPreferencesUtil.isEmpty(oMsg.inReplyToName)) {
                messageDetails += ";";
            }
            messageDetails += " "
                    + String.format(MyContextHolder.get().getLocale(),
                            context.getText(ma.alternativeTermForResourceId(R.string.reblogged_by))
                                    .toString(), oMsg.rebloggersString);
        }
        if (!SharedPreferencesUtil.isEmpty(oMsg.recipientName)) {
            messageDetails += " "
                    + String.format(MyContextHolder.get().getLocale(), context.getText(R.string.message_source_to)
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
            if (oMsg.msgId == msgId ) {
                return oMsg.historyOrder;
            }
        }
        return 0;
    }

    private void reverseListOrder() {
        for (ConversationOneMessage oMsg : oMsgs) {
            oMsg.listOrder = oMsgs.size() - oMsg.listOrder - 1; 
        }
    }
    
}
