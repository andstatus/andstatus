/*
 * Copyright (c) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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

/** @author yvolk@yurivolkov.com */
public enum Visibility {
    UNKNOWN(3),
    PUBLIC_AND_TO_FOLLOWERS(4),
    PUBLIC(5),
    NOT_PUBLIC_NEEDS_CLARIFICATION(6),
    TO_FOLLOWERS(8),
    PRIVATE(10),
    ;

    public final long id;

    Visibility(long id) {
        this.id = id;
    }

    public static Visibility fromId(long id) {
        // Special handling of values, created before v.55
        if (id == 2) return PUBLIC;
        if (id == 1) return NOT_PUBLIC_NEEDS_CLARIFICATION;

        for (Visibility tt : Visibility.values()) {
            if (tt.id == id) {
                return tt;
            }
        }
        return UNKNOWN;
    }

    public boolean isKnown() {
        return this != UNKNOWN;
    }

    public boolean isPublicCheckbox() {
        switch (this) {
            case UNKNOWN:
            case PUBLIC_AND_TO_FOLLOWERS:
            case PUBLIC:
                return true;
            default:
                return false;
        }
    }

    public boolean isFollowersCheckbox() {
        switch (this) {
            case PUBLIC_AND_TO_FOLLOWERS:
            case TO_FOLLOWERS:
                return true;
            default:
                return false;
        }
    }
}
