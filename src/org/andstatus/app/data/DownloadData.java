package org.andstatus.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase.Download;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;

import java.net.MalformedURLException;
import java.net.URL;

public class DownloadData {
    private final DownloadType downloadType;
    public final long userId;
    public final long msgId;
    private ContentType contentType = ContentType.UNKNOWN;
    private DownloadStatus status = DownloadStatus.UNKNOWN; 
    private long rowId = 0;
    private DownloadFile fileStored = DownloadFile.getEmpty();
    protected URL url = null;

    private boolean hardError = false;
    private boolean softError = false;

    private long loadTimeNew = 0;
    private DownloadFile fileNew = DownloadFile.getEmpty();

    protected DownloadData(long userIdIn, String urlString) {
        downloadType = DownloadType.AVATAR;
        userId = userIdIn;
        msgId = 0;
        contentType = ContentType.IMAGE;
        url = stringToUrl(urlString);
        if (!hardError) {
            loadOtherFields();
        }
        fixFieldsAfterLoad();
    }

    private URL stringToUrl(String urlString) {
        hardError = TextUtils.isEmpty(urlString);
        URL url1 = null;
        if (!hardError) {
            try {
                url1 = new URL(urlString);
            } catch (MalformedURLException e) {
                hardErrorLogged("Bad url='" + urlString + "'", e);
            }
        }
        return url1;
    }
    
    private void loadOtherFields() {
        String sql = "SELECT " + Download.DOWNLOAD_STATUS + ", "
                + Download._ID + ", "
                + Download.FILE_NAME
                + " FROM " + Download.TABLE_NAME 
                + " WHERE " + getWhereClause();
        
        SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, null);
            status = DownloadStatus.ABSENT;
            while (cursor.moveToNext()) {
                status = DownloadStatus.load(cursor.getInt(0));
                rowId = cursor.getLong(1);
                fileStored = new DownloadFile(cursor.getString(2));
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
    }

    protected String getWhereClause() {
        if (userId != 0) {
            return Download.USER_ID + "=" + userId
                    + " AND " + Download.URL + "=" + MyProvider.quoteIfNotQuoted(url.toExternalForm());
        } else {
            return Download.MSG_ID + "=" + msgId
                    + " AND " + Download.URL + "=" + MyProvider.quoteIfNotQuoted(url.toExternalForm());
        }
    }

    private void fixFieldsAfterLoad() {
        if ((userId == 0) == (msgId == 0)) {
            hardError = true;
        }
        if (fileStored == null) {
            fileStored = DownloadFile.getEmpty();
        }
        fileNew = fileStored;
        if (hardError) {
            status = DownloadStatus.HARD_ERROR;
        } else if (DownloadStatus.LOADED.equals(status) 
                && !fileStored.exists()) {
           status = DownloadStatus.ABSENT;
        } else if (DownloadStatus.HARD_ERROR.equals(status)) {
            hardError = true;
        }
    }

    public void onNewDownload() {
        softError = false;
        hardError = false;
        loadTimeNew =  System.currentTimeMillis();
        fileNew = new DownloadFile(Long.toString(loadTimeNew)
                + "_"
                + Long.toString(InstanceId.next())
                + getOptionalExtension());
    }

    private String getOptionalExtension() {
        return TextUtils.isEmpty(ContentType.getExtension(url.toExternalForm())) ? "" : "."
                + (ContentType.getExtension(url.toExternalForm()));
    }
    
    public void saveToDatabase() {
        if (hardError) {
            status = DownloadStatus.HARD_ERROR;
        } else if (!fileNew.exists()) {
            status = DownloadStatus.ABSENT;
        } else if (softError) {
            status = DownloadStatus.SOFT_ERROR;
        } else {
            status = DownloadStatus.LOADED;
        }
        try {
            if (rowId == 0) {
                addNew();
            } else {
                update();
            }
            if (!isError()) {
                fileStored = fileNew;
            }
        } catch (Exception e) {
            softErrorLogged("Couldn't save to database", e);
        }
    }

    private void addNew() {
       ContentValues values = new ContentValues();
       values.put(Download.DOWNLOAD_TYPE, downloadType.save());
       if (userId != 0) {
           values.put(Download.USER_ID, userId);
       }
       if (msgId != 0) {
           values.put(Download.MSG_ID, msgId);
       }
       values.put(Download.CONTENT_TYPE, contentType.save());
       values.put(Download.VALID_FROM, loadTimeNew);
       values.put(Download.URL, url.toExternalForm());
       values.put(Download.LOADED_DATE, loadTimeNew);
       values.put(Download.DOWNLOAD_STATUS, status.save());
       values.put(Download.FILE_NAME, fileNew.getFileName());

       rowId = DbUtils.addRowWithRetry(Download.TABLE_NAME, values, 3);
       if (rowId == -1) {
           softError = true;
       } else {
           MyLog.v(this, "Added userId=" + userId + "; url=" + url.toExternalForm());
       }
    }

    public boolean isHardError() {
        return hardError;
    }
    
    public boolean isSoftError() {
        return softError;
    }

    public boolean isError() {
        return softError || hardError;
    }
    
    private void update() {
        ContentValues values = new ContentValues();
        values.put(Download.DOWNLOAD_STATUS, status.save());
        boolean changeFile = !isError() && fileNew.exists() && fileStored != fileNew;
        if (changeFile) {
            values.put(Download.FILE_NAME, fileNew.getFileName());
            values.put(Download.VALID_FROM, loadTimeNew);
        }
        values.put(Download.LOADED_DATE, loadTimeNew);

        if (DbUtils.updateRowWithRetry(Download.TABLE_NAME, rowId, values, 3) != 1) {
            softError = true;
        } else {
            MyLog.v(this, "Updated userId=" + userId + "; url=" + url.toExternalForm());
        }
        if (!isError() && changeFile) {
            fileStored.delete();
        }
    }

    public void hardErrorLogged(String message, Exception e) {
        hardError = true;
        MyLog.i(this, message 
                + "; userId=" + userId 
                + "; url=" + (url == null ? "(null)" : url.toExternalForm()), e);
    }

    public void softErrorLogged(String message, Exception e) {
        softError = true;
        MyLog.v(this, message 
                + "; userId=" + userId 
                + "; url=" + (url == null ? "(null)" : url.toExternalForm()), e);
    }
    
    public void deleteOtherOfThisUser() {
        deleteOtherOfThisUser(userId, rowId);
    }

    public static void deleteAllOfThisUser(long userId) {
        deleteOtherOfThisUser(userId, 0);
    }
    
    public static void deleteOtherOfThisUser(long userId, long rowId) {
        final String method = "deleteOtherOfThisUser userId=" + userId + (rowId != 0 ? ", rowId=" + rowId : "");
        int rowsDeleted = 0;
        String where = Download.USER_ID + "=" + userId
                + (rowId != 0 ? " AND " + Download._ID + "<>" + Long.toString(rowId) : "") ;
        String sql = "SELECT " + Download._ID + ", "
                + Download.FILE_NAME
                + " FROM " + Download.TABLE_NAME 
                + " WHERE " + where;
        boolean done = false;
        for (int pass=0; !done && pass<3; pass++) {
            SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();
            Cursor cursor = null;
            try {
                cursor = db.rawQuery(sql, null);
                while (cursor.moveToNext()) {
                    long rowIdOld = cursor.getLong(0);
                    new DownloadFile(cursor.getString(1)).delete();
                    rowsDeleted += db.delete(Download.TABLE_NAME, Download._ID + "=" + Long.toString(rowIdOld), null);
                }
                done = true;
            } catch (SQLiteException e) {
                MyLog.i(AvatarData.class, method + ", Database is locked, pass=" + pass + "; sql='" + sql + "'", e);
            } finally {
                DbUtils.closeSilently(cursor);
            }
            if (!done) {
                try {
                    Thread.sleep(Math.round((Math.random() + 1) * 500));
                } catch (InterruptedException e) {
                    MyLog.e(AvatarData.class, e);
                }
            }
        }
        if (!done || rowsDeleted>0) {
            MyLog.v(AvatarData.class, method + (done ? " succeeded" : " failed") + "; deleted " + rowsDeleted + " rows");
        }
    }

    public DownloadFile getFile() {
        return fileStored;
    }
    
    public String getFileName() {
        return fileStored.getFileName();
    }
    
    public long getRowId() {
        return rowId;
    }

    public DownloadStatus getStatus() {
        return status;
    }

    public String getFileNameNew() {
        return fileNew.getFileName();
    }

    public URL getUrl() {
        return url;
    }

    public void requestDownload() {
        if (!hardError && rowId == 0) {
            saveToDatabase();
        }
        if (!DownloadStatus.LOADED.equals(status) && !hardError) {
            MyServiceManager.sendCommand(
                    userId != 0 ? new CommandData(CommandEnum.FETCH_AVATAR, null, userId)
                            : new CommandData(CommandEnum.FETCH_ATTACHMENT, null, rowId));
        }
    }

}
