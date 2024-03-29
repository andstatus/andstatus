/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.service

import android.net.Uri
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DemoNoteInserter
import org.andstatus.app.data.DownloadData
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.FileProvider
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Attachment
import org.andstatus.app.net.social.ConnectionStub
import org.andstatus.app.util.MyLog
import org.junit.Assert
import org.junit.Test
import java.io.InputStream

class AttachmentDownloaderTest {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)

    @Test
    fun testImageAttachmentLoad() {
        val method = "testImageAttachmentLoad"
        val ma: MyAccount = DemoData.demoData.getGnuSocialAccount()
        ma.setConnection()
        Assert.assertTrue(DemoData.demoData.gnusocialTestAccountName + " exists", ma.isValid)
        val body = "A note with an image attachment"
        val inserter = DemoNoteInserter(ma)
        val activity = inserter.buildActivity(inserter.buildActor(), "", body,
                null, null, DownloadStatus.LOADED)
        activity.addAttachment(Attachment.Companion.fromUri("https://img.freepik.com/premium-psd/white-t-shirts-mockup-design_53876-98813.jpg"))
        inserter.onActivity(activity)
        val dd: DownloadData = DownloadData.Companion.getSingleAttachment(activity.getNote().noteId)
        Assert.assertEquals("Image URI stored", activity.getNote().attachments.list[0].uri, dd.getUri())
        loadAndAssertStatusForRow(method, dd, DownloadStatus.ABSENT, true)
        loadAndAssertStatusForRow(method, dd, DownloadStatus.LOADED, false)
        testFileProvider(dd.downloadId)
    }

    private fun testFileProvider(downloadRowId: Long) {
        val data: DownloadData = DownloadData.Companion.fromId(downloadRowId)
        Assert.assertTrue(data.filename, data.file.existed)
        val uri: Uri = FileProvider.Companion.downloadFilenameToUri(data.file.getFilename())
        val inputStream: InputStream = myContext.context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("No stream")
        val buffer = ByteArray(100)
        val bytesRead = inputStream.read(buffer)
        Assert.assertEquals(buffer.size.toLong(), bytesRead.toLong())
        inputStream.close()
    }

    companion object {
        fun loadAndAssertStatusForRow(methodExt: String, dataIn: DownloadData, status: DownloadStatus, imitateNetworkError: Boolean) {
            val method = "loadAndAssertStatusForRow"
            TestSuite.clearHttpStubs()
            MyLog.i(method, methodExt + ": " + status + ", imitateError:" + imitateNetworkError
                    + ", uri:" + dataIn.getUri())
            val ma: MyAccount = DemoData.demoData.getGnuSocialAccount()
            val loader: FileDownloader = FileDownloader.Companion.newForDownloadData(ma.myContext, dataIn)
            if (imitateNetworkError) {
                loader.setConnectionStub(ConnectionStub.newFor(ma)
                    .let {
                        it.http.addException(ConnectionException("Imitated IO exception"))
                        it.connection
                    }
                )
            }
            val commandData: CommandData = CommandData.Companion.newActorCommand(CommandEnum.GET_AVATAR, Actor.EMPTY, "someActor")
            loader.load(commandData)
            val data: DownloadData = DownloadData.Companion.fromId(dataIn.downloadId)
            if (DownloadStatus.LOADED == status) {
                Assert.assertFalse("Has error $data\n$commandData", commandData.result.hasError)
                Assert.assertEquals("Status $data", status, loader.getStatus())
            } else {
                Assert.assertTrue("Error loading " + data.getUri(), commandData.result.hasError)
            }
            if (DownloadStatus.LOADED == status) {
                Assert.assertTrue("File exists " + data.getUri(), data.file.existed)
            } else {
                Assert.assertFalse("File doesn't exist " + data.getUri(), data.file.existed)
            }
            Assert.assertEquals("Loaded " + data.getUri(), status, loader.getStatus())
        }
    }
}
