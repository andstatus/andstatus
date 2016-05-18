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
import org.andstatus.app.database.DownloadTable;
import org.andstatus.app.database.UserTable;
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

        String tables = UserTable.TABLE_NAME;
        if (columns.contains(DownloadTable.AVATAR_FILE_NAME)) {
            tables = "(" + tables + ") LEFT OUTER JOIN (SELECT "
                    + DownloadTable.USER_ID + ", "
                    + DownloadTable.DOWNLOAD_STATUS + ", "
                    + DownloadTable.FILE_NAME
                    + " FROM " + DownloadTable.TABLE_NAME + ") AS " + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS
                    + " ON "
                    + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.DOWNLOAD_STATUS
                    + "=" + DownloadStatus.LOADED.save() + " AND "
                    + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.USER_ID
                    + "=" + UserTable.TABLE_NAME + "." + BaseColumns._ID;
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
        columnNames.add(UserTable._ID);
        columnNames.add(UserTable.USER_OID);
        columnNames.add(UserTable.USERNAME);
        columnNames.add(UserTable.WEBFINGER_ID);
        columnNames.add(UserTable.REAL_NAME);
        columnNames.add(UserTable.DESCRIPTION);
        columnNames.add(UserTable.LOCATION);

        columnNames.add(UserTable.PROFILE_URL);
        columnNames.add(UserTable.HOMEPAGE);
        if (MyPreferences.getShowAvatars()) {
            columnNames.add(DownloadTable.AVATAR_FILE_NAME);
        }

        columnNames.add(UserTable.MSG_COUNT);
        columnNames.add(UserTable.FAVORITES_COUNT);
        columnNames.add(UserTable.FOLLOWING_COUNT);
        columnNames.add(UserTable.FOLLOWERS_COUNT);

        columnNames.add(UserTable.CREATED_DATE);
        columnNames.add(UserTable.UPDATED_DATE);
        columnNames.add(UserTable.ORIGIN_ID);
        return columnNames;
    }

}
