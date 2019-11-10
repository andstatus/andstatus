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

package org.andstatus.app.util;

import org.andstatus.app.net.http.ConnectionException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;

import io.vavr.control.CheckedConsumer;
import io.vavr.control.CheckedFunction;
import io.vavr.control.Try;

public class ObjectOrId implements IsEmpty {
    private final static ObjectOrId EMPTY = of(null);
    public final Optional<JSONObject> object;
    public final Optional<JSONArray> array;
    public final Optional<String> id;
    public final Optional<ConnectionException> error;

    public static ObjectOrId of(JSONObject parentObject, String propertyName) {
        return new ObjectOrId(parentObject, propertyName);
    }

    private ObjectOrId(JSONObject parentObject, String propertyName) {
        object = Optional.ofNullable(parentObject.optJSONObject(propertyName));
        final JSONArray jsonArray = parentObject.optJSONArray(propertyName);
        array = object.isPresent() || jsonArray == null || jsonArray.length() == 0
                ? Optional.empty()
                : Optional.of(jsonArray);
        id = object.isPresent() || jsonArray != null
                ? Optional.empty()
                : StringUtils.optNotEmpty(parentObject.optString(propertyName));
        error = Optional.empty();
    }

    public static ObjectOrId of(JSONArray parentArray, int index) {
        return new ObjectOrId(Optional.ofNullable(parentArray.opt(index)));
    }

    public static ObjectOrId of(Object jso) {
        return new ObjectOrId(Optional.ofNullable(jso));
    }

    private ObjectOrId(Optional<Object> jso) {
        object = jso.flatMap(js -> js instanceof JSONObject ? Optional.of((JSONObject) js) : Optional.empty());
        final Optional<JSONArray> jsonArray = jso.flatMap(js -> js instanceof JSONArray
                ? Optional.of((JSONArray) js) : Optional.empty()).filter(a -> a.length() > 0);
        array = object.isPresent() || !jsonArray.isPresent()
                ? Optional.empty() : jsonArray;
        id = object.isPresent() || jsonArray.isPresent()
                ? Optional.empty()
                : jso.flatMap(js -> js instanceof String ? StringUtils.optNotEmpty(js) : Optional.empty());
        error = Optional.empty();
    }

    private ObjectOrId(ObjectOrId ooi, Exception e) {
        object = Optional.empty();
        array = Optional.empty();
        id = Optional.empty();
        error = Optional.of(e instanceof ConnectionException
                ? (ConnectionException) e
                : ConnectionException.loggedJsonException(this, "Parsing JSON", e, ooi.object.orElse(null)));
    }

    public static ObjectOrId empty() {
        return EMPTY;
    }

    @Override
    public boolean isEmpty() {
        return !object.isPresent() && !array.isPresent() && !id.isPresent();
    }

    public ObjectOrId ifId(CheckedConsumer<String> consumer) {
        return id.map(id -> {
            try {
                consumer.accept(id);
                return this;
            } catch (Exception t) {
                return new ObjectOrId(this, t);
            }
        }).orElse(this);
    }

    public ObjectOrId ifObject(CheckedConsumer<JSONObject> consumer) {
        return object.map(object -> {
            try {
                consumer.accept(object);
                return this;
            } catch (Exception t) {
                return new ObjectOrId(this, t);
            }
        }).orElse(this);
    }

    public ObjectOrId ifArray(CheckedConsumer<JSONArray> consumer) {
        return array.map(array -> {
            try {
                consumer.accept(array);
                return this;
            } catch (Exception t) {
                return new ObjectOrId(this, t);
            }
        }).orElse(this);
    }

    public ObjectOrId ifError(Consumer<Exception> consumer) {
        error.ifPresent(consumer::accept);
        return this;
    }

    public <T> Try<T> mapOne(CheckedFunction<JSONObject, T> fromObject, CheckedFunction<String, T> fromId) {
        if (object.isPresent()) {
            return Try.success(object.get()).map(fromObject);
        }
        if (id.isPresent()) {
            return Try.success(id.get()).map(fromId);
        }
        return Try.failure(new NoSuchElementException());
    }

    public <T> List<T> mapAll(CheckedFunction<JSONObject, T> fromObject, CheckedFunction<String, T> fromId) {
        if (object.isPresent()) {
            return Try.success(object.get()).map(fromObject).map(Collections::singletonList)
                    .getOrElse(Collections.emptyList());
        }
        if (array.isPresent()) {
            return Try.success(array.get()).map(arrayOfTo -> {
                List<T> list = new ArrayList<>();
                for (int ind = 0; ind < arrayOfTo.length(); ind++) {
                    ObjectOrId.of(arrayOfTo, ind)
                            .ifObject(o -> list.add(fromObject.apply(o)))
                            .ifId(id -> list.add(fromId.apply(id)));
                }
                return list;
            }).getOrElse(Collections.emptyList());
        }
        if (id.isPresent()) {
            return Try.success(id.get()).map(fromId).map(Collections::singletonList)
                    .getOrElse(Collections.emptyList());
        }
        return Collections.emptyList();
    }

    public <T> List<T> mapObjects(CheckedFunction<JSONObject, T> fromObject) {
        if (object.isPresent()) {
            return Try.success(object.get()).map(fromObject).map(Collections::singletonList)
                    .getOrElse(Collections.emptyList());
        }
        if (array.isPresent()) {
            return Try.success(array.get()).map(arrayOfTo -> {
                List<T> list = new ArrayList<>();
                for (int ind = 0; ind < arrayOfTo.length(); ind++) {
                    ObjectOrId.of(arrayOfTo, ind)
                            .ifObject(o -> list.add(fromObject.apply(o)));
                }
                return list;
            }).getOrElse(Collections.emptyList());
        }
        return Collections.emptyList();
    }
}
