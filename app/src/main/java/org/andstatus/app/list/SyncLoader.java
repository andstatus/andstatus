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

import android.support.annotation.NonNull;

import org.andstatus.app.timeline.LoadableListActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public abstract class SyncLoader<T> {
    protected List<T> items = new ArrayList<>();

    public void allowLoadingFromInternet() {
        // Empty
    }

    public abstract void load(LoadableListActivity.ProgressPublisher publisher);

    @NonNull
    public List<T> getList() {
        return items;
    }

    @NonNull
    public T getLoaded(@NonNull T beforeLoad) {
        int index = getList().indexOf(beforeLoad);
        return index < 0
                ? beforeLoad
                : getList().get(index);
    }

    public int size() {
        return items.size();
    }
}
