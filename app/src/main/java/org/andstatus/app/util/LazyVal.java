/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import java.util.function.Supplier;

/** Lazy holder of a non Null value
 * Inspired by https://www.sitepoint.com/lazy-computations-in-java-with-a-lazy-type/
 * and https://dzone.com/articles/be-lazy-with-java-8 */
public class LazyVal<T> implements Supplier<T> {
    private final Supplier<T> supplier;
    private volatile T value = null;

    public static <T> LazyVal<T> of(Supplier<T> supplier) {
        return new LazyVal<>(supplier);
    }

    private LazyVal(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    @Override
    public T get() {
        T storedValue = value;
        return storedValue == null ? evaluate() : storedValue;
    }

    private synchronized T evaluate() {
        if (value != null) return value;

        T evaluated = supplier.get();
        value = evaluated;
        return evaluated;
    }

    public void reset() {
        value = null;
    }
}
