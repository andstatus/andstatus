/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.actor;

import android.database.Cursor;

import org.andstatus.app.MyActivity;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.graphics.IdentifiableImageView;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.timeline.DuplicationLink;
import org.andstatus.app.timeline.TimelineFilter;
import org.andstatus.app.timeline.ViewItem;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.NullUtil;
import org.andstatus.app.util.StringUtils;

import java.util.Collections;
import java.util.stream.Stream;

import androidx.annotation.NonNull;

import static org.andstatus.app.timeline.DuplicationLink.DUPLICATES;
import static org.andstatus.app.timeline.DuplicationLink.IS_DUPLICATED;
import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;

public class ActorViewItem extends ViewItem<ActorViewItem> implements Comparable<ActorViewItem> {
    public static final ActorViewItem EMPTY = new ActorViewItem(Actor.EMPTY, true);
    boolean populated = false;
    @NonNull
    final Actor actor;
    private Actor myFollowingActorToHide = Actor.EMPTY;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ActorViewItem that = (ActorViewItem) o;
        return actor.equals(that.actor);
    }

    @Override
    public int hashCode() {
        return actor.hashCode();
    }

    private ActorViewItem(@NonNull Actor actor, boolean isEmpty) {
        super(isEmpty, DATETIME_MILLIS_NEVER);
        this.actor = actor;
    }

    public static ActorViewItem newEmpty(String description) {
        Actor actor = StringUtils.isEmpty(description) ? Actor.EMPTY :
                Actor.newUnknown(Origin.EMPTY).setSummary(description);
        return fromActor(actor);
    }

    public static ActorViewItem fromActorId(Origin origin, long actorId) {
        return actorId == 0 ? ActorViewItem.EMPTY : fromActor(Actor.fromId(origin, actorId));
    }

    public static ActorViewItem fromActor(@NonNull Actor actor) {
        return actor.isEmpty() ? ActorViewItem.EMPTY : new ActorViewItem(actor, false);
    }

    @NonNull
    public Actor getActor() {
        return actor;
    }

    public long getActorId() {
        return actor.actorId;
    }

    public String getName() {
        if (MyPreferences.getShowOrigin() && actor.nonEmpty()) {
            String name = actor.getTimelineUsername() + " / " + actor.origin.getName();
            if (actor.origin.getOriginType() == OriginType.GNUSOCIAL && MyPreferences.isShowDebuggingInfoInUi()
                    && StringUtils.nonEmpty(actor.oid)) {
                return name + " oid:" + actor.oid;
            } else return name;
        } else return actor.getTimelineUsername();
    }

    public String getDescription() {
        StringBuilder builder = new StringBuilder(actor.getSummary());
        if (MyPreferences.isShowDebuggingInfoInUi()) {
            MyStringBuilder.appendWithSpace(builder, "(id=" + getActor().actorId + ")");
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return "ActorViewItem{" +
                actor +
                '}';
    }

    public boolean isEmpty() {
        return actor.isEmpty();
    }

    @Override
    public long getId() {
        return getActor().actorId;
    }

    @Override
    public long getDate() {
        return actor.getUpdatedDate();
    }

    public String getUniqueName() {
        return actor.getUniqueName();
    }

    @Override
    public int compareTo(@NonNull ActorViewItem o) {
        return getUniqueName().compareTo(o.getUniqueName());
    }

    public AvatarFile getAvatarFile() {
        return actor.avatarFile;
    }

    public void showAvatar(MyActivity myActivity, IdentifiableImageView imageView) {
        getAvatarFile().showImage(myActivity, imageView);
    }

    @Override
    @NonNull
    public ActorViewItem fromCursor(MyContext myContext, @NonNull Cursor cursor) {
        Actor actor = Actor.fromCursor(myContext, cursor);
        ActorViewItem item = new ActorViewItem(actor, false);
        item.populated = true;
        return item;
    }

    @Override
    public boolean matches(TimelineFilter filter) {
        // TODO: implement filtering
        return super.matches(filter);
    }

    @NonNull
    @Override
    public DuplicationLink duplicates(Timeline timeline, Origin preferredOrigin, @NonNull ActorViewItem other) {
        if (isEmpty() || other.isEmpty()) return DuplicationLink.NONE;
        if (preferredOrigin.nonEmpty() && !actor.origin.equals(other.actor.origin)) {
            if (preferredOrigin.equals(actor.origin)) return IS_DUPLICATED;
            if (preferredOrigin.equals(other.actor.origin)) return DUPLICATES;
        }
        return super.duplicates(timeline, preferredOrigin, other);
    }

    public void hideTheFollower(Actor actor) {
        myFollowingActorToHide = actor;
    }

    public Stream<Actor> getMyActorsFollowingTheActor(MyContext myContext) {
        return NullUtil.getOrDefault(myContext.users().friendsOfMyActors, actor.actorId, Collections.emptySet()).stream()
                .filter(id -> id != myFollowingActorToHide.actorId)
                .map(id -> NullUtil.getOrDefault(myContext.users().actors, id, Actor.EMPTY))
                .filter(Actor::nonEmpty);
    }

}
