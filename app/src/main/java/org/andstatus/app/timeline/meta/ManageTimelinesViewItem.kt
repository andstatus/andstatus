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

package org.andstatus.app.timeline.meta;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.timeline.ViewItem;

/**
 * @author yvolk@yurivolkov.com
 */
public class ManageTimelinesViewItem extends ViewItem<ManageTimelinesViewItem> {
    final Timeline timeline;
    final TimelineTitle timelineTitle;
    final long countSince;

    protected ManageTimelinesViewItem(MyContext myContext, Timeline timeline,
                                 MyAccount accountToHide, boolean namesAreHidden) {
        super(false, timeline.getLastChangedDate());
        this.timeline = timeline;
        timelineTitle = TimelineTitle.from(myContext, timeline, accountToHide, namesAreHidden,
                TimelineTitle.Destination.DEFAULT);
        countSince = timeline.getCountSince();
    }

    @Override
    public long getId() {
        return timeline == null ? 0 : timeline.getId();
    }

    @Override
    public long getDate() {
        return timeline == null ? 0 : timeline.getLastSyncedDate();
    }
}
