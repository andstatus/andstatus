package org.andstatus.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyDatabase.Download;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;

public class DownloadData {
    private DownloadType downloadType = DownloadType.UNKNOWN;
    public long userId = 0;
    public long msgId = 0;
    private MyContentType contentType = MyContentType.UNKNOWN;
    private DownloadStatus status = DownloadStatus.UNKNOWN; 
    private long rowId = 0;
    private DownloadFile fileStored = DownloadFile.getEmpty();
    protected Uri uri = null;

    private boolean hardError = false;
    private boolean softError = false;
    private String errorMessage = "";

    private long loadTimeNew = 0;
    private DownloadFile fileNew = DownloadFile.getEmpty();

    protected DownloadData(long userIdIn, String uriString) {
        downloadType = DownloadType.AVATAR;
        userId = userIdIn;
        contentType = MyContentType.IMAGE;
        uri = UriUtils.fromString(uriString);
        loadOtherFields();
        fixFieldsAfterLoad();
    }

    public static DownloadData fromRowId(long rowIdIn) {
        DownloadData dd = new DownloadData();
        dd.rowId = rowIdIn;
        dd.loadOtherFields();
        dd.fixFieldsAfterLoad();
        return dd;
    }
    
    public static DownloadData newForMessage(long msgIdIn, MyContentType contentTypeIn, Uri uriIn) {
        return new DownloadData(msgIdIn, contentTypeIn, uriIn);
    }
    
    private DownloadData(long msgIdIn, MyContentType contentTypeIn, Uri uriIn) {
        switch (contentTypeIn) {
        case IMAGE:
            downloadType = DownloadType.IMAGE;
            break;
        case TEXT:
            downloadType = DownloadType.TEXT;
            break;
        default:
            downloadType = DownloadType.UNKNOWN;
            hardError = true;
            break;
        }
        userId = 0;
        msgId = msgIdIn;
        contentType = contentTypeIn;
        uri = uriIn;
        loadOtherFields();
        fixFieldsAfterLoad();
    }

    private DownloadData() {
        // Empty
    }

    private void loadOtherFields() {
        if (hardError) {
            return;
        }
        String sql = "SELECT " + Download.DOWNLOAD_STATUS + ", "
                + Download.FILE_NAME
                + (downloadType == DownloadType.UNKNOWN ? ", " + Download.DOWNLOAD_TYPE : "")
                + (userId == 0 ? ", " + Download.USER_ID : "")
                + (msgId == 0 ? ", " + Download.MSG_ID : "")
                + (contentType == MyContentType.UNKNOWN ? ", " + Download.CONTENT_TYPE : "")
                + (rowId == 0 ? ", " + Download._ID : "")
                + (uri == null ? ", " + Download.URI : "")
                + " FROM " + Download.TABLE_NAME 
                + " WHERE " + getWhereClause();
        
        SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, null);
            status = DownloadStatus.ABSENT;
            if (cursor.moveToNext()) {
                status = DownloadStatus.load(cursor.getLong(cursor.getColumnIndex(Download.DOWNLOAD_STATUS)));
                fileStored = new DownloadFile(cursor.getString(cursor.getColumnIndex(Download.FILE_NAME)));
                if (downloadType == DownloadType.UNKNOWN) {
                    downloadType = DownloadType.load(cursor.getLong(cursor.getColumnIndex(Download.DOWNLOAD_TYPE)));
                }
                if (userId == 0) {
                    userId = cursor.getLong(cursor.getColumnIndex(Download.USER_ID));
                }
                if (msgId == 0) {
                    msgId = cursor.getLong(cursor.getColumnIndex(Download.MSG_ID));
                }
                if (contentType == MyContentType.UNKNOWN) {
                    contentType = MyContentType.load(cursor.getLong(cursor.getColumnIndex(Download.CONTENT_TYPE)));
                }
                if (rowId == 0) {
                    rowId = cursor.getLong(cursor.getColumnIndex(Download._ID));
                }
                if (uri == null) {
                    uri = UriUtils.fromString(cursor.getString(cursor.getColumnIndex(Download.URI)));
                }
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
    }

    protected String getWhereClause() {
        StringBuilder builder = new StringBuilder();
        if (userId != 0) {
            builder.append(Download.USER_ID + "=" + userId);
        } else if (msgId != 0) {
            builder.append(Download.MSG_ID + "=" + msgId  + optionalUriWhereClause());
        } else {
            builder.append(Download._ID + "=" + rowId  + optionalUriWhereClause());
        }
        builder.append(optionalUriWhereClause());
        return builder.toString();
    }

    private String optionalUriWhereClause() {
        return uri != null ? " AND " + Download.URI + "=" + MyQuery.quoteIfNotQuoted(uri.toString()) : "";
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
        return TextUtils.isEmpty(MyContentType.getExtension(uri.toString())) ? "" : "."
                + (MyContentType.getExtension(uri.toString()));
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
       values.put(Download.URI, uri.toString());
       values.put(Download.DOWNLOAD_STATUS, status.save());
       values.put(Download.FILE_NAME, fileNew.getFilename());

       rowId = DbUtils.addRowWithRetry(Download.TABLE_NAME, values, 3);
       if (rowId == -1) {
           softError = true;
       } else {
           MyLog.v(this, "Added " + userMsgUriToString());
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
            values.put(Download.FILE_NAME, fileNew.getFilename());
            values.put(Download.VALID_FROM, loadTimeNew);
        }

        if (DbUtils.updateRowWithRetry(Download.TABLE_NAME, rowId, values, 3) != 1) {
            softError = true;
        } else {
            MyLog.v(this, "Updated " + userMsgUriToString());
        }
        if (!isError() && changeFile) {
            fileStored.delete();
        }
    }

    public String userMsgUriToString() {
        StringBuilder builder = new StringBuilder();
        if (userId != 0) {
            builder.append("userId=" + userId + "; ");
        }
        if (msgId != 0) {
            builder.append("msgId=" + msgId + "; ");
        }
        builder.append("uri=" + (uri == null ? "(null)" : uri.toString()) + "; ");
        return builder.toString();
    }
    
    public void hardErrorLogged(String message, Exception e) {
        hardError = true;
        logError(message, e);
    }
    
    public void softErrorLogged(String message, Exception e) {
        softError = true;
        logError(message, e);
    }

    private void logError(String message, Exception e) {
        errorMessage = (e == null ? "" : e.toString() + ", ") + message + "; " + userMsgUriToString();
        MyLog.v(this, message + "; " + userMsgUriToString(), e);
    }
    
    public void deleteOtherOfThisUser() {
        deleteOtherOfThisUser(userId, rowId);
    }

    public static void deleteAllOfThisUser(long userId) {
        deleteOtherOfThisUser(userId, 0);
    }
    
    public static void deleteOtherOfThisUser(long userId, long rowId) {
        final String method = "deleteOtherOfThisUser userId=" + userId + (rowId != 0 ? ", rowId=" + rowId : "");
        String where = Download.USER_ID + "=" + userId
                + (rowId != 0 ? " AND " + Download._ID + "<>" + Long.toString(rowId) : "") ;
        deleteSelected(method, where);
    }

    private static void deleteSelected(final String method, String where) {
        String sql = "SELECT " + Download._ID + ", "
                + Download.FILE_NAME
                + " FROM " + Download.TABLE_NAME 
                + " WHERE " + where;
        int rowsDeleted = 0;
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
                MyLog.i(DownloadData.class, method + ", Database is locked, pass=" + pass + "; sql='" + sql + "'", e);
            } finally {
                DbUtils.closeSilently(cursor);
            }
            if (!done) {
                try {
                    Thread.sleep(Math.round((Math.random() + 1) * 500));
                } catch (InterruptedException e) {
                    MyLog.e(DownloadData.class, e);
                }
            }
        }
        if (!done || rowsDeleted>0) {
            MyLog.v(DownloadData.class, method + (done ? " succeeded" : " failed") + "; deleted " + rowsDeleted + " rows");
        }
    }

    public static void deleteAllOfThisMsg(long msgId) {
        final String method = "deleteAllOfThisMsg msgId=" + msgId;
        deleteSelected(method, Download.MSG_ID + "=" + msgId);
    }
    
    public DownloadFile getFile() {
        return fileStored;
    }
    
    public String getFilename() {
        return fileStored.getFilename();
    }
    
    public long getRowId() {
        return rowId;
    }

    public DownloadStatus getStatus() {
        return status;
    }

    public String getFilenameNew() {
        return fileNew.getFilename();
    }

    public Uri getUri() {
        return uri;
    }

    public void requestDownload() {
        if (!hardError && rowId == 0) {
            saveToDatabase();
        }
        if (!DownloadStatus.LOADED.equals(status) && !hardError) {
            MyServiceManager.sendCommand(
                    userId != 0 ? new CommandData(CommandEnum.FETCH_AVATAR, null, userId)
                            : CommandData.fetchAttachment(msgId, rowId));
        }
    }

    public String getMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.getClass().getSimpleName());
        builder.append("; Uri:'" + getUri() + "'");
        if(userId != 0) {
            builder.append("; userId:" + userId);
        }
        if(msgId != 0) {
            builder.append("; msgId:" + msgId);
        }
        builder.append("; status:" + getStatus());
        if(!TextUtils.isEmpty(errorMessage)) {
            builder.append("; errorMessage:'" + getMessage() + "'");
        }
        
        return builder.toString();
    }

    public static void asyncRequestDownload(final long downloadRowId) {
        new AsyncTask<Void, Void, Void>(){
            @Override
            protected Void doInBackground(Void... params) {
                DownloadData.fromRowId(downloadRowId).requestDownload();
                return null;
            }
        }.execute();
    }
}
