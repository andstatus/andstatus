/**
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

package org.andstatus.app.data;

import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase.Download;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.user.UserListType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class UserListSql {
    private UserListSql() {
        // Empty
    }

    /**
     * @param uri the same as uri for
     *            {@link MyProvider#query(Uri, String[], String, String[], String)}
     * @param projection Projection
     * @return String for {@link SQLiteQueryBuilder#setTables(String)}
     */
    static String tablesForList(Uri uri, String[] projection) {
        ParsedUri uriParser = ParsedUri.fromUri(uri);
        UserListType listType = uriParser.getUserListType();
        SelectedUserIds selectedAccounts = new SelectedUserIds(uriParser.isCombined(), uriParser.getAccountUserId());

        Collection<String> columns = new java.util.HashSet<>(Arrays.asList(projection));

        String tables = User.TABLE_NAME;
        if (columns.contains(Download.AVATAR_FILE_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + Download.USER_ID + ", "
                    + Download.DOWNLOAD_STATUS + ", "
                    + Download.FILE_NAME
                    + " FROM " + Download.TABLE_NAME + ") AS " + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS
                    + " ON "
                    + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS + "." + Download.DOWNLOAD_STATUS
                    + "=" + DownloadStatus.LOADED.save() + " AND "
                    + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS + "." + Download.USER_ID
                    + "=" + User.TABLE_NAME + "." + BaseColumns._ID;
        }
        return tables;
    }

    /**
     * Table columns to use for a User item content
     */
    public static String[] getListProjection() {
        return getBaseProjection().toArray(new String[]{});
    }

    private static List<String> getBaseProjection() {
        List<String> columnNames = new ArrayList<>();
        columnNames.add(User._ID);
        columnNames.add(User.USER_OID);
        columnNames.add(User.USERNAME);
        columnNames.add(User.WEBFINGER_ID);
        columnNames.add(User.REAL_NAME);
        columnNames.add(User.DESCRIPTION);
        columnNames.add(User.LOCATION);

        columnNames.add(User.PROFILE_URL);
        columnNames.add(User.HOMEPAGE);
        if (MyPreferences.getShowAvatars()) {
            columnNames.add(Download.AVATAR_FILE_NAME);
        }

        columnNames.add(User.MSG_COUNT);
        columnNames.add(User.FAVORITES_COUNT);
        columnNames.add(User.FOLLOWING_COUNT);
        columnNames.add(User.FOLLOWERS_COUNT);

        columnNames.add(User.CREATED_DATE);
        columnNames.add(User.UPDATED_DATE);
        columnNames.add(User.ORIGIN_ID);
        return columnNames;
    }

}
