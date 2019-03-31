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

package org.andstatus.app.net.social;

import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.ObjectOrId;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import io.vavr.control.CheckedFunction;

/** https://www.w3.org/TR/activitystreams-core/#collections */
public class AJsonCollection implements IsEmpty {
    /** https://www.w3.org/TR/activitystreams-core/#dfn-collectionpage */
    public enum Type {
        EMPTY,
        COLLECTION,
        ORDERED_COLLECTION,
        PAGED_COLLECTION,
        PAGED_ORDERED_COLLECTION,
        PAGE,
        ORDERED_PAGE,
    }
    private static AJsonCollection EMPTY = AJsonCollection.of(null);

    public final Type type;
    final ObjectOrId items;
    final AJsonCollection firstPage;
    final AJsonCollection prevPage;
    final AJsonCollection currentPage;
    final AJsonCollection nextPage;
    final AJsonCollection lastPage;

    @Override
    public boolean isEmpty() {
        return type == Type.EMPTY;
    }

    public static AJsonCollection empty() {
        return EMPTY;
    }

    public static AJsonCollection of(JSONObject parentObject) {
        return new AJsonCollection(Optional.ofNullable(parentObject));
    }

    public static AJsonCollection of(JSONObject parentObject, String propertyName) {
        return new AJsonCollection(Optional.ofNullable(parentObject.optJSONObject(propertyName)));
    }

    private AJsonCollection(Optional<JSONObject> parentObject) {
        type = parentObject.map(AJsonCollection::calcType).orElse(Type.EMPTY);
        items = parentObject.map(p -> calcItems(p, type)).orElse(ObjectOrId.empty());
        firstPage = parentObject.map(p -> AJsonCollection.of(p, "first")).orElse(AJsonCollection.empty());
        prevPage = parentObject.map(p -> AJsonCollection.of(p, "prev")).orElse(AJsonCollection.empty());
        currentPage = parentObject.map(p -> AJsonCollection.of(p, "current")).orElse(AJsonCollection.empty());
        nextPage = parentObject.map(p -> AJsonCollection.of(p, "next")).orElse(AJsonCollection.empty());
        lastPage = parentObject.map(p -> AJsonCollection.of(p, "last")).orElse(AJsonCollection.empty());
    }

    private static Type calcType(@NonNull JSONObject jso) {
        switch (jso.optString("type")) {
            case "Collection":
                return jso.has("items") ? Type.COLLECTION : Type.PAGED_COLLECTION;
            case "OrderedCollection":
                return jso.has("orderedItems") ? Type.ORDERED_COLLECTION : Type.PAGED_ORDERED_COLLECTION;
            case "CollectionPage":
                return jso.has("items") ? Type.PAGE : Type.EMPTY;
            case "OrderedCollectionPage":
                return jso.has("orderedItems") ? Type.ORDERED_PAGE : Type.EMPTY;
            default:
                return Type.EMPTY;
        }
    }

    private static ObjectOrId calcItems(@NonNull JSONObject jso, Type type) {
        switch (type) {
            case COLLECTION:
            case PAGE:
                return ObjectOrId.of(jso, "items");
            case ORDERED_COLLECTION:
            case ORDERED_PAGE:
                return ObjectOrId.of(jso, "orderedItems");
            default:
                return ObjectOrId.empty();
        }
    }

    public <T extends IsEmpty> List<T> mapAll(CheckedFunction<JSONObject, T> fromObject, CheckedFunction<String, T> fromId) {
        if (isEmpty()) return Collections.emptyList();

        List<T> list = new ArrayList<>();
        list.addAll(items.mapAll(fromObject, fromId));
        list.addAll(firstPage.mapAll(fromObject, fromId));
        list.addAll(prevPage.mapAll(fromObject, fromId));
        list.addAll(currentPage.mapAll(fromObject, fromId));
        list.addAll(nextPage.mapAll(fromObject, fromId));
        list.addAll(lastPage.mapAll(fromObject, fromId));
        return list.stream().filter(IsEmpty::nonEmpty).collect(Collectors.toList());
    }

    public <T extends IsEmpty> List<T> mapObjects(CheckedFunction<JSONObject, T> fromObject) {
        if (isEmpty()) return Collections.emptyList();

        List<T> list = new ArrayList<>();
        list.addAll(items.mapObjects(fromObject));
        list.addAll(firstPage.mapObjects(fromObject));
        list.addAll(prevPage.mapObjects(fromObject));
        list.addAll(currentPage.mapObjects(fromObject));
        list.addAll(nextPage.mapObjects(fromObject));
        list.addAll(lastPage.mapObjects(fromObject));
        return list.stream().filter(IsEmpty::nonEmpty).collect(Collectors.toList());
    }
}
