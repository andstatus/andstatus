package org.andstatus.app.service

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.AttachedMediaFile
import org.andstatus.app.data.DemoNoteInserter
import org.andstatus.app.data.DownloadData
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.graphics.CacheName
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Attachment
import org.andstatus.app.net.social.ConnectionStub
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StopWatch
import org.junit.Assert
import org.junit.Test

/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
class LargeImageTest {
    init {
        TestSuite.initializeWithAccounts(this)
    }

    @Test
    fun testLargeImageAttachmentLoad() {
        val dd = insertNote()
        loadingTest(dd)
    }

    private fun insertNote(): DownloadData = runBlocking {
        val method = "testLargeImageAttachmentLoad"
        val body = "Large image attachment"
        val ma: MyAccount = DemoData.demoData.getGnuSocialAccount()
        val myContext = ma.myContext
        val inserter = DemoNoteInserter(ma)
        val activity = inserter.buildActivity(inserter.buildActor(), "", body, null, null,
                DownloadStatus.LOADED)
        activity.addAttachment(Attachment.Companion.fromUri("http://www.example.com/pictures/large_image.png"))
        inserter.onActivity(activity)
        val dd: DownloadData = DownloadData.Companion.getSingleAttachment(activity.getNote().noteId)
        Assert.assertEquals("Image URI stored", activity.getNote().attachments.list[0].uri, dd.getUri())

        // Wait till automatic attachment loading fails
        // TODO: Clarify this...
        var foundFailed = false
        StopWatch.tillPassedSeconds(20) {
            delay(500)
            (myContext.queues[QueueType.ERROR].any { commandData ->
                commandData.itemId == dd.downloadId &&
                    commandData.command == CommandEnum.GET_ATTACHMENT
            } ||
                myContext.queues[QueueType.ERROR].any { commandData ->
                    commandData.itemId == dd.downloadId &&
                        commandData.command == CommandEnum.GET_ATTACHMENT
                })
                .also { foundFailed = it }
        }
        MyLog.i(
            this,
            (if (foundFailed) "Found" else "Didn't find") +
                " failed command ${CommandEnum.GET_ATTACHMENT}" +
                " with id:${dd.downloadId} ($dd)"
        )
        MyLog.i(method, "Error queue:")
        myContext.queues[QueueType.ERROR].forEachIndexed { index, commandData ->
            MyLog.i(method, "$index: $commandData")
        }
        MyLog.i(method, "Retry queue")
        myContext.queues[QueueType.RETRY].forEachIndexed { index, commandData ->
            MyLog.i(method, "$index: $commandData")
        }

        val commandData: CommandData = CommandData.Companion.newActorCommand(CommandEnum.GET_AVATAR,
                Actor.Companion.fromId(ma.origin, 34234), "")
        val loader = AttachmentDownloader(ma.myContext, dd)
        val connStub: ConnectionStub = ConnectionStub.newFor(DemoData.demoData.gnusocialTestAccountName)
        connStub.getHttpStub().setResponseStreamSupplier {
            InstrumentationRegistry.getInstrumentation().context.resources
                    .openRawResource(org.andstatus.app.test.R.raw.large_image)
        }
        loader.setConnectionStub(connStub.connection)
        loader.load(commandData)
        Assert.assertEquals("Requested", 1, connStub.getHttpStub().getRequestsCounter())
        val data: DownloadData = DownloadData.Companion.fromId(dd.downloadId)
        Assert.assertFalse("Failed to load stubbed image " + data.getUri() + "\n$commandData", commandData.getResult().hasError())
        Assert.assertTrue("File exists " + data.getUri(), data.file.existed)
        DemoData.demoData.assertConversations()
        data
    }

    private fun loadingTest(dd: DownloadData) {
        val image = AttachedMediaFile(dd).loadAndGetImage(CacheName.ATTACHED_IMAGE)
                ?: throw IllegalStateException("No image")
        val width = image.getImageSize().x
        Assert.assertTrue("Too wide: $width", width in 11..3999)
        val height = image.getImageSize().y
        Assert.assertTrue("Too high: $height", height in 11..3999)
    }
}
