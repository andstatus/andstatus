package org.andstatus.app.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.BaseColumns
import android.webkit.MimeTypeMap
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.MyContentType.Companion.uri2MimeType
import org.andstatus.app.database.table.DownloadTable
import org.andstatus.app.graphics.MediaMetadata
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Attachment
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.TaggedClass
import org.andstatus.app.util.UriUtils
import java.util.*
import java.util.function.Consumer
import java.util.function.Function

open class DownloadData protected constructor(
    cursor: Cursor?,
    private var downloadId: Long,
    var actorId: Long,
    var noteId: Long,
    private var contentType: MyContentType,
    private var mimeType: String,
    private var downloadType: DownloadType,
    uri: Uri?
) : IsEmpty, TaggedClass {

    private var status: DownloadStatus = DownloadStatus.UNKNOWN
    private var downloadNumber: Long = 0
    private var fileStored: DownloadFile = DownloadFile.EMPTY
    private var fileSize: Long = 0
    private var uri = Uri.EMPTY
    var mediaMetadata: MediaMetadata = MediaMetadata.EMPTY
    private var previewOfDownloadId: Long = 0
    private var hardError = false
    private var softError = false
    private var errorMessage: String = ""
    private var downloadedDate = RelativeTime.DATETIME_MILLIS_NEVER
    private var fileNew: DownloadFile = DownloadFile.EMPTY

    init {
        this.uri = UriUtils.notNull(uri)
        if (cursor == null) {
            loadOtherFields()
        } else {
            loadFromCursor(cursor)
        }
        fixFieldsAfterLoad()
    }

    private fun loadOtherFields() {
        if (checkHardErrorBeforeLoad()) return
        val sql = "SELECT * FROM " + DownloadTable.TABLE_NAME + getWhere().getWhere()
        val db: SQLiteDatabase? =  MyContextHolder.myContextHolder.getNow().database
        if (db == null) {
            MyLog.databaseIsNull { this }
            softError = true
            return
        }
        db.rawQuery(sql, null).use { cursor ->
            status = DownloadStatus.ABSENT
            if (cursor.moveToNext()) {
                loadFromCursor(cursor)
            } else if (actorId != 0L) {
                downloadNumber = MyQuery.getLongs("SELECT MAX(" + DownloadTable.DOWNLOAD_NUMBER + ")" +
                        " FROM " + DownloadTable.TABLE_NAME +
                        " WHERE " + DownloadTable.ACTOR_ID + "=" + actorId + " AND " +
                        DownloadTable.DOWNLOAD_TYPE + "=" + downloadType.save())
                        .stream().findAny().orElse(-1L) + 1
            }
        }
    }

    private fun loadFromCursor(cursor: Cursor) {
        status = DownloadStatus.load(DbUtils.getLong(cursor, DownloadTable.DOWNLOAD_STATUS))
        fileStored = DownloadFile(DbUtils.getString(cursor, DownloadTable.FILE_NAME))
        if (downloadType == DownloadType.UNKNOWN) {
            downloadType = DownloadType.load(DbUtils.getLong(cursor, DownloadTable.DOWNLOAD_TYPE))
        }
        if (contentType == MyContentType.UNKNOWN) {
            contentType = MyContentType.load(DbUtils.getLong(cursor, DownloadTable.CONTENT_TYPE))
        }
        if (mimeType.isEmpty()) {
            mimeType = DbUtils.getString(cursor, DownloadTable.MEDIA_TYPE)
                { uri2MimeType(null, Uri.parse(fileStored.getFilename())) }
        }
        if (actorId == 0L) {
            actorId = DbUtils.getLong(cursor, DownloadTable.ACTOR_ID)
        }
        if (noteId == 0L) {
            noteId = DbUtils.getLong(cursor, DownloadTable.NOTE_ID)
        }
        if (downloadId == 0L) {
            downloadId = DbUtils.getLong(cursor, BaseColumns._ID)
        }
        if (previewOfDownloadId == 0L) {
            previewOfDownloadId = DbUtils.getLong(cursor, DownloadTable.PREVIEW_OF_DOWNLOAD_ID)
        }
        if (downloadNumber == 0L) {
            downloadNumber = DbUtils.getLong(cursor, DownloadTable.DOWNLOAD_NUMBER)
        }
        if (uri == Uri.EMPTY) {
            uri = UriUtils.fromString(DbUtils.getString(cursor, DownloadTable.URL))
        }
        mediaMetadata = MediaMetadata.fromCursor(cursor)
        fileSize = DbUtils.getLong(cursor, DownloadTable.FILE_SIZE)
        downloadedDate = DbUtils.getLong(cursor, DownloadTable.DOWNLOADED_DATE)
    }

    private fun checkHardErrorBeforeLoad(): Boolean {
        if (downloadId == 0L && downloadType == DownloadType.UNKNOWN) {
            hardError = true
        }
        if (actorId != 0L && noteId != 0L
                || actorId == 0L && noteId == 0L && downloadId == 0L
                || actorId != 0L && downloadType != DownloadType.AVATAR) {
            hardError = true
        }
        return hardError
    }

    private fun getWhere(): SqlWhere {
        val where = SqlWhere()
        if (downloadId != 0L) {
            where.append(BaseColumns._ID + "=" + downloadId)
        } else {
            if (actorId != 0L) {
                where.append(DownloadTable.ACTOR_ID + "=" + actorId)
            } else if (noteId != 0L) {
                where.append(DownloadTable.NOTE_ID + "=" + noteId)
            }
            if (downloadType != DownloadType.UNKNOWN) {
                where.append(DownloadTable.DOWNLOAD_TYPE + "=" + downloadType.save())
            }
            if (UriUtils.isEmpty(uri)) {
                where.append(DownloadTable.DOWNLOAD_NUMBER + "=" + downloadNumber)
            } else {
                where.append(DownloadTable.URL + "=" + MyQuery.quoteIfNotQuoted(uri.toString()))
            }
        }
        return where
    }

    private fun fixFieldsAfterLoad() {
        if (actorId == 0L && noteId == 0L || UriUtils.isEmpty(uri)) {
            hardError = true
        }
        fileNew = fileStored
        if (hardError) {
            status = DownloadStatus.HARD_ERROR
        } else if (DownloadStatus.LOADED == status && !fileStored.existsNow()) {
            onNoFile()
        } else if (DownloadStatus.HARD_ERROR == status) {
            hardError = true
        }
        if (contentType == MyContentType.UNKNOWN) {
            contentType = MyContentType.fromUri(DownloadType.ATTACHMENT,
                     MyContextHolder.myContextHolder.getNow().context.getContentResolver(), uri, mimeType)
        }
    }

    fun beforeDownload() {
        softError = false
        hardError = false
        if (downloadId == 0L) saveToDatabase()
        fileNew = DownloadFile(downloadType.filePrefix + "_" + java.lang.Long.toString(downloadId)
                + "_" + downloadNumber
                + "." + getExtension())
    }

    fun onDownloaded() {
        fileNew = DownloadFile(fileNew.getFilename())
        if (isError() || !fileNew.existed) {
            if (!fileNew.existed) onNoFile()
            return
        }
        fileSize = fileNew.getSize()
        MediaMetadata.fromFilePath(fileNew.getFilePath()).onSuccess {
            mediaMetadata = it
            downloadedDate = System.currentTimeMillis()
        } .onFailure {
            MyLog.w(this, "Failed to load metadata for $this", it)
        }
    }

    private fun onNoFile() {
        if (DownloadStatus.LOADED == status) status = DownloadStatus.ABSENT
        fileSize = 0
        mediaMetadata = MediaMetadata.EMPTY
        downloadedDate = RelativeTime.DATETIME_MILLIS_NEVER
    }

    private fun getExtension(): String {
        val fileExtension: String = MyContentType.mimeToFileExtension(mimeType)
        if (fileExtension.isNotEmpty()) return fileExtension
        val fileExtension2 = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        if (!fileExtension2.isNullOrEmpty()) return fileExtension2
        val fileExtension3 = MimeTypeMap.getFileExtensionFromUrl(fileStored.getFilename())
        if (!fileExtension3.isNullOrEmpty()) return fileExtension3
        MyLog.d(this, "Failed to find file extension $this")
        return "png"
    }

    fun setDownloadNumber(downloadNumber: Long) {
        this.downloadNumber = downloadNumber
    }

    fun setPreviewOfDownloadId(previewOfDownloadId: Long) {
        this.previewOfDownloadId = previewOfDownloadId
    }

    fun getPreviewOfDownloadId(): Long {
        return previewOfDownloadId
    }

    fun saveToDatabase() {
        status = if (hardError) {
            DownloadStatus.HARD_ERROR
        } else if (!fileNew.existsNow()) {
            DownloadStatus.ABSENT
        } else if (softError) {
            DownloadStatus.SOFT_ERROR
        } else {
            DownloadStatus.LOADED
        }
        if (status == DownloadStatus.LOADED && downloadType == DownloadType.AVATAR) {
            downloadNumber = 0
        }
        try {
            if (downloadId == 0L) {
                addNew()
            } else {
                update()
            }
            if (!isError()) {
                fileStored = fileNew
            }
            MyLog.v(this) { "Saved $this" }
        } catch (e: Exception) {
            softErrorLogged("Couldn't save to database", e)
        }
    }

    private fun addNew() {
        val values = toContentValues()
        DbUtils.addRowWithRetry( MyContextHolder.myContextHolder.getNow(), DownloadTable.TABLE_NAME, values, 3)
                .onSuccess { idAdded: Long ->
                    downloadId = idAdded
                    MyLog.v(this) { "Added " + actorNoteUriToString() }
                }
                .onFailure { e: Throwable? ->
                    softError = true
                    MyLog.w(this, "Failed to add " + actorNoteUriToString())
                }
    }

    private fun update() {
        val values = toContentValues()
        DbUtils.updateRowWithRetry( MyContextHolder.myContextHolder.getNow(), DownloadTable.TABLE_NAME, downloadId, values, 3)
                .onSuccess { _ -> MyLog.v(this) { "Updated " + actorNoteUriToString() } }
                .onFailure { _ -> softError = true }
        val filenameChanged = (!isError() && fileNew.existsNow()
                && fileStored.getFilename() != fileNew.getFilename())
        if (filenameChanged) {
            fileStored.delete()
        }
    }

    private fun toContentValues(): ContentValues {
        val values = ContentValues()
        if (downloadId == 0L) {
            values.put(DownloadTable.DOWNLOAD_TYPE, downloadType.save())
            ContentValuesUtils.putNotZero(values, DownloadTable.ACTOR_ID, actorId)
            ContentValuesUtils.putNotZero(values, DownloadTable.NOTE_ID, noteId)
            ContentValuesUtils.putNotZero(values, DownloadTable.PREVIEW_OF_DOWNLOAD_ID, previewOfDownloadId)
        }
        values.put(DownloadTable.DOWNLOAD_NUMBER, downloadNumber)
        values.put(DownloadTable.URL, uri.toString())
        values.put(DownloadTable.CONTENT_TYPE, contentType.save())
        values.put(DownloadTable.MEDIA_TYPE, mimeType)
        values.put(DownloadTable.DOWNLOAD_STATUS, status.save())
        values.put(DownloadTable.FILE_NAME, fileNew.getFilename())
        values.put(DownloadTable.FILE_SIZE, fileSize)
        mediaMetadata.toContentValues(values)
        values.put(DownloadTable.DOWNLOADED_DATE, downloadedDate)
        return values
    }

    fun isHardError(): Boolean {
        return hardError
    }

    fun isSoftError(): Boolean {
        return softError
    }

    fun isError(): Boolean {
        return softError || hardError
    }

    fun actorNoteUriToString(): String {
        val builder = StringBuilder()
        if (actorId != 0L) {
            builder.append("actorId=$actorId; ")
        }
        if (noteId != 0L) {
            builder.append("noteId=$noteId; ")
        }
        builder.append("uri=" + (if (uri === Uri.EMPTY) "(empty)" else uri.toString()) + "; ")
        return builder.toString()
    }

    fun hardErrorLogged(message: String?, e: Exception?) {
        hardError = true
        logError(message, e)
    }

    fun softErrorLogged(message: String?, e: Exception?) {
        softError = true
        logError(message, e)
    }

    private fun logError(message: String?, e: Exception?) {
        errorMessage = (if (e == null) "" else "$e, ") + message + "; " + actorNoteUriToString()
        MyLog.v(this, message + "; " + actorNoteUriToString(), e)
    }

    fun deleteOtherOfThisActor(myContext: MyContext) {
        deleteOtherOfThisActor(myContext, actorId, downloadId)
    }

    fun deleteFile() {
        if (fileStored.existed) {
            fileStored.delete()
            if (fileStored.existsNow()) return
            hardError = false
            softError = false
            onNoFile()
            saveToDatabase()
        }
    }

    fun getFile(): DownloadFile {
        return fileStored
    }

    fun getFilename(): String {
        return fileStored.getFilename()
    }

    fun getDownloadId(): Long {
        return downloadId
    }

    fun getDownloadNumber(): Long {
        return downloadNumber
    }

    fun getStatus(): DownloadStatus {
        return status
    }

    fun getFilenameNew(): String {
        return fileNew.getFilename()
    }

    fun getUri(): Uri {
        return uri
    }

    fun requestDownload(myContext: MyContext) {
        if (!hardError && downloadId == 0L) {
            saveToDatabase()
        }
        if ((DownloadStatus.LOADED != status || !fileStored.existed) && !hardError && uri !== Uri.EMPTY) {
            MyServiceManager.sendCommand(if (actorId != 0L)
                CommandData.newActorCommand(CommandEnum.GET_AVATAR, Actor.load(myContext, actorId), "")
            else CommandData.newFetchAttachment(noteId, downloadId))
        }
    }

    fun getMessage(): String {
        return errorMessage
    }

    override fun toString(): String {
        val builder = MyStringBuilder()
        builder.withComma("id", downloadId, { i: Long? -> i != 0L })
        builder.withComma("num", downloadNumber, { i: Long -> i > 0 })
        builder.withCommaQuoted("uri", getUri(), true)
        builder.withComma("mime", mimeType)
        builder.withComma("actorId", actorId, { i: Long? -> i != 0L })
        builder.withComma("noteId", noteId, { i: Long? -> i != 0L })
        builder.withComma("previewOf", previewOfDownloadId, { i: Long? -> i != 0L })
        builder.withComma("status", getStatus())
        builder.withCommaQuoted("errorMessage", getMessage(), true)
        if (fileStored.existed) {
            builder.withComma("file", getFilename())
            builder.withComma("size", fileSize)
            if (mediaMetadata.nonEmpty) builder.withComma(mediaMetadata.toString())
        }
        return MyStringBuilder.formatKeyValue(this, builder.toString())
    }

    override val isEmpty: Boolean
        get() {
            return this === EMPTY || uri == Uri.EMPTY
        }

    fun getDownloadedDate(): Long {
        return downloadedDate
    }

    fun getContentType(): MyContentType {
        return contentType
    }

    fun getMimeType(): String {
        return mimeType
    }

    class ConsumedSummary {
        var skippedSize: Long = 0
        var consumedCount: Long = 0
        var consumedSize: Long = 0
    }

    override val classTag: String get() = TAG

    companion object {
        private val TAG: String = DownloadData::class.java.simpleName
        val EMPTY: DownloadData = DownloadData(null, 0, 0, 0, MyContentType.UNKNOWN, "",
                DownloadType.UNKNOWN, Uri.EMPTY)

        fun fromCursor(cursor: Cursor): DownloadData {
            return DownloadData(cursor, 0, 0, 0, MyContentType.UNKNOWN, "",
                    DownloadType.UNKNOWN, Uri.EMPTY)
        }

        fun fromId(downloadId: Long): DownloadData {
            return DownloadData(null, downloadId, 0, 0, MyContentType.UNKNOWN, "",
                    DownloadType.UNKNOWN, Uri.EMPTY)
        }

        /**
         * Currently we assume that there is no more than one attachment of a message
         */
        fun getSingleAttachment(noteId: Long): DownloadData {
            return DownloadData(null, 0, 0, noteId, MyContentType.UNKNOWN, "",
                    DownloadType.ATTACHMENT, Uri.EMPTY)
        }

        fun fromAttachment(noteId: Long, attachment: Attachment): DownloadData {
            return DownloadData(null, attachment.getDownloadId(), 0, noteId,
                    attachment.contentType, attachment.mimeType, DownloadType.ATTACHMENT, attachment.uri)
        }

        fun fromNoteId(myContext: MyContext, noteId: Long): List<DownloadData> {
            if (myContext.isEmptyOrExpired || noteId == 0L) return emptyList()
            val sql = ("SELECT *"
                    + " FROM " + DownloadTable.TABLE_NAME
                    + " WHERE " + DownloadTable.NOTE_ID + "=" + noteId
                    + " ORDER BY " + DownloadTable.DOWNLOAD_NUMBER)
            return MyQuery.foldLeft(myContext, sql, ArrayList(), { list: ArrayList<DownloadData> ->
                Function { cursor: Cursor ->
                    list.add(fromCursor(cursor))
                    list
                }
            })
        }

        fun deleteAllOfThisActor(myContext: MyContext, actorId: Long) {
            deleteOtherOfThisActor(myContext, actorId, 0)
        }

        private fun deleteOtherOfThisActor(myContext: MyContext, actorId: Long, rowId: Long) {
            if (actorId == 0L) return
            val method = "deleteOtherOfThisActor actorId=" + actorId + if (rowId != 0L) ", downloadId=$rowId" else ""
            val where = (DownloadTable.ACTOR_ID + "=" + actorId
                    + if (rowId == 0L) "" else " AND " + BaseColumns._ID + "<>" + rowId)
            deleteSelected(method, myContext.database, where)
        }

        private fun deleteSelected(method: String?, db: SQLiteDatabase?, where: String?) {
            val sql = ("SELECT " + BaseColumns._ID + ", " + DownloadTable.FILE_NAME
                    + " FROM " + DownloadTable.TABLE_NAME
                    + " WHERE " + where)
            var rowsDeleted = 0
            var done = false
            for (pass in 0..2) {
                if (db == null) {
                    MyLog.databaseIsNull { TAG }
                    return
                }
                try {
                    db.rawQuery(sql, null).use { cursor ->
                        while (cursor.moveToNext()) {
                            val rowIdOld = DbUtils.getLong(cursor, BaseColumns._ID)
                            DownloadFile(DbUtils.getString(cursor, DownloadTable.FILE_NAME)).delete()
                            rowsDeleted += db.delete(DownloadTable.TABLE_NAME, BaseColumns._ID
                                    + "=" + rowIdOld, null)
                        }
                        done = true
                    }
                } catch (e: SQLiteException) {
                    MyLog.i(DownloadData::class.java, "$method, Database error, pass=$pass; sql='$sql'", e)
                }
                if (done) break
                DbUtils.waitMs(method, 500)
            }
            if (MyLog.isVerboseEnabled() && (!done || rowsDeleted > 0)) {
                MyLog.v(DownloadData::class.java, method + (if (done) " succeeded" else " failed")
                        + "; deleted " + rowsDeleted + " rows")
            }
        }

        fun deleteAllOfThisNote(db: SQLiteDatabase?, noteId: Long) {
            if (noteId == 0L) return
            val method = "deleteAllOfThisNote noteId=$noteId"
            deleteSelected(method, db, DownloadTable.NOTE_ID + "=" + noteId)
        }

        fun deleteOtherOfThisNote(myContext: MyContext, noteId: Long, downloadIds: MutableList<Long>) {
            if (noteId == 0L || downloadIds.isEmpty()) return
            val method = "deleteOtherOfThisNote noteId=" + noteId + ", rowIds:" + toSqlList(downloadIds)
            val where = (DownloadTable.NOTE_ID + "=" + noteId
                    + " AND " + BaseColumns._ID + " NOT IN(" + toSqlList(downloadIds) + ")")
            deleteSelected(method, myContext.database, where)
        }

        fun toSqlList(longs: MutableList<Long>?): String {
            if (longs == null || longs.isEmpty()) {
                return "0"
            }
            var list = ""
            for (theLong in longs) {
                if (list.length > 0) {
                    list += ","
                }
                list += java.lang.Long.toString(theLong)
            }
            return list
        }

        fun pruneFiles(myContext: MyContext, downloadType: DownloadType, bytesToKeep: Long): ConsumedSummary {
            return consumeOldest(myContext, downloadType, bytesToKeep) { obj: DownloadData -> obj.deleteFile() }
        }

        private fun consumeOldest(myContext: MyContext, downloadType: DownloadType, totalSizeToSkip: Long,
                                  consumer: Consumer<DownloadData>): ConsumedSummary {
            val sql = ("SELECT *"
                    + " FROM " + DownloadTable.TABLE_NAME
                    + " WHERE " + DownloadTable.DOWNLOAD_TYPE + "='" + downloadType.save() + "'"
                    + " AND " + DownloadTable.DOWNLOAD_STATUS + "=" + DownloadStatus.LOADED.save()
                    + " ORDER BY " + DownloadTable.DOWNLOADED_DATE + " DESC")
            return MyQuery.foldLeft(myContext, sql,
                    ConsumedSummary(),
                    { summary: ConsumedSummary ->
                        Function { cursor: Cursor ->
                            val data = fromCursor(cursor)
                            if (data.fileStored.existed) {
                                if (summary.skippedSize < totalSizeToSkip) {
                                    summary.skippedSize += data.fileSize
                                } else {
                                    summary.consumedCount += 1
                                    summary.consumedSize += data.fileSize
                                    consumer.accept(data)
                                }
                            }
                            summary
                        }
                    }
            )
        }
    }
}
