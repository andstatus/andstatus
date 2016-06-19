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

package org.andstatus.app.timeline;

import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;

/**
 * Data to show on UI. Its creation requires async work
 * @author yvolk@yurivolkov.com
 */
public class TimelineTitle {
    public String title = "";
    public String subTitle = "";

    public void updateActivityTitle(MyActivity activity, String additionalTitleText) {
        activity.setTitle(title);
        activity.setSubtitle(I18n.appendWithSpace(new StringBuilder(subTitle), additionalTitleText));
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(activity, "Title: " + toString());
        }
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder(title);
        I18n.appendWithSpace(stringBuilder, ";");
        I18n.appendWithSpace(stringBuilder, subTitle);
        return stringBuilder.toString();
    }

    /** Requires async work */
    public static TimelineTitle load(Timeline timeline, MyAccount currentMyAccount) {
        TimelineTitle timelineTitle = new TimelineTitle();
        timelineTitle.title = toTimelineTitle(timeline, currentMyAccount);
        timelineTitle.subTitle = toTimelineSubtitle(timeline, currentMyAccount);
        return timelineTitle;
    }

    private static String toTimelineTitle(Timeline timeline, MyAccount currentMyAccount) {
        StringBuilder title = new StringBuilder();
        I18n.appendWithSpace(title, timeline.getTimelineType().getTitle(MyContextHolder.get().context()));
        if (timeline.hasSearchQuery()) {
            I18n.appendWithSpace(title, "'" + timeline.getSearchQuery() + "'");
        }
        if (timeline.getTimelineType().requiresUserToBeDefined()) {
            I18n.appendWithSpace(title, MyQuery.userIdToWebfingerId(timeline.getUserId()));
        }
        if (timeline.isCombined()) {
            I18n.appendWithSpace(title,
                    MyContextHolder.get().context() == null ? "combined" : MyContextHolder.get().context().getText(R.string.combined_timeline_on));
        }
        return title.toString();
    }

    private static String toTimelineSubtitle(Timeline timeline, MyAccount currentMyAccount) {
        final StringBuilder subTitle = new StringBuilder();
        if (!timeline.isCombined()) {
            I18n.appendWithSpace(subTitle, timeline.getTimelineType()
                    .getPrepositionForNotCombinedTimeline(MyContextHolder.get().context()));
            if (timeline.getTimelineType().isAtOrigin()) {
                I18n.appendWithSpace(subTitle, timeline.getOrigin().getName()
                        + ";");
            }
        }
        I18n.appendWithSpace(subTitle, currentMyAccount.toAccountButtonText());
        return subTitle.toString();
    }

}
