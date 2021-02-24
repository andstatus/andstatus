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
package org.andstatus.app.net.social

import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import org.andstatus.app.context.MyContext
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyProvider
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.ActorEndpointTable
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.util.UriUtils
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiConsumer
import java.util.function.Function

class ActorEndpoints private constructor(private val myContext: MyContext, private val actorId: Long) {
    enum class State {
        EMPTY, ADDING, LAZYLOAD, LOADED
    }

    private val initialized: AtomicBoolean = AtomicBoolean()
    private val state: AtomicReference<State> = AtomicReference(if (actorId == 0L) State.EMPTY else State.LAZYLOAD)

    @Volatile
    private var map: Map<ActorEndpointType, MutableList<Uri>> = emptyMap()
    fun add(type: ActorEndpointType, value: String?): ActorEndpoints {
        return add(type, UriUtils.fromString(value))
    }

    fun add(type: ActorEndpointType, uri: Uri?): ActorEndpoints {
        if (initialize().state.get() == State.ADDING) {
            add(map, type, uri)
        }
        return this
    }

    fun findFirst(type: ActorEndpointType?): Optional<Uri> {
        return if (type == ActorEndpointType.EMPTY) Optional.empty()
            else initialize().map.getOrDefault(type, emptyList<Uri>()).stream().findFirst()
    }

    fun initialize(): ActorEndpoints {
        while (state.get() == State.EMPTY) {
            if (initialized.compareAndSet(false, true)) {
                map = ConcurrentHashMap()
                state.set(State.ADDING)
            }
        }
        while (state.get() == State.LAZYLOAD && myContext.isReady() && MyAsyncTask.Companion.nonUiThread()) {
            if (initialized.compareAndSet(false, true)) {
                return load()
            }
        }
        return this
    }

    override fun toString(): String {
        return map.toString()
    }

    private fun load(): ActorEndpoints {
        val map: MutableMap<ActorEndpointType, MutableList<Uri>> = ConcurrentHashMap()
        val sql = "SELECT " + ActorEndpointTable.ENDPOINT_TYPE +
                "," + ActorEndpointTable.ENDPOINT_INDEX +
                "," + ActorEndpointTable.ENDPOINT_URI +
                " FROM " + ActorEndpointTable.TABLE_NAME +
                " WHERE " + ActorEndpointTable.ACTOR_ID + "=" + actorId +
                " ORDER BY " + ActorEndpointTable.ENDPOINT_TYPE +
                "," + ActorEndpointTable.ENDPOINT_INDEX
        MyQuery.foldLeft(myContext, sql, map, Function { m: MutableMap<ActorEndpointType, MutableList<Uri>> ->
            Function { cursor: Cursor? ->
                add(m, ActorEndpointType.fromId(DbUtils.getLong(cursor, ActorEndpointTable.ENDPOINT_TYPE)),
                        UriUtils.fromString(DbUtils.getString(cursor, ActorEndpointTable.ENDPOINT_URI)))
            }
        })
        this.map = Collections.unmodifiableMap(map)
        state.set(State.LOADED)
        return this
    }

    fun save(actorId: Long) {
        if (actorId == 0L || !state.compareAndSet(State.ADDING, State.LOADED)) return
        val old = from(myContext, actorId).initialize()
        if (this == old) return
        MyProvider.delete(myContext, ActorEndpointTable.TABLE_NAME, ActorEndpointTable.ACTOR_ID, actorId)
        map.forEach { (key: ActorEndpointType, value: MutableList<Uri>) ->
            var index: Long = 0
            for (uri in value) {
                val contentValues = ContentValues()
                contentValues.put(ActorEndpointTable.ACTOR_ID, actorId)
                contentValues.put(ActorEndpointTable.ENDPOINT_TYPE, key.id)
                contentValues.put(ActorEndpointTable.ENDPOINT_INDEX, index)
                contentValues.put(ActorEndpointTable.ENDPOINT_URI, uri.toString())
                MyProvider.Companion.insert(myContext, ActorEndpointTable.TABLE_NAME, contentValues)
                index++
            }
        }
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val endpoints = o as ActorEndpoints?
        return map == endpoints.map
    }

    override fun hashCode(): Int {
        return Objects.hash(map)
    }

    companion object {
        fun from(myContext: MyContext, actorId: Long): ActorEndpoints {
            return ActorEndpoints(myContext, actorId)
        }

        private fun add(map: MutableMap<ActorEndpointType, MutableList<Uri>>, type: ActorEndpointType,
                        uri: Uri?): MutableMap<ActorEndpointType, List<Uri>> {
            if (UriUtils.isEmpty(uri) || type == ActorEndpointType.EMPTY) return map
            val urisOld = map.get(type)
            if (urisOld == null) {
                map[type] = listOf(uri)
            } else {
                val uris: MutableList<Uri?> = ArrayList(urisOld)
                if (!uris.contains(uri)) {
                    uris.add(uri)
                    map[type] = uris
                }
            }
            return map
        }
    }
}