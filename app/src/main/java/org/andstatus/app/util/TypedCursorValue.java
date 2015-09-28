/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.util;

import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;

public class TypedCursorValue {
    private static final String KEY_TYPE = "type";
    private static final String KEY_VALUE = "value";

    public final CursorFieldType type;
    public final Object value;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypedCursorValue that = (TypedCursorValue) o;

        if (type != CursorFieldType.UNKNOWN && that.type != CursorFieldType.UNKNOWN) {
            if (type != that.type) return false;
        }
        return !(value != null ? !value.toString().equals(that.value.toString()) : that.value != null);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + (value != null ? value.toString().hashCode() : 0);
        return result;
    }

    private enum CursorFieldType {
        UNKNOWN(-1),
        STRING(Cursor.FIELD_TYPE_STRING) {
            @Override
            public Object columnToObject(Cursor cursor, int columnIndex) {
                return cursor.getString(columnIndex);
            }
        },
        INTEGER(Cursor.FIELD_TYPE_INTEGER) {
            @Override
            public Object columnToObject(Cursor cursor, int columnIndex) {
                return cursor.getLong(columnIndex);
            }
        },
        BLOB(Cursor.FIELD_TYPE_BLOB) {
            @Override
            public Object columnToObject(Cursor cursor, int columnIndex) {
                return cursor.getBlob(columnIndex);
            }
        },
        FLOAT(Cursor.FIELD_TYPE_FLOAT) {
            @Override
            public Object columnToObject(Cursor cursor, int columnIndex) {
                return cursor.getDouble(columnIndex);
            }
        },
        NULL(Cursor.FIELD_TYPE_NULL);

        final int code;
        CursorFieldType(int fieldType) {
            code = fieldType;
        }

        public Object columnToObject(Cursor cursor, int columnIndex) {
            return  null;
        }

        public static CursorFieldType fromColumnType(int cursorColumnType) {
            for(CursorFieldType val : values()) {
                if (val.code == cursorColumnType) {
                    return val;
                }
            }
            return UNKNOWN;
        }

    }

    public static TypedCursorValue fromJson(JSONObject json) {
        CursorFieldType type = CursorFieldType.UNKNOWN ;
        if (json.has(KEY_TYPE)) {
            type = CursorFieldType.fromColumnType(json.optInt(KEY_TYPE));
        }
        return new TypedCursorValue(type, json.opt(KEY_VALUE));
    }

    public TypedCursorValue(Cursor cursor, int columnIndex) {
        type =  CursorFieldType.fromColumnType(cursor.getType(columnIndex));
        value = type.columnToObject(cursor, columnIndex);
    }

    public TypedCursorValue(Object object) {
        this(CursorFieldType.UNKNOWN, object);
    }

    public TypedCursorValue(CursorFieldType type, Object object) {
        this.type = type ;
        value = object;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(KEY_TYPE, type.code);
        json.put(KEY_VALUE, value);
        return json;
    }
}
