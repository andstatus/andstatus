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
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;

import java.util.HashSet;
import java.util.Set;

/**
 * Helper class to construct sql WHERE clause selecting by UserIds
 * @author yvolk@yurivolkov.com
 */
public class SelectedUserIds {
    private final Set<Long> ids = new HashSet<>();

    public SelectedUserIds(Timeline timeline) {
        if (timeline.getTimelineType() == TimelineType.USER) {
            if ( timeline.getUserId() != 0) {
                ids.add(timeline.getUserId());
            }
        } else if (timeline.isCombined() || timeline.getTimelineType().isAtOrigin()) {
            StringBuilder sb = new StringBuilder();
            for (MyAccount ma : MyContextHolder.get().persistentAccounts().list()) {
                if (!timeline.getOrigin().isValid() || timeline.getOrigin().equals(ma.getOrigin())) {
                    ids.add(ma.getUserId());
                }
            }
        } else if (timeline.getMyAccount().isValid()) {
            ids.add(timeline.getMyAccount().getUserId());
        }
    }

    public SelectedUserIds(@NonNull Audience audience) {
        for (MbUser user : audience.getRecipients()) {
            ids.add(user.userId);
        }
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
