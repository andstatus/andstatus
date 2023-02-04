/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.actor

import android.view.View
import android.widget.TextView
import org.andstatus.app.R
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Audience
import org.andstatus.app.net.social.SpanUtil
import org.andstatus.app.timeline.LoadableListActivity
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.MyUrlSpan
import java.util.stream.Collectors

class ActorViewItemPopulator(myActivity: LoadableListActivity<*>, isCombined: Boolean,
                             showAvatars: Boolean) {
    private val myActivity: LoadableListActivity<*>
    private val isCombined: Boolean
    private val showAvatars: Boolean
    fun populateView(view: View, item: ActorViewItem, position: Int) {
        MyUrlSpan.showText(view, R.id.realname, item.actor.getRealName(), false, false)
        MyUrlSpan.showText(view, R.id.username, item.actor.uniqueName, false, false)
        if (showAvatars) {
            showAvatar(item, view)
        }
        MyUrlSpan.showText(view, R.id.homepage, item.actor.getHomepage(), true, false)
        MyUrlSpan.showSpannable(view.findViewById<TextView?>(R.id.description),
                SpanUtil.textToSpannable(item.getDescription(), TextMediaType.UNKNOWN, Audience.EMPTY), false)
        MyUrlSpan.showText(view, R.id.location, item.actor.location, false, false)
        MyUrlSpan.showText(view, R.id.profile_url, item.actor.getProfileUrl(), true, false)
        showCounter(view, R.id.msg_count, item.actor.notesCount)
        showCounter(view, R.id.favorites_count, item.actor.favoritesCount)
        showCounter(view, R.id.following_count, item.actor.followingCount)
        showCounter(view, R.id.followers_count, item.actor.followersCount)
        MyUrlSpan.showText(view, R.id.location, item.actor.location, false, false)
        showMyActorsFollowingTheActor(view, item)
        showMyActorsFollowedByTheActor(view, item)
    }

    private fun showAvatar(item: ActorViewItem, view: View) {
        item.showAvatar(myActivity, view.findViewById(R.id.avatar_image))
    }

    private fun showMyActorsFollowingTheActor(view: View, item: ActorViewItem) {
        val builder: MyStringBuilder = MyStringBuilder.of(
                item.getMyActorsFollowingTheActor(myActivity.myContext)
                        .map { actor: Actor -> myActivity.myContext.accounts.fromActorOfAnyOrigin(actor).accountName }
                        .collect(Collectors.joining(", ")))
        if (builder.nonEmpty) {
            builder.prependWithSeparator(myActivity.getText(R.string.followed_by), " ")
        }
        MyUrlSpan.showText(view, R.id.followed_by, builder.toString(), false, false)
    }

    private fun showMyActorsFollowedByTheActor(view: View, item: ActorViewItem) {
        val builder: MyStringBuilder = MyStringBuilder.of(
                item.getMyActorsFollowedByTheActor(myActivity.myContext)
                        .map { actor: Actor -> myActivity.myContext.accounts.fromActorOfAnyOrigin(actor).accountName }
                        .collect(Collectors.joining(", ")))
        if (builder.nonEmpty) {
            builder.prependWithSeparator(myActivity.getText(R.string.follows), " ")
        }
        MyUrlSpan.showText(view, R.id.follows, builder.toString(), false, false)
    }

    companion object {
        private fun showCounter(parentView: View, viewId: Int, counter: Long) {
            MyUrlSpan.showText(parentView, viewId, if (counter <= 0) "-" else counter.toString(), false, false)
        }
    }

    init {
        this.myActivity = myActivity
        this.isCombined = isCombined
        this.showAvatars = showAvatars
    }
}
