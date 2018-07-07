/*
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

package org.andstatus.app.actor;

import android.content.Context;

import org.andstatus.app.R;
import org.andstatus.app.timeline.ListScope;

/**
 * These values define different named UserList filters
 */
public enum ActorListType {
    UNKNOWN("unknown", R.string.unknown_userlist, ListScope.ORIGIN),
    /** Actors, related to the selected note, including mentioned actors */
    ACTORS_OF_NOTE("actors_of_note", R.string.users_of_message, ListScope.ORIGIN),
    FOLLOWERS("followers", R.string.followers, ListScope.USER),
    FRIENDS("friends", R.string.friends, ListScope.USER),
    ACTORS_AT_ORIGIN("actors", R.string.user_list, ListScope.ORIGIN);

    /** code of the enum that is used in notes */
    private final String code;
    /** The id of the string resource with the localized name of this enum to use in UI */
    private final int titleResId;
    public final ListScope scope;

    ActorListType(String code, int resId, ListScope scope) {
        this.code = code;
        this.titleResId = resId;
        this.scope = scope;
    }
    
    /**
     * String to be used for persistence
     */
    public String save() {
        return code;
    }
    
    @Override
    public String toString() {
        return "ActorList:" + code;
    }

    /** Localized title for UI */
    public CharSequence getTitle(Context context) {
        if (titleResId == 0 || context == null) {
            return this.code;
        } else {
            return context.getText(titleResId);        
        }
    }

    /**
     * Returns the enum or UNKNOWN
     */
    public static ActorListType load(String strCode) {
        for (ActorListType tt : ActorListType.values()) {
            if (tt.code.equals(strCode)) {
                return tt;
            }
        }
        return UNKNOWN;
    }
}