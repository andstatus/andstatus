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
package org.andstatus.app.net.social

import io.vavr.control.CheckedFunction
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.ObjectOrId
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.stream.Collectors

/** https://www.w3.org/TR/activitystreams-core/#collections  */
class AJsonCollection private constructor(parentObjectIn: JSONObject?, propertyName: String?) : IsEmpty {
    /** https://www.w3.org/TR/activitystreams-core/#dfn-collectionpage  */
    enum class Type {
        EMPTY, COLLECTION, ORDERED_COLLECTION, PAGED_COLLECTION, PAGED_ORDERED_COLLECTION, PAGE, ORDERED_PAGE
    }

    val objectOrId: ObjectOrId?
    val id: Optional<String>
    val type: Type?
    val items: ObjectOrId?
    val firstPage: AJsonCollection?
    val prevPage: AJsonCollection?
    val currentPage: AJsonCollection?
    val nextPage: AJsonCollection?
    val lastPage: AJsonCollection?
    override val isEmpty: Boolean
        get() {
            return type == Type.EMPTY
        }

    fun <T : IsEmpty?> mapAll(fromObject: CheckedFunction<JSONObject?, T?>?, fromId: CheckedFunction<String?, T?>?): MutableList<T?>? {
        if (isEmpty) return emptyList()
        val list: MutableList<T?> = ArrayList()
        list.addAll(items.mapAll(fromObject, fromId))
        list.addAll(firstPage.mapAll<T?>(fromObject, fromId))
        list.addAll(prevPage.mapAll<T?>(fromObject, fromId))
        list.addAll(currentPage.mapAll<T?>(fromObject, fromId))
        list.addAll(nextPage.mapAll<T?>(fromObject, fromId))
        list.addAll(lastPage.mapAll<T?>(fromObject, fromId))
        return list.stream().filter { obj: T? -> obj.nonEmpty }.collect(Collectors.toList())
    }

    fun <T : IsEmpty?> mapObjects(fromObject: CheckedFunction<JSONObject?, T?>?): MutableList<T?>? {
        if (isEmpty) return emptyList()
        val list: MutableList<T?> = ArrayList()
        list.addAll(items.mapObjects(fromObject))
        list.addAll(firstPage.mapObjects<T?>(fromObject))
        list.addAll(prevPage.mapObjects<T?>(fromObject))
        list.addAll(currentPage.mapObjects<T?>(fromObject))
        list.addAll(nextPage.mapObjects<T?>(fromObject))
        list.addAll(lastPage.mapObjects<T?>(fromObject))
        return list.stream().filter { obj: T? -> obj.nonEmpty }.collect(Collectors.toList())
    }

    fun getId(): String? {
        return id.orElse("")
    }

    fun getPrevId(): String? {
        if (prevPage.id.isPresent()) return prevPage.id.get()
        if (firstPage.prevPage.id.isPresent()) return firstPage.prevPage.id.get()
        if (nextPage.id.isPresent()) return nextPage.id.get()
        return if (firstPage.id.isPresent()) firstPage.id.get() else getId()
    }

    fun getNextId(): String? {
        if (nextPage.id.isPresent()) return nextPage.id.get()
        if (firstPage.nextPage.id.isPresent()) return firstPage.nextPage.id.get()
        if (prevPage.id.isPresent()) return prevPage.id.get()
        return if (firstPage.id.isPresent()) firstPage.id.get() else getId()
    }

    override fun toString(): String {
        return objectOrId.name + ":" + objectOrId.parentObject.map { obj: Any? -> obj.toString() }.orElse("(empty)")
    }

    companion object {
        val EMPTY = of("")
        fun empty(): AJsonCollection? {
            return EMPTY
        }

        fun of(strRoot: String?): AJsonCollection? {
            val parentObject: JSONObject
            parentObject = try {
                if (strRoot.isNullOrEmpty()) {
                    JSONObject()
                } else {
                    JSONObject(strRoot)
                }
            } catch (e: JSONException) {
                JSONObject()
            }
            return of(parentObject)
        }

        @JvmOverloads
        fun of(parentObject: JSONObject?, propertyName: String? = ""): AJsonCollection? {
            return AJsonCollection(parentObject, propertyName)
        }

        private fun calcType(jso: JSONObject): Type? {
            return when (JsonUtils.optString(jso, "type")) {
                "Collection" -> if (jso.has("items")) Type.COLLECTION else Type.PAGED_COLLECTION
                "OrderedCollection" -> if (jso.has("orderedItems")) Type.ORDERED_COLLECTION else Type.PAGED_ORDERED_COLLECTION
                "CollectionPage" -> if (jso.has("items")) Type.PAGE else Type.EMPTY
                "OrderedCollectionPage" -> if (jso.has("orderedItems")) Type.ORDERED_PAGE else Type.EMPTY
                else -> Type.EMPTY
            }
        }

        private fun calcItems(jso: JSONObject, type: Type?): ObjectOrId? {
            return when (type) {
                Type.COLLECTION, Type.PAGE -> ObjectOrId.Companion.of(jso, "items")
                Type.ORDERED_COLLECTION, Type.ORDERED_PAGE -> ObjectOrId.Companion.of(jso, "orderedItems")
                else -> ObjectOrId.Companion.empty()
            }
        }
    }

    init {
        objectOrId = if (propertyName.isNullOrEmpty()) ObjectOrId.Companion.of(parentObjectIn) else ObjectOrId.Companion.of(parentObjectIn, propertyName)
        val parent = objectOrId.optObj
        id = if (objectOrId.id.isPresent) objectOrId.id else parent.flatMap { p: JSONObject? -> ObjectOrId.Companion.of(p, "id").id }
        type = parent.map { jso: JSONObject? -> calcType(jso) }.orElse(Type.EMPTY)
        items = parent.map { p: JSONObject? -> calcItems(p, type) }.orElse(ObjectOrId.Companion.empty())
        firstPage = parent.map { p: JSONObject? -> of(p, "first") }.orElse(empty())
        prevPage = parent.map { p: JSONObject? -> of(p, "prev") }.orElse(empty())
        currentPage = parent.map { p: JSONObject? -> of(p, "current") }.orElse(empty())
        nextPage = parent.map { p: JSONObject? -> of(p, "next") }.orElse(empty())
        lastPage = parent.map { p: JSONObject? -> of(p, "last") }.orElse(empty())
    }
}