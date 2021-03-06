package org.andstatus.app.service

import androidx.test.platform.app.InstrumentationRegistry
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

    private fun insertNote(): DownloadData {
        val body = "Large image attachment"
        val ma: MyAccount = DemoData.demoData.getGnuSocialAccount()
        val inserter = DemoNoteInserter(ma)
        val activity = inserter.buildActivity(inserter.buildActor(), "", body, null, null,
                DownloadStatus.LOADED)
        activity.addAttachment(Attachment.Companion.fromUri("http://www.example.com/pictures/large_image.png"))
        inserter.onActivity(activity)
        val dd: DownloadData = DownloadData.Companion.getSingleAttachment(activity.getNote().noteId
        )
        Assert.assertEquals("Image URI stored", activity.getNote().attachments.list[0].uri, dd.getUri())
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
        val data: DownloadData = DownloadData.Companion.fromId(dd.getDownloadId())
        Assert.assertFalse("Loaded " + data.getUri(), commandData.getResult().hasError())
        Assert.assertTrue("File exists " + data.getUri(), data.getFile().existed)
        DemoData.demoData.assertConversations()
        return data
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
