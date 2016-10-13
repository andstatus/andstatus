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

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.andstatus.app.R;
import org.andstatus.app.graphics.MyImageCache;
import org.andstatus.app.util.MyUrlSpan;

import java.util.Collections;
import java.util.List;

public class ConversationViewAdapter extends MessageListAdapter {
    private final Context context;
    private final long selectedMessageId;
    private final List<ConversationViewItem> oMsgs;
    private final boolean showThreads;

    public ConversationViewAdapter(MessageContextMenu contextMenu,
                                   long selectedMessageId,
                                   List<ConversationViewItem> oMsgs,
                                   boolean showThreads,
                                   boolean oldMessagesFirst) {
        super(contextMenu);
        this.context = this.contextMenu.getActivity();
        this.selectedMessageId = selectedMessageId;
        this.oMsgs = oMsgs;
        this.showThreads = showThreads;
        for (ConversationItem oMsg : oMsgs) {
            oMsg.setReversedListOrder(oldMessagesFirst);
            setInReplyToViewItem(oMsg);
        }
        Collections.sort(this.oMsgs);
    }

    private void setInReplyToViewItem(ConversationItem viewItem) {
        if (viewItem.inReplyToMsgId == 0) {
            return;
        }
        for (ConversationViewItem oMsg : oMsgs) {
            if (oMsg.getMsgId() == viewItem.inReplyToMsgId ) {
                viewItem.inReplyToViewItem = oMsg;
                break;
            }
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
        View view = convertView == null ? newView() : convertView;
        view.setOnCreateContextMenuListener(contextMenu);
        view.setOnClickListener(this);
        setPosition(view, position);
        ConversationViewItem item = oMsgs.get(position);
        showRebloggers(item, view);
        MyUrlSpan.showText(view, R.id.message_author, item.authorName, false, false);
        showMessageBody(item, view);
        MyUrlSpan.showText(view, R.id.message_details, item.getDetails(contextMenu.getActivity()).toString(), false, false);
        showIndent(item, view);
        if (showAttachedImages) {
            showAttachedImage(item, view);
        }
        if (markReplies) {
            showMarkReplies(item, view);
        }
        if (showButtonsBelowMessages) {
            showButtonsBelowMessage(item, view);
        } else {
            showFavorited(item, view);
        }
        showMessageNumber(item, view);
        return view;
    }

    private void showIndent(ConversationViewItem item, View messageView) {
        final int indentLevel = showThreads ? item.indentLevel : 0;
        int indentPixels = dpToPixes(10) * indentLevel;

        LinearLayout messageIndented = (LinearLayout) messageView.findViewById(R.id.message_indented);
        if (item.getMsgId() == selectedMessageId  && oMsgs.size() > 1) {
            messageIndented.setBackground(MyImageCache.getStyledDrawable(
                    R.drawable.current_message_background_light,
                    R.drawable.current_message_background));
        } else {
            messageIndented.setBackgroundResource(0);
        }

        showIndentView(messageIndented, indentPixels);

        int viewToTheLeftId = indentLevel == 0 ? 0 : R.id.indent_image;
        showDivider(messageView, viewToTheLeftId);

        if (showAvatars) {
            indentPixels = showAvatar(item, messageIndented, viewToTheLeftId, indentPixels);
        }
        messageIndented.setPadding(indentPixels + 6, 2, 6, 2);
    }

    private void showIndentView(LinearLayout messageIndented, int indentPixels) {
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

    private void showDivider(View messageView, int viewToTheLeftId) {
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        setRightOf(layoutParams, viewToTheLeftId);
        View divider = messageView.findViewById(R.id.divider);
        divider.setLayoutParams(layoutParams);
    }

    private int showAvatar(ConversationViewItem oMsg, LinearLayout messageIndented, int viewToTheLeftId, int indentPixels) {
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
        if (oMsg.indentLevel > 0) {
            layoutParams.leftMargin = 1;
        }
        setRightOf(layoutParams, viewToTheLeftId);
        avatarView.setLayoutParams(layoutParams);
        if (oMsg.avatarDrawable != null) {
            avatarView.setImageDrawable(oMsg.avatarDrawable);
        }
        indentPixels += size;
        if (newView) {
            parentView.addView(avatarView);
        }
        return indentPixels;
    }

    private void setRightOf(RelativeLayout.LayoutParams layoutParams, int viewToTheLeftId) {
        if (viewToTheLeftId == 0) {
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        } else {
            layoutParams.addRule(RelativeLayout.RIGHT_OF, viewToTheLeftId);
        }
    }

    private void showMessageNumber(ConversationViewItem item, View messageView) {
        TextView number = (TextView) messageView.findViewById(R.id.message_number);
        number.setText(Integer.toString(item.historyOrder));
    }
}
