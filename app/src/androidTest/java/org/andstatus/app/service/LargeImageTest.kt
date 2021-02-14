package org.andstatus.app.service;
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

import androidx.test.platform.app.InstrumentationRegistry;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.AttachedMediaFile;
import org.andstatus.app.data.DemoNoteInserter;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.graphics.CacheName;
import org.andstatus.app.graphics.CachedImage;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.ConnectionMock;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LargeImageTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithAccounts(this);
    }

    @Test
    public void testLargeImageAttachmentLoad() throws IOException {
        DownloadData dd = insertNote();
        loadingTest(dd);
    }

    private DownloadData insertNote() {
        String body = "Large image attachment";
        MyAccount ma = demoData.getGnuSocialAccount();
        DemoNoteInserter inserter = new DemoNoteInserter(ma);
        AActivity activity = inserter.buildActivity(inserter.buildActor(), "", body, null, null,
                DownloadStatus.LOADED);
        activity.addAttachment(Attachment.fromUri("http://www.example.com/pictures/large_image.png"));
        inserter.onActivity(activity);
        
        DownloadData dd = DownloadData.getSingleAttachment(activity.getNote().noteId
        );
        assertEquals("Image URI stored", activity.getNote().attachments.list.get(0).getUri(), dd.getUri());

        CommandData commandData = CommandData.newActorCommand(CommandEnum.GET_AVATAR,
                Actor.fromId(ma.getOrigin(), 34234), "");
        AttachmentDownloader loader = new AttachmentDownloader(ma.getOrigin().myContext, dd);
        ConnectionMock connMock = ConnectionMock.newFor(demoData.gnusocialTestAccountName);
        connMock.getHttpMock().setResponseStreamSupplier(o ->
                InstrumentationRegistry.getInstrumentation().getContext().getResources()
                    .openRawResource(org.andstatus.app.tests.R.raw.large_image));
        loader.setConnectionMock(connMock.connection);
        loader.load(commandData);
        assertEquals("Requested", 1, connMock.getHttpMock().getRequestsCounter());

        DownloadData data = DownloadData.fromId(dd.getDownloadId());
        assertFalse("Loaded " + data.getUri(), commandData.getResult().hasError());
        assertTrue("File exists " + data.getUri(), data.getFile().existed);

        demoData.assertConversations();
        return data;
    }

    private void loadingTest(DownloadData dd) {
        CachedImage image = new AttachedMediaFile(dd).loadAndGetImage(CacheName.ATTACHED_IMAGE);
        int width = image.getImageSize().x;
        assertTrue("Too wide: " + width, width < 4000 && width > 10);
        int height = image.getImageSize().y;
        assertTrue("Too high: " + height, height < 4000 && height > 10);
    }
}
