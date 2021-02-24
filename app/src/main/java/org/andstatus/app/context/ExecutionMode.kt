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
package org.andstatus.app.context

/**
 * We can change application's behaviour based on this
 * @author yvolk@yurivolkov.com
 */
enum class ExecutionMode(val code: String?, val description: String?) {
    UNKNOWN("unknown", "Unknown"),
    DEVICE("device", "Normal operation"),
    TEST("test", "General testing, app may contain real data"),
    FIREBASE_TEST("firebaseTest", "Firebase Test Lab testing"),
    ROBO_TEST("roboTest", "Robo Test"),
    TRAVIS_TEST("travisTest", "Travis CI testing. Screen is unavailable");

    companion object {
        fun load(code: String?): ExecutionMode {
            for (`val` in values()) {
                if (`val`.code == code) {
                    return `val`
                }
            }
            return UNKNOWN
        }
    }
}