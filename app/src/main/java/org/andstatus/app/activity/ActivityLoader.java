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

import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.timeline.TimelineLoader;
import org.andstatus.app.timeline.TimelineParameters;
import org.andstatus.app.user.UserListLoader;
import org.andstatus.app.user.UserListType;

import java.util.List;

public class ActivityLoader extends TimelineLoader<ActivityViewItem> {

    public ActivityLoader(@NonNull TimelineParameters params, long instanceId) {
        super(params, instanceId);
    }

    @Override
    protected void filter(List<ActivityViewItem> items) {
        super.filter(loadUsers(items));
    }

    private List<ActivityViewItem> loadUsers(List<ActivityViewItem> items) {
        UserListLoader loader = new UserListLoader(UserListType.USERS, getParams().getMyAccount(),
                getParams().getTimeline().getOrigin(), 0, "");
        for (ActivityViewItem item: items) {
            if (item.activityType != MbActivityType.CREATE && item.activityType != MbActivityType.UPDATE) {
                item.actor = loader.addUserIdToList(item.getOrigin(), item.actor.getId());
            }
            if (item.userId != 0) {
                item.user = loader.addUserIdToList(item.getOrigin(), item.userId);
                item.user.setParent(item);
            }
        }
        loader.load(progress -> {});
        return items;
    }
}
