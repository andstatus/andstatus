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
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbObjectType;
import org.andstatus.app.user.UserListViewItem;
import org.andstatus.app.widget.TimelineViewItem;

/** View on ActivityStream
 * @author yvolk@yurivolkov.com
 */
public class ActivityViewItem implements TimelineViewItem, Comparable<ActivityViewItem> {
    public static final ActivityViewItem EMPTY = new ActivityViewItem();
    private long insDate = 0;
    private long id = 0;
    MbActivityType activityType = MbActivityType.EMPTY;
    MbObjectType objectType = MbObjectType.EMPTY;
    UserListViewItem actor = UserListViewItem.EMPTY;
    MessageViewItem message = MessageViewItem.EMPTY;
    UserListViewItem user = UserListViewItem.EMPTY;

    @Override
    public long getId() {
        return id;
    }

    @Override
    public long getDate() {
        return insDate;
    }

    @Override
    public int compareTo(@NonNull ActivityViewItem o) {
        // TODO: replace with Long#compare
        return insDate < o.insDate ? -1 : (insDate == o.insDate ? 0 : 1);
    }
}
