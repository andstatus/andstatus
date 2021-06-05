/**
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
package org.andstatus.app.util

import android.database.Cursor
import org.json.JSONObject

class TypedCursorValue {
    private val type: CursorFieldType
    val value: Any?

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as TypedCursorValue
        if (type !== CursorFieldType.UNKNOWN && that.type !== CursorFieldType.UNKNOWN) {
            if (type !== that.type) return false
        }
        return !if (value != null) value.toString() != that.value.toString() else that.value != null
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + (value?.toString()?.hashCode() ?: 0)
        return result
    }

    private enum class CursorFieldType(val code: Int) {
        UNKNOWN(-1), STRING(Cursor.FIELD_TYPE_STRING) {
            override fun columnToObject(cursor: Cursor, columnIndex: Int): Any? {
                return cursor.getString(columnIndex)
            }
        },
        INTEGER(Cursor.FIELD_TYPE_INTEGER) {
            override fun columnToObject(cursor: Cursor, columnIndex: Int): Any {
                return cursor.getLong(columnIndex)
            }
        },
        BLOB(Cursor.FIELD_TYPE_BLOB) {
            override fun columnToObject(cursor: Cursor, columnIndex: Int): Any? {
                return cursor.getBlob(columnIndex)
            }
        },
        FLOAT(Cursor.FIELD_TYPE_FLOAT) {
            override fun columnToObject(cursor: Cursor, columnIndex: Int): Any {
                return cursor.getDouble(columnIndex)
            }
        },
        NULL(Cursor.FIELD_TYPE_NULL);

        open fun columnToObject(cursor: Cursor, columnIndex: Int): Any? {
            return null
        }

        companion object {
            fun fromColumnType(cursorColumnType: Int): CursorFieldType {
                for (`val` in values()) {
                    if (`val`.code == cursorColumnType) {
                        return `val`
                    }
                }
                return UNKNOWN
            }
        }
    }

    constructor(cursor: Cursor, columnIndex: Int) {
        type = CursorFieldType.fromColumnType(cursor.getType(columnIndex))
        value = type.columnToObject(cursor, columnIndex)
    }

    constructor(any: Any?) : this(CursorFieldType.UNKNOWN, any) {}
    private constructor(type: CursorFieldType, any: Any?) {
        this.type = type
        value = any
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put(KEY_TYPE, type.code)
        json.put(KEY_VALUE, value)
        return json
    }

    companion object {
        private val KEY_TYPE: String = "type"
        private val KEY_VALUE: String = "value"
        fun fromJson(json: JSONObject): TypedCursorValue {
            var type: CursorFieldType = CursorFieldType.UNKNOWN
            if (json.has(KEY_TYPE)) {
                type = CursorFieldType.fromColumnType(json.optInt(KEY_TYPE))
            }
            return TypedCursorValue(type, json.opt(KEY_VALUE))
        }
    }
}
