/*
 * Copyright (C) 2015-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.provider.BaseColumns
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.DownloadTable
import org.andstatus.app.database.table.UserTable
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream

object ActorSql {
    private val AVATAR_IMAGE_TABLE_ALIAS: String = "av"
    val fullProjectionMap: MutableMap<String, String> = HashMap()
    private val userOnlyProjectionMap: MutableMap<String, String> = HashMap()
    private val baseProjectionMap: MutableMap<String, String> = HashMap()
    private val avatarProjectionMap: MutableMap<String, String> = HashMap()

    fun baseProjection(): Array<String> {
        return (if (MyPreferences.getShowAvatars()) Stream.concat(baseProjectionMap.keys.stream(),
                    avatarProjectionMap.keys.stream()) else baseProjectionMap.keys.stream())
                .collect(Collectors.toSet())
                .toTypedArray()
    }

    fun selectFullProjection(): String {
        return select(fullProjection = true, userOnly = false)
    }

    fun select(fullProjection: Boolean, userOnly: Boolean): String {
        return (when {
            fullProjection -> fullProjectionMap.values.stream()
            userOnly -> userOnlyProjectionMap.values.stream()
            MyPreferences.getShowAvatars() -> Stream.concat(baseProjectionMap.values.stream(), avatarProjectionMap.values.stream())
            else -> baseProjectionMap.values.stream()
        })
                .collect(Collectors.joining(", "))
    }

    fun allTables(): String {
        return tables(isFullProjection = true, userOnly = false, userIsOptional = true)
    }

    fun tables(isFullProjection: Boolean, userOnly: Boolean, userIsOptional: Boolean): String {
        val tables = (ActorTable.TABLE_NAME + " "
                + (if (userIsOptional) "LEFT" else "INNER") + " JOIN " + UserTable.TABLE_NAME
                + " ON " + ActorTable.TABLE_NAME + "." + ActorTable.USER_ID
                + "=" + UserTable.TABLE_NAME + "." + BaseColumns._ID)
        return if (isFullProjection || !userOnly && MyPreferences.getShowAvatars()) addAvatarImageTable(tables) else tables
    }

    private fun addAvatarImageTable(tables: String?): String {
        return ("(" + tables + ") LEFT OUTER JOIN (SELECT "
                + DownloadTable.ACTOR_ID + ", "
                + DownloadTable.WIDTH + ", "
                + DownloadTable.HEIGHT + ", "
                + DownloadTable.DOWNLOAD_STATUS + ", "
                + DownloadTable.DOWNLOAD_NUMBER + ", "
                + DownloadTable.DOWNLOADED_DATE + ", "
                + DownloadTable.FILE_NAME
                + " FROM " + DownloadTable.TABLE_NAME + ") AS " + AVATAR_IMAGE_TABLE_ALIAS
                + " ON "
                + AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.ACTOR_ID
                + "=" + ActorTable.TABLE_NAME + "." + BaseColumns._ID
                + " AND "
                + AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.DOWNLOAD_NUMBER + "=0")
    }

    init {
        fullProjectionMap[BaseColumns._ID] = ActorTable.TABLE_NAME + "." + BaseColumns._ID + " AS " + BaseColumns._ID
        userOnlyProjectionMap[ActorTable.ACTOR_ID] = (ActorTable.TABLE_NAME + "." + BaseColumns._ID
                + " AS " + ActorTable.ACTOR_ID)
        baseProjectionMap[ActorTable.ORIGIN_ID] = ActorTable.TABLE_NAME + "." + ActorTable.ORIGIN_ID
        baseProjectionMap[ActorTable.ACTOR_OID] = ActorTable.TABLE_NAME + "." + ActorTable.ACTOR_OID
        baseProjectionMap[ActorTable.GROUP_TYPE] = ActorTable.TABLE_NAME + "." + ActorTable.GROUP_TYPE
        fullProjectionMap[ActorTable.PARENT_ACTOR_ID] = ActorTable.TABLE_NAME + "." + ActorTable.PARENT_ACTOR_ID
        baseProjectionMap[ActorTable.REAL_NAME] = ActorTable.TABLE_NAME + "." + ActorTable.REAL_NAME
        baseProjectionMap[ActorTable.USERNAME] = ActorTable.TABLE_NAME + "." + ActorTable.USERNAME
        baseProjectionMap[ActorTable.WEBFINGER_ID] = ActorTable.TABLE_NAME + "." + ActorTable.WEBFINGER_ID
        baseProjectionMap[ActorTable.SUMMARY] = ActorTable.TABLE_NAME + "." + ActorTable.SUMMARY
        baseProjectionMap[ActorTable.LOCATION] = ActorTable.TABLE_NAME + "." + ActorTable.LOCATION
        baseProjectionMap[ActorTable.PROFILE_PAGE] = ActorTable.TABLE_NAME + "." + ActorTable.PROFILE_PAGE
        baseProjectionMap[ActorTable.HOMEPAGE] = ActorTable.TABLE_NAME + "." + ActorTable.HOMEPAGE
        avatarProjectionMap[ActorTable.AVATAR_URL] = ActorTable.TABLE_NAME + "." + ActorTable.AVATAR_URL
        baseProjectionMap[ActorTable.NOTES_COUNT] = ActorTable.TABLE_NAME + "." + ActorTable.NOTES_COUNT
        baseProjectionMap[ActorTable.FAVORITES_COUNT] = ActorTable.TABLE_NAME + "." + ActorTable.FAVORITES_COUNT
        baseProjectionMap[ActorTable.FOLLOWING_COUNT] = ActorTable.TABLE_NAME + "." + ActorTable.FOLLOWING_COUNT
        baseProjectionMap[ActorTable.FOLLOWERS_COUNT] = ActorTable.TABLE_NAME + "." + ActorTable.FOLLOWERS_COUNT
        fullProjectionMap[ActorTable.ACTOR_ACTIVITY_ID] = ActorTable.TABLE_NAME + "." + ActorTable.ACTOR_ACTIVITY_ID
        fullProjectionMap[ActorTable.ACTOR_ACTIVITY_DATE] = ActorTable.TABLE_NAME + "." + ActorTable.ACTOR_ACTIVITY_DATE
        baseProjectionMap[ActorTable.CREATED_DATE] = ActorTable.TABLE_NAME + "." + ActorTable.CREATED_DATE
        baseProjectionMap[ActorTable.UPDATED_DATE] = ActorTable.TABLE_NAME + "." + ActorTable.UPDATED_DATE
        fullProjectionMap[ActorTable.INS_DATE] = ActorTable.TABLE_NAME + "." + ActorTable.INS_DATE
        avatarProjectionMap[DownloadTable.AVATAR_FILE_NAME] = (AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.FILE_NAME
                + " AS " + DownloadTable.AVATAR_FILE_NAME)
        avatarProjectionMap[DownloadTable.WIDTH] = DownloadTable.WIDTH
        avatarProjectionMap[DownloadTable.HEIGHT] = DownloadTable.HEIGHT
        avatarProjectionMap[DownloadTable.DOWNLOAD_STATUS] = DownloadTable.DOWNLOAD_STATUS
        avatarProjectionMap[DownloadTable.DOWNLOADED_DATE] = DownloadTable.DOWNLOADED_DATE
        userOnlyProjectionMap[ActorTable.USER_ID] = ActorTable.TABLE_NAME + "." + ActorTable.USER_ID
        userOnlyProjectionMap[UserTable.IS_MY] = UserTable.TABLE_NAME + "." + UserTable.IS_MY
        userOnlyProjectionMap[UserTable.KNOWN_AS] = UserTable.TABLE_NAME + "." + UserTable.KNOWN_AS
        baseProjectionMap.putAll(userOnlyProjectionMap)
        fullProjectionMap.putAll(baseProjectionMap)
        fullProjectionMap.putAll(avatarProjectionMap)
    }
}
