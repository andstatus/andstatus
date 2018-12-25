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

package org.andstatus.app.data;

import android.provider.BaseColumns;
import androidx.annotation.NonNull;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.database.table.UserTable;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ActorSql {
    public static final String AVATAR_IMAGE_TABLE_ALIAS = "av";

    public static final Map<String, String> fullProjectionMap = new HashMap<>();
    private static final Map<String, String> userOnlyProjectionMap = new HashMap<>();
    private static final Map<String, String> baseProjectionMap = new HashMap<>();
    private static final Map<String, String> avatarProjectionMap = new HashMap<>();

    static {
        fullProjectionMap.put(BaseColumns._ID, ActorTable.TABLE_NAME + "." + BaseColumns._ID + " AS " + BaseColumns._ID);
        userOnlyProjectionMap.put(ActorTable.ACTOR_ID, ActorTable.TABLE_NAME + "." + BaseColumns._ID
                + " AS " + ActorTable.ACTOR_ID);
        baseProjectionMap.put(ActorTable.ORIGIN_ID, ActorTable.TABLE_NAME + "." + ActorTable.ORIGIN_ID);
        baseProjectionMap.put(ActorTable.ACTOR_OID, ActorTable.TABLE_NAME + "." + ActorTable.ACTOR_OID);

        baseProjectionMap.put(ActorTable.REAL_NAME, ActorTable.TABLE_NAME + "." + ActorTable.REAL_NAME);
        baseProjectionMap.put(ActorTable.USERNAME, ActorTable.TABLE_NAME + "." + ActorTable.USERNAME);
        baseProjectionMap.put(ActorTable.WEBFINGER_ID, ActorTable.TABLE_NAME + "." + ActorTable.WEBFINGER_ID);

        baseProjectionMap.put(ActorTable.SUMMARY, ActorTable.TABLE_NAME + "." + ActorTable.SUMMARY);
        baseProjectionMap.put(ActorTable.LOCATION, ActorTable.TABLE_NAME + "." + ActorTable.LOCATION);

        baseProjectionMap.put(ActorTable.PROFILE_PAGE, ActorTable.TABLE_NAME + "." + ActorTable.PROFILE_PAGE);
        baseProjectionMap.put(ActorTable.HOMEPAGE, ActorTable.TABLE_NAME + "." + ActorTable.HOMEPAGE);
        avatarProjectionMap.put(ActorTable.AVATAR_URL, ActorTable.TABLE_NAME + "." + ActorTable.AVATAR_URL);

        baseProjectionMap.put(ActorTable.NOTES_COUNT, ActorTable.TABLE_NAME + "." + ActorTable.NOTES_COUNT);
        baseProjectionMap.put(ActorTable.FAVORITES_COUNT, ActorTable.TABLE_NAME + "." + ActorTable.FAVORITES_COUNT);
        baseProjectionMap.put(ActorTable.FOLLOWING_COUNT, ActorTable.TABLE_NAME + "." + ActorTable.FOLLOWING_COUNT);
        baseProjectionMap.put(ActorTable.FOLLOWERS_COUNT, ActorTable.TABLE_NAME + "." + ActorTable.FOLLOWERS_COUNT);

        fullProjectionMap.put(ActorTable.ACTOR_ACTIVITY_ID, ActorTable.TABLE_NAME + "." + ActorTable.ACTOR_ACTIVITY_ID);
        fullProjectionMap.put(ActorTable.ACTOR_ACTIVITY_DATE, ActorTable.TABLE_NAME + "." + ActorTable.ACTOR_ACTIVITY_DATE);

        baseProjectionMap.put(ActorTable.CREATED_DATE, ActorTable.TABLE_NAME + "." + ActorTable.CREATED_DATE);
        baseProjectionMap.put(ActorTable.UPDATED_DATE, ActorTable.TABLE_NAME + "." + ActorTable.UPDATED_DATE);
        fullProjectionMap.put(ActorTable.INS_DATE, ActorTable.TABLE_NAME + "." + ActorTable.INS_DATE);

        avatarProjectionMap.put(DownloadTable.AVATAR_FILE_NAME, AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.FILE_NAME
                + " AS " + DownloadTable.AVATAR_FILE_NAME);
        avatarProjectionMap.put(DownloadTable.DOWNLOAD_STATUS, DownloadTable.DOWNLOAD_STATUS);
        avatarProjectionMap.put(DownloadTable.DOWNLOADED_DATE, DownloadTable.DOWNLOADED_DATE);

        userOnlyProjectionMap.put(ActorTable.USER_ID, ActorTable.TABLE_NAME + "." + ActorTable.USER_ID);
        userOnlyProjectionMap.put(UserTable.IS_MY, UserTable.TABLE_NAME + "." + UserTable.IS_MY);
        userOnlyProjectionMap.put(UserTable.KNOWN_AS, UserTable.TABLE_NAME + "." + UserTable.KNOWN_AS);

        baseProjectionMap.putAll(userOnlyProjectionMap);
        fullProjectionMap.putAll(baseProjectionMap);
        fullProjectionMap.putAll(avatarProjectionMap);
    }

    private ActorSql() {
        // Empty
    }

    public static String[] projection() {
        return (MyPreferences.getShowAvatars()
                    ? Stream.concat(baseProjectionMap.keySet().stream(), avatarProjectionMap.keySet().stream())
                    : baseProjectionMap.keySet().stream())
            .collect(Collectors.toSet())
            .toArray(new String[]{});
    }

    @NonNull
    public static String select() {
        return select(false, false);
    }

    @NonNull
    public static String select(boolean userOnly, boolean optionalUser) {
        return (userOnly
                ? userOnlyProjectionMap.values().stream()
                : optionalUser
                    ? fullProjectionMap.values().stream()
                    : MyPreferences.getShowAvatars()
                        ? Stream.concat(baseProjectionMap.values().stream(), avatarProjectionMap.values().stream())
                        : baseProjectionMap.values().stream())
                .collect(Collectors.joining(", "));
    }

    @NonNull
    public static String tables() {
        return tables(false, false);
    }

    @NonNull
    public static String tables(boolean userOnly, boolean optionalUser) {
        final String tables = ActorTable.TABLE_NAME + " "
                + (optionalUser ? "LEFT" : "INNER") + " JOIN " + UserTable.TABLE_NAME
                + " ON " + ActorTable.TABLE_NAME + "." + ActorTable.USER_ID
                + "=" + UserTable.TABLE_NAME + "." + UserTable._ID;
        return !userOnly && MyPreferences.getShowAvatars()
                ? addAvatarImageTable(tables)
                : tables;
    }

    @NonNull
    private static String addAvatarImageTable(String tables) {
        return "(" + tables + ") LEFT OUTER JOIN (SELECT "
                + DownloadTable.ACTOR_ID + ", "
                + DownloadTable.DOWNLOAD_STATUS + ", "
                + DownloadTable.DOWNLOAD_NUMBER + ", "
                + DownloadTable.DOWNLOADED_DATE + ", "
                + DownloadTable.FILE_NAME
                + " FROM " + DownloadTable.TABLE_NAME + ") AS " + AVATAR_IMAGE_TABLE_ALIAS
                + " ON "
                + AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.ACTOR_ID
                + "=" + ActorTable.TABLE_NAME + "." + BaseColumns._ID
                + " AND "
                + AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.DOWNLOAD_NUMBER + "=0";
    }
}
