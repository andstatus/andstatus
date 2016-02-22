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

import android.content.Context;
import android.database.Cursor;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.view.Display;
import android.view.WindowManager;

import org.andstatus.app.util.MyLog;

public class AttachedImageDrawable {
    private final long downloadRowId;
    private final DownloadFile downloadFile;
    private Point size = null;
    
    public static Drawable drawableFromCursor(Cursor cursor) {
        long imageRowId = DbUtils.getLong(cursor, MyDatabase.Download.IMAGE_ID);
        if (imageRowId == 0) {
            return null;
        } else {
            return new AttachedImageDrawable(imageRowId,
                    DbUtils.getString(cursor,
                            MyDatabase.Download.IMAGE_FILE_NAME)).getDrawable();
        }
    }
    
    public AttachedImageDrawable(long downloadRowIdIn, String filename) {
        downloadRowId = downloadRowIdIn;
        downloadFile = new DownloadFile(filename);
    }

    public Point getSize() {
        if (size == null && downloadFile.exists()) {
            size = MyImageCache.getImageSize(downloadFile.getFile().getAbsolutePath());
        }
        return size == null ? new Point() : size;
    }

    public Drawable getDrawable() {
        if (downloadFile.exists()) {
            String path = downloadFile.getFile().getAbsolutePath();
            return MyImageCache.getAttachedImageDrawable(this, path);
        }
        if (downloadRowId == 0) {
            // TODO: Why we get here?
            MyLog.d(this, "rowId=0; " + downloadFile);
        } else {
            DownloadData.asyncRequestDownload(downloadRowId);
        }
        return null;
    }

    public static final double MAX_ATTACHED_IMAGE_PART = 0.75;

    @Override
    public String toString() {
        return MyLog.objTagToString(this) + " [rowId=" + downloadRowId + ", " + downloadFile + "]";
    }

    /**
     * See http://stackoverflow.com/questions/1016896/how-to-get-screen-dimensions
     */
    public static Point getDisplaySize(Context context) {
        Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size;
    }
}
