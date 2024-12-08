/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import io.vavr.control.Try
import java.util.*
import java.util.function.BiFunction
import java.util.function.Predicate
import java.util.stream.Collectors

/**
 * @author yvolk@yurivolkov.com
 * See also http://www.javacreed.com/sorting-a-copyonwritearraylist/
 */
object CollectionsUtil {
    fun <T : Comparable<T>> sort(list: MutableList<T>) {
        val sortableList: MutableList<T> = ArrayList(list)
        sortableList.sort()
        list.clear()
        list.addAll(sortableList)
    }

    fun compareCheckbox(lhs: Boolean, rhs: Boolean): Int {
        val result = if (lhs == rhs) 0 else if (lhs) 1 else -1
        return 0 - result
    }

    /** Helper for [java.util.Map.compute] where the map value is an immutable [Set].  */
    fun <T> addValue(toAdd: T): BiFunction<T, MutableSet<T>?, MutableSet<T>?> {
        return BiFunction { key: T, valuesNullable: MutableSet<T>? ->
            if (valuesNullable != null && valuesNullable.contains(toAdd)) {
                valuesNullable
            } else {
                val values: MutableSet<T> = HashSet()
                if (valuesNullable != null) values.addAll(valuesNullable)
                values.add(toAdd)
                values
            }
        }
    }

    /** Helper for [java.util.Map.compute] where the map value is an immutable [Set].  */
    fun <T> removeValue(toRemove: T): BiFunction<T, MutableSet<T>?, MutableSet<T>?> {
        return BiFunction { key: T, valuesNullable: MutableSet<T>? ->
            if (valuesNullable != null && valuesNullable.contains(toRemove))
                valuesNullable.stream()
                        .filter { key2: T? -> key2 !== toRemove }
                        .collect(Collectors.toSet()) else valuesNullable
        }
    }

    fun <T> findAny(collection: Collection<T>, predicate: Predicate<T>): Try<T> {
        for (item in collection) {
            if (predicate.test(item)) {
                return Try.success(item)
            }
        }
        return TryUtils.notFound()
    }
}

fun List<String>?.toCsv(): String? = this?.fold(null) { acc, item ->
    (acc?.let<String, String> { "$it," } ?: "") + item
}
