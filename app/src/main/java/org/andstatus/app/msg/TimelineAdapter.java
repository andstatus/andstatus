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

import android.view.View;
import android.view.ViewGroup;

import org.andstatus.app.R;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyUrlSpan;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineAdapter extends MessageListAdapter<TimelineViewItem> {
    private final TimelineData listData;
    private int positionPrev = -1;
    private int messageNumberShownCounter = 0;
    private final String TOP_TEXT;

    public TimelineAdapter(MessageContextMenu contextMenu, TimelineData listData) {
        super(contextMenu);
        this.listData = listData;
        TOP_TEXT = myContext.context().getText(R.string.top).toString();
    }

    @Override
    public int getCount() {
        return listData.size();
    }

    @Override
    public TimelineViewItem getItem(int position) {
        return listData.getItem(position);
    }

    @Override
    protected void showAvatarEtc(ViewGroup view, TimelineViewItem item) {
        if (showAvatars) {
            showAvatar(view, item);
        } else {
            View message = view.findViewById(R.id.message_indented);
            if (message != null) {
                message.setPadding(dpToPixes(2), 0, dpToPixes(6), dpToPixes(2));
            }
        }
    }

    private void preloadAttachments(int position) {
        if (positionPrev < 0 || position == positionPrev) {
            return;
        }
        Integer positionToPreload = position;
        for (int i = 0; i < 5; i++) {
            positionToPreload = positionToPreload + (position > positionPrev ? 1 : -1);
            if (positionToPreload < 0 || positionToPreload >= listData.size()) {
                break;
            }
            TimelineViewItem item = getItem(positionToPreload);
            if (!preloadedImages.contains(item.getMsgId())) {
                preloadedImages.add(item.getMsgId());
                item.getAttachedImageFile().preloadAttachedImage(contextMenu.getActivity());
                break;
            }
        }
    }

    @Override
    protected void showMessageNumberEtc(ViewGroup view, TimelineViewItem item, int position) {
        preloadAttachments(position);
        String text;
        switch (position) {
            case 0:
                text = listData.mayHaveYoungerPage() ? "1" : TOP_TEXT;
                break;
            case 1:
            case 2:
                text = Integer.toString(position + 1);
                break;
            default:
                text = messageNumberShownCounter < 3 ? Integer.toString(position + 1) : "";
                break;
        }
        MyUrlSpan.showText(view, R.id.message_number, text, false, false);
        messageNumberShownCounter++;
        positionPrev = position;
    }

    @Override
    public String toString() {
        return MyLog.formatKeyValue(this, listData);
    }

    @Override
    public void onClick(View v) {
        boolean handled = false;
        if (MyPreferences.isLongPressToOpenContextMenu()) {
            TimelineViewItem item = getItem(v);
            if (TimelineActivity.class.isAssignableFrom(contextMenu.menuContainer.getClass())) {
                ((TimelineActivity) contextMenu.menuContainer).onItemClick(item);
                handled = true;
            }
        }
        if (!handled) {
            super.onClick(v);
        }
    }

    @Override
    public int getPositionById(long itemId) {
        int position = super.getPositionById(itemId);
        if (position < 0 && listData.isCollapseDuplicates()) {
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
