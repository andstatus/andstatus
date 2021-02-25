/*
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
package org.andstatus.app.origin

import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import org.andstatus.app.SearchObjects
import org.andstatus.app.context.MyContextImpl
import org.andstatus.app.data.DbUtils.closeSilently
import org.andstatus.app.database.table.OriginTable
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StringUtil
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

class PersistentOrigins private constructor(val myContext: MyContextImpl) {
    private val mOrigins: MutableMap<String, Origin> = ConcurrentHashMap()

    @JvmOverloads
    fun initialize(db: SQLiteDatabase? = myContext.getDatabase()): Boolean {
        if (db == null) return true

        val sql = "SELECT * FROM " + OriginTable.TABLE_NAME
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(sql, null)
            mOrigins.clear()
            while (cursor.moveToNext()) {
                val origin = Origin.Builder(myContext, cursor).build()
                mOrigins[origin.name] = origin
            }
        } catch (e: SQLException) {
            MyLog.e(TAG, """Failed to initialize origins ${MyLog.getStackTrace(e)}""".trimIndent())
            return false
        } finally {
            closeSilently(cursor)
        }
        MyLog.v(TAG) { "Initialized " + mOrigins.size + " origins" }
        return true
    }

    /**
     * @return EMPTY Origin if not found
     */
    fun fromId(originId: Long): Origin {
        if (originId == 0L) return  Origin.EMPTY
        for (origin in mOrigins.values) {
            if (origin.id == originId) {
                return origin
            }
        }
        return  Origin.EMPTY
    }

    /**
     * @return Origin of UNKNOWN type if not found
     */
    fun fromName(originName: String?): Origin {
        var origin: Origin? = null
        if (!originName.isNullOrEmpty()) {
            origin = mOrigins[originName]
        }
        return origin ?:  Origin.EMPTY
    }

    fun fromOriginInAccountNameAndHost(originInAccountName: String?, host: String?): Origin {
        val origins = allFromOriginInAccountNameAndHost(originInAccountName, host)
        return when (origins.size) {
            0 ->  Origin.EMPTY
            1 -> origins[0]
            else ->                 // Select Origin that was added earlier
                origins.stream().min { o1: Origin, o2: Origin -> java.lang.Long.signum(o1.id - o2.id) }
                        .orElse(Origin.EMPTY)
        }
    }

    fun allFromOriginInAccountNameAndHost(originInAccountName: String?, host: String?): List<Origin> {
        val origins = fromOriginInAccountName(originInAccountName)
        return when (origins.size) {
            0, 1 -> origins
            else -> origins.stream()
                    .filter { origin: Origin ->
                        origin.getAccountNameHost().isEmpty() ||
                                origin.getAccountNameHost().equals(host, ignoreCase = true)
                    }
                    .collect(Collectors.toList())
        }
    }

    fun fromOriginInAccountName(originInAccountName: String?): List<Origin> {
        return StringUtil.optNotEmpty(originInAccountName).map { name: String ->
            val originType: OriginType = OriginType.fromTitle(name)
            val originsOfType: List<Origin> = if (originType === OriginType.UNKNOWN) emptyList<Origin>()
            else mOrigins.values.stream().filter { origin: Origin -> origin.getOriginType() === originType }
                    .collect(Collectors.toList())
            if (originsOfType.size == 1) {
                return@map originsOfType
            }
            val originsWithName = mOrigins.values.stream()
                    .filter { origin: Origin ->
                        (origin.name.equals(name, ignoreCase = true)
                                && (originType === OriginType.UNKNOWN || origin.getOriginType() === originType))
                    }
                    .collect(Collectors.toList())
            if (originsOfType.size > originsWithName.size) originsOfType else originsWithName
        }
                .orElse(emptyList())
    }

    /**
     * @return Origin of this type or empty Origin of UNKNOWN type if not found
     */
    fun firstOfType(originType: OriginType?): Origin {
        for (origin in mOrigins.values) {
            if (origin.getOriginType() === originType) {
                return origin
            }
        }
        return  Origin.EMPTY
    }

    fun collection(): MutableCollection<Origin> {
        return mOrigins.values
    }

    fun originsToSync(originIn: Origin?, forAllOrigins: Boolean, isSearch: Boolean): MutableList<Origin> {
        val hasSynced = hasSyncedForAllOrigins(isSearch)
        val origins: MutableList<Origin> = ArrayList()
        if (forAllOrigins) {
            for (origin in collection()) {
                addMyOriginToSync(origins, origin, isSearch, hasSynced)
            }
        } else if (originIn != null) {
            addMyOriginToSync(origins, originIn, isSearch, false)
        }
        return origins
    }

    fun hasSyncedForAllOrigins(isSearch: Boolean): Boolean {
        for (origin in mOrigins.values) {
            if (origin.isSyncedForAllOrigins(isSearch)) {
                return true
            }
        }
        return false
    }

    private fun addMyOriginToSync(origins: MutableList<Origin>, origin: Origin, isSearch: Boolean, hasSynced: Boolean) {
        if (!origin.isValid()) {
            return
        }
        if (hasSynced && !origin.isSyncedForAllOrigins(isSearch)) {
            return
        }
        origins.add(origin)
    }

    fun isSearchSupported(searchObjects: SearchObjects?, origin: Origin?, forAllOrigins: Boolean): Boolean {
        return originsForInternetSearch(searchObjects, origin, forAllOrigins).size > 0
    }

    fun originsForInternetSearch(searchObjects: SearchObjects?, originIn: Origin?, forAllOrigins: Boolean): MutableList<Origin> {
        val origins: MutableList<Origin> = ArrayList()
        if (forAllOrigins) {
            for (account in myContext.accounts().get()) {
                if (account.origin.isInCombinedGlobalSearch() &&
                        account.isValidAndSucceeded() && account.isSearchSupported(searchObjects)
                        && !origins.contains(account.origin)) {
                    origins.add(account.origin)
                }
            }
        } else if (originIn != null && originIn.isValid()) {
            val account = myContext.accounts().getFirstPreferablySucceededForOrigin(originIn)
            if (account.isValidAndSucceeded() && account.isSearchSupported(searchObjects)) {
                origins.add(originIn)
            }
        }
        return origins
    }

    fun originsOfType(originType: OriginType): MutableList<Origin?> {
        val origins: MutableList<Origin?> = ArrayList()
        for (origin in collection()) {
            if (origin.getOriginType() == originType) {
                origins.add(origin)
            }
        }
        return origins
    }

    companion object {
        private val TAG: String = PersistentOrigins::class.java.simpleName
        fun newEmpty(myContext: MyContextImpl): PersistentOrigins {
            return PersistentOrigins(myContext)
        }
    }
}