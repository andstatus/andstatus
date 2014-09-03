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

package org.andstatus.app.service;

import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.ContentType;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MessageInserter;
import org.andstatus.app.net.MbAttachment;
import org.andstatus.app.net.MbMessage;
import org.andstatus.app.util.MyLog;

import java.io.IOException;
import java.net.URL;

public class AttachmentDownloaderTest extends InstrumentationTestCase {
    MyAccount ma;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.STATUSNET_TEST_ACCOUNT_NAME); 
        assertTrue(TestSuite.STATUSNET_TEST_ACCOUNT_NAME + " exists", ma != null);
    }
    
    public void testImageAttachmentLoad() throws IOException {
        String body = "A message with an image attachment";
        MessageInserter mi = new MessageInserter(ma);
        MbMessage message = mi.buildMessage(mi.buildUser(), body, null, null);
        MbAttachment attachment = MbAttachment.fromOriginAndOid(ma.getOriginId(), "anything");
        attachment.contentType = ContentType.IMAGE;
        attachment.url = new URL("http://www.publicdomainpictures.net/pictures/60000/nahled/landscape-1376582205Yno.jpg");
        message.attachments.add(attachment);
        long msgId = mi.addMessage(message);
        
        DownloadData dd = DownloadData.newForMessage(msgId, message.attachments.get(0).contentType, null);
        assertEquals("Image URL stored", message.attachments.get(0).url , dd.getUrl());
        
        loadAndAssertStatusForRow(dd.getRowId(), DownloadStatus.ABSENT, true);

        loadAndAssertStatusForRow(dd.getRowId(), DownloadStatus.LOADED, false);
    }
    
    private long loadAndAssertStatusForRow(long downloadRowId, DownloadStatus status, boolean mockNetworkError) throws IOException {
        FileDownloader loader = FileDownloader.newForDownloadRow(downloadRowId);
        loader.mockNetworkError = mockNetworkError;
        CommandData commandData = new CommandData(CommandEnum.FETCH_AVATAR, null);
        loader.load(commandData);

        DownloadData data = DownloadData.fromRowId(downloadRowId);
        if (DownloadStatus.LOADED.equals(status)) {
            assertFalse("Loaded " + data.getUrl(), commandData.getResult().hasError());
            assertEquals("Loaded " + data.getUrl(), status, loader.getStatus());
        } else {
            assertTrue("Error loading " + data.getUrl(), commandData.getResult().hasError());
        }
        
        if (DownloadStatus.LOADED.equals(status)) {
            assertTrue("File exists " + data.getUrl(), data.getFile().exists());
        } else {
            assertFalse("File doesn't exist " + data.getUrl(), data.getFile().exists());
        }

        assertEquals("Loaded " + data.getUrl(), status, loader.getStatus());
        
        return data.getRowId();
    }
    
}
