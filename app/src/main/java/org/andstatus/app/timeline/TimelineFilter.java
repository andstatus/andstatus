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

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.msg.KeywordsFilter;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.SharedPreferencesUtil;

public class TimelineFilter {
    public final KeywordsFilter keywordsFilter;
    public final boolean hideRepliesNotToMeOrFriends;
    public final KeywordsFilter searchQuery;

    TimelineFilter(Timeline timeline) {
        keywordsFilter = new KeywordsFilter(
                SharedPreferencesUtil.getString(MyPreferences.KEY_FILTER_HIDE_MESSAGES_BASED_ON_KEYWORDS, ""));
        hideRepliesNotToMeOrFriends = timeline.getTimelineType() == TimelineType.HOME
                && SharedPreferencesUtil.getBoolean(MyPreferences.KEY_FILTER_HIDE_REPLIES_NOT_TO_ME_OR_FRIENDS, false);
        searchQuery = new KeywordsFilter(timeline.getSearchQuery());
    }
}
