/**
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

import org.andstatus.app.data.AvatarData;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.net.HttpJavaNetUtils;
import org.andstatus.app.util.MyLog;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class AvatarDownloader {
    private final AvatarData data;
    boolean mockNetworkError = false;
    
    AvatarDownloader(long userIdIn) {
        data = new AvatarData(userIdIn);
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
        downloadAvatarFile();
        data.saveToDatabase();
        if (!data.isError()) {
            data.deleteOtherOfThisUser();
            MyLog.v(this, "Loaded avatar userId:" + data.userId + "; url:" + data.getUrl().toExternalForm());
        }
    }

    private void downloadAvatarFile() {
        final String method = "downloadAvatarFile";
        AvatarFile fileTemp = new AvatarFile("temp_" + data.getFileNameNew());
        try {
            InputStream is = HttpJavaNetUtils.urlOpenStream(data.getUrl());
            try {
                byte[] buffer = new byte[1024];
                int length;
                OutputStream out = null;
                out = new BufferedOutputStream(new FileOutputStream(fileTemp.getFile()));
                try {
                    if (mockNetworkError) {
                        throw new IOException(method + ", Mocked IO exception");
                    }
                    while ((length = is.read(buffer)) > 0) {
                        out.write(buffer, 0, length);
                    }
                } finally {
                    DbUtils.closeSilently(out);
                }
            } finally {
                DbUtils.closeSilently(is);
            }
        } catch (FileNotFoundException e) {
            data.hardErrorLogged(method + ", File not found", e);
        } catch (IOException e) {
            data.softErrorLogged(method, e);
        }
        if (data.isError()) {
            fileTemp.delete();
        }
        AvatarFile fileNew = new AvatarFile(data.getFileNameNew());
        fileNew.delete();
        if (!data.isError() && !fileTemp.getFile().renameTo(fileNew.getFile())) {
            data.softErrorLogged(method + ", Couldn't rename file " + fileTemp + " to " + fileNew, null);
        }
    }

    public DownloadStatus getStatus() {
        return data.getStatus();
    }
    
    public String getFileName() {
        return data.getFileName();
    }
}
