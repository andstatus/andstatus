/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.view.View;
import android.widget.ImageView;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.widget.MyBaseAdapter;

/**
 * @author yvolk@yurivolkov.com
 */
public abstract class MessageListAdapter extends MyBaseAdapter {
    protected final boolean showButtonsBelowMessages =
            SharedPreferencesUtil.getBoolean(MyPreferences.KEY_SHOW_BUTTONS_BELOW_MESSAGE, true);
    protected final MessageContextMenu contextMenu;

    public MessageListAdapter(MessageContextMenu contextMenu) {
        super(contextMenu.getMyContext());
        this.contextMenu = contextMenu;
    }

    protected void showRebloggers(MessageViewItem item, View view) {
        View viewGroup = view.findViewById(R.id.reblogged);
        if (viewGroup == null) {
            return;
        } else if (item.isReblogged()) {
            viewGroup.setVisibility(View.VISIBLE);
            String rebloggers = "";
            for (String name : item.rebloggers.values()) {
                if (!rebloggers.isEmpty()) {
                    rebloggers += ", ";
                }
                rebloggers += name;
            }
            MyUrlSpan.showText(viewGroup, R.id.rebloggers, rebloggers, false, false);
        } else {
            viewGroup.setVisibility(View.GONE);
        }
    }

    protected void setupButtons(View view) {
        if (showButtonsBelowMessages) {
            View buttons = view.findViewById(R.id.message_buttons);
            if (buttons != null) {
                buttons.setVisibility(View.VISIBLE);
                setOnButtonClick(buttons, R.id.reply_button, MessageListContextMenuItem.REPLY);
                setOnButtonClick(buttons, R.id.reblog_button, MessageListContextMenuItem.REBLOG);
                setOnButtonClick(buttons, R.id.reblog_button_tinted, MessageListContextMenuItem.DESTROY_REBLOG);
                setOnButtonClick(buttons, R.id.favorite_button, MessageListContextMenuItem.FAVORITE);
                setOnButtonClick(buttons, R.id.favorite_button_tinted, MessageListContextMenuItem.DESTROY_FAVORITE);
                setOnButtonClick(buttons, R.id.more_button, MessageListContextMenuItem.UNKNOWN);
            }
        }
    }

    private void setOnButtonClick(final View viewGroup, int buttonId, final MessageListContextMenuItem menuItem) {
        viewGroup.findViewById(buttonId).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (menuItem.equals(MessageListContextMenuItem.UNKNOWN)) {
                            viewGroup.showContextMenu();
                        } else {
                            onButtonClick(v, menuItem);
                        }
                    }
                }
        );
    }

    private void onButtonClick(View v, MessageListContextMenuItem contextMenuItemIn) {
        MessageViewItem item = (MessageViewItem) getItem(v);
        if (item != null && item.msgStatus == DownloadStatus.LOADED) {
            MyAccount actor = item.getLinkedMyAccount();
            // Currently selected account is the best candidate as an actor
            MyAccount ma = myContext.persistentAccounts().fromUserId(
                    contextMenu.getCurrentMyAccountUserId());
            if (ma.isValid() && ma.getOriginId() == item.getOriginId()) {
                actor = ma;
            }
            contextMenu.onContextMenuItemSelected(contextMenuItemIn, item.getMsgId(), actor);
        }
    }

    protected void showButtonsBelowMessage(MessageViewItem item, View view) {
        View viewGroup = view.findViewById(R.id.message_buttons);
        if (viewGroup == null) {
            return;
        } else if (showButtonsBelowMessages && item.msgStatus == DownloadStatus.LOADED) {
            viewGroup.setVisibility(View.VISIBLE);
            tintIcon(viewGroup, item.reblogged, R.id.reblog_button, R.id.reblog_button_tinted);
            tintIcon(viewGroup, item.favorited, R.id.favorite_button, R.id.favorite_button_tinted);
        } else {
            viewGroup.setVisibility(View.GONE);
        }
    }

    private void tintIcon(View viewGroup, boolean colored, int viewId, int viewIdColored) {
        ImageView imageView = (ImageView) viewGroup.findViewById(viewId);
        ImageView imageViewTinted = (ImageView) viewGroup.findViewById(viewIdColored);
        imageView.setVisibility(colored ? View.GONE : View.VISIBLE);
        imageViewTinted.setVisibility(colored ? View.VISIBLE : View.GONE);
    }

    protected void showFavorited(MessageViewItem item, View view) {
        View favorited = view.findViewById(R.id.message_favorited);
        favorited.setVisibility(item.favorited ? View.VISIBLE : View.GONE );
    }
}
