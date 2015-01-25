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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.Display;
import android.view.WindowManager;

import org.andstatus.app.context.MyContextHolder;
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
            String path = downloadFile.getFile().getAbsolutePath();
            return drawableFromPath(this, path);
        } 
        DownloadData.fromRowId(downloadRowId).requestDownload();
        return null;
    }

    public static final double MAX_ATTACHED_IMAGE_PART = 0.75;

    public static Drawable drawableFromPath(Object objTag, String path) {
        Bitmap bitmap = BitmapFactory
                .decodeFile(path, calculateScaling(objTag, getImageSize(path)));
        if (MyLog.isLoggable(MyLog.VERBOSE)) {
            MyLog.v(objTag, "Loaded bitmap " + bitmap.getWidth() + "x" + bitmap.getHeight()
                    + " '" + path + "'");
        }
        return new BitmapDrawable(MyContextHolder.get().context().getResources(), bitmap);
    }

    private static Point getImageSize(String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        return new Point(options.outWidth, options.outHeight);
    }

    private static BitmapFactory.Options calculateScaling(Object objTag,
            Point imageSize) {
        BitmapFactory.Options options2 = new BitmapFactory.Options();
        Point displaySize = getDisplaySize(MyContextHolder.get().context());
        int maxHeight = (int) (MAX_ATTACHED_IMAGE_PART * displaySize.y);
        if (imageSize.y > maxHeight || imageSize.x > displaySize.x) {
            options2.inSampleSize = (int) Math.floor(imageSize.y / maxHeight);
            int inSampleSize2 = (int) Math.floor(imageSize.x / displaySize.x);
            if (options2.inSampleSize < inSampleSize2) {
                options2.inSampleSize = inSampleSize2;
            }
            if (options2.inSampleSize < 2) {
                options2.inSampleSize = 2;
            }
            MyLog.v(objTag, "Large bitmap " + imageSize.x + "x" + imageSize.y
                    + " scaling by=" + options2.inSampleSize);
        }
        return options2;
    }

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
