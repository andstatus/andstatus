/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

import android.support.annotation.NonNull;
import android.text.TextUtils;

/**
 * @author yvolk@yurivolkov.com
 */
public class SqlWhere {
    String where = "";

    public void append(String condition) {
        if (TextUtils.isEmpty(condition)) {
            return;
        }
        if (!TextUtils.isEmpty(where)) {
            where += " AND ";
        }
        where += "(" + condition + ")";
    }

    @NonNull
    public String getCondition() {
        return where;
    }

    @NonNull
    public String getWhere() {
        return TextUtils.isEmpty(where) ? "" : " WHERE (" + where + ")";
    }

    @NonNull
    public String getAndWhere() {
        return TextUtils.isEmpty(where) ? "" : " AND " + where;
    }
}
