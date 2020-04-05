/*
 * Copyright (C) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social;

import org.andstatus.app.util.HasEmpty;

import java.util.List;

public abstract class InputPage<T> implements HasEmpty<T> {
    public final TimelinePosition firstPosition;
    public final TimelinePosition youngerPosition;
    public final TimelinePosition thisPosition;
    public final TimelinePosition olderPosition;
    public final List<T> activities;
    private final boolean isEmpty;

    protected InputPage(AJsonCollection jsonCollection, List<T> activities) {
        firstPosition = TimelinePosition.of(jsonCollection.firstPage.getId());
        youngerPosition = TimelinePosition.of(jsonCollection.prevPage.getId());
        thisPosition = TimelinePosition.of(jsonCollection.getId());
        olderPosition = TimelinePosition.of(jsonCollection.nextPage.getId());
        this.activities = activities;
        isEmpty = jsonCollection.isEmpty();
    }

    public boolean isEmpty() {
        return isEmpty;
    }

    public T get(int ind) {
        return ind < 0 || ind > activities.size()
                ? empty()
                : activities.get(ind);
    }

    public int size() {
        return activities.size();
    }
}