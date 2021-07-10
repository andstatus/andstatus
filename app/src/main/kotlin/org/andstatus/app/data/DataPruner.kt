/*
 * Copyright (C) 2013-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.data

import android.content.ContentResolver
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import org.andstatus.app.backup.ProgressLogger
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.MyStorage
import org.andstatus.app.data.DbUtils.closeSilently
import org.andstatus.app.data.DownloadData.ConsumedSummary
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.AudienceTable
import org.andstatus.app.database.table.DownloadTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.timeline.meta.DisplayedInSelector
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.SelectionAndArgs
import org.andstatus.app.util.SharedPreferencesUtil
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Function

/**
 * Clean database from outdated information
 * old Notes, log files...
 */
class DataPruner(private val myContext: MyContext) {
    private val db: SQLiteDatabase? = myContext.database
    private val mContentResolver: ContentResolver = myContext.context.contentResolver
    private var pruneNow = false
    private var logger: ProgressLogger = ProgressLogger.getEmpty(TAG)
    private var mDeleted: Long = 0
    private var latestTimestamp: Long = 0
    fun setPruneNow(): DataPruner {
        pruneNow = true
        return this
    }

    fun setLogger(logger: ProgressLogger): DataPruner {
        this.logger = logger
        return this
    }

    /**
     * @return true if done successfully, false if skipped or an error
     */
    fun prune(): Boolean {
        val method = "prune"
        if (db == null) {
            MyLog.databaseIsNull { DataPruner::class.java }
            return false
        }
        if (!mayPruneNow()) {
            return false
        }
        logger.logProgress("$method started")
        val pruned = pruneActivities()
        if (mDeleted > 0) {
            pruneParentlessAttachments()
        }
        deleteTempFiles()
        pruneMedia()
        pruneTimelines(java.lang.Long.max(latestTimestamp, getLatestTimestamp(MAX_DAYS_UNUSED_TIMELINES_TO_KEEP)))
        pruneTempActors()
        pruneLogs(MAX_DAYS_LOGS_TO_KEEP)
        setDataPrunedNow()
        logger.onComplete(pruned)
        return pruned
    }

    private fun pruneActivities(): Boolean {
        val method = "pruneActivities"
        logger.logProgress("$method started")
        var pruned = false
        mDeleted = 0
        var nDeletedTime = 0
        // We're using global preferences here
        val sp = SharedPreferencesUtil.getDefaultSharedPreferences()

        // Don't delete my activities
        val myActorIds: SqlIds = SqlIds.myActorsIds()
        val sqlNotMyActivity = ActivityTable.TABLE_NAME + "." + ActivityTable.ACTOR_ID + myActorIds.getNotSql()
        val sqlNotLatestActivityByActor = (ActivityTable.TABLE_NAME + "." + BaseColumns._ID + " NOT IN("
                + " SELECT " + ActorTable.ACTOR_ACTIVITY_ID + " FROM " + ActorTable.TABLE_NAME + ")")
        val maxDays = sp?.getString(MyPreferences.KEY_HISTORY_TIME, "3")?.toLong() ?: 0
        latestTimestamp = getLatestTimestamp(maxDays)
        var nActivities: Long = 0
        val nToDeleteSize: Long
        var nDeletedSize: Long = 0
        val maxSize = sp?.getString(MyPreferences.KEY_HISTORY_SIZE, "2000")?.toLong() ?: 0
        var latestTimestampSize: Long = 0
        var cursor: Cursor? = null
        try {
            if (maxDays > 0) {
                val sa = SelectionAndArgs()
                sa.addSelection(ActivityTable.TABLE_NAME + "." + ActivityTable.INS_DATE + " <  ?",
                        java.lang.Long.toString(latestTimestamp))
                sa.addSelection(sqlNotMyActivity)
                sa.addSelection(sqlNotLatestActivityByActor)
                nDeletedTime = MyProvider.deleteActivities(myContext, sa.selection, sa.selectionArgs, false)
            }
            if (maxSize > 0) {
                nActivities = MyQuery.getCountOfActivities("")
                nToDeleteSize = nActivities - maxSize
                if (nToDeleteSize > 0) {
                    // Find INS_DATE of the most recent tweet to delete
                    cursor = mContentResolver.query(MatchedUri.ACTIVITY_CONTENT_URI, arrayOf<String>(
                            ActivityTable.INS_DATE
                    ), null, null, ActivityTable.INS_DATE + " ASC LIMIT 0," + nToDeleteSize)
                            ?.also {
                                if (it.moveToLast()) {
                                    latestTimestampSize = it.getLong(0)
                                }
                                it.close()
                            }
                    if (latestTimestampSize > 0) {
                        val sa = SelectionAndArgs()
                        sa.addSelection(ActivityTable.TABLE_NAME + "." + ActivityTable.INS_DATE + " <=  ?",
                                java.lang.Long.toString(latestTimestampSize))
                        sa.addSelection(sqlNotMyActivity)
                        sa.addSelection(sqlNotLatestActivityByActor)
                        nDeletedSize = MyProvider.deleteActivities(myContext, sa.selection, sa.selectionArgs, false).toLong()
                    }
                }
            }
            pruned = true
        } catch (e: Exception) {
            MyLog.i(logger.logTag, "$method failed", e)
        } finally {
            closeSilently(cursor)
        }
        mDeleted = nDeletedTime + nDeletedSize
        logger.logProgressAndPause(
                """$method ${if (pruned) "succeeded" else "failed"}; History time=$maxDays days; deleted $nDeletedTime , before ${Date(latestTimestamp)}
History size=$maxSize notes; deleted $nDeletedSize of $nActivities notes, before ${Date(latestTimestampSize)}""", mDeleted)
        return pruned
    }

    private fun deleteTempFiles() {
        MyStorage.getMediaFiles()
                .filter(MyStorage::isTempFile)
                .forEach(File::delete)
    }

    fun pruneMedia(): Long {
        val dirSize = MyStorage.getMediaFilesSize()
        val maxSize = MyPreferences.maximumSizeOfCachedMediaBytes
        val bytesToPrune = dirSize - maxSize
        val bytesToPruneMin = ATTACHMENTS_TO_STORE_MIN * MyPreferences.getMaximumSizeOfAttachmentBytes()
        logger.logProgress("Size of media files: " + I18n.formatBytes(dirSize)
                + (if (bytesToPrune > bytesToPruneMin) " exceeds" else " less than")
                + " maximum: " + I18n.formatBytes(maxSize) + " + min to prune: " + I18n.formatBytes(bytesToPruneMin)
        )
        if (bytesToPrune < bytesToPruneMin) return 0
        val pruned1: ConsumedSummary = DownloadData.pruneFiles(myContext, DownloadType.ATTACHMENT,
                Math.round(maxSize * ATTACHMENTS_SIZE_PART))
        val pruned2: ConsumedSummary = DownloadData.pruneFiles(myContext, DownloadType.AVATAR,
                Math.round(maxSize * (1 - ATTACHMENTS_SIZE_PART)))
        val prunedCount = pruned1.consumedCount + pruned2.consumedCount
        logger.logProgressAndPause("""Pruned ${pruned1.consumedCount} attachment files, ${I18n.formatBytes(pruned1.consumedSize)}
Pruned ${pruned2.consumedCount} avatar files, ${I18n.formatBytes(pruned2.consumedSize)}""", prunedCount)
        return prunedCount
    }

    fun pruneParentlessAttachments(): Long {
        val method = "pruneParentlessAttachments"
        val sql = ("SELECT DISTINCT " + DownloadTable.NOTE_ID + " FROM " + DownloadTable.TABLE_NAME
                + " WHERE " + DownloadTable.NOTE_ID + " <> 0"
                + " AND NOT EXISTS ("
                + "SELECT * FROM " + NoteTable.TABLE_NAME
                + " WHERE " + NoteTable.TABLE_NAME + "." + BaseColumns._ID + "=" + DownloadTable.NOTE_ID
                + ")")
        var nDeleted: Long = 0
        for (noteId in MyQuery.getLongs(myContext, sql)) {
            DownloadData.deleteAllOfThisNote(db, noteId)
            nDeleted++
        }
        logger.logProgressAndPause("$method; Attachments deleted for $nDeleted notes", nDeleted)
        return nDeleted
    }

    private fun pruneTimelines(latestTimestamp: Long) {
        myContext.timelines.stream().filter { t: Timeline ->
            (!t.isRequired()
                    && t.isDisplayedInSelector() == DisplayedInSelector.NEVER && t.getLastChangedDate() < latestTimestamp)
        }.forEach { t: Timeline -> t.delete(myContext) }
    }

    private fun mayPruneNow(): Boolean {
        return if (pruneNow) true else !myContext.isInForeground &&
                RelativeTime.moreSecondsAgoThan(
                        SharedPreferencesUtil.getLong(MyPreferences.KEY_DATA_PRUNED_DATE),
                        TimeUnit.DAYS.toSeconds(PRUNE_MIN_PERIOD_DAYS))
    }

    fun pruneLogs(maxDaysToKeep: Long): Long {
        val method = "pruneLogs"
        val latestTimestamp = getLatestTimestamp(maxDaysToKeep)
        var deletedCount: Long = 0
        val dir: File = MyStorage.getLogsDir(true) ?: return deletedCount
        var errorCount: Long = 0
        var skippedCount: Long = 0
        dir.list()?.forEach { filename ->
            val file = File(dir, filename)
            if (file.isFile && file.lastModified() < latestTimestamp) {
                if (file.delete()) {
                    deletedCount++
                    if (deletedCount < 10 && MyLog.isVerboseEnabled()) {
                        MyLog.v(logger.logTag, method + "; deleted: " + file.name)
                    }
                } else {
                    errorCount++
                    if (errorCount < 10 && MyLog.isVerboseEnabled()) {
                        MyLog.v(logger.logTag, method + "; couldn't delete: " + file.absolutePath)
                    }
                }
            } else {
                skippedCount++
                if (skippedCount < 10 && MyLog.isVerboseEnabled()) {
                    MyLog.v(logger.logTag, method + "; skipped: " + file.name + ", modified " + Date(file.lastModified()).toString())
                }
            }
        }
        logger.logProgressAndPause(method + "; deleted " + deletedCount
                + " files, before " + Date(latestTimestamp).toString()
                + ", skipped " + skippedCount + ", couldn't delete " + errorCount, deletedCount)
        return deletedCount
    }

    private fun pruneTempActors() {
        logger.logProgress("Delete temporary unused actors started")
        val myActorIds: SqlIds = SqlIds.myActorsIds()
        val sql = ("SELECT " + ActorSql.selectFullProjection()
                + " FROM " + ActorSql.allTables() +
                " WHERE " + ActorTable.TABLE_NAME + "." + ActorTable.PARENT_ACTOR_ID + " = 0" +
                " AND " + ActorTable.TABLE_NAME + "." + ActorTable.ACTOR_OID + " LIKE ('andstatustemp:%')" +
                " AND " + ActorTable.TABLE_NAME + "." + BaseColumns._ID + myActorIds.getNotSql() +
                " AND NOT EXISTS (SELECT * FROM " + AudienceTable.TABLE_NAME +
                " WHERE " + AudienceTable.ACTOR_ID + " = " + ActorTable.TABLE_NAME + "." + BaseColumns._ID + ")" +
                " AND NOT EXISTS (SELECT * FROM " + ActivityTable.TABLE_NAME +
                " WHERE " + ActivityTable.ACTOR_ID + " = " + ActorTable.TABLE_NAME + "." + BaseColumns._ID + ")")
        val function = Function<Cursor, Actor> { cursor: Cursor -> Actor.fromCursor(myContext, cursor, true) }
        val actors = MyQuery.get(myContext, sql, function)
        logger.logProgress("To delete: " + actors.size + " temporary unused actors")
        val counter = AtomicInteger()
        actors.forEach(Consumer { actor: Actor ->
            counter.incrementAndGet()
            MyLog.v(TAG) { counter.get().toString() + ". Deleting: " + actor.uniqueName + "; " + actor }
            MyProvider.deleteActor(myContext, actor.actorId)
            logger.logProgressIfLongProcess { counter.get().toString() + ". Deleting: " + actor.uniqueName }
        })
        logger.logProgress("Deleted " + actors.size + " temporary unused actors")
    }

    /**
     * @return number of notes deleted
     */
    fun getDeleted(): Long {
        return mDeleted
    }

    companion object {
        val TAG: String = DataPruner::class.java.simpleName
        const val ATTACHMENTS_TO_STORE_MIN: Long = 5
        const val MAX_DAYS_LOGS_TO_KEEP: Long = 10
        const val MAX_DAYS_UNUSED_TIMELINES_TO_KEEP: Long = 31
        private const val PRUNE_MIN_PERIOD_DAYS: Long = 1
        private const val ATTACHMENTS_SIZE_PART = 0.90
        fun getLatestTimestamp(maxDays: Long): Long {
            return if (maxDays <= 0) 0 else System.currentTimeMillis() - TimeUnit.DAYS.toMillis(maxDays)
        }

        fun setDataPrunedNow() {
            SharedPreferencesUtil.putLong(MyPreferences.KEY_DATA_PRUNED_DATE, System.currentTimeMillis())
        }
    }

}
