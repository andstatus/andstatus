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
    
    public AttachedImageDrawable(long downloadRowIdIn, String fileName) {
        downloadRowId = downloadRowIdIn;
        downloadFile = new DownloadFile(fileName);
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
