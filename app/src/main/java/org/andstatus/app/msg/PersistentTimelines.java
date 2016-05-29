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

package org.andstatus.app.msg;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.TimelineTable;
import org.andstatus.app.util.MyLog;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author yvolk@yurivolkov.com
 */
public class PersistentTimelines {
    private final Map<Long,Timeline> timelines = new TreeMap<>();

    private PersistentTimelines() {
        // Empty
    }

    public PersistentTimelines initialize() {
        return initialize(MyContextHolder.get());
    }

    public PersistentTimelines initialize(MyContext myContext) {
        final String method = "initialize";
        Context context = myContext.context();

        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.d(context, method + "; Database is unavailable");
        }
        timelines.clear();
        String sql = "SELECT * FROM " + TimelineTable.TABLE_NAME;
        Cursor c = null;
        try {
            c = db.rawQuery(sql, null);
            while (c.moveToNext()) {
                Timeline cd = Timeline.fromCursor(c);
                if (!cd.isValid()) {
                    MyLog.e(context, method + "; invalid skipped " + cd);
                } else {
                    timelines.put(1L, cd);
                    if (MyLog.isVerboseEnabled() && timelines.size() < 5) {
                        MyLog.v(context, method + "; " + cd);
                    }
                }
            }
        } finally {
            DbUtils.closeSilently(c);
        }
        MyLog.v(this, "Timelines initialized, " + timelines.size() + " timelines");
        return this;
    }

    public Timeline fromId(long id) {
        Timeline timelineFound = Timeline.getEmpty();
        if (id != 0) {
            for (Timeline timeline : timelines.values()) {
                if (timeline.getId() == id) {
                    timelineFound = timeline;
                    break;
                }
            }
        }
        return timelineFound;
    }

}
