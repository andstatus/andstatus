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
import android.test.InstrumentationTestCase;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.AttachedImageDrawable;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.MessageInserter;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.social.ConnectionTwitterGnuSocialMock;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.service.AttachmentDownloader;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class LargeImageTest extends InstrumentationTestCase {
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
    }
    
    public void testLargeImageAttachmentLoad() throws IOException {
        DownloadData dd = insertMessage();
        loadingTest(dd);
    }

    private DownloadData insertMessage() throws MalformedURLException, IOException {
        String body = "Large image attachment";
        MessageInserter mi = new MessageInserter(MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME));
        MbMessage message = mi.buildMessage(mi.buildUser(), body, null, null);
        message.attachments
                .add(MbAttachment
                        .fromUrlAndContentType(
                                new URL(
                                        "http://www.example.com/pictures/large_image.png"),
                                MyContentType.IMAGE));
        long msgId = mi.addMessage(message);
        
        DownloadData dd = DownloadData.newForMessage(msgId, message.attachments.get(0).contentType, null);
        assertEquals("Image URL stored", message.attachments.get(0).getUrl(), dd.getUrl());

        CommandData commandData = new CommandData(CommandEnum.FETCH_AVATAR, null);
        AttachmentDownloader loader = new AttachmentDownloader(dd);
        ConnectionTwitterGnuSocialMock connection = new ConnectionTwitterGnuSocialMock();
        InputStream inputStream = this.getInstrumentation().getContext().getResources()
                .openRawResource(org.andstatus.app.tests.R.raw.large_image);
        connection.getHttpMock().setResponseFileStream(inputStream);
        loader.connectionMock = connection;
        loader.load(commandData);
        inputStream.close();
        assertEquals("Requested", 1, connection.getHttpMock().getRequestsCounter());

        DownloadData data = DownloadData.fromRowId(dd.getRowId());
        assertFalse("Loaded " + data.getUrl(), commandData.getResult().hasError());
        assertTrue("File exists " + data.getUrl(), data.getFile().exists());
        return data;
    }

    private void loadingTest(DownloadData dd) {
        new AttachedImageDrawable(dd.getRowId(), dd.getFilename())
                .getDrawable();
    }
}
