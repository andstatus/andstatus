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

package org.andstatus.app.net.social;

import android.content.ContentValues;
import android.net.Uri;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActorEndpointTable;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.UriUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ActorEndpoints {
    public enum State {
        EMPTY,
        ADDING,
        LAZYLOAD,
        LOADED
    }
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final MyContext myContext;
    private final long actorId;
    private final AtomicReference<State> state;
    private volatile Map<ActorEndpointType, List<Uri>> map = Collections.emptyMap();

    public static ActorEndpoints from(MyContext myContext, long actorId) {
        return new ActorEndpoints(myContext, actorId);
    }

    private ActorEndpoints(MyContext myContext, long actorId) {
        this.myContext = myContext;
        this.actorId = actorId;
        this.state = new AtomicReference<>(actorId == 0 ? State.EMPTY : State.LAZYLOAD);
    }

    public ActorEndpoints add(ActorEndpointType type, String value) {
        return add(type, UriUtils.fromString(value));
    }

    public ActorEndpoints add(ActorEndpointType type, Uri uri) {
        if (initialize().state.get() == State.ADDING) {
            add(map, type, uri);
        }
        return this;
    }

    private static Map<ActorEndpointType, List<Uri>> add(Map<ActorEndpointType, List<Uri>> map, ActorEndpointType type,
                                                         Uri uri) {
        if (UriUtils.isEmpty(uri) || type == ActorEndpointType.EMPTY) return map;

        final List<Uri> urisOld = map.get(type);
        if (urisOld == null) {
            map.put(type, Collections.singletonList(uri));
        } else {
            List<Uri> uris = new ArrayList<>(urisOld);
            if (!uris.contains(uri)) {
                uris.add(uri);
                map.put(type,  uris);
            }
        }
        return map;
    }

    public Optional<Uri> findFirst(ActorEndpointType type) {
        return type == ActorEndpointType.EMPTY
            ? Optional.empty()
            : initialize().map.getOrDefault(type, Collections.emptyList()).stream().findFirst();
    }

    public ActorEndpoints initialize() {
        while (state.get() == State.EMPTY) {
            if (initialized.compareAndSet(false, true)) {
                map = new ConcurrentHashMap<>();
                state.set(State.ADDING);
            }
        }
        while (state.get() == State.LAZYLOAD && myContext.isReady() && MyAsyncTask.nonUiThread()) {
            if (initialized.compareAndSet(false, true)) {
                return load();
            }
        }
        return this;
    }

    @Override
    public String toString() {
        return map.toString();
    }

    private ActorEndpoints load() {
        Map<ActorEndpointType, List<Uri>> map = new ConcurrentHashMap<>();
        String sql = "SELECT " + ActorEndpointTable.ENDPOINT_TYPE +
                "," + ActorEndpointTable.ENDPOINT_INDEX +
                "," + ActorEndpointTable.ENDPOINT_URI +
                " FROM " + ActorEndpointTable.TABLE_NAME +
                " WHERE " + ActorEndpointTable.ACTOR_ID + "=" + actorId +
                " ORDER BY " + ActorEndpointTable.ENDPOINT_TYPE +
                "," + ActorEndpointTable.ENDPOINT_INDEX;
        MyQuery.foldLeft(myContext, sql, map, m -> cursor ->
            add(m, ActorEndpointType.fromId(DbUtils.getLong(cursor, ActorEndpointTable.ENDPOINT_TYPE)),
                    UriUtils.fromString(DbUtils.getString(cursor, ActorEndpointTable.ENDPOINT_URI))));
        this.map = Collections.unmodifiableMap(map);
        state.set(State.LOADED);
        return this;
    }

    public void save(long actorId) {
        if (actorId == 0 || !state.compareAndSet(State.ADDING, State.LOADED)) return;

        ActorEndpoints old = ActorEndpoints.from(myContext, actorId).initialize();
        if (this.equals(old)) return;

        MyProvider.delete(myContext, ActorEndpointTable.TABLE_NAME, ActorEndpointTable.ACTOR_ID, actorId);
        map.forEach((key, value) -> {
            long index = 0;
            for (Uri uri : value) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(ActorEndpointTable.ACTOR_ID, actorId);
                contentValues.put(ActorEndpointTable.ENDPOINT_TYPE, key.id);
                contentValues.put(ActorEndpointTable.ENDPOINT_INDEX, index);
                contentValues.put(ActorEndpointTable.ENDPOINT_URI, uri.toString());
                MyProvider.insert(myContext, ActorEndpointTable.TABLE_NAME, contentValues);
                index++;
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActorEndpoints endpoints = (ActorEndpoints) o;
        return Objects.equals(map, endpoints.map);
    }

    @Override
    public int hashCode() {
        return Objects.hash(map);
    }
}