/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.widget.Spinner;

import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.actor.ActorList;

public enum SearchObjects {
    MESSAGES(TimelineActivity.class),
    USERS(ActorList.class);

    private final Class<?> aClass;

    SearchObjects(Class<?> aClass) {
        this.aClass = aClass;
    }

    public Class<?> getActivityClass() {
        return aClass;
    }

    public static SearchObjects fromSpinner(Spinner spinner) {
        SearchObjects obj = MESSAGES;
        if (spinner != null) {
            for (SearchObjects val : values()) {
                if (val.ordinal() == spinner.getSelectedItemPosition()) {
                    obj = val;
                    break;
                }
            }
        }
        return obj;
    }
}
