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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyDatabase.Avatar;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;

import java.net.MalformedURLException;
import java.net.URL;

public class AvatarData {
    public final long userId;
    private AvatarStatus status = AvatarStatus.UNKNOWN; 
    private long rowId = 0;
    private AvatarFile fileStored = AvatarFile.getEmpty();
    private URL url = null;

    private boolean hardError = false;
    private boolean softError = false;

    private long loadTimeNew = 0;
    private AvatarFile fileNew = AvatarFile.getEmpty();

    public AvatarData(long userIdIn) {
        userId = userIdIn;
        loadUrl();
        if (!hardError) {
            loadOtherFields();
        }
        fixFieldsAfterLoad();
    }

    private void loadUrl() {
        String urlString = MyProvider.userIdToStringColumnValue(User.AVATAR_URL, userId);
        hardError = TextUtils.isEmpty(urlString);
        if (!hardError) {
            try {
                url = new URL(urlString);
            } catch (MalformedURLException e) {
                hardErrorLogged("Bad url='" + urlString + "'", e);
            }
        }
    }

    private void loadOtherFields() {
        String where = Avatar.USER_ID + "=" + userId
                + " AND " + Avatar.URL + "=" + MyProvider.quoteIfNotQuoted(url.toExternalForm()) ;
        String sql = "SELECT " + Avatar.STATUS + ", "
                + Avatar._ID + ", "
                + Avatar.FILE_NAME
                + " FROM " + Avatar.TABLE_NAME 
                + " WHERE " + where;
        
        SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, null);
            status = AvatarStatus.ABSENT;
            while (cursor.moveToNext()) {
                status = AvatarStatus.load(cursor.getInt(0));
                rowId = cursor.getLong(1);
                fileStored = new AvatarFile(cursor.getString(2));
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
    }

    private void fixFieldsAfterLoad() {
        if (fileStored == null) {
            fileStored = AvatarFile.getEmpty();
        }
        fileNew = fileStored;
        if (hardError) {
            status = AvatarStatus.HARD_ERROR;
        } else if (AvatarStatus.LOADED.equals(status) 
                && !fileStored.exists()) {
           status = AvatarStatus.ABSENT;
        } else if (AvatarStatus.HARD_ERROR.equals(status)) {
            hardError = true;
        }
    }

    public void onNewDownload() {
        softError = false;
        hardError = false;
        loadTimeNew =  System.currentTimeMillis();
        fileNew =  new AvatarFile(Long.toString(userId) + "_" + Long.toString(loadTimeNew));
    }
    
    public void saveToDatabase() {
        if (hardError) {
            status = AvatarStatus.HARD_ERROR;
        } else if (!fileNew.exists()) {
            status = AvatarStatus.ABSENT;
        } else if (softError) {
            status = AvatarStatus.SOFT_ERROR;
        } else {
            status = AvatarStatus.LOADED;
        }
        try {
            if (rowId == 0) {
                addNew();
            } else {
                update();
            }
            if (!isError()) {
                fileStored = fileNew;
            }
        } catch (Exception e) {
            softErrorLogged("Couldn't save to database", e);
        }
    }

    private void addNew() {
       ContentValues values = new ContentValues();
       values.put(Avatar.USER_ID, userId);
       values.put(Avatar.URL, url.toExternalForm());
       values.put(Avatar.VALID_FROM, loadTimeNew);
       values.put(Avatar.STATUS, status.save());
       values.put(Avatar.FILE_NAME, fileNew.getFileName());
       values.put(Avatar.LOADED_DATE, loadTimeNew);

       rowId = DbUtils.addRowWithRetry(Avatar.TABLE_NAME, values, 3);
       if (rowId == -1) {
           softError = true;
       }
    }

    public boolean isHardError() {
        return hardError;
    }
    
    public boolean isSoftError() {
        return softError;
    }

    public boolean isError() {
        return softError || hardError;
    }
    
    private void update() {
        ContentValues values = new ContentValues();
        values.put(Avatar.STATUS, status.save());
        boolean changeFile = !isError() && fileNew.exists() && fileStored != fileNew;
        if (changeFile) {
            values.put(Avatar.FILE_NAME, fileNew.getFileName());
            values.put(Avatar.VALID_FROM, loadTimeNew);
        }
        values.put(Avatar.LOADED_DATE, loadTimeNew);

        if (DbUtils.updateRowWithRetry(Avatar.TABLE_NAME, rowId, values, 3) != 1) {
            softError = true;
        }
        if (!isError() && changeFile) {
            fileStored.delete();
        }
    }

    public void hardErrorLogged(String message, Exception e) {
        hardError = true;
        MyLog.i(this, message 
                + "; userId=" + userId 
                + "; url=" + (url == null ? "(null)" : url.toExternalForm()), e);
    }

    public void softErrorLogged(String message, Exception e) {
        softError = true;
        MyLog.v(this, message 
                + "; userId=" + userId 
                + "; url=" + (url == null ? "(null)" : url.toExternalForm()), e);
    }
    
    public void deleteOherOfThisUser() {
        deleteOherOfThisUser(userId, rowId);
    }

    public static void deleteAllOfThisUser(long userId) {
        deleteOherOfThisUser(userId, 0);
    }
    
    public static void deleteOherOfThisUser(long userId, long rowId) {
        final String method = "deleteOherOfThisUser userId=" + userId + (rowId != 0 ? ", rowId=" + rowId : "");
        int rowsDeleted = 0;
        String where = Avatar.USER_ID + "=" + userId
                + (rowId != 0 ? " AND " + Avatar._ID + "<>" + Long.toString(rowId) : "") ;
        String sql = "SELECT " + Avatar._ID + ", "
                + Avatar.FILE_NAME
                + " FROM " + Avatar.TABLE_NAME 
                + " WHERE " + where;
        boolean done = false;
        for (int pass=0; !done && pass<3; pass++) {
            SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();
            Cursor cursor = null;
            try {
                cursor = db.rawQuery(sql, null);
                while (cursor.moveToNext()) {
                    long rowIdOld = cursor.getLong(0);
                    new AvatarFile(cursor.getString(1)).delete();
                    rowsDeleted += db.delete(Avatar.TABLE_NAME, Avatar._ID + "=" + Long.toString(rowIdOld), null);
                }
                done = true;
            } catch (SQLiteException e) {
                MyLog.i(AvatarData.class, method + ", Database is locked, pass=" + pass + "; sql='" + sql + "'", e);
            } finally {
                DbUtils.closeSilently(cursor);
            }
            if (!done) {
                try {
                    Thread.sleep(Math.round((Math.random() + 1) * 500));
                } catch (InterruptedException e) {
                    MyLog.e(AvatarData.class, e);
                }
            }
        }
        MyLog.v(AvatarData.class, method + (done ? " succeeded" : " failed") + "; deleted " + rowsDeleted + " rows");
    }
    
    public AvatarFile getFile() {
        return fileStored;
    }
    
    public String getFileName() {
        return fileStored.getFileName();
    }
    
    public long getRowId() {
        return rowId;
    }

    public AvatarStatus getStatus() {
        return status;
    }

    public String getFileNameNew() {
        return fileNew.getFileName();
    }

    public URL getUrl() {
        return url;
    }

    public void requestDownload() {
        if (!AvatarStatus.LOADED.equals(status) && !hardError) {
            MyServiceManager.sendCommand(new CommandData(CommandEnum.FETCH_AVATAR, null, userId));
        }
    }
}
