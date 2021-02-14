package org.andstatus.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.graphics.MediaMetadata;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TaggedClass;
import org.andstatus.app.util.UriUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

public class DownloadData implements IsEmpty, TaggedClass {
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
    private long fileSize = 0;
    protected Uri uri = Uri.EMPTY;
    MediaMetadata mediaMetadata = MediaMetadata.EMPTY;
    private long previewOfDownloadId = 0;

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

    public static DownloadData fromAttachment(long noteId, Attachment attachment) {
        return new DownloadData(null, attachment.getDownloadId(), 0, noteId,
                attachment.contentType, attachment.mimeType, DownloadType.ATTACHMENT, attachment.uri);
    }

    public static List<DownloadData> fromNoteId(MyContext myContext, long noteId) {
        if (myContext.isEmptyOrExpired() || noteId == 0) return Collections.emptyList();

        String sql = "SELECT *"
                + " FROM " + DownloadTable.TABLE_NAME
                + " WHERE " + DownloadTable.NOTE_ID + "=" + noteId
                + " ORDER BY " + DownloadTable.DOWNLOAD_NUMBER;
        return MyQuery.foldLeft(myContext, sql, new ArrayList<>(), list -> cursor -> {
            list.add(DownloadData.fromCursor(cursor));
            return list;
        });
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
        SQLiteDatabase db = myContextHolder.getNow().getDatabase();
        if (db == null) {
            MyLog.databaseIsNull(() -> this);
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
        if (StringUtil.isEmpty(mimeType)) {
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
        if (previewOfDownloadId == 0) {
            previewOfDownloadId = DbUtils.getLong(cursor, DownloadTable.PREVIEW_OF_DOWNLOAD_ID);
        }
        if (downloadNumber == 0) {
            downloadNumber = DbUtils.getLong(cursor, DownloadTable.DOWNLOAD_NUMBER);
        }
        if (uri.equals(Uri.EMPTY)) {
            uri = UriUtils.fromString(DbUtils.getString(cursor, DownloadTable.URL));
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
                where.append(DownloadTable.URL + "=" + MyQuery.quoteIfNotQuoted(uri.toString()));
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
                    myContextHolder.getNow().context().getContentResolver(), uri, mimeType);
        }
    }

    public void beforeDownload() {
        softError = false;
        hardError = false;
        if (downloadId == 0) saveToDatabase();
        fileNew = new DownloadFile(downloadType.filePrefix + "_" + Long.toString(downloadId)
                + "_" + downloadNumber
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
        if (StringUtil.nonEmpty(fileExtension)) return fileExtension;

        final String fileExtension2 = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        if (StringUtil.nonEmpty(fileExtension2)) return fileExtension2;

        final String fileExtension3 = MimeTypeMap.getFileExtensionFromUrl(fileStored.getFilename());
        if (StringUtil.nonEmpty(fileExtension3)) return fileExtension3;

        MyLog.d(this, "Failed to find file extension " + this);
        return "png";
    }

    public void setDownloadNumber(long downloadNumber) {
        this.downloadNumber = downloadNumber;
    }

    public void setPreviewOfDownloadId(long previewOfDownloadId) {
        this.previewOfDownloadId = previewOfDownloadId;
    }

    public long getPreviewOfDownloadId() {
        return previewOfDownloadId;
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
        DbUtils.addRowWithRetry(myContextHolder.getNow(), DownloadTable.TABLE_NAME, values, 3)
        .onSuccess(idAdded -> {
            downloadId = idAdded;
            MyLog.v(this, () -> "Added " + actorNoteUriToString());
        })
        .onFailure(e -> {
            softError = true;
            MyLog.w(this, "Failed to add " + actorNoteUriToString());
        });
    }

    private void update() {
        ContentValues values = toContentValues();
        DbUtils.updateRowWithRetry(myContextHolder.getNow(), DownloadTable.TABLE_NAME, downloadId, values, 3)
        .onSuccess(o -> MyLog.v(this, () -> "Updated " + actorNoteUriToString()))
        .onFailure(e -> softError = true);

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
            ContentValuesUtils.putNotZero(values, DownloadTable.PREVIEW_OF_DOWNLOAD_ID, previewOfDownloadId);
        }
        values.put(DownloadTable.DOWNLOAD_NUMBER, downloadNumber);
        values.put(DownloadTable.URL, uri.toString());
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
    
    public void deleteOtherOfThisActor(MyContext myContext) {
        deleteOtherOfThisActor(myContext, actorId, downloadId);
    }

    public static void deleteAllOfThisActor(MyContext myContext, long actorId) {
        deleteOtherOfThisActor(myContext, actorId, 0);
    }
    
    private static void deleteOtherOfThisActor(MyContext myContext, long actorId, long rowId) {
        if (actorId == 0) return;

        final String method = "deleteOtherOfThisActor actorId=" + actorId + (rowId != 0 ? ", downloadId=" + rowId : "");
        String where = DownloadTable.ACTOR_ID + "=" + actorId
                + (rowId == 0 ? "" : " AND " + DownloadTable._ID + "<>" + rowId) ;
        deleteSelected(method, myContext.getDatabase(), where);
    }

    private static void deleteSelected(final String method, SQLiteDatabase db, String where) {
        String sql = "SELECT " + DownloadTable._ID + ", " + DownloadTable.FILE_NAME
                + " FROM " + DownloadTable.TABLE_NAME
                + " WHERE " + where;
        int rowsDeleted = 0;
        boolean done = false;
        for (int pass=0; pass<3; pass++) {
            if (db == null) {
                MyLog.databaseIsNull(() -> TAG);
                return;
            }
            try (Cursor cursor = db.rawQuery(sql, null)) {
                while (cursor.moveToNext()) {
                    long rowIdOld = DbUtils.getLong(cursor, DownloadTable._ID);
                    new DownloadFile(DbUtils.getString(cursor, DownloadTable.FILE_NAME)).delete();
                    rowsDeleted += db.delete(DownloadTable.TABLE_NAME, DownloadTable._ID
                            + "=" + rowIdOld, null);
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

    public long getDownloadNumber() {
        return downloadNumber;
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

    public void requestDownload(MyContext myContext) {
        if (!hardError && downloadId == 0) {
            saveToDatabase();
        }
        if ((!DownloadStatus.LOADED.equals(status) || !fileStored.existed) && !hardError && uri != Uri.EMPTY) {
            MyServiceManager.sendCommand(actorId != 0
                    ? CommandData.newActorCommand(CommandEnum.GET_AVATAR, Actor.load(myContext, actorId), "")
                    : CommandData.newFetchAttachment(noteId, downloadId));
        }
    }

    public String getMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        MyStringBuilder builder = new MyStringBuilder();
        builder.withComma("id", downloadId, i -> i != 0);
        builder.withComma("num", downloadNumber, i -> i > 0);
        builder.withCommaQuoted("uri", getUri(), true);
        builder.withComma("mime", mimeType);
        builder.withComma("actorId", actorId, i -> i != 0);
        builder.withComma("noteId", noteId, i -> i != 0);
        builder.withComma("previewOf", previewOfDownloadId, i -> i != 0);
        builder.withComma("status", getStatus());
        builder.withCommaQuoted("errorMessage",getMessage(), true);
        if (fileStored.existed) {
            builder.withComma("file", getFilename());
            builder.withComma("size", fileSize);
            if (mediaMetadata.nonEmpty()) builder.withComma(mediaMetadata.toString());
        }
        return MyStringBuilder.formatKeyValue(this, builder.toString());
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

    public MyContentType getContentType() {
        return contentType;
    }

    public String getMimeType() {
        return mimeType;
    }

    public static class ConsumedSummary {
        long skippedSize = 0;
        long consumedCount = 0;
        long consumedSize = 0;
    }

    @Override
    public String classTag() {
        return TAG;
    }
}
