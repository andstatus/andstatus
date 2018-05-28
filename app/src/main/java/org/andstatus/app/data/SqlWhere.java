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

import org.andstatus.app.util.StringUtils;

/**
 * @author yvolk@yurivolkov.com
 */
public class SqlWhere {
    private String where = "";

    public SqlWhere append(String field, SqlActorIds actorIds) {
        return append(field, actorIds.getSql());
    }

    public SqlWhere append(String field, String condition) {
        if (StringUtils.isEmpty(condition)) {
            return this;
        }
        return append(field + condition);
    }

    public SqlWhere append(String condition) {
        if (StringUtils.isEmpty(condition)) {
            return this;
        }
        if (!StringUtils.isEmpty(where)) {
            where += " AND ";
        }
        where += "(" + condition + ")";
        return this;
    }

    @NonNull
    public String getCondition() {
        return where;
    }

    @NonNull
    public String getWhere() {
        return StringUtils.isEmpty(where) ? "" : " WHERE (" + where + ")";
    }

    @NonNull
    public String getAndWhere() {
        return StringUtils.isEmpty(where) ? "" : " AND " + where;
    }

    public boolean isEmpty() {
        return StringUtils.isEmpty(where);
    }
}
