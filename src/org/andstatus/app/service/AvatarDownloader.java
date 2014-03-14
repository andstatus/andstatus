package org.andstatus.app.service;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.AvatarDrawable;
import org.andstatus.app.data.AvatarStatus;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.Avatar;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.net.HttpJavaNetUtils;
import org.andstatus.app.util.MyLog;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

class AvatarDownloader {
    private long userId;
    private long rowId = 0;
    private String fileNameStored = "";
    private AvatarStatus status = AvatarStatus.UNKNOWN; 

    private boolean hardError = false;
    private boolean softError = false;
    private URL url = null;
    private long loadTimeNew = 0;
    private String fileNameNew = "";

    boolean mockNetworkError = false;
    
    AvatarDownloader(long userIdIn) {
        userId = userIdIn;
        loadStoredData();
    }

    private void loadStoredData() {
        String urlString = MyProvider.userIdToStringColumnValue(User.AVATAR_URL, userId);
        hardError = TextUtils.isEmpty(urlString);
        if (!hardError) {
            try {
                url = new URL(urlString);
            } catch (MalformedURLException e) {
                logError("Bad url='" + urlString + "'", e);
                hardError = true;
            }
        }
        if (hardError) {
            return;
        }
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
                fileNameStored = cursor.getString(2);
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
        if (AvatarStatus.LOADED.equals(status) 
                && !new AvatarDrawable(userId, fileNameStored).exists()) {
           status = AvatarStatus.ABSENT;
        }
    }
    
    void load(CommandData commandData) {
        if (!hardError) {
            switch (status) {
                case LOADED:
                    break;
                case HARD_ERROR:
                    hardError = true;
                    break;
                default:
                    loadUrl();
                    break;
            }
        }
        if (hardError) {
            commandData.getResult().incrementParseExceptions();
        }
        if (softError) {
            commandData.getResult().incrementNumIoExceptions();
        }
    }

    private void loadUrl() {
        loadTimeNew =  System.currentTimeMillis();
        fileNameNew =  Long.toString(userId) + "_" + Long.toString(loadTimeNew);
        downloadAvatarFile();
        saveToDatabase();
        if (!isError()) {
            removeOld();
            MyLog.v(this, "Loaded avatar userId=" + userId);
        }
    }

    private void downloadAvatarFile() {
        String method = "downloadAvatarFile";
        File fileTemp = new AvatarDrawable(userId, "temp_" + fileNameNew).getFile();
        try {
            InputStream is = HttpJavaNetUtils.urlOpenStream(url);
            try {
                byte[] buffer = new byte[1024];
                int length;
                OutputStream out = null;
                out = new BufferedOutputStream(new FileOutputStream(fileTemp));
                try {
                    if (mockNetworkError) {
                        throw new IOException(method + ", Mocked IO exception");
                    }
                    while ((length = is.read(buffer))>0) {
                        out.write(buffer, 0, length);
                      }
                } finally {
                    DbUtils.closeSilently(out);
                }
            } finally {
                DbUtils.closeSilently(is);
            }
        } catch (FileNotFoundException e) {
            logError(method + ", File not found", e);
            hardError = true;
        } catch (IOException e) {
            logError(method, e);
            softError = true;
        }
        if (isError()) {
            deleteFileLogged(fileTemp);
        }
        File fileNew = new AvatarDrawable(userId, fileNameNew).getFile();
        deleteFileLogged(fileNew);
        if (!isError() && !fileTemp.renameTo(fileNew)) {
            MyLog.v(this, method + ", Couldn't rename file " + fileTemp + " to " + fileNew);
            softError = true;
        }
    }

    private void deleteFileLogged(File file) {
        if(file.exists()) {
            if (file.delete()) {
                MyLog.v(this, "Deleted file " + file.toString());
            } else {
                MyLog.e(this, "Couldn't delete file " + file.toString());
            }
        }
    }
    
    private void saveToDatabase() {
        if (hardError) {
            status = AvatarStatus.HARD_ERROR;
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
                fileNameStored = fileNameNew;
            }
        } catch (Exception e) {
            logError("Couldn't save to database", e);
            softError = true;
        }
    }

    private void addNew() {
       ContentValues values = new ContentValues();
       values.put(Avatar.USER_ID, userId);
       values.put(Avatar.VALID_FROM, loadTimeNew);
       values.put(Avatar.URL, url.toExternalForm());
       values.put(Avatar.STATUS, status.save());
       values.put(Avatar.FILE_NAME, fileNameNew);
       values.put(Avatar.LOADED_DATE, loadTimeNew);

       rowId = DbUtils.addRowWithRetry(Avatar.TABLE_NAME, values, 3);
       if (rowId == -1) {
           softError = true;
       }
    }

    private boolean isError() {
        return softError || hardError;
    }
    
    private void update() {
        ContentValues values = new ContentValues();
        values.put(Avatar.STATUS, status.save());
        if (!isError()) {
            values.put(Avatar.FILE_NAME, fileNameNew);
        }
        values.put(Avatar.LOADED_DATE, loadTimeNew);

        if (DbUtils.updateRowWithRetry(Avatar.TABLE_NAME, rowId, values, 3) != 1) {
            softError = true;
        }
        if (!isError()) {
            deleteAvatarByFileName(fileNameStored);
        }
    }

    private void deleteAvatarByFileName(String fileNameToDelete) {
        AvatarDrawable avatarDrawable = new AvatarDrawable(userId, fileNameToDelete);
        if (avatarDrawable.exists()) {
            deleteFileLogged(avatarDrawable.getFile());
        }
    }
    
    private void logError(String message, Exception e) {
        MyLog.e(this, message 
                + "; userId=" + userId 
                + "; url=" + (url == null ? "(null)" : url.toExternalForm()), e);
    }
    
    private void removeOld() {
        String method = "removeOld";
        int rowsDeleted = 0;
        String where = Avatar.USER_ID + "=" + userId
                + " AND " + Avatar._ID + "<>" + Long.toString(rowId) ;
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
                    deleteAvatarByFileName(cursor.getString(1));
                    rowsDeleted += db.delete(Avatar.TABLE_NAME, Avatar._ID + "=" + Long.toString(rowIdOld), null);
                }
                done = true;
            } catch (SQLiteException e) {
                MyLog.i(this, method + ", Database is locked, pass=" + pass + "; sql='" + sql + "'", e);
            } finally {
                DbUtils.closeSilently(cursor);
            }
            if (!done) {
                try {
                    Thread.sleep(Math.round((Math.random() + 1) * 500));
                } catch (InterruptedException e) {
                    MyLog.e(this, e);
                }
            }
        }
        MyLog.v(this, method + (done ? " succeeded" : " failed") + "; deleted " + rowsDeleted + " old rows");
    }

    protected String getFileName() {
        return fileNameStored;
    }
    
    protected long getRowId() {
        return rowId;
    }

    public AvatarStatus getStatus() {
        return status;
    }
}
