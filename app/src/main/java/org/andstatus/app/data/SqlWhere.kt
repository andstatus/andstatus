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

import androidx.annotation.NonNull;

import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.StringUtil;

/**
 * @author yvolk@yurivolkov.com
 */
public class SqlWhere implements IsEmpty {
    private String where = "";

    public SqlWhere append(String field, SqlIds actorIds) {
        return actorIds.isEmpty() ? this : append(field, actorIds.getSql());
    }

    public SqlWhere append(String field, String condition) {
        if (StringUtil.isEmpty(condition)) {
            return this;
        }
        return append(field + condition);
    }

    public SqlWhere append(String condition) {
        if (StringUtil.isEmpty(condition)) {
            return this;
        }
        if (!StringUtil.isEmpty(where)) {
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
        return StringUtil.isEmpty(where) ? "" : " WHERE (" + where + ")";
    }

    @NonNull
    public String getAndWhere() {
        return StringUtil.isEmpty(where) ? "" : " AND " + where;
    }

    public boolean isEmpty() {
        return StringUtil.isEmpty(where);
    }
}
