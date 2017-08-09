/*
 * Copyright (c) 201 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.support.annotation.NonNull;

import org.andstatus.app.widget.TimelineViewItem;

import java.util.Collections;
import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelinePage<T extends TimelineViewItem> {
    @NonNull
    final TimelineListParameters params;
    @NonNull
    public final List<T> items;

    public T getEmptyItem() {
        return  (T) TimelineViewItem.getEmpty(params.getTimelineType());
    }

    public TimelinePage(@NonNull TimelineListParameters params, List<T> items) {
        this.params = params;
        this.items = items == null ? Collections.EMPTY_LIST : items;
    }
}
