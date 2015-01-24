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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadFile;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.util.MyLog;

import java.io.File;

abstract class FileDownloader {
    protected final DownloadData data;
    public Connection connectionMock;

    static FileDownloader newForDownloadRow(long rowIdIn) {
        DownloadData data = DownloadData.fromRowId(rowIdIn);
        if (data.userId != 0) {
            return new AvatarDownloader(data);
        } else {
            return new AttachmentDownloader(data);
        }
    }
    
    protected FileDownloader(DownloadData dataIn) {
        data = dataIn;
    }
    
    void load(CommandData commandData) {
        switch (data.getStatus()) {
            case LOADED:
            case HARD_ERROR:
                break;
            default:
                loadUrl();
                break;
        }
        if (data.isError()) {
            commandData.getResult().setMessage(data.getMessage());
        }
        if (data.isHardError()) {
            commandData.getResult().incrementParseExceptions();
        }
        if (data.isSoftError()) {
            commandData.getResult().incrementNumIoExceptions();
        }
    }

    private void loadUrl() {
        if (data.isHardError()) {
            return;
        }
        data.onNewDownload();
        downloadFile();
        data.saveToDatabase();
        if (!data.isError()) {
            onSuccessfulLoad();
        }
    }

    protected abstract void onSuccessfulLoad();

    private void downloadFile() {
        final String method = "downloadFile";
        DownloadFile fileTemp = new DownloadFile("temp_" + data.getFilenameNew());
        try {
            String url = data.getUrl().toExternalForm();
            File file = fileTemp.getFile();
            MyAccount ma = findBestAccountForDownload();
            MyLog.v(this, "About to download " + data.toString() + "; account:" + ma.getAccountName());
            if (ma.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED) {
                ((connectionMock != null) ? connectionMock : ma.getConnection()).downloadFile(url, file);
            } else {
                data.hardErrorLogged(method + ", No account to download the file", null);
            }
        } catch (ConnectionException e) {
            if (e.isHardError()) {
                data.hardErrorLogged(method, e);
            } else {
                data.softErrorLogged(method, e);
            }
        }
        if (data.isError()) {
            fileTemp.delete();
        }
        DownloadFile fileNew = new DownloadFile(data.getFilenameNew());
        fileNew.delete();
        if (!data.isError() && !fileTemp.getFile().renameTo(fileNew.getFile())) {
            data.softErrorLogged(method + ", Couldn't rename file " + fileTemp + " to " + fileNew, null);
        }
    }
    
    public DownloadStatus getStatus() {
        return data.getStatus();
    }
    
    public String getFilename() {
        return data.getFilename();
    }

    protected abstract MyAccount findBestAccountForDownload();
}
