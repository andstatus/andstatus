/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static java.util.stream.Collectors.toList;

/**
 * Helper class to construct sql WHERE clause selecting by UserIds
 * @author yvolk@yurivolkov.com
 */
public class SelectedUserIds {
    private final Set<Long> ids;

    public static SelectedUserIds fromTimeline(@NonNull Timeline timeline) {
        if (timeline.getTimelineType() == TimelineType.USER) {
            if ( timeline.getUserId() != 0) {
                return new SelectedUserIds(timeline.getUserId());
            }
        } else if (timeline.isCombined() || timeline.getTimelineType().isAtOrigin()) {
            return new SelectedUserIds(MyContextHolder.get().persistentAccounts().list().stream()
                    .filter(ma -> !timeline.getOrigin().isValid() || timeline.getOrigin().equals(ma.getOrigin()))
                    .map(MyAccount::getUserId).collect(toList()));
        } else if (timeline.getMyAccount().isValid()) {
            return new SelectedUserIds(timeline.getMyAccount().getUserId());
        }
        return new SelectedUserIds();
    }

    public static SelectedUserIds fromUsers(@NonNull Collection<MbUser> users) {
        return new SelectedUserIds(users.stream().map(user -> user.userId).collect(toList()));
    }

    public SelectedUserIds(@NonNull Collection<Long> ids) {
        this.ids = new HashSet<>(ids);
    }

    public SelectedUserIds(Long ... id) {
        this.ids = new HashSet<>(Arrays.asList(id));
    }

    public static SelectedUserIds fromIds(@NonNull Collection<Long> ids) {
        return new SelectedUserIds(ids);
    }

    public int size() {
        return ids.size();
    }

    public String getList() {
        StringBuilder sb = new StringBuilder();
        for (long id : ids) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(Long.toString(id));
        }
        return sb.toString();
    }

    public String getSql() {
        if (size() == 0) {
            return "";
        } else if (size() == 1) {
            return "=" + ids.iterator().next();
        } else {
            return " IN (" + getList() + ")";
        }
    }
}
