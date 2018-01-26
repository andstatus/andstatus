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

package org.andstatus.app.user;

import android.content.Context;

import org.andstatus.app.R;

/**
 * These values define different named UserList filters
 */
public enum ActorListType {
    /**
     * The type is unknown
     */
    UNKNOWN("unknown", R.string.unknown_userlist, false),
    /**
     * Users, related to the selected message, including mentioned users
     */
    USERS_OF_MESSAGE("users_of_message", R.string.users_of_message, true),
    FOLLOWERS("followers", R.string.followers, true),
    FRIENDS("friends", R.string.friends, true),
    USERS("users", R.string.user_list, true);

    /**
     * code of the enum that is used in messages
     */
    private final String code;
    /**
     * The id of the string resource with the localized name of this enum to use in UI
     */
    private final int titleResId;
    private final boolean mAtOrigin;
    
    ActorListType(String code, int resId, boolean atOrigin) {
        this.code = code;
        this.titleResId = resId;
        this.mAtOrigin = atOrigin;
    }
    
    /**
     * String to be used for persistence
     */
    public String save() {
        return code;
    }
    
    @Override
    public String toString() {
        return "UserList:" + code;
    }

    /** Localized title for UI */
    public CharSequence getTitle(Context context) {
        if (titleResId == 0 || context == null) {
            return this.code;
        } else {
            return context.getText(titleResId);        
        }
    }
    
    public CharSequence getPrepositionForNotCombined(Context context) {
        if (context == null) {
            return "";
        } else if (atOrigin()) {
            return context.getText(R.string.combined_timeline_off_origin);
        } else {
            return context.getText(R.string.combined_timeline_off_account);
        }
    }
    
    public boolean atOrigin() {
        return mAtOrigin;
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