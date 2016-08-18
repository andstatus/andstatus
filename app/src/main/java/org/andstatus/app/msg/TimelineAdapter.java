/*
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.graphics.MyImageCache;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.widget.MyBaseAdapter;

import java.util.HashSet;
import java.util.Set;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineAdapter extends MyBaseAdapter {
    private final MessageContextMenu contextMenu;
    private final int listItemLayoutId;
    private final TimelinePages pages;
    private final boolean showAvatars = MyPreferences.getShowAvatars();
    private final boolean showAttachedImages = MyPreferences.getDownloadAndDisplayAttachedImages();
    private final boolean showButtonsBelowMessages =
            SharedPreferencesUtil.getBoolean(MyPreferences.KEY_SHOW_BUTTONS_BELOW_MESSAGE, true);
    private final boolean markReplies = SharedPreferencesUtil.getBoolean(
            MyPreferences.KEY_MARK_REPLIES_IN_TIMELINE, false);
    private int positionPrev = -1;
    private Set<Long> preloadedImages = new HashSet<>(100);
    private int messageNumberShownCounter = 0;
    private final String TOP_TEXT;

    public TimelineAdapter(MessageContextMenu contextMenu,
                           TimelineAdapter oldAdapter, TimelinePage loadedPage) {
        super(contextMenu.getActivity().getMyContext());
        this.contextMenu = contextMenu;
        this.listItemLayoutId = R.layout.message_avatar;
        this.pages = new TimelinePages( oldAdapter == null ? null : oldAdapter.getPages(), loadedPage);
        TOP_TEXT = myContext.context().getText(R.string.top).toString();
    }

    @Override
    public int getCount() {
        return pages.getItemsCount();
    }

    @Override
    public TimelineViewItem getItem(View view) {
        return (TimelineViewItem) super.getItem(view);
    }

    @Override
    public TimelineViewItem getItem(int position) {
        return pages.getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getMsgId();
    }

    public TimelinePages getPages() {
        return pages;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView == null ? newView() : convertView;
        view.setOnCreateContextMenuListener(contextMenu);
        view.setOnClickListener(this);
        setPosition(view, position);
        TimelineViewItem item = getItem(position);
        MyUrlSpan.showText(view, R.id.message_author, item.authorName, false, false);
        showRebloggers(item, view);
        showMessageBody(item, view);
        MyUrlSpan.showText(view, R.id.message_details, item.getDetails(contextMenu.getActivity()), false, false);
        if (showAvatars) {
            showAvatar(item, view);
        }
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
        preloadAttachments(position);
        showMessageNumber(position, view);
        positionPrev = position;
        return view;
    }

    private void showRebloggers(TimelineViewItem item, View view) {
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

    private void showMessageBody(TimelineViewItem item, View messageView) {
        TextView body = (TextView) messageView.findViewById(R.id.message_body);
        MyUrlSpan.showText(body, item.body, true, true);
    }

    private void preloadAttachments(int position) {
        if (positionPrev < 0 || position == positionPrev) {
            return;
        }
        Integer positionToPreload = position;
        for (int i = 0; i < 5; i++) {
            positionToPreload = positionToPreload + (position > positionPrev ? 1 : -1);
            if (positionToPreload < 0 || positionToPreload >= pages.getItemsCount()) {
                break;
            }
            TimelineViewItem item = getItem(positionToPreload);
            if (!preloadedImages.contains(item.getMsgId())) {
                preloadedImages.add(item.getMsgId());
                item.getAttachedImageFile().preloadAttachedImage(contextMenu.messageList);
                break;
            }
        }
    }

    private void showMessageNumber(int position, View view) {
        String text;
        switch (position) {
            case 0:
                text = getPages().mayHaveYoungerPage() ? "1" : TOP_TEXT;
                break;
            case 1:
                text = "2";
                break;
            default:
                text = messageNumberShownCounter < 2 ? Integer.toString(position + 1) : "";
                break;
        }
        MyUrlSpan.showText(view, R.id.message_number, text, false, false);
        messageNumberShownCounter++;
    }

    private View newView() {
        View view = LayoutInflater.from(contextMenu.getActivity()).inflate(listItemLayoutId, null);
        if (showButtonsBelowMessages) {
            View buttons = view.findViewById(R.id.message_buttons);
            if (buttons != null) {
                buttons.setVisibility(View.VISIBLE);
                setOnButtonClick(buttons, R.id.reply_button, MessageListContextMenuItem.REPLY);
                setOnButtonClick(buttons, R.id.reblog_button, MessageListContextMenuItem.REBLOG);
                setOnButtonClick(buttons, R.id.reblog_button_tinted, MessageListContextMenuItem.DESTROY_REBLOG);
                setOnButtonClick(buttons, R.id.favorite_button, MessageListContextMenuItem.FAVORITE);
                setOnButtonClick(buttons, R.id.favorite_button_tinted, MessageListContextMenuItem.DESTROY_FAVORITE);
            }
        }
        if (!showAvatars) {
            View message = view.findViewById(R.id.message_indented);
            if (view != null) {
                message.setPadding(dpToPixes(2), 0, dpToPixes(6), dpToPixes(2));
            }
        }
        return view;
    }

    private void setOnButtonClick(View viewGroup, int buttonId, final MessageListContextMenuItem menuItem) {
        viewGroup.findViewById(buttonId).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onButtonClick(v, menuItem);
                    }
                }
        );
    }

    private void onButtonClick(View v, MessageListContextMenuItem contextMenuItemIn) {
        TimelineViewItem item = getItem(v);
        if (item != null && item.msgStatus == DownloadStatus.LOADED) {
            long actorId = item.getLinkedUserId();
            // Currently selected account is the best candidate as an actor
            MyAccount ma = myContext.persistentAccounts().fromUserId(
                    contextMenu.getCurrentMyAccountUserId());
            if (ma.isValid() && ma.getOriginId() == item.originId) {
                actorId = ma.getUserId();
            }
            contextMenu.onContextMenuItemSelected(contextMenuItemIn, item.getMsgId(), actorId);
        }
    }

    private void showAvatar(TimelineViewItem item, View view) {
        ImageView avatar = (ImageView) view.findViewById(R.id.avatar_image);
        avatar.setImageDrawable(item.getAvatar());
    }

    private void showAttachedImage(TimelineViewItem item, View view) {
        preloadedImages.add(item.getMsgId());
        item.getAttachedImageFile().showAttachedImage(contextMenu.messageList,
                (ImageView) view.findViewById(R.id.attached_image));
    }

    private void showFavorited(TimelineViewItem item, View view) {
        View favorited = view.findViewById(R.id.message_favorited);
        favorited.setVisibility(item.favorited ? View.VISIBLE : View.GONE );
    }

    private void showMarkReplies(TimelineViewItem item, View view) {
        if (item.inReplyToUserId != 0 && myContext.persistentAccounts().
                fromUserId(item.inReplyToUserId).isValid()) {
            // For some reason, referring to the style drawable doesn't work
            // (to "?attr:replyBackground" )
            view.setBackground( MyImageCache.getStyledDrawable(
                    R.drawable.reply_timeline_background_light,
                    R.drawable.reply_timeline_background));
        } else {
            view.setBackgroundResource(0);
            view.setPadding(0, 0, 0, 0);
        }
    }

    private void showButtonsBelowMessage(TimelineViewItem item, View view) {
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

    @Override
    public String toString() {
        return MyLog.formatKeyValue(this, pages);
    }

    @Override
    public void onClick(View v) {
        boolean handled = false;
        if (MyPreferences.isLongPressToOpenContextMenu()) {
            TimelineViewItem item = getItem(v);
            if (TimelineActivity.class.isAssignableFrom(contextMenu.messageList.getClass())) {
                ((TimelineActivity) contextMenu.messageList).onItemClick(item);
                handled = true;
            }
        }
        if (!handled) {
            super.onClick(v);
        }
    }

    @Override
    public void setCollapseDuplicates(boolean collapse, long itemId) {
        pages.setCollapseDuplicates(collapse, itemId);
        super.setCollapseDuplicates(collapse, itemId);
    }

    @Override
    public int getPositionById(long itemId) {
        int position = super.getPositionById(itemId);
        if (position < 0 && pages.isCollapseDuplicates()) {
            for (int position2 = 0; position2 < getCount(); position2++) {
                TimelineViewItem item = getItem(position2);
                if (item.isCollapsed()) {
                    for (TimelineViewItem child : item.getChildren()) {
                        if (child.getMsgId() == itemId) {
                            return position2;
                        }
                    }
                }
            }
        }
        return position;
    }
}
