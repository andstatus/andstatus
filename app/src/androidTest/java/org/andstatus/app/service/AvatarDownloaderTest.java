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
import org.andstatus.app.database.DatabaseHolder.Download;
import org.andstatus.app.database.DatabaseHolder.User;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.ConnectionTwitterGnuSocialMock;
import org.andstatus.app.util.MyLog;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class AvatarDownloaderTest extends InstrumentationTestCase {
    private MyAccount ma = MyAccount.getEmpty(MyContextHolder.get(), "");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);
        MyLog.i(this, "setUp ended");
    }

    public void testLoadPumpio() throws IOException {
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(TestSuite.CONVERSATION_ACCOUNT_NAME + " exists", ma.isValid());
        loadForOneMyAccount(TestSuite.CONVERSATION_ACCOUNT_AVATAR_URL);
    }

    public void testLoadBasicAuth() throws IOException {
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME);
        assertTrue(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME + " exists", ma.isValid());
        loadForOneMyAccount(TestSuite.GNUSOCIAL_TEST_ACCOUNT_AVATAR_URL);
    }
    
    private void loadForOneMyAccount(String urlStringInitial) throws IOException {
        String urlString1 = MyQuery.userIdToStringColumnValue(User.AVATAR_URL, ma.getUserId());
        assertEquals(urlStringInitial, urlString1);
        
        AvatarData.deleteAllOfThisUser(ma.getUserId());
        
        FileDownloader loader = new AvatarDownloader(ma.getUserId());
        assertEquals("Not loaded yet", DownloadStatus.ABSENT, loader.getStatus());
        loadAndAssertStatusForMa(DownloadStatus.LOADED, false);
        
        String urlString = "http://andstatus.org/nonexistent_avatar_" + System.currentTimeMillis() +  ".png";
        changeMaAvatarUrl(urlString);
        // Non-existent file is a hard error
        loadAndAssertStatusForMa(DownloadStatus.HARD_ERROR, false);
        
        urlString = "https://raw.githubusercontent.com/andstatus/andstatus/master/app/src/main/res/drawable-mdpi/notification_icon.png";
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
        
        changeMaAvatarUrl(urlStringInitial);
        long rowIdError = loadAndAssertStatusForMa(DownloadStatus.ABSENT, true);
        long rowIdRecovered = loadAndAssertStatusForMa(DownloadStatus.LOADED, false);
        assertEquals("Updated the same row ", rowIdError, rowIdRecovered);
    }

    private void deleteMaAvatar() {
        DownloadData data = AvatarData.getForUser(ma.getUserId());
        assertTrue("Loaded avatar file deleted", data.getFile().delete());
    }

    public void testDeletedFile() throws IOException {
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        
        changeMaAvatarUrl(TestSuite.CONVERSATION_ACCOUNT_AVATAR_URL);
        String urlString = MyQuery.userIdToStringColumnValue(User.AVATAR_URL, ma.getUserId());
        assertEquals(TestSuite.CONVERSATION_ACCOUNT_AVATAR_URL, urlString);
        
        loadAndAssertStatusForMa(DownloadStatus.LOADED, false);
        DownloadData data = AvatarData.getForUser(ma.getUserId());
        assertTrue("Existence of " + data.getFilename(), data.getFile().exists());
        assertTrue("Is File" + data.getFilename(), data.getFile().getFile().isFile());

        DownloadFile avatarFile = data.getFile();
        AvatarData.deleteAllOfThisUser(ma.getUserId());
        assertFalse(avatarFile.exists());

        loadAndAssertStatusForMa(DownloadStatus.LOADED, false);
        data = AvatarData.getForUser(ma.getUserId());
        assertTrue(data.getFile().exists());
    }
    
    private int changeMaAvatarUrl(String urlString) {
        return changeAvatarUrl(ma, urlString);
    }

    static int changeAvatarUrl(MyAccount myAccount, String urlString) {
        ContentValues values = new ContentValues();
        values.put(User.AVATAR_URL, urlString);
        return MyContextHolder.get().getDatabase()
                .update(User.TABLE_NAME, values, User._ID + "=" + myAccount.getUserId(), null);
    }

    private int changeMaAvatarStatus(String urlString, DownloadStatus status) throws MalformedURLException {
        URL url = new URL(urlString); 
        ContentValues values = new ContentValues();
        values.put(Download.DOWNLOAD_STATUS, status.save());
        return MyContextHolder.get().getDatabase()
                .update(Download.TABLE_NAME, values, Download.USER_ID + "=" + ma.getUserId() 
                        + " AND " + Download.URI + "=" + MyQuery.quoteIfNotQuoted(url.toExternalForm()), null);
    }

    private long loadAndAssertStatusForMa(DownloadStatus status, boolean mockNetworkError) {
        FileDownloader loader = new AvatarDownloader(ma.getUserId());
        if (mockNetworkError) {
            loader.connectionMock = new ConnectionTwitterGnuSocialMock(new ConnectionException("Mocked IO exception"));
        }
        CommandData commandData = new CommandData(CommandEnum.FETCH_AVATAR, null);
        loader.load(commandData);

        DownloadData data = AvatarData.getForUser(ma.getUserId());
        if (DownloadStatus.LOADED.equals(status)) {
            assertFalse("Loaded " + data, commandData.getResult().hasError());
            assertEquals("Loaded " + data.getUri(), status, loader.getStatus());
        } else {
            assertTrue("Error loading " + data.getUri(), commandData.getResult().hasError());
        }
        
        if (DownloadStatus.LOADED.equals(status)) {
            assertTrue("Exists avatar " + data.getUri(), data.getFile().exists());
        } else {
            assertFalse("Doesn't exist avatar " + data.getUri(), data.getFile().exists());
        }

        assertEquals("Loaded '" + data.getUri() + "'", status, loader.getStatus());
        
        return data.getDownloadId();
    }
}
