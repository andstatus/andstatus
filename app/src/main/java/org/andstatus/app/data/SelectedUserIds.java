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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;

/**
 * Helper class to construct sql WHERE clause selecting by UserIds
 * @author yvolk@yurivolkov.com
 */
public class SelectedUserIds {
    private int mSize = 0;
    private String sqlUserIds = "";

    public SelectedUserIds(Timeline timeline) {
        if (timeline.getTimelineType() == TimelineType.USER) {
            if ( timeline.getUserId() != 0) {
                mSize = 1;
                sqlUserIds = Long.toString(timeline.getUserId());
            }
        } else if (timeline.isCombined() || timeline.getTimelineType().isAtOrigin()) {
            StringBuilder sb = new StringBuilder();
            for (MyAccount ma : MyContextHolder.get().persistentAccounts().list()) {
                if (!timeline.getOrigin().isValid() || timeline.getOrigin().equals(ma.getOrigin())) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    mSize += 1;
                    sb.append(Long.toString(ma.getUserId()));
                }
            }
            sqlUserIds = sb.toString();
        } else if (timeline.getMyAccount().isValid()) {
            mSize = 1;
            sqlUserIds = Long.toString(timeline.getMyAccount().getUserId());
        }
    }

    public int size() {
        return mSize;
    }

    public String getList() {
        return sqlUserIds;
    }

    public String getSql() {
        if (mSize == 1) {
            return "=" + sqlUserIds;
        } else if (mSize > 1) {
            return " IN (" + sqlUserIds + ")";
        }
        return "";
    }
}
