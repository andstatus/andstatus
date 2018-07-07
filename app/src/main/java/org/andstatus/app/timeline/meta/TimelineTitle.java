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

import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.timeline.ListScope;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtils;

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
        activity.setSubtitle(new MyStringBuilder(subTitle).withSpace(additionalTitleText));
        MyLog.v(activity, () -> "Title: " + toString());
    }

    @Override
    public String toString() {
        MyStringBuilder builder = new MyStringBuilder(title);
        if (StringUtils.nonEmpty(subTitle)) {
            builder.withSpace(subTitle);
        }
        return builder.toString();
    }

    public static TimelineTitle load(MyContext myContext, Timeline timeline, @NonNull MyAccount currentMyAccount) {
        Objects.requireNonNull(currentMyAccount);
        TimelineTitle timelineTitle = new TimelineTitle();
        timelineTitle.title = toTimelineTitle(myContext, timeline, currentMyAccount);
        timelineTitle.subTitle = toTimelineSubtitle(myContext, timeline, currentMyAccount);

        timelineTitle.accountName = timeline.myAccountToSync.isValid() ?
                timeline.myAccountToSync.toAccountButtonText(myContext) : "";
        timelineTitle.originName = timeline.getOrigin().isValid() ?
                timeline.getOrigin().getName() : "";

        return timelineTitle;
    }

    private static String toTimelineTitle(MyContext myContext, Timeline timeline, MyAccount currentMyAccount) {
        return timeline.getTimelineType().scope == ListScope.ORIGIN
                ? toOriginTitle(myContext, timeline, currentMyAccount)
                : toUserTitle(myContext, timeline, currentMyAccount);
    }

    private static String toOriginTitle(MyContext myContext, Timeline timeline, MyAccount currentMyAccount) {
        MyStringBuilder title = new MyStringBuilder();
        title.withSpace(timeline.getTimelineType().title(myContext.context()));
        if (timeline.hasSearchQuery()) {
            title.withSpace("'" + timeline.getSearchQuery() + "'");
        }
        if (timeline.isCombined()) {
            title.withSpace(
                    myContext.context() == null ? "combined" : myContext.context().getText(R.string.combined_timeline_on));
        } else {
            if (currentMyAccount.nonEmpty()) {
                title.withSpace(timeline.getTimelineType().scope.timelinePreposition(myContext));
                title.withSpace(timeline.getOrigin().getName());
            }
        }
        return title.toString();
    }

    private static String toUserTitle(MyContext myContext, Timeline timeline, MyAccount currentMyAccount) {
        MyStringBuilder title = new MyStringBuilder();
        if (addUserToTitle(timeline, currentMyAccount)) {
            title.withSpace(
                    timeline.getTimelineType().title(myContext.context(), timeline.getActorInTimeline()));
        } else {
            title.withSpace(timeline.getTimelineType().title(myContext.context()));
        }
        if (timeline.hasSearchQuery()) {
            title.withSpace("'" + timeline.getSearchQuery() + "'");
        }
        if (timeline.isCombined()) {
            title.withSpace(
                myContext.context() == null ? "combined" : myContext.context().getText(R.string.combined_timeline_on));
        }
        return title.toString();
    }

    private static boolean addUserToTitle(Timeline timeline, MyAccount currentMyAccount) {
        if (timeline.isCombined() || timeline.actor.isEmpty()) return false;

        if (timeline.actor.user.isMyUser().untrue) return true;

        if (currentMyAccount.isEmpty() && timeline.myAccountToSync.getActor().notSameUser(timeline.actor)) return true;

        return currentMyAccount.nonEmpty() && currentMyAccount.getActor().notSameUser(timeline.actor);
    }

    private static String toTimelineSubtitle(MyContext myContext, Timeline timeline, @NonNull MyAccount currentMyAccount) {
        final MyStringBuilder title = new MyStringBuilder();
        if (currentMyAccount.nonEmpty()) {
            title.withSpace(currentMyAccount.toAccountButtonText(myContext));
        }
        return title.toString();
    }
}
