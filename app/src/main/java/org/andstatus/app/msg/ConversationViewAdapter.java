/**
 * Copyright (C) 2014-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.msg;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.graphics.MyImageCache;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.widget.MyBaseAdapter;

import java.util.Collections;
import java.util.List;

public class ConversationViewAdapter extends MyBaseAdapter {
    private final MessageContextMenu contextMenu;
    private final Context context;
    private final MyAccount ma;
    private final long selectedMessageId;
    private final List<ConversationViewItem> oMsgs;
    private final boolean showThreads;

    public ConversationViewAdapter(MessageContextMenu contextMenu,
                                   long selectedMessageId,
                                   List<ConversationViewItem> oMsgs,
                                   boolean showThreads,
                                   boolean oldMessagesFirst) {
        super(contextMenu.getActivity().getMyContext());
        this.contextMenu = contextMenu;
        this.context = this.contextMenu.getActivity();
        this.ma = myContext.persistentAccounts().fromUserId(this.contextMenu.getCurrentMyAccountUserId());
        this.selectedMessageId = selectedMessageId;
        this.oMsgs = oMsgs;
        this.showThreads = showThreads;
        setReversedListOrder(oldMessagesFirst);
        Collections.sort(this.oMsgs);
    }

    private void setReversedListOrder(boolean oldMessagesFirst) {
        for (ConversationItem oMsg : oMsgs) {
            oMsg.setReversedListOrder(oldMessagesFirst);
        }
    }

    @Override
    public int getCount() {
        return oMsgs.size();
    }

    @Override
    public Object getItem(int position) {
        return oMsgs.get(position);
    }

    @Override
    public long getItemId(int position) {
        return oMsgs.get(position).getMsgId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final String method = "getView";
        ConversationViewItem item = oMsgs.get(position);
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method + ": msgId:" + item.getMsgId() + ", author:" + item.mAuthor);
        }
        View view = convertView == null ? newView() : convertView;
        view.setOnCreateContextMenuListener(contextMenu);
        view.setOnClickListener(this);
        setPosition(view, position);

        showIndent(item, view);
        showMessageAuthor(item, view);
        showMessageNumber(item, view);
        showMessageBody(item, view);
        showMessageDetails(item, view);
        showFavorited(item, view);
        return view;
    }

    private View newView() {
        LayoutInflater inflater = LayoutInflater.from(context);
        int layoutResource = R.layout.message_conversation;
        if (!Activity.class.isAssignableFrom(context.getClass())) {
            MyLog.w(this, "Context should be from an Activity");
        }
        return inflater.inflate(layoutResource, null);
    }
    
    private void showIndent(ConversationViewItem item, View messageView) {
        final int indentLevel = showThreads ? item.mIndentLevel : 0;
        int indentPixels = dpToPixes(10) * indentLevel;

        LinearLayout messageIndented = (LinearLayout) messageView.findViewById(R.id.message_indented);
        if (item.getMsgId() == selectedMessageId  && oMsgs.size() > 1) {
            messageIndented.setBackground(MyImageCache.getStyledDrawable(
                    R.drawable.current_message_background_light,
                    R.drawable.current_message_background));
        } else {
            messageIndented.setBackgroundResource(0);
        }

        item.mImageFile.showAttachedImage(contextMenu.messageList,
                (ImageView) messageView.findViewById(R.id.attached_image));

        int viewToTheLeftId = 0;
        if (indentLevel > 0) {
            viewToTheLeftId = R.id.indent_image;
        }
        View divider = messageView.findViewById(R.id.divider);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        layoutParams.leftMargin = indentPixels > 3 ? indentPixels - 4 : 0;
        divider.setLayoutParams(layoutParams);

        setIndentView(messageIndented, indentPixels);

        if (MyPreferences.getShowAvatars()) {
            indentPixels = setAvatar(item, messageIndented, viewToTheLeftId, indentPixels);
        }
        messageIndented.setPadding(indentPixels + 6, 2, 6, 2);
    }

    private void setIndentView(LinearLayout messageIndented, int indentPixels) {
        ViewGroup parentView = ((ViewGroup) messageIndented.getParent());
        ImageView oldView = (ImageView) parentView.findViewById(R.id.indent_image);
        if (oldView != null) {
            parentView.removeView(oldView);
        }
        if (indentPixels > 0) {
            ImageView indentView = new ConversationIndentImageView(context, messageIndented, indentPixels);
            indentView.setId(R.id.indent_image);
            parentView.addView(indentView, 0);
        }
    }

    private int setAvatar(ConversationViewItem oMsg, LinearLayout messageIndented, int viewToTheLeftId, int indentPixels) {
        ViewGroup parentView = ((ViewGroup) messageIndented.getParent());
        ImageView avatarView = (ImageView) parentView.findViewById(R.id.avatar_image);
        boolean newView = avatarView == null;
        if (newView) {
            avatarView = new ImageView(context);
            avatarView.setId(R.id.avatar_image);
        }
        int size = MyImageCache.getAvatarWidthPixels();
        avatarView.setScaleType(ScaleType.FIT_CENTER);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(size, size);
        layoutParams.topMargin = 3;
        if (oMsg.mIndentLevel > 0) {
            layoutParams.leftMargin = 1;
        }
        if (viewToTheLeftId == 0) {
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        } else {
            layoutParams.addRule(RelativeLayout.RIGHT_OF, viewToTheLeftId);
        }
        avatarView.setLayoutParams(layoutParams);
        if (oMsg.mAvatarDrawable != null) {
            avatarView.setImageDrawable(oMsg.mAvatarDrawable);
        }
        indentPixels += size;
        if (newView) {
            parentView.addView(avatarView);
        }
        return indentPixels;
    }

    private void showMessageAuthor(ConversationViewItem item, View messageView) {
        TextView author = (TextView) messageView.findViewById(R.id.message_author);
        author.setText(item.mAuthor);
    }

    private void showMessageNumber(ConversationViewItem item, View messageView) {
        TextView number = (TextView) messageView.findViewById(R.id.message_number);
        number.setText(Integer.toString(item.mHistoryOrder));
    }

    private void showMessageBody(ConversationViewItem item, View messageView) {
        TextView body = (TextView) messageView.findViewById(R.id.message_body);
        MyUrlSpan.showText(body, item.body, true, true);
    }

    private void showMessageDetails(ConversationViewItem item, View messageView) {
        String messageDetails = RelativeTime.getDifference(context, item.createdDate);
        if (!SharedPreferencesUtil.isEmpty(item.messageSource)) {
            messageDetails += " " + String.format(
                    context.getText(R.string.message_source_from).toString(),
                    item.messageSource);
        }
        String inReplyToName = "";
        if (!TextUtils.isEmpty(item.mInReplyToName)) {
            inReplyToName = item.mInReplyToName;
            if (SharedPreferencesUtil.isEmpty(inReplyToName)) {
                inReplyToName = "...";
            }
            messageDetails += " "
                    + String.format(
                            context.getText(R.string.message_source_in_reply_to).toString(),
                            inReplyToName)
                    + (item.mInReplyToMsgId != 0 ? " (" + msgIdToHistoryOrder(item.mInReplyToMsgId) + ")" : "");
        }

        String rebloggersString = "";
        for (String reblogger : item.rebloggers.values()) {
            if (!TextUtils.isEmpty(rebloggersString)) {
                rebloggersString += ", ";
            }
            rebloggersString += reblogger;
        }
        if (!SharedPreferencesUtil.isEmpty(rebloggersString)
                && !rebloggersString.equals(item.mAuthor)) {
            if (!TextUtils.isEmpty(inReplyToName)) {
                messageDetails += ";";
            }
            messageDetails += " "
                    + String.format(
                            context.getText(ma.alternativeTermForResourceId(R.string.reblogged_by))
                                    .toString(), rebloggersString);
        }

        if (!SharedPreferencesUtil.isEmpty(item.mRecipientName)) {
            messageDetails += " "
                    + String.format(
                            context.getText(R.string.message_source_to)
                            .toString(), item.mRecipientName);
        }
        if (item.mStatus != DownloadStatus.LOADED) {
            messageDetails += " (" + item.mStatus.getTitle(context) + ")";
        }
        if (MyPreferences.getShowDebuggingInfoInUi()) {
            messageDetails = messageDetails + " (i" + item.mIndentLevel + ",r" + item.mReplyLevel + ")";
        }
        ((TextView) messageView.findViewById(R.id.message_details)).setText(messageDetails);
    }

    private void showFavorited(ConversationViewItem item, View messageView) {
        ImageView favorited = (ImageView) messageView.findViewById(R.id.message_favorited);
        favorited.setVisibility(item.favorited ? View.VISIBLE : View.GONE );
    }
    
    private int msgIdToHistoryOrder(long msgId) {
        for (ConversationViewItem oMsg : oMsgs) {
            if (oMsg.getMsgId() == msgId ) {
                return oMsg.mHistoryOrder;
            }
        }
        return 0;
    }
}
