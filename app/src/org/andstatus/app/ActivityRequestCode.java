/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app;

// Request codes for called activities
public enum ActivityRequestCode {
    ATTACH(6),
    EDIT_ORIGIN(4),
    MOVE_DATA_BETWEEN_STORAGES(7),
    REMOVE_ACCOUNT(8),
    SELECT_ACCOUNT(1),
    SELECT_ACCOUNT_TO_ACT_AS(2),
    SELECT_ACCOUNT_TO_SHARE_VIA(5),
    SELECT_ORIGIN(3),
    SELECT_OPEN_INSTANCE(9),
    UNKNOWN(100);

    public final int id;
    
    private ActivityRequestCode(int id) {
        this.id = TimelineActivity.RESULT_FIRST_USER + id;
    }
    
    public static ActivityRequestCode fromId(int id) {
        for (ActivityRequestCode item : ActivityRequestCode.values()) {
            if (item.id == id) {
                return item;
            }
        }
        return UNKNOWN;
    }        
}