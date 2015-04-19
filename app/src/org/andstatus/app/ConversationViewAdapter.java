/**
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

package org.andstatus.app;

import android.app.Activity;
import android.content.Context;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ImageView.ScaleType;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AvatarDrawable;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.widget.MySimpleCursorAdapter;

import java.util.List;

public class ConversationViewAdapter extends BaseAdapter {
    private MessageContextMenu contextMenu;
    private Context context;
    private MyAccount ma;
    private long selectedMessageId;
    private List<ConversationViewItem> oMsgs;

    public ConversationViewAdapter(MessageContextMenu contextMenu,
            long selectedMessageId,
            List<ConversationViewItem> oMsgs) {
        this.contextMenu = contextMenu;
        this.context = this.contextMenu.getContext();
        this.ma = MyContextHolder.get().persistentAccounts().fromUserId(this.contextMenu.getCurrentMyAccountUserId());
        this.selectedMessageId = selectedMessageId;
        this.oMsgs = oMsgs;
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
        return oneMessageToView(oMsgs.get(position));
    }
    
    private View oneMessageToView(ConversationViewItem oMsg) {
        final String method = "oneMessageToView";
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method
                    + ": msgId=" + oMsg.getMsgId()
                    + (oMsg.mAvatarDrawable != null ? ", avatar="
                            + oMsg.mAvatarDrawable : ""));
        }
        View messageView = findMessageView();
        messageView.setOnCreateContextMenuListener(contextMenu);
        TextView id = (TextView) messageView.findViewById(R.id.id);
        id.setText(Long.toString(oMsg.getMsgId()));
        TextView linkedUserId = (TextView) messageView.findViewById(R.id.linked_user_id);
        linkedUserId.setText(Long.toString(oMsg.mLinkedUserId));

        setIndent(oMsg, messageView);
        setMessageAuthor(oMsg, messageView);
        setMessageNumber(oMsg, messageView);
        setMessageBody(oMsg, messageView);
        setMessageDetails(oMsg, messageView);
        setFavorited(oMsg, messageView);
        return messageView;
    }

    private View findMessageView() {
        LayoutInflater inflater = LayoutInflater.from(context);
        int layoutResource = R.layout.message_conversation;
        if (!Activity.class.isAssignableFrom(context.getClass())) {
            MyLog.w(this, "Context should be from an Activity");
        }
        View messageView = inflater.inflate(layoutResource, null);
        return messageView;
    }
    
    private void setIndent(ConversationViewItem oMsg, View messageView) {
        float displayDensity = context.getResources().getDisplayMetrics().density;
        // See  http://stackoverflow.com/questions/2238883/what-is-the-correct-way-to-specify-dimensions-in-dip-from-java-code
        int indent0 = (int)( 10 * displayDensity);
        int indentPixels = indent0 * oMsg.mIndentLevel;

        LinearLayout messageIndented = (LinearLayout) messageView.findViewById(R.id.message_indented);
        if (oMsg.getMsgId() == selectedMessageId  && oMsgs.size() > 1) {
            MySimpleCursorAdapter.setBackgroundCompat(messageIndented, context.getResources().getDrawable(R.drawable.message_current_background));
        }

        AttachedImageView imageView = (AttachedImageView) messageView.findViewById(R.id.attached_image);
        if (oMsg.mImageDrawable != null) {
            imageView.setImageDrawable(oMsg.mImageDrawable);
            imageView.setVisibility(View.VISIBLE);
        } else {
            imageView.setVisibility(View.GONE);
        }
        
        int viewToTheLeftId = 0;
        if (oMsg.mIndentLevel > 0) {
            View divider = messageView.findViewById(R.id.divider);
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
            layoutParams.leftMargin = indentPixels - 4;
            divider.setLayoutParams(layoutParams);
            
            if (MyLog.isVerboseEnabled()) {
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
            if (oMsg.mIndentLevel > 0) {
                layoutParams.leftMargin = 1;
            }
            if (viewToTheLeftId == 0) {
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
            } else {
                layoutParams.addRule(RelativeLayout.RIGHT_OF, viewToTheLeftId);
            }
            avatarView.setLayoutParams(layoutParams);
            avatarView.setImageDrawable(oMsg.mAvatarDrawable.getDrawable());
            indentPixels += size;
            ((ViewGroup) messageIndented.getParent()).addView(avatarView);
        }
        messageIndented.setPadding(indentPixels + 6, 2, 6, 2);
    }

    private void setMessageAuthor(ConversationViewItem oMsg, View messageView) {
        TextView author = (TextView) messageView.findViewById(R.id.message_author);

        author.setText(oMsg.mAuthor);
    }

    private void setMessageNumber(ConversationViewItem oMsg, View messageView) {
        TextView number = (TextView) messageView.findViewById(R.id.message_number);
        number.setText(Integer.toString(oMsg.mHistoryOrder));
    }

    private void setMessageBody(ConversationViewItem oMsg, View messageView) {
        if (!TextUtils.isEmpty(oMsg.mBody)) {
            TextView body = (TextView) messageView.findViewById(R.id.message_body);
            body.setLinksClickable(true);
            body.setMovementMethod(LinkMovementMethod.getInstance());                
            body.setFocusable(true);
            body.setFocusableInTouchMode(true);
            Spanned spanned = Html.fromHtml(oMsg.mBody);
            body.setText(spanned);
            if (!MyHtml.hasUrlSpans(spanned)) {
                Linkify.addLinks(body, Linkify.ALL);
            }
        }
    }

    private void setMessageDetails(ConversationViewItem oMsg, View messageView) {
        String messageDetails = RelativeTime.getDifference(context, oMsg.mCreatedDate);
        if (!SharedPreferencesUtil.isEmpty(oMsg.mVia)) {
            messageDetails += " " + String.format(
                    context.getText(R.string.message_source_from).toString(),
                    oMsg.mVia);
        }
        if (oMsg.mInReplyToMsgId !=0) {
            String inReplyToName = oMsg.mInReplyToName;
            if (SharedPreferencesUtil.isEmpty(inReplyToName)) {
                inReplyToName = "...";
            }
            messageDetails += " "
                    + String.format(
                            context.getText(R.string.message_source_in_reply_to).toString(),
                            oMsg.mInReplyToName)
                    + " (" + msgIdToHistoryOrder(oMsg.mInReplyToMsgId) + ")";
        }
        if (!SharedPreferencesUtil.isEmpty(oMsg.mRebloggersString)
                && !oMsg.mRebloggersString.equals(oMsg.mAuthor)) {
            if (!SharedPreferencesUtil.isEmpty(oMsg.mInReplyToName)) {
                messageDetails += ";";
            }
            messageDetails += " "
                    + String.format(
                            context.getText(ma.alternativeTermForResourceId(R.string.reblogged_by))
                                    .toString(), oMsg.mRebloggersString);
        }
        if (!SharedPreferencesUtil.isEmpty(oMsg.mRecipientName)) {
            messageDetails += " "
                    + String.format(
                            context.getText(R.string.message_source_to)
                            .toString(), oMsg.mRecipientName);
        }
        if (MyPreferences.getBoolean(MyPreferences.KEY_DEBUGGING_INFO_IN_UI, false)) {
            messageDetails = messageDetails + " (i" + oMsg.mIndentLevel + ",r" + oMsg.mReplyLevel + ")";
        }
        ((TextView) messageView.findViewById(R.id.message_details)).setText(messageDetails);
    }

    private void setFavorited(ConversationViewItem oMsg, View messageView) {
        ImageView favorited = (ImageView) messageView.findViewById(R.id.message_favorited);
        favorited.setImageResource(oMsg.mFavorited ? android.R.drawable.star_on : android.R.drawable.star_off);
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
