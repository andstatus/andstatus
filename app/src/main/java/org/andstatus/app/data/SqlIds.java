/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

import androidx.annotation.NonNull;

import org.andstatus.app.net.social.Actor;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.IsEmpty;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * Helper class to construct sql WHERE clause selecting by Ids
 * @author yvolk@yurivolkov.com
 */
public class SqlIds implements IsEmpty {
    public static final SqlIds EMPTY = new SqlIds();
    private final Set<Long> ids;

    /** We may not have our actor in some origin... ?! */
    public static SqlIds notifiedActorIdsOfTimeline(@NonNull Timeline timeline) {
        if (timeline.isCombined()) {
            return SqlIds.EMPTY;
        } else {
            return actorIdsOfTimelineActor(timeline);
        }
    }

    public static SqlIds actorIdsOfTimelineActor(@NonNull Timeline timeline) {
        if (timeline.isCombined()) {
            return myActorsIds();
        } else if (timeline.getTimelineType().isAtOrigin()) {
            return SqlIds.EMPTY;
        } else {
            return SqlIds.fromIds(timeline.actor.user.actorIds);
        }
    }

    public static SqlIds myActorsIds() {
        return SqlIds.fromIds(myContextHolder.getNow().users().myActors.keySet());
    }

    public static SqlIds actorIdsOfTimelineAccount(@NonNull Timeline timeline) {
        if (timeline.isCombined() || timeline.getTimelineType().isAtOrigin()) {
            return SqlIds.EMPTY;
        }
        return SqlIds.fromIds(timeline.actor.user.actorIds);
    }

    public static SqlIds actorIdsOf(@NonNull Collection<Actor> actors) {
        return new SqlIds(actors.stream().map(actor -> actor.actorId).collect(toList()));
    }

    public static SqlIds fromId(long id) {
        return new SqlIds(Collections.singleton(id));
    }

    public static SqlIds fromIds(@NonNull Collection<Long> ids) {
        return new SqlIds(ids);
    }

    public static SqlIds fromIds(Long ... ids) {
        return new SqlIds(Arrays.asList(ids));
    }

    private SqlIds(@NonNull Collection<Long> ids) {
        this.ids = new HashSet<>(ids);
    }

    private SqlIds(Long ... id) {
        this.ids = new HashSet<>(Arrays.asList(id));
    }

    public int size() {
        return ids.size();
    }

    public String getList() {
        StringBuilder sb = new StringBuilder();
        for (long id : ids) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(Long.toString(id));
        }
        return sb.toString();
    }

    public String getSql() {
        if (size() == 0) {
            return getInexistentId();
        } else if (size() == 1) {
            return "=" + ids.iterator().next();
        } else {
            return " IN (" + getList() + ")";
        }
    }

    public static String getInexistentId() {
        return "=" + Long.MIN_VALUE;
    }

    public String getNotSql() {
        if (size() == 0) {
            return "";
        } else if (size() == 1) {
            return "!=" + ids.iterator().next();
        } else {
            return " NOT IN (" + getList() + ")";
        }
    }

    @Override
    public boolean isEmpty() {
        return ids.isEmpty();
    }
}
