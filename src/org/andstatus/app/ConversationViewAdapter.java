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
import org.andstatus.app.support.android.v11.widget.MySimpleCursorAdapter;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.List;

public class ConversationViewAdapter extends BaseAdapter {
    private MessageContextMenu contextMenu;
    private Context context;
    private MyAccount ma;
    private long selectedMessageId;
    private List<ConversationOneMessage> oMsgs;

    public ConversationViewAdapter(MessageContextMenu contextMenu,
            long selectedMessageId,
            List<ConversationOneMessage> oMsgs) {
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
        return oMsgs.get(position).msgId;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return oneMessageToView(oMsgs.get(position), parent);
    }
    
    private View oneMessageToView(ConversationOneMessage oMsg, ViewGroup parent) {
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
        if (oMsg.msgId == selectedMessageId  && oMsgs.size() > 1) {
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
}
