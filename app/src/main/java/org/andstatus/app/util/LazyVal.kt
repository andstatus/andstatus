/*
 * Copyright (C) 2018-2021 yvolk (Yuri Volkov), http://yurivolkov.com
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

/** Lazy holder of a non Null / or Nullable value
 * Blocks on parallel evaluation
 * Inspired by https://www.sitepoint.com/lazy-computations-in-java-with-a-lazy-type/
 * and https://dzone.com/articles/be-lazy-with-java-8  */
class LazyVal<T> private constructor(private val supplier: () -> T?, private val isNullable: Boolean) {
    @Volatile
    private var value: T? = null

    @Volatile
    private var isEvaluated = false

    fun getNullable(): T? {
        val storedValue = value
        return if (isEvaluated) storedValue else evaluate()
    }

    fun get(): T {
        val storedValue = value
        return (if (isEvaluated) storedValue else evaluate()) ?: throw IllegalArgumentException("Evaluated to null")
    }

    fun isEvaluated(): Boolean {
        return isEvaluated
    }

    @Synchronized
    private fun evaluate(): T? {
        value?.let { return it }
        val evaluatedValue: T? = supplier()
        value = evaluatedValue
        isEvaluated = isNullable || value != null
        return evaluatedValue
    }

    fun reset() {
        value = null
    }

    companion object {
        fun <T> of(supplier: () -> T): LazyVal<T> {
            return LazyVal(supplier, false)
        }

        fun <T> of(value: T): LazyVal<T> {
            val lazyVal = LazyVal<T>( { -> value}, value == null)
            lazyVal.value = value
            lazyVal.isEvaluated = true
            return lazyVal
        }

        fun <T> ofNullable(supplier: () -> T?): LazyVal<T?> {
            return LazyVal(supplier, true)
        }
    }
}