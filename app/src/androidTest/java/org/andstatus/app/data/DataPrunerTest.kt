package org.andstatus.app.data

import android.net.Uri
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.context.MyStorage
import org.andstatus.app.context.TestSuite
import org.andstatus.app.net.social.Attachment
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.SharedPreferencesUtil
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.net.MalformedURLException
import java.util.*
import java.util.concurrent.TimeUnit

class DataPrunerTest {
    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initialize(this)
        Assert.assertTrue(TestSuite.setAndWaitForIsInForeground(false))
    }

    @Test
    @Throws(MalformedURLException::class)
    fun testPrune() {
        val method = "testPrune"
        MyLog.v(this, "$method; Started")
        val isLogEnabled = MyLog.isLogToFileEnabled()
        MyLog.setLogToFile(false)
        MyLog.setLogToFile(true)
        val filename = MyLog.getLogFilename()
        val logFile1 = MyLog.getFileInLogDir(filename, true)
        MyLog.v(this, method)
        MyLog.setLogToFile(false)
        Assert.assertTrue(logFile1.exists())
        clearPrunedDate()
        val dp = DataPruner( MyContextHolder.myContextHolder.getNow())
        Assert.assertTrue("Pruned", dp.prune())
        Assert.assertTrue("File is fresh", logFile1.exists())
        val pruneDate1 = SharedPreferencesUtil.getLong(MyPreferences.KEY_DATA_PRUNED_DATE)
        Assert.assertTrue(
                "Pruning date updated $pruneDate1 - "
                        + RelativeTime.getDifference( MyContextHolder.myContextHolder.getNow().context(),
                        pruneDate1),
                !RelativeTime.moreSecondsAgoThan(pruneDate1, 300))
        Assert.assertFalse("Second prune skipped", dp.prune())
        Assert.assertEquals("No more pruning", pruneDate1,
                SharedPreferencesUtil.getLong(MyPreferences.KEY_DATA_PRUNED_DATE))

        // See http://stackoverflow.com/questions/6633748/file-lastmodified-is-never-what-was-set-with-file-setlastmodified
        val lastModifiedNew = (System.currentTimeMillis()
                - TimeUnit.DAYS.toMillis(DataPruner.Companion.MAX_DAYS_LOGS_TO_KEEP + 1)) / 1000 * 1000
        if (logFile1.setLastModified(lastModifiedNew)) {
            MyLog.v(this, "$method; Last modified date set for $filename")
            clearPrunedDate()
            val logFile2 = MyLog.getFileInLogDir(filename, true)
            Assert.assertEquals(lastModifiedNew, logFile2.lastModified())
            Assert.assertTrue("Pruned", dp.prune())
            Assert.assertFalse("File " + logFile2.name + " was old: " + millisToDateString(lastModifiedNew),
                    logFile2.exists())
        } else {
            val msg = (method + "; Couldn't set modification date of '" + logFile1.absolutePath
                    + "' to " + millisToDateString(lastModifiedNew)
                    + " actual: " + millisToDateString(logFile1.lastModified()))
            // TODO: Is this really a bug in Android?!
            MyLog.e(this, msg)
        }
        clearPrunedDate()
        Assert.assertTrue(TestSuite.setAndWaitForIsInForeground(true))
        Assert.assertFalse("Prune while in foreground skipped", dp.prune())
        if (isLogEnabled) {
            MyLog.setLogToFile(true)
        }
        MyLog.v(this, "$method; Ended")
    }

    @Test
    fun testPruneParentlessAttachments() {
        val dp = DataPruner( MyContextHolder.myContextHolder.getNow())
        dp.pruneParentlessAttachments()
        val dd: DownloadData = DownloadData.Companion.fromAttachment(-555L,
                Attachment.Companion.fromUriAndMimeType(Uri.parse("http://example.com/image.png"), ""))
        dd.saveToDatabase()
        Assert.assertEquals(1, dp.pruneParentlessAttachments())
        Assert.assertEquals(0, dp.pruneParentlessAttachments())
    }

    @Test
    fun testPruneMedia() {
        val dirSize1 = MyStorage.getMediaFilesSize()
        val newSizeOfAttachmentMb: Long = 1
        val newSizeOfAttachment = newSizeOfAttachmentMb * MyPreferences.BYTES_IN_MB
        val attachmentsStoredMin: Long = DataPruner.Companion.ATTACHMENTS_TO_STORE_MIN + 2
        if (dirSize1 < attachmentsStoredMin * newSizeOfAttachment) {
            MyLog.i(this, "Too few media files to prune, size $dirSize1")
            return
        }
        val maximumSizeOfStoredMediaMb = Math.round((
                (dirSize1 - DataPruner.Companion.ATTACHMENTS_TO_STORE_MIN * newSizeOfAttachment) / MyPreferences.BYTES_IN_MB - 1
                ).toFloat())
        SharedPreferencesUtil.forget()
        SharedPreferencesUtil.putLong(MyPreferences.KEY_MAXIMUM_SIZE_OF_ATTACHMENT_MB, newSizeOfAttachmentMb)
        SharedPreferencesUtil.putLong(MyPreferences.KEY_MAXIMUM_SIZE_OF_CACHED_MEDIA_MB, maximumSizeOfStoredMediaMb)
        val dp = DataPruner( MyContextHolder.myContextHolder.getNow())
        val prunedCount1 = dp.pruneMedia()
        val dirSize2 = MyStorage.getMediaFilesSize()
        val prunedCount2 = dp.pruneMedia()
        val dirSize3 = MyStorage.getMediaFilesSize()
        SharedPreferencesUtil.removeKey(MyPreferences.KEY_MAXIMUM_SIZE_OF_ATTACHMENT_MB)
        SharedPreferencesUtil.removeKey(MyPreferences.KEY_MAXIMUM_SIZE_OF_CACHED_MEDIA_MB)
        Assert.assertNotEquals("Something should be pruned, dir size: " + dirSize1
                + " max: " + maximumSizeOfStoredMediaMb + " MB", 0, prunedCount1)
        Assert.assertTrue("Dir size should decrease $dirSize1 -> $dirSize2", dirSize1 > dirSize2)
        Assert.assertEquals("Nothing should be pruned, $dirSize2 -> $dirSize3", 0, prunedCount2)
    }

    private fun clearPrunedDate() {
        SharedPreferencesUtil.putLong(MyPreferences.KEY_DATA_PRUNED_DATE, 0)
    }

    private fun millisToDateString(dateTime: Long): String? {
        return Date(dateTime).toString()
    }
}