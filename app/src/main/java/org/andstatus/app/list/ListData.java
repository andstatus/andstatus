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

package org.andstatus.app.list;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.widget.DuplicatesCollapsible;
import org.andstatus.app.widget.DuplicationLink;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ListData {
    // Parameters, which may be changed during presentation of the timeline
    protected volatile boolean collapseDuplicates = MyPreferences.isCollapseDuplicates();
    protected final Set<Long> individualCollapsedStateIds = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());

    public ListData(ListData oldData) {
        if (oldData != null) {
            this.collapseDuplicates = oldData.collapseDuplicates;
            this.individualCollapsedStateIds.addAll(oldData.individualCollapsedStateIds);
        }
    }

    public boolean isCollapseDuplicates() {
        return collapseDuplicates;
    }

    public boolean canBeCollapsed(int position) {
        DuplicatesCollapsible item = getItem(position);
        if (item != null) {
            if (position > 0) {
                if (item.duplicates(getItem(position - 1)) != DuplicationLink.NONE) {
                    return true;
                }
            }
            if (item.duplicates(getItem(position + 1)) != DuplicationLink.NONE) {
                return true;
            }
        }
        return false;
    }

    // See http://stackoverflow.com/questions/300522/count-vs-length-vs-size-in-a-collection
    public int size() {
        return 0;
    }

    public DuplicatesCollapsible getItem(int position) {
        return null;
    }

    public void collapseDuplicates(boolean collapse, long itemId) {
        if (itemId == 0 && this.collapseDuplicates != collapse) {
            this.collapseDuplicates = collapse;
            individualCollapsedStateIds.clear();
        }
    }

    protected void setIndividualCollapsedStatus(boolean collapse, long itemId) {
        if (collapse == isCollapseDuplicates()) {
            individualCollapsedStateIds.remove(itemId);
        } else {
            individualCollapsedStateIds.add(itemId);
        }
    }
}
