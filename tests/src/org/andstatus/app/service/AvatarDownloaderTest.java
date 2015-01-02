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

import android.content.ContentValues;
import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.AvatarData;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadFile;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyDatabase.Download;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.service.FileDownloader;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.util.MyLog;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class AvatarDownloaderTest extends InstrumentationTestCase {
    private MyAccount ma;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(TestSuite.CONVERSATION_ACCOUNT_NAME + " exists", ma.isValid());
        MyLog.i(this, "setUp ended");
    }

    public void testLoad() throws IOException {
        String urlStringOld = MyProvider.userIdToStringColumnValue(User.AVATAR_URL, ma.getUserId());
        assertEquals(TestSuite.CONVERSATION_ACCOUNT_AVATAR_URL, urlStringOld);
        
        AvatarData.deleteAllOfThisUser(ma.getUserId());
        
        FileDownloader loader = FileDownloader.newForUser(ma.getUserId());
        assertEquals("Not loaded yet", DownloadStatus.ABSENT, loader.getStatus());
        loadAndAssertStatusForMa(DownloadStatus.LOADED, false);
        
        String urlString = "http://andstatus.org/nonexistent_avatar_" + System.currentTimeMillis() +  ".png";
        changeMaAvatarUrl(urlString);
        // Non-existent file is a hard error
        loadAndAssertStatusForMa(DownloadStatus.HARD_ERROR, false);
        
        urlString = "https://raw.github.com/andstatus/andstatus/master/app/res/drawable-mdpi/notification_icon.png";
        assertEquals("Changed 1 row ", 1, changeMaAvatarUrl(urlString));
        loadAndAssertStatusForMa(DownloadStatus.LOADED, false);

        deleteMaAvatar();
        loadAndAssertStatusForMa(DownloadStatus.LOADED, false);
        
        deleteMaAvatar();
        assertEquals("Changed 1 row ", 1, changeMaAvatarStatus(urlString, DownloadStatus.HARD_ERROR));
        // Don't reload if hard error
        loadAndAssertStatusForMa(DownloadStatus.HARD_ERROR, false);

        changeMaAvatarStatus(urlString, DownloadStatus.SOFT_ERROR);
        // Reload on Soft error
        loadAndAssertStatusForMa(DownloadStatus.LOADED, false);
        
        changeMaAvatarUrl("");
        loadAndAssertStatusForMa(DownloadStatus.HARD_ERROR, false);
        
        changeMaAvatarUrl(urlStringOld);
        long rowIdError = loadAndAssertStatusForMa(DownloadStatus.ABSENT, true);
        long rowIdRecovered = loadAndAssertStatusForMa(DownloadStatus.LOADED, false);
        assertEquals("Updated the same row ", rowIdError, rowIdRecovered);
    }

    private void deleteMaAvatar() {
        DownloadData data = AvatarData.newForUser(ma.getUserId());
        assertTrue("Loaded avatar file deleted", data.getFile().delete());
    }

    public void testDeletedFile() throws IOException {
        changeMaAvatarUrl(TestSuite.CONVERSATION_ACCOUNT_AVATAR_URL);
        String urlString = MyProvider.userIdToStringColumnValue(User.AVATAR_URL, ma.getUserId());
        assertEquals(TestSuite.CONVERSATION_ACCOUNT_AVATAR_URL, urlString);
        
        loadAndAssertStatusForMa(DownloadStatus.LOADED, false);
        DownloadData data = AvatarData.newForUser(ma.getUserId());
        assertTrue("Existence of " + data.getFilename(), data.getFile().exists());
        assertTrue("Is File" + data.getFilename(), data.getFile().getFile().isFile());

        DownloadFile avatarFile = data.getFile();
        AvatarData.deleteAllOfThisUser(ma.getUserId());
        assertFalse(avatarFile.exists());

        loadAndAssertStatusForMa(DownloadStatus.LOADED, false);
        data = AvatarData.newForUser(ma.getUserId());
        assertTrue(data.getFile().exists());
    }
    
    private int changeMaAvatarUrl(String urlString) throws MalformedURLException {
        return changeAvatarUrl(ma, urlString);
    }

    static int changeAvatarUrl(MyAccount myAccount, String urlString) throws MalformedURLException {
        ContentValues values = new ContentValues();
        values.put(User.AVATAR_URL, urlString);
        return MyContextHolder.get().getDatabase().getWritableDatabase()
                .update(User.TABLE_NAME, values, User._ID + "=" + myAccount.getUserId(), null);
    }

    private int changeMaAvatarStatus(String urlString, DownloadStatus status) throws MalformedURLException {
        URL url = new URL(urlString); 
        ContentValues values = new ContentValues();
        values.put(Download.DOWNLOAD_STATUS, status.save());
        return MyContextHolder.get().getDatabase().getWritableDatabase()
                .update(Download.TABLE_NAME, values, Download.USER_ID + "=" + ma.getUserId() 
                        + " AND " + Download.URL + "=" + MyProvider.quoteIfNotQuoted(url.toExternalForm()), null);
    }

    private long loadAndAssertStatusForMa(DownloadStatus status, boolean mockNetworkError) throws IOException {
        FileDownloader loader = FileDownloader.newForUser(ma.getUserId());
        loader.mockNetworkError = mockNetworkError;
        CommandData commandData = new CommandData(CommandEnum.FETCH_AVATAR, null);
        loader.load(commandData);

        DownloadData data = AvatarData.newForUser(ma.getUserId());
        if (DownloadStatus.LOADED.equals(status)) {
            assertFalse("Loaded " + data.getUrl(), commandData.getResult().hasError());
            assertEquals("Loaded " + data.getUrl(), status, loader.getStatus());
        } else {
            assertTrue("Error loading " + data.getUrl(), commandData.getResult().hasError());
        }
        
        if (DownloadStatus.LOADED.equals(status)) {
            assertTrue("Exists avatar " + data.getUrl(), data.getFile().exists());
        } else {
            assertFalse("Doesn't exist avatar " + data.getUrl(), data.getFile().exists());
        }

        assertEquals("Loaded " + data.getUrl(), status, loader.getStatus());
        
        return data.getRowId();
    }
}
