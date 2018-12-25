package org.andstatus.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import androidx.annotation.NonNull;
import android.webkit.MimeTypeMap;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.graphics.MediaMetadata;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UriUtils;

import java.util.List;
import java.util.function.Consumer;

public class DownloadData implements IsEmpty {
    private static final String TAG = DownloadData.class.getSimpleName();
    public static final DownloadData EMPTY = new DownloadData(null, 0, 0, 0, MyContentType.UNKNOWN, "",
            DownloadType.UNKNOWN, Uri.EMPTY);
    private DownloadType downloadType = DownloadType.UNKNOWN;
    public long actorId = 0;
    public long noteId = 0;
    private MyContentType contentType = MyContentType.UNKNOWN;
    private String mimeType = "";
    private DownloadStatus status = DownloadStatus.UNKNOWN;
    private long downloadId = 0;
    private long downloadNumber = 0;
    @NonNull
    private DownloadFile fileStored = DownloadFile.EMPTY;
    public long fileSize = 0;
    protected Uri uri = Uri.EMPTY;
    public MediaMetadata mediaMetadata = MediaMetadata.EMPTY;

    private boolean hardError = false;
    private boolean softError = false;
    private String errorMessage = "";

    private long downloadedDate = RelativeTime.DATETIME_MILLIS_NEVER;

    @NonNull
    private DownloadFile fileNew = DownloadFile.EMPTY;

    public static DownloadData fromCursor(@NonNull Cursor cursor) {
        return new DownloadData(cursor, 0, 0, 0, MyContentType.UNKNOWN, "",
                DownloadType.UNKNOWN, Uri.EMPTY);
    }

    public static DownloadData fromId(long downloadId) {
        return new DownloadData(null, downloadId, 0, 0, MyContentType.UNKNOWN, "",
                DownloadType.UNKNOWN, Uri.EMPTY);
    }

    /**
     * Currently we assume that there is no more than one attachment of a message
     */
    public static DownloadData getSingleAttachment(long noteId) {
        return new DownloadData(null, 0, 0, noteId, MyContentType.UNKNOWN, "",
                DownloadType.ATTACHMENT, Uri.EMPTY);
    }

    public static DownloadData getThisForAttachment(long noteId, Attachment attachment) {
        return new DownloadData(null, 0, 0, noteId,
                attachment.contentType, attachment.mimeType, DownloadType.ATTACHMENT, attachment.uri);
    }

    protected DownloadData(Cursor cursor, long downloadId, long actorId, long noteId, MyContentType contentType,
                           String mimeType, DownloadType downloadType, Uri uri) {
        this.downloadId = downloadId;
        this.actorId = actorId;
        this.noteId = noteId;
        this.downloadType = downloadType;
        this.contentType = contentType;
        this.mimeType = mimeType;
        this.uri = UriUtils.notNull(uri);
        if (cursor == null) {
            loadOtherFields();
        } else {
            loadFromCursor(cursor);
        }
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
                loadFromCursor(cursor);
            } else if (actorId != 0) {
                downloadNumber = MyQuery.getLongs("SELECT MAX(" + DownloadTable.DOWNLOAD_NUMBER + ")" +
                    " FROM " + DownloadTable.TABLE_NAME +
                    " WHERE " + DownloadTable.ACTOR_ID + "=" + actorId + " AND " +
                    DownloadTable.DOWNLOAD_TYPE + "=" + downloadType.save())
                    .stream().findAny().orElse(-1L) + 1;
            }
        }
    }

    private void loadFromCursor(@NonNull Cursor cursor) {
        status = DownloadStatus.load(DbUtils.getLong(cursor, DownloadTable.DOWNLOAD_STATUS));
        fileStored = new DownloadFile(DbUtils.getString(cursor, DownloadTable.FILE_NAME));
        if (downloadType == DownloadType.UNKNOWN) {
            downloadType = DownloadType.load(DbUtils.getLong(cursor, DownloadTable.DOWNLOAD_TYPE));
        }
        if (contentType == MyContentType.UNKNOWN) {
            contentType = MyContentType.load(DbUtils.getLong(cursor, DownloadTable.CONTENT_TYPE));
        }
        if (StringUtils.isEmpty(mimeType)) {
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
        mediaMetadata = MediaMetadata.fromCursor(cursor);
        fileSize = DbUtils.getLong(cursor, DownloadTable.FILE_SIZE);
        downloadedDate = DbUtils.getLong(cursor, DownloadTable.DOWNLOADED_DATE);
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
            if (UriUtils.isEmpty(uri)) {
                where.append(DownloadTable.DOWNLOAD_NUMBER + "=" + downloadNumber);
            } else {
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
           onNoFile();
        } else if (DownloadStatus.HARD_ERROR == status) {
            hardError = true;
        }
        if (contentType == MyContentType.UNKNOWN) {
            contentType = MyContentType.fromUri(DownloadType.ATTACHMENT,
                    MyContextHolder.get().context().getContentResolver(), uri, mimeType);
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
            if (!fileNew.existed) onNoFile();
            return;
        }
        fileSize = fileNew.getSize();
        mediaMetadata = MediaMetadata.fromFilePath(fileNew.getFilePath());
        downloadedDate = System.currentTimeMillis();
    }

    private void onNoFile() {
        if (DownloadStatus.LOADED == status) status = DownloadStatus.ABSENT;
        fileSize = 0;
        mediaMetadata = MediaMetadata.EMPTY;
        downloadedDate = RelativeTime.DATETIME_MILLIS_NEVER;
    }

    private String getExtension() {
        final String fileExtension = MyContentType.mimeToFileExtension(mimeType);
        if (StringUtils.nonEmpty(fileExtension)) return fileExtension;

        final String fileExtension2 = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        if (StringUtils.nonEmpty(fileExtension2)) return fileExtension2;

        final String fileExtension3 = MimeTypeMap.getFileExtensionFromUrl(fileStored.getFilename());
        if (StringUtils.nonEmpty(fileExtension3)) return fileExtension3;

        MyLog.d(this, "Failed to find file extension " + this);
        return "png";
    }

    public void setDownloadNumber(long downloadNumber) {
        this.downloadNumber = downloadNumber;
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
        if (status == DownloadStatus.LOADED && downloadType == DownloadType.AVATAR) {
            downloadNumber = 0;
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
            MyLog.v(this, () -> "Saved " + this);
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
            MyLog.v(this, () -> "Added " + actorNoteUriToString());
        }
    }

    private void update() {
        ContentValues values = toContentValues();
        if (DbUtils.updateRowWithRetry(MyContextHolder.get(), DownloadTable.TABLE_NAME, downloadId, values, 3) != 1) {
            softError = true;
        } else {
            MyLog.v(this, () -> "Updated " + actorNoteUriToString());
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
            values.put(DownloadTable.DOWNLOAD_TYPE, downloadType.save());
            ContentValuesUtils.putNotZero(values, DownloadTable.ACTOR_ID, actorId);
            ContentValuesUtils.putNotZero(values, DownloadTable.NOTE_ID, noteId);
        }
        values.put(DownloadTable.DOWNLOAD_NUMBER, downloadNumber);
        values.put(DownloadTable.URI, uri.toString());
        values.put(DownloadTable.CONTENT_TYPE, contentType.save());
        values.put(DownloadTable.MEDIA_TYPE, mimeType);
        values.put(DownloadTable.DOWNLOAD_STATUS, status.save());
        values.put(DownloadTable.FILE_NAME, fileNew.getFilename());
        values.put(DownloadTable.FILE_SIZE, fileSize);
        mediaMetadata.toContentValues(values);
        values.put(DownloadTable.DOWNLOADED_DATE, downloadedDate);
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
    
    private static void deleteOtherOfThisActor(long actorId, long rowId) {
        if (actorId == 0) return;

        final String method = "deleteOtherOfThisActor actorId=" + actorId + (rowId != 0 ? ", downloadId=" + rowId : "");
        String where = DownloadTable.ACTOR_ID + "=" + actorId
                + (rowId == 0 ? "" : " AND " + DownloadTable._ID + "<>" + Long.toString(rowId)) ;
        deleteSelected(method, MyContextHolder.get().getDatabase(), where);
    }

    private static void deleteSelected(final String method, SQLiteDatabase db, String where) {
        String sql = "SELECT " + DownloadTable._ID + ", " + DownloadTable.FILE_NAME
                + " FROM " + DownloadTable.TABLE_NAME
                + " WHERE " + where;
        int rowsDeleted = 0;
        boolean done = false;
        for (int pass=0; pass<3; pass++) {
            if (db == null) {
                MyLog.v(TAG, "Database is null");
                return;
            }
            try (Cursor cursor = db.rawQuery(sql, null)) {
                while (cursor.moveToNext()) {
                    long rowIdOld = DbUtils.getLong(cursor, DownloadTable._ID);
                    new DownloadFile(DbUtils.getString(cursor, DownloadTable.FILE_NAME)).delete();
                    rowsDeleted += db.delete(DownloadTable.TABLE_NAME, DownloadTable._ID
                            + "=" + Long.toString(rowIdOld), null);
                }
                done = true;
            } catch (SQLiteException e) {
                MyLog.i(DownloadData.class, method + ", Database error, pass=" + pass + "; sql='" + sql + "'", e);
            }
            if (done) break;
            DbUtils.waitMs(method, 500);
        }
        if (MyLog.isVerboseEnabled() && (!done || rowsDeleted>0)) {
            MyLog.v(DownloadData.class, method + (done ? " succeeded" : " failed")
                    + "; deleted " + rowsDeleted + " rows");
        }
    }

    public void deleteFile() {
        if (fileStored.existed) {
            fileStored.delete();
            if (fileStored.existsNow()) return;

            hardError = false;
            softError = false;
            onNoFile();
            saveToDatabase();
        }
    }

    public static void deleteAllOfThisNote(SQLiteDatabase db, long noteId) {
        if (noteId == 0) return;

        final String method = "deleteAllOfThisNote noteId=" + noteId;
        deleteSelected(method, db, DownloadTable.NOTE_ID + "=" + noteId);
    }

    public static void deleteOtherOfThisNote(MyContext myContext, long noteId, @NonNull List<Long> downloadIds) {
        if (noteId == 0 || downloadIds.isEmpty()) return;

        final String method = "deleteOtherOfThisNote noteId=" + noteId + ", rowIds:" + toSqlList(downloadIds);
        String where = DownloadTable.NOTE_ID + "=" + noteId
                + " AND " + DownloadTable._ID + " NOT IN(" + toSqlList(downloadIds) + ")" ;
        deleteSelected(method, myContext.getDatabase(), where);
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
        if ((!DownloadStatus.LOADED.equals(status) || !fileStored.existed) && !hardError) {
            MyServiceManager.sendCommand(actorId != 0
                    ? CommandData.newActorCommand(CommandEnum.GET_AVATAR, actorId, "")
                    : CommandData.newFetchAttachment(noteId, downloadId));
        }
    }

    public String getMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        MyStringBuilder builder = new MyStringBuilder();
        if(downloadNumber > 0) builder.withComma("num:" + downloadNumber);
        builder.withComma("uri:'" + getUri() + "'");
        if (StringUtils.nonEmpty(mimeType)) builder.withComma("mime:" + mimeType);
        if(actorId != 0) {
            builder.withComma("actorId:" + actorId);
        }
        if(noteId != 0) {
            builder.withComma("msgId:" + noteId);
        }
        builder.withComma("status:" + getStatus());
        if(StringUtils.nonEmpty(errorMessage)) {
            builder.withComma("errorMessage:'" + getMessage() + "'");
        }
        if (fileStored.existed) {
            builder.withComma("file:" + getFilename());
            builder.withComma("size:" + fileSize);
            if (mediaMetadata.nonEmpty()) builder.withComma(mediaMetadata.toString());
        }
        return MyLog.formatKeyValue(this, builder.toString());
    }

    public Uri mediaUriToBePosted() {
      if (isEmpty() || UriUtils.isDownloadable(getUri())) {
          return Uri.EMPTY;
      }
      return FileProvider.downloadFilenameToUri(getFile().getFilename());
    }

    @Override
    public boolean isEmpty() {
        return this == EMPTY || uri.equals(Uri.EMPTY);
    }

    static ConsumedSummary pruneFiles(MyContext myContext, DownloadType downloadType, long bytesToKeep) {
        return consumeOldest(myContext, downloadType, bytesToKeep, DownloadData::deleteFile);
    }

    private static ConsumedSummary consumeOldest(MyContext myContext, DownloadType downloadType, long totalSizeToSkip,
                                                 Consumer<DownloadData> consumer) {
        final String sql = "SELECT *"
                + " FROM " + DownloadTable.TABLE_NAME
                + " WHERE " + DownloadTable.DOWNLOAD_TYPE + "='" + downloadType.save() + "'"
                + " AND " + DownloadTable.DOWNLOAD_STATUS + "=" + DownloadStatus.LOADED.save()
                + " ORDER BY " + DownloadTable.DOWNLOADED_DATE + " DESC";
        return MyQuery.foldLeft(myContext, sql,
                new ConsumedSummary(),
                summary -> cursor -> {
                        DownloadData data = DownloadData.fromCursor(cursor);
                        if (data.fileStored.existed) {
                            if (summary.skippedSize < totalSizeToSkip) {
                                summary.skippedSize += data.fileSize;
                            } else {
                                summary.consumedCount += 1;
                                summary.consumedSize += data.fileSize;
                                consumer.accept(data);
                            }
                        }
                    return summary;
                }
            );
    }

    public long getDownloadedDate() {
        return downloadedDate;
    }

    public static class ConsumedSummary {
        long skippedSize = 0;
        long consumedCount = 0;
        long consumedSize = 0;
    }

}
