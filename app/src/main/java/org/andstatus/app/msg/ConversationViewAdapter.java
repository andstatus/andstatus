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
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.andstatus.app.R;
import org.andstatus.app.graphics.MyImageCache;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.ViewUtils;

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
        ViewGroup view = getEmptyView(convertView);
        view.setOnCreateContextMenuListener(contextMenu);
        view.setOnClickListener(this);
        setPosition(view, position);
        ConversationViewItem item = oMsgs.get(position);
        showRebloggers(item, view);
        MyUrlSpan.showText(view, R.id.message_author, item.authorName, false, false);
        showMessageBody(item, view);
        MyUrlSpan.showText(view, R.id.message_details, item.getDetails(contextMenu.getActivity()).toString(), false, false);

        int indentPixels = getIndentPixels(item);
        showIndentImage(view, indentPixels);
        showDivider(view, indentPixels == 0 ? 0 : R.id.indent_image);
        if (showAvatars) {
            indentPixels = showAvatar(item, view, indentPixels);
        }
        indentMessage(view, indentPixels);
        showCentralItem(item, view);
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

    public int getIndentPixels(ConversationViewItem item) {
        final int indentLevel = showThreads ? item.indentLevel : 0;
        return dpToPixes(10) * indentLevel;
    }

    private void showCentralItem(ConversationViewItem item, View view) {
        if (item.getMsgId() == selectedMessageId  && oMsgs.size() > 1) {
            view.findViewById(R.id.message_indented).setBackground(
                    MyImageCache.getStyledDrawable(
                            R.drawable.current_message_background_light,
                            R.drawable.current_message_background));
        }
    }

    private void showIndentImage(View view, int indentPixels) {
        View referencedView = view.findViewById(R.id.message_indented);
        ViewGroup parentView = ((ViewGroup) referencedView.getParent());
        ImageView oldView = (ImageView) parentView.findViewById(R.id.indent_image);
        if (oldView != null) {
            parentView.removeView(oldView);
        }
        if (indentPixels > 0) {
            ImageView indentView = new ConversationIndentImageView(context, referencedView, indentPixels,
                    R.drawable.conversation_indent3, R.drawable.conversation_indent3);
            indentView.setId(R.id.indent_image);
            parentView.addView(indentView, 0);
        }
    }

    private void showDivider(View view, int viewToTheLeftId) {
        View divider = view.findViewById(R.id.divider);
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) divider.getLayoutParams();
        setRightOf(layoutParams, viewToTheLeftId);
        divider.setLayoutParams(layoutParams);
    }

    private int showAvatar(MessageViewItem item, View view, int indentPixels) {
        ImageView avatarView = (ImageView) view.findViewById(R.id.avatar_image);
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) avatarView.getLayoutParams();
        layoutParams.leftMargin = dpToPixes(indentPixels == 0 ? 2 : 1) + indentPixels;
        avatarView.setLayoutParams(layoutParams);
        avatarView.setImageDrawable(item.getAvatar());
        return ViewUtils.getWidthWithMargins(avatarView);
    }

    private void setRightOf(RelativeLayout.LayoutParams layoutParams, int viewToTheLeftId) {
        if (viewToTheLeftId == 0) {
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
        } else {
            layoutParams.addRule(RelativeLayout.RIGHT_OF, viewToTheLeftId);
        }
    }

    private void indentMessage(View view, int indentPixels) {
        View messageIndented = view.findViewById(R.id.message_indented);
        messageIndented.setPadding(indentPixels + 6, messageIndented.getPaddingTop(), messageIndented.getPaddingRight(),
                messageIndented.getPaddingBottom());
    }

    private void showMessageNumber(ConversationViewItem item, View view) {
        TextView number = (TextView) view.findViewById(R.id.message_number);
        number.setText(Integer.toString(item.historyOrder));
    }
}
