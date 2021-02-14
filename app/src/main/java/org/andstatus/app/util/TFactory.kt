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

/**
 * Utility class to create an instances of a generic type <T>
 * Java doesn't have a way to create Instances of Type Parameters
 * Solutions, which use reflection, are of very limited usage and don't help here,
 * see http://stackoverflow.com/questions/75175/create-instance-of-generic-type-in-java
 * and http://stackoverflow.com/questions/1901164/get-type-of-a-generic-parameter-in-java-with-reflection
 */
public class TFactory<T> {
    private final Class<T> mTClass;

    public TFactory(Class<T> tClass) {
        if (tClass == null) {
            throw new IllegalArgumentException("tClass is null");
        }
        this.mTClass = tClass;
    }
    
    public T newT() {
        try {
            return mTClass.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Creating instance", e);
        }
    }
}
