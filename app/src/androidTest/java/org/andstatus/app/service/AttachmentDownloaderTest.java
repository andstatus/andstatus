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

import android.net.Uri;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DemoNoteInserter;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.FileProvider;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.ConnectionTwitterGnuSocialMock;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.util.MyLog;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AttachmentDownloaderTest {
    private MyAccount ma;
    
    @Before
    public void setUp() throws Exception {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);
        ma = demoData.getMyAccount(demoData.gnusocialTestAccountName);
        assertTrue(demoData.gnusocialTestAccountName + " exists", ma.isValid());
    }

    @Test
    public void testImageAttachmentLoad() throws IOException {
        String body = "A note with an image attachment";
        DemoNoteInserter inserter = new DemoNoteInserter(ma);
        AActivity activity = inserter.buildActivity(inserter.buildActor(), body, null, null,
                DownloadStatus.LOADED);
        activity.getNote().attachments.add(Attachment.fromUrlAndContentType(
                new URL("http://www.publicdomainpictures.net/pictures/60000/nahled/landscape-1376582205Yno.jpg"),
                MyContentType.IMAGE));
        inserter.onActivity(activity);
        
        DownloadData dd = DownloadData.getSingleForNote(activity.getNote().noteId,
                activity.getNote().attachments.get(0).contentType, null);
        assertEquals("Image URI stored", activity.getNote().attachments.get(0).getUri(), dd.getUri());
        
        loadAndAssertStatusForRow(dd.getDownloadId(), DownloadStatus.ABSENT, true);

        loadAndAssertStatusForRow(dd.getDownloadId(), DownloadStatus.LOADED, false);
        
        testFileProvider(dd.getDownloadId());
    }
    
    private void testFileProvider(long downloadRowId) throws IOException {
        DownloadData data = DownloadData.fromId(downloadRowId);
        assertTrue(data.getFilename(), data.getFile().exists());

        Uri uri = FileProvider.downloadFilenameToUri(data.getFile().getFilename());
        InputStream in = MyContextHolder.get().context().getContentResolver().openInputStream(uri);
        byte[] buffer = new byte[100];
        int bytesRead = in.read(buffer);
        assertEquals(buffer.length, bytesRead);
        in.close();
    }

    public static void loadAndAssertStatusForRow(long downloadRowId, DownloadStatus status, boolean mockNetworkError) {
        FileDownloader loader = FileDownloader.newForDownloadRow(downloadRowId);
        if (mockNetworkError) {
            loader.connectionMock = new ConnectionTwitterGnuSocialMock(new ConnectionException("Mocked IO exception"));
        }
        CommandData commandData = CommandData.newCommand(CommandEnum.GET_AVATAR);
        loader.load(commandData);

        DownloadData data = DownloadData.fromId(downloadRowId);
        if (DownloadStatus.LOADED.equals(status)) {
            assertFalse("Loaded " + data.getUri() + "; " + data, commandData.getResult().hasError());
            assertEquals("Loaded " + data.getUri(), status, loader.getStatus());
        } else {
            assertTrue("Error loading " + data.getUri(), commandData.getResult().hasError());
        }
        
        if (DownloadStatus.LOADED.equals(status)) {
            assertTrue("File exists " + data.getUri(), data.getFile().exists());
        } else {
            assertFalse("File doesn't exist " + data.getUri(), data.getFile().exists());
        }

        assertEquals("Loaded " + data.getUri(), status, loader.getStatus());
    }

}
