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

import android.support.test.InstrumentationRegistry;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.AttachedImageFile;
import org.andstatus.app.data.DemoMessageInserter;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.graphics.CachedImage;
import org.andstatus.app.net.social.ConnectionTwitterGnuSocialMock;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.Attachment;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LargeImageTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void testLargeImageAttachmentLoad() throws IOException {
        DownloadData dd = insertMessage();
        loadingTest(dd);
    }

    private DownloadData insertMessage() throws IOException {
        String body = "Large image attachment";
        MyAccount ma = demoData.getMyAccount(demoData.GNUSOCIAL_TEST_ACCOUNT_NAME);
        DemoMessageInserter mi = new DemoMessageInserter(ma);
        AActivity activity = mi.buildActivity(mi.buildUser(), body, null, null, DownloadStatus.LOADED);
        activity.getMessage().attachments
                .add(Attachment
                        .fromUrlAndContentType(
                                new URL(
                                        "http://www.example.com/pictures/large_image.png"),
                                MyContentType.IMAGE));
        mi.onActivity(activity);
        
        DownloadData dd = DownloadData.getSingleForMessage(activity.getMessage().msgId,
                activity.getMessage().attachments.get(0).contentType, null);
        assertEquals("Image URI stored", activity.getMessage().attachments.get(0).getUri(), dd.getUri());

        CommandData commandData = CommandData.newCommand(CommandEnum.FETCH_AVATAR);
        AttachmentDownloader loader = new AttachmentDownloader(dd);
        ConnectionTwitterGnuSocialMock connection = new ConnectionTwitterGnuSocialMock();
        InputStream inputStream = InstrumentationRegistry.getInstrumentation().getContext().getResources()
                .openRawResource(org.andstatus.app.tests.R.raw.large_image);
        connection.getHttpMock().setResponseFileStream(inputStream);
        loader.connectionMock = connection;
        loader.load(commandData);
        inputStream.close();
        assertEquals("Requested", 1, connection.getHttpMock().getRequestsCounter());

        DownloadData data = DownloadData.fromId(dd.getDownloadId());
        assertFalse("Loaded " + data.getUri(), commandData.getResult().hasError());
        assertTrue("File exists " + data.getUri(), data.getFile().exists());

        demoData.assertConversations();
        return data;
    }

    private void loadingTest(DownloadData dd) {
        CachedImage image = new AttachedImageFile(dd.getDownloadId(), dd.getFilename()).loadAndGetImage();
        int width = image.getImageSize().x;
        assertTrue("Not wide already " + width, width < 4000 && width > 10);
        int height = image.getImageSize().y;
        assertTrue("Not high already " + height, height < 4000 && height > 10);
    }
}
