/*
 * Copyright (c) 2016-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtils;

/**
 * Data to show on UI. May be created on UI thread
 * @author yvolk@yurivolkov.com
 */
public class TimelineTitle {
    public enum Destination {
        TIMELINE_ACTIVITY,
        DEFAULT
    }


    public final String title;
    public final String subTitle;

    // Optional names 
    public final String accountName;
    public final String originName;

    private TimelineTitle(String title, String subtitle, String accountName, String originName) {
        this.title = title;
        this.subTitle = subtitle;
        this.accountName = accountName;
        this.originName = originName;
    }

    public static TimelineTitle from(MyContext myContext, Timeline timeline, MyAccount accountToHide,
                                     boolean namesAreHidden, Destination destination) {
        return new TimelineTitle(
                calcTitle(myContext, timeline, accountToHide, namesAreHidden, destination),
                calcSubtitle(myContext, timeline, accountToHide, namesAreHidden, destination),
                timeline.getTimelineType().isForUser() && timeline.myAccountToSync.isValid()
                        ? timeline.myAccountToSync.toAccountButtonText() : "",
                timeline.getTimelineType().isAtOrigin() && timeline.getOrigin().isValid()
                        ? timeline.getOrigin().getName() : ""
        );
    }

    public static TimelineTitle from(MyContext myContext, Timeline timeline) {
        return from(myContext, timeline, MyAccount.EMPTY, true, Destination.DEFAULT);
    }

    private static String calcTitle(MyContext myContext, Timeline timeline, MyAccount accountToHide,
                                    boolean namesAreHidden, Destination destination) {
        if (timeline.isEmpty() && destination == Destination.TIMELINE_ACTIVITY) {
            return "AndStatus";
        }

        MyStringBuilder title = new MyStringBuilder();
        if (showActor(timeline, accountToHide, namesAreHidden)) {
            if (isActorMayBeShownInSubtitle(timeline)) {
                title.withSpace(timeline.getTimelineType().title(myContext.context()));
            } else {
                title.withSpace(
                        timeline.getTimelineType().title(myContext.context(), getActorName(timeline)));
            }
        } else {
            title.withSpace(timeline.getTimelineType().title(myContext.context()));
            if (showOrigin(timeline, namesAreHidden)) {
                title.withSpaceQuoted(timeline.getSearchQuery());
            }
        }
        return title.toString();
    }

    private static boolean isActorMayBeShownInSubtitle(Timeline timeline) {
        return !timeline.hasSearchQuery() && timeline.getTimelineType().titleResWithParamsId == 0;
    }

    private static String calcSubtitle(MyContext myContext, Timeline timeline, MyAccount accountToHide,
                                       boolean namesAreHidden, Destination destination) {
        if (timeline.isEmpty() && destination == Destination.TIMELINE_ACTIVITY) {
            return "";
        }

        MyStringBuilder title = new MyStringBuilder();
        if (showActor(timeline, accountToHide, namesAreHidden)) {
            if (isActorMayBeShownInSubtitle(timeline)) {
                title.withSpace(getActorName(timeline));
            }
        } else if (showOrigin(timeline, namesAreHidden)) {
            title.withSpace(timeline.getTimelineType().scope.timelinePreposition(myContext));
            title.withSpace(timeline.getOrigin().getName());
        }
        if (!showOrigin(timeline, namesAreHidden)) {
            title.withSpaceQuoted(timeline.getSearchQuery());
        }
        if (timeline.isCombined()) {
            title.withSpace(myContext.context() == null
                    ? "combined"
                    : myContext.context().getText(R.string.combined_timeline_on));
        }
        return title.toString();
    }

    private static String getActorName(Timeline timeline) {
        return timeline.isSyncedByOtherUser()
                ? timeline.actor.getTimelineUsername()
                : timeline.myAccountToSync.toAccountButtonText();
    }

    private static boolean showActor(Timeline timeline, MyAccount accountToHide, boolean namesAreHidden) {
        return timeline.getTimelineType().isForUser()
                && !timeline.isCombined() && timeline.actor.nonEmpty()
                && timeline.actor.notSameUser(accountToHide.getActor())
                && (timeline.actor.user.isMyUser().untrue || namesAreHidden);
    }

    private static boolean showOrigin(Timeline timeline, boolean namesAreHidden) {
        return timeline.getTimelineType().isAtOrigin() && !timeline.isCombined() && namesAreHidden;
    }

    public void updateActivityTitle(MyActivity activity, String additionalTitleText) {
        activity.setTitle(StringUtils.nonEmpty(additionalTitleText) && StringUtils.isEmpty(subTitle)
                ? MyStringBuilder.of(title).withSpace(additionalTitleText)
                : title);
        activity.setSubtitle(StringUtils.isEmpty(subTitle)
                ? ""
                : MyStringBuilder.of(subTitle).withSpace(additionalTitleText));
        MyLog.v(activity, () -> "Title: " + toString());
    }

    @Override
    public String toString() {
        return MyStringBuilder.of(title).withSpace(subTitle).toString();
    }
}
