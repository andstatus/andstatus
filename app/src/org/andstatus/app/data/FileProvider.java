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

import android.app.Activity;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.andstatus.app.ClassInApplicationPackage;

import java.io.FileNotFoundException;

public class FileProvider extends ContentProvider {

    public static final String AUTHORITY = ClassInApplicationPackage.PACKAGE_NAME + ".data.FileProvider";
    
    public static final String DOWNLOAD_FILE_PATH = "downloadfile";
    public static final Uri DOWNLOAD_FILE_URI = Uri.parse("content://" + AUTHORITY + "/" + DOWNLOAD_FILE_PATH);
    
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        DownloadFile downloadFile = new DownloadFile(uriToFilename(uri));
        if(!downloadFile.exists()) {
            throw new FileNotFoundException(downloadFile.getFilename());
        }
        return ParcelFileDescriptor.open(downloadFile.getFile(), ParcelFileDescriptor.MODE_READ_ONLY);        
    }

    private String uriToFilename(Uri uri) {
        String filename = null;
        switch(uri.getPathSegments().get(0)) {
            case DOWNLOAD_FILE_PATH:
                filename = uri.getPathSegments().get(1);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        return filename;
    }

    public static Uri downloadFilenameToUri(String filename) {
        return Uri.withAppendedPath(DOWNLOAD_FILE_URI, filename);
    }

    public static void viewImage(Activity activity, String imageFilename) {
        Intent intent = new Intent();                   
        intent.setAction(android.content.Intent.ACTION_VIEW);
        intent.setDataAndType(downloadFilenameToUri(imageFilename),"image/*");
        activity.startActivity(intent);
    }
    
    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

}
