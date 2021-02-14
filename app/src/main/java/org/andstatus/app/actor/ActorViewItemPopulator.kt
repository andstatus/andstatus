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

package org.andstatus.app.actor;

import android.view.View;

import androidx.annotation.NonNull;

import org.andstatus.app.R;
import org.andstatus.app.data.TextMediaType;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.SpanUtil;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.MyUrlSpan;

import java.util.stream.Collectors;

public class ActorViewItemPopulator {
    private final LoadableListActivity myActivity;
    private final boolean isCombined;
    private final boolean showAvatars;

    public ActorViewItemPopulator(@NonNull LoadableListActivity myActivity, boolean isCombined,
                                  boolean showAvatars) {
        this.myActivity = myActivity;
        this.isCombined = isCombined;
        this.showAvatars = showAvatars;
    }

    public void populateView(View view, ActorViewItem item, int position) {
        MyUrlSpan.showText(view, R.id.realname, item.actor.getRealName(), false, false);
        MyUrlSpan.showText(view, R.id.username, item.actor.getUniqueName(), false, false);
        if (showAvatars) {
            showAvatar(item, view);
        }
        MyUrlSpan.showText(view, R.id.homepage, item.actor.getHomepage(), true, false);
        MyUrlSpan.showSpannable(view.findViewById(R.id.description),
                SpanUtil.textToSpannable(item.getDescription(), TextMediaType.UNKNOWN, Audience.EMPTY), false);
        MyUrlSpan.showText(view, R.id.location, item.actor.location, false, false);
        MyUrlSpan.showText(view, R.id.profile_url, item.actor.getProfileUrl(), true, false);

        showCounter(view, R.id.msg_count, item.actor.notesCount);
        showCounter(view, R.id.favorites_count, item.actor.favoritesCount);
        showCounter(view, R.id.following_count, item.actor.followingCount);
        showCounter(view, R.id.followers_count, item.actor.followersCount);

        MyUrlSpan.showText(view, R.id.location, item.actor.location, false, false);
        showMyActorsFollowingTheActor(view, item);
        showMyActorsFollowedByTheActor(view, item);
    }

    private static void showCounter(View parentView, int viewId, long counter) {
        MyUrlSpan.showText(parentView, viewId, counter <= 0 ? "-" : String.valueOf(counter) , false, false);
    }

    private void showAvatar(ActorViewItem item, View view) {
        item.showAvatar(myActivity, view.findViewById(R.id.avatar_image));
    }

    private void showMyActorsFollowingTheActor(View view, ActorViewItem item) {
        MyStringBuilder builder = MyStringBuilder.of(
            item.getMyActorsFollowingTheActor(myActivity.getMyContext())
                .map(actor -> myActivity.getMyContext().accounts().fromActorOfAnyOrigin(actor).getAccountName())
                .collect(Collectors.joining(", ")));
        if (builder.nonEmpty()) {
            builder.prependWithSeparator(myActivity.getText(R.string.followed_by), " ");
        }
        MyUrlSpan.showText(view, R.id.followed_by, builder.toString(), false, false);
    }

    private void showMyActorsFollowedByTheActor(View view, ActorViewItem item) {
        MyStringBuilder builder = MyStringBuilder.of(
                item.getMyActorsFollowedByTheActor(myActivity.getMyContext())
                        .map(actor -> myActivity.getMyContext().accounts().fromActorOfAnyOrigin(actor).getAccountName())
                        .collect(Collectors.joining(", ")));
        if (builder.nonEmpty()) {
            builder.prependWithSeparator(myActivity.getText(R.string.follows), " ");
        }
        MyUrlSpan.showText(view, R.id.follows, builder.toString(), false, false);
    }
}
