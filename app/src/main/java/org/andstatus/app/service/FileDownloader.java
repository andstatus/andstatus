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
import org.andstatus.app.context.MyStorage;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadFile;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.ConnectionLocal;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.UriUtils;

import java.io.File;

public abstract class FileDownloader {
    protected final DownloadData data;
    private Connection connectionMock;
    private ConnectionRequired connectionRequired = ConnectionRequired.ANY;

    static FileDownloader newForDownloadData(DownloadData data) {
        if (data.actorId != 0) {
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
                break;
            default:
                loadUrl();
                break;
        }
        if (data.isError() && StringUtil.nonEmpty(data.getMessage())) {
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
        data.beforeDownload();
        downloadFile();
        data.saveToDatabase();
        if (!data.isError()) {
            onSuccessfulLoad();
        }
    }

    protected abstract void onSuccessfulLoad();

    private void downloadFile() {
        final String method = "downloadFile";
        DownloadFile fileTemp = new DownloadFile(MyStorage.TEMP_FILENAME_PREFIX + data.getFilenameNew());
        try {
            File file = fileTemp.getFile();
            MyAccount ma = findBestAccountForDownload();
            MyLog.v(this, () -> "About to download " + data.toString() + "; account:" + ma.getAccountName());
            if (ma.isValidAndSucceeded()) {
                ((connectionMock != null) ? connectionMock : getConnection(ma, data.getUri()))
                        .downloadFile(connectionRequired, data.getUri(), file);
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
            data.softErrorLogged(method + "; Couldn't rename file " + fileTemp + " to " + fileNew, null);
        }
        data.onDownloaded();
    }

    public FileDownloader setConnectionRequired(ConnectionRequired connectionRequired) {
        this.connectionRequired = connectionRequired;
        return this;
    }

    private Connection getConnection(MyAccount ma, Uri uri) throws ConnectionException {
        if (UriUtils.isEmpty(uri)) {
            throw new ConnectionException(ConnectionException.StatusCode.NOT_FOUND, "No Uri to (down)load from: '" + uri + "'");
        }
        if (UriUtils.isDownloadable(uri)) {
            return ma.getConnection();
        } else {
            return new ConnectionLocal();
        }
    }

    public DownloadStatus getStatus() {
        return data.getStatus();
    }

    protected abstract MyAccount findBestAccountForDownload();

    public static void load(DownloadData downloadData, CommandData commandData) {
        FileDownloader downloader = FileDownloader.newForDownloadData(downloadData);
        downloader.load(commandData);
    }

    public FileDownloader setConnectionMock(Connection connectionMock) {
        this.connectionMock = connectionMock;
        return this;
    }
}
