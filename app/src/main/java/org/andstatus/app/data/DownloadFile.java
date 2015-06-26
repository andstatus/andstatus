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

package org.andstatus.app.data;

import android.text.TextUtils;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.MyLog;

import java.io.File;

public class DownloadFile {
    private final String filename;
    private final File file;
    private static final DownloadFile EMPTY_DOWNLOAD_FILE = new DownloadFile(null);

    public DownloadFile(String filename) {
        this.filename = filename;
        if (!TextUtils.isEmpty(filename)) {
            file = new File(MyPreferences.getDataFilesDir(MyPreferences.DIRECTORY_DOWNLOADS, null), filename);
        } else {
            file = null;
        }
    }

    public boolean exists() {
        return file != null && file.exists() && file.isFile();
    }
    
    public File getFile() {
        return file;
    }

    public String getFilename() {
        return filename;
    }

    public static DownloadFile getEmpty() {
        return EMPTY_DOWNLOAD_FILE;
    }

    /** returns true if the file existed and was deleted */
    public boolean delete() {
        return deleteFileLogged(file);
    }
    
    private boolean deleteFileLogged(File file) {
        boolean deleted = false;
        if(exists()) {
            deleted = file.delete();
            if (deleted) {
                MyLog.v(this, "Deleted file " + file.toString());
            } else {
                MyLog.e(this, "Couldn't delete file " + file.toString());
            }
        }
        return deleted;
    }

    @Override
    public String toString() {
        return MyLog.objTagToString(this) + " [filename=" + filename + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((filename == null) ? 0 : filename.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DownloadFile other = (DownloadFile) obj;
        if (filename == null) {
            if (other.filename != null) {
                return false;
            }
        } else if (!filename.equals(other.filename)) {
            return false;
        }
        return true;
    }
}
