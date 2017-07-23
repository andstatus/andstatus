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

import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.context.MyStorage;
import org.andstatus.app.util.MyLog;

import java.io.File;

public class DownloadFile {
    private final String filename;
    private final File file;
    public static final DownloadFile EMPTY = new DownloadFile(null);

    public DownloadFile(String filename) {
        this.filename = filename;
        if (!TextUtils.isEmpty(filename)) {
            file = new File(MyStorage.getDataFilesDir(MyStorage.DIRECTORY_DOWNLOADS), filename);
        } else {
            file = null;
        }
    }

    public boolean isEmpty() {
        return file == null;
    }

    public boolean exists() {
        return !isEmpty() && file.exists() && file.isFile();
    }
    
    public File getFile() {
        return file;
    }

    @NonNull
    public String getFilePath() {
        return file == null ? "" : file.getAbsolutePath();
    }

    public long getSize() {
        if (exists()) {
            return file.length();
        }
        return 0;
    }

    public String getFilename() {
        return filename;
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
        return MyLog.objToLongTag(this) + " [filename=" + filename + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((filename == null) ? 0 : filename.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DownloadFile other = (DownloadFile) o;
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
