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
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.MyActivity;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.graphics.AvatarView;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.TimelineFilter;
import org.andstatus.app.timeline.ViewItem;
import org.andstatus.app.util.I18n;

import java.util.HashSet;
import java.util.Set;

public class ActorViewItem extends ViewItem<ActorViewItem> implements Comparable<ActorViewItem> {
    public static final ActorViewItem EMPTY = new ActorViewItem(Actor.EMPTY, true);
    boolean populated = false;
    @NonNull
    final Actor actor;
    private AvatarFile avatarFile = null;
    Set<Long> myFollowers = new HashSet<>();

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
        super(isEmpty);
        this.actor = actor;
    }

    public static ActorViewItem newEmpty(String description) {
        Actor actor = TextUtils.isEmpty(description) ? Actor.EMPTY :
                Actor.fromOriginAndActorId(Origin.EMPTY, 0L).setDescription(description);
        return fromActor(actor);
    }

    public static ActorViewItem fromActorId(Origin origin, long actorId) {
        Actor actor = Actor.EMPTY;
        if (actorId != 0) {
            actor = Actor.fromOriginAndActorId(origin, actorId);
        }
        return fromActor(actor);
    }

    public static ActorViewItem fromActor(@NonNull Actor actor) {
        return new ActorViewItem(actor, false);
    }

    public long getActorId() {
        return actor.actorId;
    }

    public String getDescription() {
        StringBuilder builder = new StringBuilder(actor.getDescription());
        if (MyPreferences.isShowDebuggingInfoInUi()) {
            I18n.appendWithSpace(builder, "(id=" + getActorId() + ")");
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

    public boolean actorIsFollowedBy(MyAccount ma) {
        return myFollowers.contains(ma.getActorId());
    }

    @Override
    public long getId() {
        return getActorId();
    }

    @Override
    public long getDate() {
        return actor.getUpdatedDate();
    }

    @NonNull
    @Override
    public ActorViewItem getNew() {
        return newEmpty("");
    }

    public String getWebFingerIdOrUsername() {
        return actor.getNamePreferablyWebFingerId();
    }

    @Override
    public int compareTo(@NonNull ActorViewItem o) {
        return getWebFingerIdOrUsername().compareTo(o.getWebFingerIdOrUsername());
    }

    public void setAvatarFile(AvatarFile avatarFile) {
        this.avatarFile = avatarFile;
    }

    public void showAvatar(MyActivity myActivity, AvatarView imageView) {
        if (avatarFile != null) {
            avatarFile.showImage(myActivity, imageView);
        }
    }

    @Override
    @NonNull
    public ActorViewItem fromCursor(@NonNull Cursor cursor) {
        Actor actor = Actor.fromOriginAndActorOid(
                MyContextHolder.get().persistentOrigins().fromId(DbUtils.getLong(cursor, ActorTable.ORIGIN_ID)),
                DbUtils.getString(cursor, ActorTable.ACTOR_OID)
        );
        actor.actorId = DbUtils.getLong(cursor, BaseColumns._ID);
        actor.setUsername(DbUtils.getString(cursor, ActorTable.USERNAME));
        actor.setWebFingerId(DbUtils.getString(cursor, ActorTable.WEBFINGER_ID));
        actor.setRealName(DbUtils.getString(cursor, ActorTable.REAL_NAME));
        actor.setDescription(DbUtils.getString(cursor, ActorTable.DESCRIPTION));
        actor.location = DbUtils.getString(cursor, ActorTable.LOCATION);

        actor.setProfileUrl(DbUtils.getString(cursor, ActorTable.PROFILE_URL));
        actor.setHomepage(DbUtils.getString(cursor, ActorTable.HOMEPAGE));

        actor.notesCount = DbUtils.getLong(cursor, ActorTable.MSG_COUNT);
        actor.favoritesCount = DbUtils.getLong(cursor, ActorTable.FAVORITES_COUNT);
        actor.followingCount = DbUtils.getLong(cursor, ActorTable.FOLLOWING_COUNT);
        actor.followersCount = DbUtils.getLong(cursor, ActorTable.FOLLOWERS_COUNT);

        actor.setCreatedDate(DbUtils.getLong(cursor, ActorTable.CREATED_DATE));
        actor.setUpdatedDate(DbUtils.getLong(cursor, ActorTable.UPDATED_DATE));

        ActorViewItem item = new ActorViewItem(actor, false);

        item.myFollowers = MyQuery.getMyFollowersOf(actor.actorId);
        AvatarFile avatarFile = AvatarFile.fromCursor(actor.actorId, cursor, DownloadTable.AVATAR_FILE_NAME);
        item.setAvatarFile(avatarFile);
        item.populated = true;
        return item;
    }

    @Override
    public boolean matches(TimelineFilter filter) {
        // TODO: implement filtering
        return super.matches(filter);
    }

    public void hideActor(long actorId) {
        myFollowers.remove(actorId);
    }
}
