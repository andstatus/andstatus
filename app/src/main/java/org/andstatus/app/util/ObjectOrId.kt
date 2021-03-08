/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.util

import io.vavr.control.CheckedConsumer
import io.vavr.control.CheckedFunction
import io.vavr.control.Try
import org.andstatus.app.net.http.ConnectionException
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.function.Consumer

class ObjectOrId : IsEmpty {
    val parentObject: Optional<Any>
    val name: String?
    val optObj: Optional<JSONObject>
    val array: Optional<JSONArray>
    val id: Optional<String>
    val error: Optional<ConnectionException>

    private constructor(parentObject: JSONObject, propertyName: String) {
        this.parentObject = Optional.of(parentObject)
        name = propertyName
        optObj = Optional.ofNullable(parentObject.optJSONObject(propertyName))
        val jsonArray = parentObject.optJSONArray(propertyName)
        array = if (optObj.isPresent || jsonArray == null || jsonArray.length() == 0) Optional.empty()
            else Optional.of(jsonArray)
        id = if (optObj.isPresent || jsonArray != null) Optional.empty()
            else StringUtil.optNotEmpty(JsonUtils.optString(parentObject, propertyName))
        error = Optional.empty()
    }

    private constructor(jso: Optional<Any>) {
        parentObject = jso
        name = ""
        optObj = jso.flatMap { js: Any? -> if (js is JSONObject) Optional.of(js) else Optional.empty() }
        val jsonArray = jso.flatMap { js: Any? -> if (js is JSONArray) Optional.of(js) else Optional.empty() }
                .filter { a: JSONArray -> a.length() > 0 }
        array = if (optObj.isPresent || !jsonArray.isPresent) Optional.empty() else jsonArray
        id = if (optObj.isPresent || jsonArray.isPresent) Optional.empty()
            else jso.flatMap { js: Any? -> if (js is String) StringUtil.optNotEmpty(js) else Optional.empty() }
        error = Optional.empty()
    }

    private constructor(ooi: ObjectOrId, e: Exception?) {
        parentObject = ooi.parentObject
        name = ooi.name
        optObj = Optional.empty()
        array = Optional.empty()
        id = Optional.empty()
        error = Optional.of(
                if (e is ConnectionException) e
                else ConnectionException.loggedJsonException(this, "Parsing JSON", e, ooi.optObj.orElse(null))
        )
    }

    override val isEmpty: Boolean
        get() {
            return !optObj.isPresent && !array.isPresent && !id.isPresent
        }

    fun ifId(consumer: CheckedConsumer<String>): ObjectOrId {
        return id.map { id: String? ->
            try {
                consumer.accept(id)
                return@map this
            } catch (t: Exception) {
                return@map ObjectOrId(this, t)
            }
        }.orElse(this)
    }

    fun ifObject(consumer: CheckedConsumer<JSONObject>): ObjectOrId {
        return optObj.map { `object`: JSONObject? ->
            try {
                consumer.accept(`object`)
                return@map this
            } catch (t: Exception) {
                return@map ObjectOrId(this, t)
            }
        }.orElse(this)
    }

    fun ifArray(consumer: CheckedConsumer<JSONArray>): ObjectOrId {
        return array.map { array: JSONArray? ->
            try {
                consumer.accept(array)
                return@map this
            } catch (t: Exception) {
                return@map ObjectOrId(this, t)
            }
        }.orElse(this)
    }

    fun ifError(consumer: Consumer<Exception>): ObjectOrId {
        error.ifPresent { t: ConnectionException -> consumer.accept(t) }
        return this
    }

    fun <T> mapOne(fromObject: CheckedFunction<JSONObject?, T?>, fromId: CheckedFunction<String?, T?>): Try<T> {
        if (optObj.isPresent()) {
            return Try.success(optObj.get()).map(fromObject)
        }
        return if (id.isPresent()) {
            Try.success(id.get()).map(fromId)
        } else Try.failure(NoSuchElementException())
    }

    fun <T> mapAll(fromObject: CheckedFunction<JSONObject, T>, fromId: CheckedFunction<String, T>): List<T> {
        if (optObj.isPresent) {
            return Try.success(optObj.get())
                    .map(fromObject)
                    .map { o: T -> listOf(o) }
                    .getOrElse(emptyList())
        }
        if (array.isPresent) {
            return Try.success(array.get()).map { arrayOfTo: JSONArray ->
                val list: MutableList<T> = ArrayList()
                for (ind in 0 until arrayOfTo.length()) {
                    of(arrayOfTo, ind)
                            .ifObject { o: JSONObject? -> list.add(fromObject.apply(o)) }
                            .ifId { id: String? -> list.add(fromId.apply(id)) }
                }
                list as List<T>
            }.getOrElse(emptyList())
        }
        return if (id.isPresent) {
            Try.success(id.get())
                    .map(fromId)
                    .map { o: T -> listOf(o) }
                    .getOrElse(emptyList())
        } else emptyList()
    }

    fun <T> mapObjects(fromObject: CheckedFunction<JSONObject, T>): List<T> {
        if (optObj.isPresent) {
            return Try.success(optObj.get()).map(fromObject).map { o: T -> listOf(o) }
                    .getOrElse(emptyList())
        }
        return if (array.isPresent) {
            Try.success(array.get()).map { arrayOfTo: JSONArray ->
                val list: MutableList<T> = ArrayList()
                for (ind in 0 until arrayOfTo.length()) {
                    of(arrayOfTo, ind)
                            .ifObject { o: JSONObject -> list.add(fromObject.apply(o)) }
                }
                list as List<T>
            }.getOrElse(emptyList())
        } else emptyList()
    }

    companion object {
        private val EMPTY = of(null)
        fun of(parentObject: JSONObject, propertyName: String): ObjectOrId {
            return ObjectOrId(parentObject, propertyName)
        }

        fun of(parentArray: JSONArray, index: Int): ObjectOrId {
            return ObjectOrId(Optional.ofNullable(parentArray.opt(index)))
        }

        fun of(jso: Any?): ObjectOrId {
            return ObjectOrId(Optional.ofNullable(jso))
        }

        fun empty(): ObjectOrId {
            return EMPTY
        }
    }
}