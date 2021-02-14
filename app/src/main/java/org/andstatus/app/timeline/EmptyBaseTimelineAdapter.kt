/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.timeline;

import android.view.View;
import android.view.ViewGroup;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.timeline.meta.Timeline;

import java.util.Collections;

/** Avoiding null value for an adapter */
public class EmptyBaseTimelineAdapter<T extends ViewItem<T>> extends BaseTimelineAdapter<T> {
    public final static EmptyBaseTimelineAdapter EMPTY = new EmptyBaseTimelineAdapter();

    private EmptyBaseTimelineAdapter() {
        super(MyContext.EMPTY, Timeline.EMPTY, Collections.emptyList());
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return null;
    }
}
