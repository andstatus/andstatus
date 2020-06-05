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
import org.andstatus.app.util.StringUtil;

import androidx.annotation.StringRes;

/**
 * These values define different named filters for lists of Actors / Users / Groups
 */
public enum ActorsScreenType {
    UNKNOWN("unknown", R.string.unknown_userlist, 0, ListScope.ORIGIN),
    /** Actors, related to the selected note, including mentioned actors */
    ACTORS_OF_NOTE("actors_of_note", R.string.users_of_message, 0, ListScope.ORIGIN),
    FOLLOWERS("followers", R.string.followers, R.string.followers_of, ListScope.USER),
    FRIENDS("friends", R.string.friends, R.string.friends_of, ListScope.USER),
    ACTORS_AT_ORIGIN("actors", R.string.user_list, 0, ListScope.ORIGIN),
    GROUPS_AT_ORIGIN("groups", R.string.groups, 0, ListScope.ORIGIN);

    /** code of the enum that is used in notes */
    private final String code;
    @StringRes
    private final int titleResId;
    @StringRes
    private final int titleResWithParamsId;
    public final ListScope scope;

    ActorsScreenType(String code, int resId, int resWithParamsId, ListScope scope) {
        this.code = code;
        this.titleResId = resId;
        titleResWithParamsId = resWithParamsId;
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
        return "ActorsScreen:" + code;
    }

    /** Localized title for UI */
    public CharSequence title(Context context) {
        if (titleResId == 0 || context == null) {
            return this.code;
        } else {
            return context.getText(titleResId);        
        }
    }

    public CharSequence title(Context context, Object ... params) {
        return StringUtil.format(context, titleResWithParamsId, params);
    }

    /**
     * Returns the enum or UNKNOWN
     */
    public static ActorsScreenType load(String strCode) {
        for (ActorsScreenType tt : ActorsScreenType.values()) {
            if (tt.code.equals(strCode)) {
                return tt;
            }
        }
        return UNKNOWN;
    }
}