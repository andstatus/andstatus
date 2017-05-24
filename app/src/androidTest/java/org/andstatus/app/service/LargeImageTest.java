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

import android.graphics.drawable.Drawable;
import android.support.test.InstrumentationRegistry;

import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.context.Travis;
import org.andstatus.app.data.AttachedImageFile;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MessageInserter;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.data.MyDataCheckerConversations;
import org.andstatus.app.net.social.ConnectionTwitterGnuSocialMock;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbMessage;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Travis
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
        MessageInserter mi = new MessageInserter(MyContextHolder.get().persistentAccounts()
                .fromAccountName(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME));
        MbMessage message = mi.buildMessage(mi.buildUser(), body, null, null, DownloadStatus.LOADED);
        message.attachments
                .add(MbAttachment
                        .fromUrlAndContentType(
                                new URL(
                                        "http://www.example.com/pictures/large_image.png"),
                                MyContentType.IMAGE));
        long msgId = mi.addMessage(message);
        
        DownloadData dd = DownloadData.getSingleForMessage(msgId, message.attachments.get(0).contentType, null);
        assertEquals("Image URI stored", message.attachments.get(0).getUri(), dd.getUri());

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

        assertEquals("Conversations need fixes", 0, new MyDataCheckerConversations(MyContextHolder.get(),
                ProgressLogger.getEmpty()).countChanges());
        return data;
    }

    private void loadingTest(DownloadData dd) {
        Drawable drawable = new AttachedImageFile(dd.getDownloadId(), dd.getFilename())
                .getDrawableSync();
        int width = drawable.getIntrinsicWidth();
        assertTrue("Not wide already " + width, width < 4000 && width > 10);
        int height = drawable.getIntrinsicHeight();
        assertTrue("Not high already " + height, height < 4000 && height > 10);
    }
}
