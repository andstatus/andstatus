/* 
 * Copyright (c) 2013-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.os.Bundle
import org.andstatus.app.IntentExtra

/** @author yvolk@yurivolkov.com
 */
enum class TriState(val id: Long, val isTrue: Boolean, isFalse: Boolean) {
    TRUE(2, true, false), FALSE(1, false, true), UNKNOWN(3, false, false);

    /** https://philosophy.stackexchange.com/questions/38542/what-is-the-difference-if-any-between-not-true-and-false  */
    val untrue: Boolean
    val isFalse: Boolean
    val notFalse: Boolean
    val known: Boolean
    val unknown: Boolean
    fun getEntriesPosition(): Int {
        return ordinal
    }

    override fun toString(): String {
        return "TriState:" + name
    }

    fun toBoolean(defaultValue: Boolean): Boolean {
        return when (this) {
            FALSE -> false
            TRUE -> true
            else -> defaultValue
        }
    }

    fun toBundle(bundle: Bundle?, key: String?): Bundle? {
        bundle.putLong(key, id)
        return bundle
    }

    fun <T> select(ifTrue: T?, ifFalse: T?, ifUnknown: T?): T? {
        return when (this) {
            TRUE -> ifTrue
            FALSE -> ifFalse
            else -> ifUnknown
        }
    }

    companion object {
        fun fromId(id: Long): TriState? {
            for (tt in values()) {
                if (tt.id == id) {
                    return tt
                }
            }
            return UNKNOWN
        }

        fun fromBundle(bundle: Bundle?, intentExtra: IntentExtra?): TriState? {
            return fromId(BundleUtils.fromBundle(bundle, intentExtra, UNKNOWN.id))
        }

        fun fromEntriesPosition(position: Int): TriState? {
            var obj: TriState? = UNKNOWN
            for (`val` in values()) {
                if (`val`.ordinal == position) {
                    obj = `val`
                    break
                }
            }
            return obj
        }

        fun fromBoolean(booleanToConvert: Boolean): TriState? {
            return if (booleanToConvert) {
                TRUE
            } else {
                FALSE
            }
        }
    }

    init {
        untrue = !isTrue
        this.isFalse = isFalse
        notFalse = !isFalse
        known = isTrue || isFalse
        unknown = !known
    }
}