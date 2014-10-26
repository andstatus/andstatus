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
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadFile;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.net.HttpApacheUtils;
import org.andstatus.app.util.MyLog;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class FileDownloader {
    private final DownloadData data;
    boolean mockNetworkError = false;
    
    static FileDownloader newForUser(long userIdIn) {
        return new FileDownloader(AvatarData.newForUser(userIdIn));
    }

    static FileDownloader newForDownloadRow(long rowIdIn) {
        return new FileDownloader(DownloadData.fromRowId(rowIdIn));
    }
    
    private FileDownloader(DownloadData dataIn) {
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
            data.deleteOtherOfThisUser();
            MyLog.v(this, "Loaded avatar userId:" + data.userId + "; url:" + data.getUrl().toExternalForm());
        }
    }

    private void downloadFile() {
        final String method = "downloadFile";
        DownloadFile fileTemp = new DownloadFile("temp_" + data.getFileNameNew());
        try {
            // See http://hc.apache.org/httpcomponents-client-ga/tutorial/html/fundamentals.html
            HttpGet httpget = new HttpGet(data.getUrl().toExternalForm());
            HttpResponse response = HttpApacheUtils.getHttpClient().execute(httpget);
            parseStatusCode(response.getStatusLine().getStatusCode());
            try {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    InputStream in = entity.getContent();
                    try {
                        byte[] buffer = new byte[1024];
                        int length;
                        OutputStream out = null;
                        out = new BufferedOutputStream(new FileOutputStream(fileTemp.getFile()));
                        try {
                            if (mockNetworkError) {
                                throw new IOException(method + ", Mocked IO exception");
                            }
                            while ((length = in.read(buffer)) > 0) {
                                out.write(buffer, 0, length);
                            }
                        } finally {
                            DbUtils.closeSilently(out);
                        }
                    } finally {
                        DbUtils.closeSilently(in);
                    }
                }
            } finally {
                DbUtils.closeSilently(response);
            }            
        } catch (FileNotFoundException e) {
            data.hardErrorLogged(method + ", File not found", e);
        } catch (IOException e) {
            data.softErrorLogged(method, e);
        }
        if (data.isError()) {
            fileTemp.delete();
        }
        DownloadFile fileNew = new DownloadFile(data.getFileNameNew());
        fileNew.delete();
        if (!data.isError() && !fileTemp.getFile().renameTo(fileNew.getFile())) {
            data.softErrorLogged(method + ", Couldn't rename file " + fileTemp + " to " + fileNew, null);
        }
    }

    private void parseStatusCode(int code) throws IOException {
        switch (code) {
        case 200:
        case 304:
            break;
        case 401:
            throw new IOException(String.valueOf(code));
        case 400:
        case 403:
        case 404:
            throw new FileNotFoundException(String.valueOf(code));
        case 500:
        case 502:
        case 503:
            throw new IOException(String.valueOf(code));
        default:
            break;
        }
    }
    
    public DownloadStatus getStatus() {
        return data.getStatus();
    }
    
    public String getFileName() {
        return data.getFileName();
    }
}
