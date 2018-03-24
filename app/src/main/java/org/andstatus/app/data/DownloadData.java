package org.andstatus.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.graphics.ImageCache;
import org.andstatus.app.graphics.MediaMetadata;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UriUtils;

import java.util.List;

public class DownloadData {
    private static final String TAG = DownloadData.class.getSimpleName();
    public static final DownloadData EMPTY = new DownloadData(0, 0, 0, "",
            DownloadType.UNKNOWN, Uri.EMPTY);
    private DownloadType downloadType = DownloadType.UNKNOWN;
    public long actorId = 0;
    public long noteId = 0;
    private String mimeType = "";
    private DownloadStatus status = DownloadStatus.UNKNOWN;
    private long downloadId = 0;
    private long downloadNumber = 0;
    @NonNull
    private DownloadFile fileStored = DownloadFile.EMPTY;
    public long fileSize = 0;
    protected Uri uri = Uri.EMPTY;
    public long width = 0;
    public long height = 0;
    public long duration = 0;

    private boolean hardError = false;
    private boolean softError = false;
    private String errorMessage = "";

    @NonNull
    private DownloadFile fileNew = DownloadFile.EMPTY;

    public static DownloadData fromId(long downloadId) {
        return new DownloadData(downloadId, 0, 0, "", DownloadType.UNKNOWN, Uri.EMPTY);
    }

    /**
     * Currently we assume that there is no more than one attachment of a message
     */
    public static DownloadData getSingleAttachment(long noteId) {
        return new DownloadData(0, 0, noteId, "", DownloadType.ATTACHMENT, Uri.EMPTY);
    }

    public static DownloadData getThisForNote(long noteId, String mimeType, DownloadType downloadType, Uri uriIn) {
        return new DownloadData(0, 0, noteId, mimeType, downloadType, uriIn);
    }

    protected DownloadData(long downloadId, long actorId, long noteId, String mimeType, DownloadType downloadType,
                           Uri uri) {
        this.downloadId = downloadId;
        this.actorId = actorId;
        this.noteId = noteId;
        this.downloadType = downloadType;
        this.mimeType = mimeType;
        this.uri = UriUtils.notNull(uri);
        loadOtherFields();
        fixFieldsAfterLoad();
    }

    private void loadOtherFields() {
        if (checkHardErrorBeforeLoad()) return;
        String sql = "SELECT * FROM " + DownloadTable.TABLE_NAME + getWhere().getWhere();
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
                if (TextUtils.isEmpty(mimeType)) {
                    mimeType = DbUtils.getString(cursor, DownloadTable.MEDIA_TYPE,
                            () -> MyContentType.uri2MimeType(null, Uri.parse(fileStored.getFilename())));
                }
                if (actorId == 0) {
                    actorId = DbUtils.getLong(cursor, DownloadTable.ACTOR_ID);
                }
                if (noteId == 0) {
                    noteId = DbUtils.getLong(cursor, DownloadTable.NOTE_ID);
                }
                if (downloadId == 0) {
                    downloadId = DbUtils.getLong(cursor, DownloadTable._ID);
                }
                if (downloadNumber == 0) {
                    downloadNumber = DbUtils.getLong(cursor, DownloadTable.DOWNLOAD_NUMBER);
                }
                if (uri.equals(Uri.EMPTY)) {
                    uri = UriUtils.fromString(DbUtils.getString(cursor, DownloadTable.URI));
                }
                width = DbUtils.getLong(cursor, DownloadTable.WIDTH);
                height = DbUtils.getLong(cursor, DownloadTable.HEIGHT);
                duration = DbUtils.getLong(cursor, DownloadTable.DURATION);
                fileSize = DbUtils.getLong(cursor, DownloadTable.FILE_SIZE);
            }
        }
    }

    private boolean checkHardErrorBeforeLoad() {
        if ( downloadId == 0 && downloadType == DownloadType.UNKNOWN) {
            hardError = true;
        }
        if (((actorId != 0) && (noteId != 0))
            || (actorId == 0 && noteId == 0 && downloadId == 0)
            || (actorId != 0 && downloadType != DownloadType.AVATAR)) {
            hardError = true;
        }
        return hardError;
    }

    private SqlWhere getWhere() {
        SqlWhere where = new SqlWhere();
        if (downloadId != 0) {
            where.append(DownloadTable._ID + "=" + downloadId);
            where.append(DownloadTable.DOWNLOAD_NUMBER + "=" + downloadNumber);
        } else {
            if (actorId != 0) {
                where.append(DownloadTable.ACTOR_ID + "=" + actorId);
            } else if (noteId != 0) {
                where.append(DownloadTable.NOTE_ID + "=" + noteId);
            }
            if (downloadType != DownloadType.UNKNOWN) {
                where.append(DownloadTable.DOWNLOAD_TYPE + "=" + downloadType.save());
            }
            if (UriUtils.nonEmpty(uri)) {
                where.append(DownloadTable.URI + "=" + MyQuery.quoteIfNotQuoted(uri.toString()));
            }
        }
        return where;
    }

    private void fixFieldsAfterLoad() {
        if ((actorId == 0) && (noteId == 0) || UriUtils.isEmpty(uri)) {
            hardError = true;
        }
        fileNew = fileStored;
        if (hardError) {
            status = DownloadStatus.HARD_ERROR;
        } else if (DownloadStatus.LOADED == status && !fileStored.existsNow()) {
           status = DownloadStatus.ABSENT;
        } else if (DownloadStatus.HARD_ERROR == status) {
            hardError = true;
        }
    }

    public void beforeDownload() {
        softError = false;
        hardError = false;
        if (downloadId == 0) saveToDatabase();
        fileNew = new DownloadFile(downloadType.filePrefix + "_" + Long.toString(downloadId)
                + "_" + Long.toString(downloadNumber)
                + "." + getExtension());
    }

    public void onDownloaded() {
        fileNew = new DownloadFile(fileNew.getFilename());
        if (isError() || !fileNew.existed) {
            fileSize = 0;
            width = 0;
            height = 0;
            duration = 0;
            return;
        }
        fileSize = fileNew.getSize();
        MediaMetadata metadata = ImageCache.getMetadata(fileNew.getFilePath());
        width = metadata.size.x;
        height = metadata.size.y;
        duration = metadata.duration;
    }

    private String getExtension() {
        final String fileExtension = TextUtils.isEmpty(mimeType) ? MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                : MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        return TextUtils.isEmpty(fileExtension)
                ? MimeTypeMap.getFileExtensionFromUrl(fileStored.getFilename())
                : fileExtension;
    }
    
    public void saveToDatabase() {
        if (hardError) {
            status = DownloadStatus.HARD_ERROR;
        } else if (!fileNew.existsNow()) {
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
        ContentValues values = toContentValues();
        downloadId = DbUtils.addRowWithRetry(MyContextHolder.get(), DownloadTable.TABLE_NAME, values, 3);
        if (downloadId == -1) {
            softError = true;
        } else {
            MyLog.v(this, "Added " + actorNoteUriToString());
        }
    }

    private void update() {
        ContentValues values = toContentValues();
        if (DbUtils.updateRowWithRetry(MyContextHolder.get(), DownloadTable.TABLE_NAME, downloadId, values, 3) != 1) {
            softError = true;
        } else {
            MyLog.v(this, "Updated " + actorNoteUriToString());
        }
        boolean filenameChanged = !isError() && fileNew.existsNow()
                && !fileStored.getFilename().equals(fileNew.getFilename());
        if (filenameChanged) {
            fileStored.delete();
        }
    }

    private ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        if (downloadId == 0) {
            values.put(DownloadTable.DOWNLOAD_NUMBER, downloadNumber);
            values.put(DownloadTable.DOWNLOAD_TYPE, downloadType.save());
            ContentValuesUtils.putNotZero(values, DownloadTable.ACTOR_ID, actorId);
            ContentValuesUtils.putNotZero(values, DownloadTable.NOTE_ID, noteId);
        }
        values.put(DownloadTable.URI, uri.toString());
        values.put(DownloadTable.MEDIA_TYPE, mimeType);
        values.put(DownloadTable.DOWNLOAD_STATUS, status.save());
        values.put(DownloadTable.FILE_NAME, fileNew.getFilename());
        values.put(DownloadTable.FILE_SIZE, fileSize);
        values.put(DownloadTable.WIDTH, width);
        values.put(DownloadTable.HEIGHT, height);
        values.put(DownloadTable.DURATION, duration);
        return values;
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
            MyServiceManager.sendCommand(actorId != 0
                    ? CommandData.newActorCommand(CommandEnum.GET_AVATAR, MyAccount.EMPTY, Origin.EMPTY, actorId, "")
                    : CommandData.newFetchAttachment(noteId, downloadId));
        }
    }

    public String getMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if(downloadNumber > 0) builder.append("num:" + downloadNumber + ",");
        builder.append("uri:'" + getUri() + "',");
        if (StringUtils.nonEmpty(mimeType)) builder.append("mime:" + getUri() + ",");
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
        if (fileStored.existed) {
            builder.append("file:" + getFilename() + ",");
            builder.append("size:" + fileSize + ",");
            if (width > 0) builder.append("width:" + width + ",");
            if (height > 0) builder.append("height:" + height + ",");
            if (duration > 0) builder.append("duration:" + height + ",");
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
      if (isEmpty() || UriUtils.isDownloadable(getUri())) {
          return Uri.EMPTY;
      }
      return FileProvider.downloadFilenameToUri(getFile().getFilename());
    }

    public boolean nonEmpty() {
        return !isEmpty();
    }

    public boolean isEmpty() {
        return this == EMPTY || uri.equals(Uri.EMPTY);
    }
}
