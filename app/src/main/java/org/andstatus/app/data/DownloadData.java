package org.andstatus.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;

import java.util.List;

public class DownloadData {
    private static final String TAG = DownloadData.class.getSimpleName();
    public static final DownloadData EMPTY = new DownloadData();

    private DownloadType downloadType = DownloadType.UNKNOWN;
    public long actorId = 0;
    public long noteId = 0;
    private MyContentType contentType = MyContentType.UNKNOWN;
    private DownloadStatus status = DownloadStatus.UNKNOWN; 
    private long downloadId = 0;
    private DownloadFile fileStored = DownloadFile.EMPTY;
    protected Uri uri = Uri.EMPTY;

    private boolean hardError = false;
    private boolean softError = false;
    private String errorMessage = "";

    private long loadTimeNew = 0;
    private DownloadFile fileNew = DownloadFile.EMPTY;

    public static DownloadData fromId(long downloadId) {
        DownloadData dd = new DownloadData();
        dd.downloadId = downloadId;
        dd.loadOtherFields();
        dd.fixFieldsAfterLoad();
        return dd;
    }

    /**
     * Currently we assume that there is no more than one attachment of a message
     */
    public static DownloadData getSingleForNote(long msgIdIn, MyContentType contentTypeIn, Uri uriIn) {
        DownloadData data = new DownloadData(0, msgIdIn, contentTypeIn, Uri.EMPTY);
        if (!UriUtils.isEmpty(uriIn) && !data.getUri().equals(uriIn)) {
            deleteAllOfThisNote(MyContextHolder.get().getDatabase(), msgIdIn);
            data = getThisForNote(msgIdIn, contentTypeIn, uriIn);
        }
        return data;
    }

    public static DownloadData getThisForNote(long msgIdIn, MyContentType contentTypeIn, Uri uriIn) {
        return new DownloadData(0, msgIdIn, contentTypeIn, uriIn);
    }

    protected DownloadData(long actorIdIn, long msgIdIn, MyContentType contentTypeIn, Uri uriIn) {
        switch (contentTypeIn) {
        case IMAGE:
            downloadType = (actorIdIn == 0) ? DownloadType.IMAGE : DownloadType.AVATAR;
            break;
        case TEXT:
            downloadType = DownloadType.TEXT;
            break;
        default:
            downloadType = DownloadType.UNKNOWN;
            hardError = true;
            break;
        }
        actorId = actorIdIn;
        noteId = msgIdIn;
        contentType = contentTypeIn;
        uri = UriUtils.notNull(uriIn);
        loadOtherFields();
        fixFieldsAfterLoad();
    }

    private DownloadData() {
        // Empty
    }

    private void loadOtherFields() {
        if (checkHardErrorBeforeLoad()) return;
        String sql = "SELECT " + DownloadTable.DOWNLOAD_STATUS + ", "
                + DownloadTable.FILE_NAME
                + (downloadType == DownloadType.UNKNOWN ? ", " + DownloadTable.DOWNLOAD_TYPE : "")
                + (actorId == 0 ? ", " + DownloadTable.ACTOR_ID : "")
                + (noteId == 0 ? ", " + DownloadTable.NOTE_ID : "")
                + (contentType == MyContentType.UNKNOWN ? ", " + DownloadTable.CONTENT_TYPE : "")
                + (downloadId == 0 ? ", " + DownloadTable._ID : "")
                + (uri.equals(Uri.EMPTY) ? ", " + DownloadTable.URI : "")
                + " FROM " + DownloadTable.TABLE_NAME
                + " WHERE " + getWhereClause();
        
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(this, "Database is null");
            softError = true;
            return;
        }
        try (Cursor cursor = db.rawQuery(sql, null)) {
            status = DownloadStatus.ABSENT;
            if (cursor.moveToNext()) {
                status = DownloadStatus.load(DbUtils.getLong(cursor, DownloadTable.DOWNLOAD_STATUS));
                fileStored = new DownloadFile(DbUtils.getString(cursor, DownloadTable.FILE_NAME));
                if (downloadType == DownloadType.UNKNOWN) {
                    downloadType = DownloadType.load(DbUtils.getLong(cursor, DownloadTable.DOWNLOAD_TYPE));
                }
                if (actorId == 0) {
                    actorId = DbUtils.getLong(cursor, DownloadTable.ACTOR_ID);
                }
                if (noteId == 0) {
                    noteId = DbUtils.getLong(cursor, DownloadTable.NOTE_ID);
                }
                if (contentType == MyContentType.UNKNOWN) {
                    contentType = MyContentType.load(DbUtils.getLong(cursor, DownloadTable.CONTENT_TYPE));
                }
                if (downloadId == 0) {
                    downloadId = DbUtils.getLong(cursor, DownloadTable._ID);
                }
                if (uri.equals(Uri.EMPTY)) {
                    uri = UriUtils.fromString(DbUtils.getString(cursor, DownloadTable.URI));
                }
            }
        }
    }

    private boolean checkHardErrorBeforeLoad() {
        if ((actorId != 0) && (noteId != 0)
                || (actorId == 0 && noteId == 0 && downloadId == 0)
                || (actorId != 0 && downloadType != DownloadType.AVATAR)) {
            hardError = true;
        }
        return hardError;
    }

    private String getWhereClause() {
        StringBuilder builder = new StringBuilder();
        if (actorId != 0) {
            builder.append(DownloadTable.ACTOR_ID + "=" + actorId);
        } else if (noteId != 0) {
            builder.append(DownloadTable.NOTE_ID + "=" + noteId);
        } else {
            builder.append(DownloadTable._ID + "=" + downloadId);
        }
        if (contentType != MyContentType.UNKNOWN) {
            builder.append(" AND " + DownloadTable.CONTENT_TYPE + "=" + contentType.save());
        }
        if (!UriUtils.isEmpty(uri)) {
            builder.append(" AND " + DownloadTable.URI + "=" + MyQuery.quoteIfNotQuoted(uri.toString()));
        }
        return builder.toString();
    }

    private void fixFieldsAfterLoad() {
        if ((actorId == 0) && (noteId == 0) || UriUtils.isEmpty(uri)) {
            hardError = true;
        }
        if (fileStored == null) {
            fileStored = DownloadFile.EMPTY;
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
            if (downloadId == 0) {
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
       values.put(DownloadTable.DOWNLOAD_TYPE, downloadType.save());
       if (actorId != 0) {
           values.put(DownloadTable.ACTOR_ID, actorId);
       }
       if (noteId != 0) {
           values.put(DownloadTable.NOTE_ID, noteId);
       }
       values.put(DownloadTable.CONTENT_TYPE, contentType.save());
       values.put(DownloadTable.VALID_FROM, loadTimeNew);
       values.put(DownloadTable.URI, uri.toString());
       values.put(DownloadTable.DOWNLOAD_STATUS, status.save());
       values.put(DownloadTable.FILE_NAME, fileNew.getFilename());

       downloadId = DbUtils.addRowWithRetry(MyContextHolder.get(), DownloadTable.TABLE_NAME, values, 3);
       if (downloadId == -1) {
           softError = true;
       } else {
           MyLog.v(this, "Added " + actorNoteUriToString());
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
        values.put(DownloadTable.DOWNLOAD_STATUS, status.save());
        boolean changeFile = !isError() && fileNew.exists() && fileStored != fileNew;
        if (changeFile) {
            values.put(DownloadTable.FILE_NAME, fileNew.getFilename());
            values.put(DownloadTable.VALID_FROM, loadTimeNew);
        }

        if (DbUtils.updateRowWithRetry(MyContextHolder.get(), DownloadTable.TABLE_NAME, downloadId, values, 3) != 1) {
            softError = true;
        } else {
            MyLog.v(this, "Updated " + actorNoteUriToString());
        }
        if (!isError() && changeFile) {
            fileStored.delete();
        }
    }

    public String actorNoteUriToString() {
        StringBuilder builder = new StringBuilder();
        if (actorId != 0) {
            builder.append("actorId=" + actorId + "; ");
        }
        if (noteId != 0) {
            builder.append("noteId=" + noteId + "; ");
        }
        builder.append("uri=" + (uri == Uri.EMPTY ? "(empty)" : uri.toString()) + "; ");
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
        errorMessage = (e == null ? "" : e.toString() + ", ") + message + "; " + actorNoteUriToString();
        MyLog.v(this, message + "; " + actorNoteUriToString(), e);
    }
    
    public void deleteOtherOfThisActor() {
        deleteOtherOfThisActor(actorId, downloadId);
    }

    public static void deleteAllOfThisActor(long actorId) {
        deleteOtherOfThisActor(actorId, 0);
    }
    
    public static void deleteOtherOfThisActor(long actorId, long rowId) {
        final String method = "deleteOtherOfThisActor actorId=" + actorId + (rowId != 0 ? ", downloadId=" + rowId : "");
        String where = DownloadTable.ACTOR_ID + "=" + actorId
                + (rowId != 0 ? " AND " + DownloadTable._ID + "<>" + Long.toString(rowId) : "") ;
        deleteSelected(method, MyContextHolder.get().getDatabase(), where);
    }

    private static void deleteSelected(final String method, SQLiteDatabase db, String where) {
        String sql = "SELECT " + DownloadTable._ID + ", "
                + DownloadTable.FILE_NAME
                + " FROM " + DownloadTable.TABLE_NAME
                + " WHERE " + where;
        int rowsDeleted = 0;
        boolean done = false;
        for (int pass=0; !done && pass<3; pass++) {
            if (db == null) {
                MyLog.v(TAG, "Database is null");
                return;
            }
            try (Cursor cursor = db.rawQuery(sql, null)) {
                while (cursor.moveToNext()) {
                    long rowIdOld = cursor.getLong(0);
                    new DownloadFile(cursor.getString(1)).delete();
                    rowsDeleted += db.delete(DownloadTable.TABLE_NAME, DownloadTable._ID + "=" + Long.toString(rowIdOld), null);
                }
                done = true;
            } catch (SQLiteException e) {
                MyLog.i(DownloadData.class, method + ", Database is locked, pass=" + pass + "; sql='" + sql + "'", e);
            }
            if (!done) {
                DbUtils.waitMs(method, 500);
            }
        }
        if (MyLog.isVerboseEnabled() && (!done || rowsDeleted>0)) {
            MyLog.v(DownloadData.class, method + (done ? " succeeded" : " failed") + "; deleted " + rowsDeleted + " rows");
        }
    }

    public static void deleteAllOfThisNote(SQLiteDatabase db, long noteId) {
        final String method = "deleteAllOfThisNote noteId=" + noteId;
        deleteSelected(method, db, DownloadTable.NOTE_ID + "=" + noteId);
    }

    public static void deleteOtherOfThisNote(long noteId, List<Long> downloadIds) {
        if (noteId == 0 || downloadIds == null || downloadIds.isEmpty()) {
            return;
        }
        final String method = "deleteOtherOfThisNote noteId=" + noteId + ", rowIds:" + toSqlList(downloadIds);
        String where = DownloadTable.NOTE_ID + "=" + noteId
                + " AND " + DownloadTable._ID + " NOT IN(" + toSqlList(downloadIds) + ")" ;
        deleteSelected(method, MyContextHolder.get().getDatabase(), where);
    }

    public static String toSqlList(List<Long> longs) {
        if (longs == null || longs.isEmpty()) {
            return "0";
        }
        String list = "";
        for (Long theLong : longs) {
            if (list.length() > 0) {
                list += ",";
            }
            list += Long.toString(theLong);
        }
        return list;
    }

    public DownloadFile getFile() {
        return fileStored;
    }
    
    public String getFilename() {
        return fileStored.getFilename();
    }
    
    public long getDownloadId() {
        return downloadId;
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
        if (!hardError && downloadId == 0) {
            saveToDatabase();
        }
        if (!DownloadStatus.LOADED.equals(status) && !hardError) {
            MyServiceManager.sendCommand(
                    actorId != 0 ?
                            CommandData.newActorCommand(CommandEnum.GET_AVATAR, null, null, actorId, "")
                            : CommandData.newFetchAttachment(noteId, downloadId));
        }
    }

    public String getMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("uri:'" + getUri() + "',");
        if(actorId != 0) {
            builder.append("actorId:" + actorId + ",");
        }
        if(noteId != 0) {
            builder.append("msgId:" + noteId + ",");
        }
        builder.append("status:" + getStatus() + ",");
        if(!TextUtils.isEmpty(errorMessage)) {
            builder.append("errorMessage:'" + getMessage() + "',");
        }
        if (!fileStored.equals(DownloadFile.EMPTY)) {
            builder.append("file:" + getFilename() + ",");
        }
        return MyLog.formatKeyValue(this, builder.toString());
    }

    public static void asyncRequestDownload(final long downloadId) {
        AsyncTaskLauncher.execute(TAG, false,
                new MyAsyncTask<Void, Void, Void>(TAG + downloadId, MyAsyncTask.PoolEnum.FILE_DOWNLOAD) {
                    @Override
                    protected Void doInBackground2(Void... params) {
                        DownloadData.fromId(downloadId).requestDownload();
                        return null;
                    }
                }
        );
    }

    public Uri mediaUriToBePosted() {
      if (getUri().equals(Uri.EMPTY) || UriUtils.isDownloadable(getUri())) {
          return Uri.EMPTY;
      }
      return FileProvider.downloadFilenameToUri(getFile().getFilename());
    }
}
