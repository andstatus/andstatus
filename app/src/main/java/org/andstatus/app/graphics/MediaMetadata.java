/*
 * Copyright (c) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.graphics;

import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Point;
import android.support.annotation.NonNull;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.util.MyLog;

public class MediaMetadata {
    public static final MediaMetadata EMPTY = new MediaMetadata(0, 0, 0);
    public final int width;
    public final int height;
    public final long duration;

    @NonNull
    public static MediaMetadata fromCursor(Cursor cursor) {
        return new MediaMetadata(
                DbUtils.getInt(cursor, DownloadTable.WIDTH),
                DbUtils.getInt(cursor, DownloadTable.HEIGHT),
                DbUtils.getLong(cursor, DownloadTable.DURATION)
        );
    }

    public MediaMetadata(int width, int height, long duration) {
        this.width = width;
        this.height = height;
        this.duration = duration;
    }

    public Point size() {
        return new Point(width, height);
    }

    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }

    public void toContentValues(ContentValues values) {
        values.put(DownloadTable.WIDTH, width);
        values.put(DownloadTable.HEIGHT, height);
        values.put(DownloadTable.DURATION, duration);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (width > 0) builder.append("width:" + width + ",");
        if (height > 0) builder.append("height:" + height + ",");
        if (duration > 0) builder.append("duration:" + height + ",");
        return MyLog.formatKeyValue(this, builder.toString());
    }

    public boolean nonEmpty() {
        return !isEmpty();
    }

    public String toDetails() {
        return nonEmpty()
                ? width + "x" + height + (duration == 0 ? "" : " " + duration)
                : "";
    }
}
