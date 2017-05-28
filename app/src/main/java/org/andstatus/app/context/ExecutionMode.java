/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.context;

import android.support.annotation.NonNull;

/**
 * We can change application's behaviour based on this
 * @author yvolk@yurivolkov.com
 */
public enum ExecutionMode {
    UNKNOWN("unknown", "Unknown"),
    DEVICE("device", "Normal operation"),
    TEST("test", "General testing, app may contain real data"),
    FIREBASE_TEST("firebaseTest", "Firebase Test Lab testing"),
    ROBO_TEST("roboTest", "Robo Test"),
    TRAVIS_TEST("travisTest", "Travis CI testing. Screen is unavailable");

    final public String code;
    final String description;

    ExecutionMode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @NonNull
    public static ExecutionMode load(String code) {
        for(ExecutionMode val : values()) {
            if (val.code.equals(code)) {
                return val;
            }
        }
        return UNKNOWN;
    }
}
