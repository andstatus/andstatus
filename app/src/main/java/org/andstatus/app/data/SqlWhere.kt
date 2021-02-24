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
package org.andstatus.app.data

import org.andstatus.app.util.IsEmpty

/**
 * @author yvolk@yurivolkov.com
 */
class SqlWhere : IsEmpty {
    private var where: String? = ""
    fun append(field: String?, actorIds: SqlIds?): SqlWhere? {
        return if (actorIds.isEmpty()) this else append(field, actorIds.getSql())
    }

    fun append(field: String?, condition: String?): SqlWhere? {
        return if (condition.isNullOrEmpty()) {
            this
        } else append(field + condition)
    }

    fun append(condition: String?): SqlWhere? {
        if (condition.isNullOrEmpty()) {
            return this
        }
        if (!where.isNullOrEmpty()) {
            where += " AND "
        }
        where += "($condition)"
        return this
    }

    fun getCondition(): String {
        return where
    }

    fun getWhere(): String {
        return if (where.isNullOrEmpty()) "" else " WHERE ($where)"
    }

    fun getAndWhere(): String {
        return if (where.isNullOrEmpty()) "" else " AND $where"
    }

    override fun isEmpty(): Boolean {
        return where.isNullOrEmpty()
    }
}