/*
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

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.net.social.Actor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ActorListSql {
    private ActorListSql() {
        // Empty
    }

    /**
     * @param uri the same as uri for
     *            {@link MyProvider#query(Uri, String[], String, String[], String)}
     * @param projection Projection
     * @return String for {@link SQLiteQueryBuilder#setTables(String)}
     */
    static String tablesForList(Uri uri, String[] projection) {
        return Actor.getActorAndUserSqlTables(false,
                Arrays.asList(projection).contains(DownloadTable.AVATAR_FILE_NAME));
    }

    /**
     * Table columns to use for an Actor item content
     */
    public static String[] getListProjection() {
        return getBaseProjection().toArray(new String[]{});
    }

    private static List<String> getBaseProjection() {
        List<String> columnNames = new ArrayList<>();
        columnNames.add(ActorTable._ID);
        columnNames.add(ActorTable.ACTOR_OID);
        columnNames.add(ActorTable.USERNAME);
        columnNames.add(ActorTable.WEBFINGER_ID);
        columnNames.add(ActorTable.REAL_NAME);
        columnNames.add(ActorTable.DESCRIPTION);
        columnNames.add(ActorTable.LOCATION);

        columnNames.add(ActorTable.PROFILE_URL);
        columnNames.add(ActorTable.HOMEPAGE);
        if (MyPreferences.getShowAvatars()) {
            columnNames.add(DownloadTable.AVATAR_FILE_NAME);
        }

        columnNames.add(ActorTable.NOTES_COUNT);
        columnNames.add(ActorTable.FAVORITES_COUNT);
        columnNames.add(ActorTable.FOLLOWING_COUNT);
        columnNames.add(ActorTable.FOLLOWERS_COUNT);

        columnNames.add(ActorTable.CREATED_DATE);
        columnNames.add(ActorTable.UPDATED_DATE);
        columnNames.add(ActorTable.ORIGIN_ID);
        return columnNames;
    }

}
