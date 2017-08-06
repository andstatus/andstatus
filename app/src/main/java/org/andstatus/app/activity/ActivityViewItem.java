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

import org.andstatus.app.ViewItem;
import org.andstatus.app.msg.TimelineViewItem;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbObjectType;
import org.andstatus.app.user.UserListViewItem;

/** View on ActivityStream
 * @author yvolk@yurivolkov.com
 */
public class ActivityViewItem implements ViewItem, Comparable<ActivityViewItem> {
    long insDate = 0;
    MbActivityType activityType = MbActivityType.EMPTY;
    MbObjectType objectType = MbObjectType.EMPTY;
    UserListViewItem actor = UserListViewItem.EMPTY;
    TimelineViewItem message = TimelineViewItem.EMPTY;
    UserListViewItem user = UserListViewItem.EMPTY;

    @Override
    public long getId() {
        return 0;
    }

    @Override
    public int compareTo(@NonNull ActivityViewItem o) {
        // TODO: replace with Long#compare
        return insDate < o.insDate ? -1 : (insDate == o.insDate ? 0 : 1);
    }
}
