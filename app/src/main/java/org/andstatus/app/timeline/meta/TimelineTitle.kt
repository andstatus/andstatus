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
package org.andstatus.app.timeline.meta

import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder

/**
 * Data to show on UI. May be created on UI thread
 * @author yvolk@yurivolkov.com
 */
class TimelineTitle private constructor(val title: String?, val subTitle: String?, // Optional names 
                                        val accountName: String?, val originName: String?) {
    enum class Destination {
        TIMELINE_ACTIVITY, DEFAULT
    }

    fun updateActivityTitle(activity: MyActivity?, additionalTitleText: String?) {
        activity.setTitle(if (!additionalTitleText.isNullOrEmpty() && subTitle.isNullOrEmpty()) MyStringBuilder.Companion.of(title).withSpace(additionalTitleText) else title)
        activity.setSubtitle(if (subTitle.isNullOrEmpty()) "" else MyStringBuilder.Companion.of(subTitle).withSpace(additionalTitleText))
        MyLog.v(activity) { "Title: " + toString() }
    }

    override fun toString(): String {
        return MyStringBuilder.Companion.of(title).withSpace(subTitle).toString()
    }

    companion object {
        @JvmOverloads
        fun from(myContext: MyContext?, timeline: Timeline?, accountToHide: MyAccount? = MyAccount.EMPTY,
                 namesAreHidden: Boolean = true, destination: Destination? = Destination.DEFAULT): TimelineTitle? {
            return TimelineTitle(
                    calcTitle(myContext, timeline, accountToHide, namesAreHidden, destination),
                    calcSubtitle(myContext, timeline, accountToHide, namesAreHidden, destination),
                    if (timeline.timelineType.isForUser && timeline.myAccountToSync.isValid) timeline.myAccountToSync.toAccountButtonText() else "",
                    if (timeline.timelineType.isAtOrigin && timeline.getOrigin().isValid) timeline.getOrigin().name else ""
            )
        }

        private fun calcTitle(myContext: MyContext?, timeline: Timeline?, accountToHide: MyAccount?,
                              namesAreHidden: Boolean, destination: Destination?): String? {
            if (timeline.isEmpty && destination == Destination.TIMELINE_ACTIVITY) {
                return "AndStatus"
            }
            val title = MyStringBuilder()
            if (showActor(timeline, accountToHide, namesAreHidden)) {
                if (isActorMayBeShownInSubtitle(timeline)) {
                    title.withSpace(timeline.timelineType.title(myContext.context()))
                } else {
                    title.withSpace(
                            timeline.timelineType.title(myContext.context(), getActorName(timeline)))
                }
            } else {
                title.withSpace(timeline.timelineType.title(myContext.context()))
                if (showOrigin(timeline, namesAreHidden)) {
                    title.withSpaceQuoted(timeline.getSearchQuery())
                }
            }
            return title.toString()
        }

        private fun isActorMayBeShownInSubtitle(timeline: Timeline?): Boolean {
            return !timeline.hasSearchQuery() && timeline.timelineType.titleResWithParamsId == 0
        }

        private fun calcSubtitle(myContext: MyContext?, timeline: Timeline?, accountToHide: MyAccount?,
                                 namesAreHidden: Boolean, destination: Destination?): String? {
            if (timeline.isEmpty && destination == Destination.TIMELINE_ACTIVITY) {
                return ""
            }
            val title = MyStringBuilder()
            if (showActor(timeline, accountToHide, namesAreHidden)) {
                if (isActorMayBeShownInSubtitle(timeline)) {
                    title.withSpace(getActorName(timeline))
                }
            } else if (showOrigin(timeline, namesAreHidden)) {
                title.withSpace(timeline.timelineType.scope.timelinePreposition(myContext))
                title.withSpace(timeline.getOrigin().name)
            }
            if (!showOrigin(timeline, namesAreHidden)) {
                title.withSpaceQuoted(timeline.getSearchQuery())
            }
            if (timeline.isCombined()) {
                title.withSpace(if (myContext.context() == null) "combined" else myContext.context().getText(R.string.combined_timeline_on))
            }
            return title.toString()
        }

        private fun getActorName(timeline: Timeline?): String? {
            return if (timeline.isSyncedByOtherUser()) timeline.actor.actorNameInTimeline else timeline.myAccountToSync.toAccountButtonText()
        }

        private fun showActor(timeline: Timeline?, accountToHide: MyAccount?, namesAreHidden: Boolean): Boolean {
            return (timeline.timelineType.isForUser
                    && !timeline.isCombined() && timeline.actor.nonEmpty
                    && timeline.actor.notSameUser(accountToHide.getActor())
                    && (timeline.actor.user.isMyUser.untrue || namesAreHidden))
        }

        private fun showOrigin(timeline: Timeline?, namesAreHidden: Boolean): Boolean {
            return timeline.timelineType.isAtOrigin && !timeline.isCombined() && namesAreHidden
        }
    }
}