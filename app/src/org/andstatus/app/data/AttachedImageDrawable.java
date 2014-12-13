/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.database.Cursor;
import android.graphics.drawable.Drawable;

import org.andstatus.app.util.MyLog;

public class AttachedImageDrawable {
    private final long downloadRowId;
    private final DownloadFile downloadFile;
    
    public static Drawable drawableFromCursor(Cursor cursor) {
        int columnIndex = cursor.getColumnIndex(MyDatabase.Download.IMAGE_ID);
        Long imageRowId = null;
        if (columnIndex >= 0) {
            imageRowId = cursor.getLong(columnIndex);
        }
        if (imageRowId == null || imageRowId == 0L) {
            return null;
        } else {
            return new AttachedImageDrawable(imageRowId, cursor.getString(cursor
                    .getColumnIndex(MyDatabase.Download.IMAGE_FILE_NAME))).getDrawable();
        }
    }
    
    public AttachedImageDrawable(long downloadRowIdIn, String filename) {
        downloadRowId = downloadRowIdIn;
        downloadFile = new DownloadFile(filename);
    }
    
    public Drawable getDrawable() {
        if (downloadFile.exists()) {
            return Drawable.createFromPath(downloadFile.getFile().getAbsolutePath());
        } 
        DownloadData.fromRowId(downloadRowId).requestDownload();
        return null;
    }

    @Override
    public String toString() {
        return MyLog.objTagToString(this) + " [rowId=" + downloadRowId + ", " + downloadFile + "]";
    }
}
