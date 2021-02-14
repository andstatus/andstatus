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

package org.andstatus.app.data;

import androidx.annotation.NonNull;

import org.andstatus.app.context.MyStorage;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtil;

import java.io.File;
import java.util.Objects;

public class DownloadFile implements IsEmpty {
    public static final DownloadFile EMPTY = new DownloadFile("");

    private final String filename;
    private final File file;
    /** Existence is checked at the moment of the object creation */
    public final boolean existed;

    public DownloadFile(String filename) {
        Objects.requireNonNull(filename);
        this.filename = filename;
        if (StringUtil.isEmpty(filename)) {
            file = null;
            existed = false;
        } else {
            file = MyStorage.newMediaFile(filename);
            existed = existsNow();
        }
    }

    public final boolean isEmpty() {
        return file == null;
    }

    public final boolean existsNow() {
        return nonEmpty() && file.exists() && file.isFile();
    }
    
    public File getFile() {
        return file;
    }

    @NonNull
    public String getFilePath() {
        return file == null ? "" : file.getAbsolutePath();
    }

    public long getSize() {
        return existsNow() ? file.length() : 0;
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
        if(existsNow()) {
            deleted = file.delete();
            if (deleted) {
                MyLog.v(this, () -> "Deleted file " + file);
            } else {
                MyLog.e(this, "Couldn't delete file " + file);
            }
        }
        return deleted;
    }

    @Override
    public String toString() {
        return MyStringBuilder.objToTag(this)
                + " filename:" + filename
                + (existed ? ", existed" : "");
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + filename.hashCode();
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
        return filename.equals(other.filename);
    }
}
