/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.timeline.TimelineLoader;
import org.andstatus.app.timeline.TimelineParameters;
import org.andstatus.app.actor.ActorListLoader;
import org.andstatus.app.actor.ActorListType;

import java.util.List;

public class ActivityLoader extends TimelineLoader<ActivityViewItem> {

    public ActivityLoader(@NonNull TimelineParameters params, long instanceId) {
        super(params, instanceId);
    }

    @Override
    protected void filter(List<ActivityViewItem> items) {
        super.filter(loadActors(items));
    }

    private List<ActivityViewItem> loadActors(List<ActivityViewItem> items) {
        ActorListLoader loader = new ActorListLoader(ActorListType.ACTORS, getParams().getMyAccount(),
                getParams().getTimeline().getOrigin(), 0, "");
        for (ActivityViewItem item: items) {
            if (item.activityType != ActivityType.CREATE && item.activityType != ActivityType.UPDATE) {
                loader.addActorIdToList(item.origin, item.actor.getId());
            }
            if (item.objActorId != 0) {
                loader.addActorIdToList(item.origin, item.objActorId);
            }
        }
        if (loader.getList().isEmpty()) return items;

        loader.load(progress -> {});
        for (ActivityViewItem item: items) {
            if (item.activityType != ActivityType.CREATE && item.activityType != ActivityType.UPDATE) {
                int index = loader.getList().indexOf(item.actor);
                if (index >= 0) {
                    item.actor = loader.getList().get(index);
                }
            }
            if (item.objActorId != 0) {
                int index = loader.getList().indexOf(item.getObjActorItem());
                if (index >= 0) {
                    item.setObjActorItem(loader.getList().get(index));
                }
                item.getObjActorItem().setParent(item);
            }
        }
        return items;
    }
}
