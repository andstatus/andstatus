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

package org.andstatus.app.util;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * @author yvolk@yurivolkov.com
 * See also http://www.javacreed.com/sorting-a-copyonwritearraylist/
 */
public class CollectionsUtil {

    private CollectionsUtil() {
        // Empty
    }

    public static <T extends Comparable<? super T>> void sort(List<T> list) {
        List<T> sortableList = new ArrayList<>(list);
        Collections.sort(sortableList);
        list.clear();
        list.addAll(sortableList);
    }

    public static int compareCheckbox(boolean lhs, boolean rhs) {
        int result = lhs == rhs ? 0 : lhs ? 1 : -1;
        return 0 - result;
    }

    /** Helper for {@link java.util.Map#compute(Object, BiFunction)} where the map value is an immutable {@link Set}. */
    @NonNull
    public static <T> BiFunction<T, Set<T>, Set<T>> addValue(T toAdd) {
        return (key, valuesNullable) -> {
            if (valuesNullable != null && valuesNullable.contains(toAdd)) return valuesNullable;

            Set<T> values = new HashSet<>();
            if (valuesNullable != null) values.addAll(valuesNullable);
            values.add(toAdd);
            return values;
        };
    }

    /** Helper for {@link java.util.Map#compute(Object, BiFunction)} where the map value is an immutable {@link Set}. */
    @NonNull
    public static <T> BiFunction<T, Set<T>, Set<T>> removeValue(T toRemove) {
        return (key, valuesNullable) -> valuesNullable != null && valuesNullable.contains(toRemove)
                ? valuesNullable.stream().filter(key2 -> key2 != toRemove).collect(Collectors.toSet())
                : valuesNullable;
    }

}
