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

package org.andstatus.app.timeline.meta;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;

import java.util.Objects;

/**
 * Data to show on UI. May be create on UI thread
 * @author yvolk@yurivolkov.com
 */
public class TimelineTitle {
    public String title = "";
    public String subTitle = "";

    public String accountName = "";
    public String originName = "";

    private TimelineTitle() {
        // Empty
    }

    public void updateActivityTitle(MyActivity activity, String additionalTitleText) {
        activity.setTitle(title);
        activity.setSubtitle(I18n.appendWithSpace(new StringBuilder(subTitle), additionalTitleText));
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(activity, "Title: " + toString());
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(title);
        if (!TextUtils.isEmpty(subTitle)) {
            I18n.appendWithSpace(builder, subTitle);
        }
        return builder.toString();
    }

    public static TimelineTitle load(MyContext myContext, Timeline timeline, @NonNull MyAccount currentMyAccount) {
        Objects.requireNonNull(currentMyAccount);
        TimelineTitle timelineTitle = new TimelineTitle();
        timelineTitle.title = toTimelineTitle(myContext, timeline, currentMyAccount);
        timelineTitle.subTitle = toTimelineSubtitle(myContext, timeline, currentMyAccount);

        timelineTitle.accountName = timeline.getMyAccount().isValid() ?
                timeline.getMyAccount().toAccountButtonText(myContext) : "";
        timelineTitle.originName = timeline.getOrigin().isValid() ?
                timeline.getOrigin().getName() : "";

        return timelineTitle;
    }

    private static String toTimelineTitle(MyContext myContext, Timeline timeline, MyAccount currentMyAccount) {
        StringBuilder title = new StringBuilder();
        I18n.appendWithSpace(title, timeline.getTimelineType().getTitle(myContext.context()));
        if (timeline.hasSearchQuery()) {
            I18n.appendWithSpace(title, "'" + timeline.getSearchQuery() + "'");
        }
        if (timeline.isCombined()) {
            I18n.appendWithSpace(title,
                    myContext.context() == null ? "combined" : myContext.context().getText(R.string.combined_timeline_on));
        } else {
            if (timeline.getTimelineType().isAtOrigin()) {
                if (currentMyAccount != MyAccount.EMPTY) {
                    I18n.appendWithSpace(title, timeline.getTimelineType()
                            .getPrepositionForNotCombinedTimeline(myContext.context()));
                    I18n.appendWithSpace(title, timeline.getOrigin().getName());
                }
            } else {
                if (currentMyAccount == MyAccount.EMPTY) {
                    if (timeline.isActorDifferentFromAccount()) {
                        I18n.appendWithSpace(title, timeline.getActorInTimeline());
                    }
                } else {
                    if (timeline.getActorId() != currentMyAccount.getActorId()) {
                        I18n.appendWithSpace(title, timeline.getActorInTimeline());
                    }
                }
            }
        }
        return title.toString();
    }

    private static String toTimelineSubtitle(MyContext myContext, Timeline timeline, @NonNull MyAccount currentMyAccount) {
        final StringBuilder title = new StringBuilder();
        if (currentMyAccount != MyAccount.EMPTY) {
            I18n.appendWithSpace(title, currentMyAccount.toAccountButtonText(myContext));
        }
        return title.toString();
    }
}
