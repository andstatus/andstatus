/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.activity;

import android.support.annotation.NonNull;

import org.andstatus.app.msg.MessageViewItem;
import org.andstatus.app.timeline.TimelineData;
import org.andstatus.app.timeline.ViewItem;

class TimelineDataMessageWrapper extends TimelineDataWrapper<MessageViewItem> {

    TimelineDataMessageWrapper(TimelineData<ActivityViewItem> listData) {
        super(listData);
    }

    @NonNull
    @Override
    public MessageViewItem getItem(int position) {
        return listData.getItem(position).message;
    }

    @Override
    public int getPositionById(long itemId) {
        int position = -1;
        if (itemId != 0) {
            for (int ind=0; ind < listData.size(); ind++) {
                ActivityViewItem item = listData.getItem(ind);
                if (item.message.getId() == itemId) {
                    return position;
                } else if (item.isCollapsed()) {
                    for (ViewItem child : item.getChildren()) {
                        if ( ((ActivityViewItem) child).message.getId() == itemId) {
                            return position;
                        }
                    }
                }
            }
        }
        return -1;
    }
}
