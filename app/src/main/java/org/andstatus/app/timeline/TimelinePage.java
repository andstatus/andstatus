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

package org.andstatus.app.timeline;

import android.support.annotation.NonNull;

import org.andstatus.app.actor.ActorListLoader;
import org.andstatus.app.actor.ActorViewItem;
import org.andstatus.app.net.social.ActivityType;

import java.util.Collections;
import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelinePage<T extends ViewItem<T>> {
    @NonNull
    final TimelineParameters params;
    @NonNull
    ActorViewItem actorViewItem = ActorViewItem.EMPTY;
    private final T emptyItem;
    @NonNull
    public final List<T> items;

    @NonNull
    public T getEmptyItem() {
        return emptyItem;
    }

    public TimelinePage(@NonNull TimelineParameters params, List<T> items) {
        this.params = params;
        emptyItem = new ViewItem<T>(true).getEmpty(params.getTimelineType());
        this.items = items == null ? Collections.EMPTY_LIST : items;
    }

    public void setLoadedActor(ActorListLoader loader) {
        if (params.timeline.getTimelineType().isForUser()) {
            int index = loader.getList().indexOf(ActorViewItem.fromActor(params.timeline.actor));
            if (index >= 0) actorViewItem = loader.getList().get(index);
        }
    }
}
