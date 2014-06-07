/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.service;

import org.andstatus.app.util.MyLog;

/**
 * Event in {@link MyService} 
 */
public enum MyServiceEvent {
    ON_COMMAND_RECEIVED,
    BEFORE_EXECUTING_COMMAND,
    AFTER_EXECUTING_COMMAND,
    ON_STOP,
    UNKNOWN;

    /**
     * Like valueOf but doesn't throw exceptions: it returns UNKNOWN instead 
     */
    public static MyServiceEvent load(String str) {
        MyServiceEvent state;
        try {
            state = valueOf(str);
        } catch (IllegalArgumentException e) {
            MyLog.v(MyServiceEvent.class, e);
            state = UNKNOWN;
        }
        return state;
    }
    public String save() {
        return this.toString();
    }
}
