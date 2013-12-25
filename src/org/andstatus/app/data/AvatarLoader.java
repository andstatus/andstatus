package org.andstatus.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.text.TextUtils;

import org.andstatus.app.CommandData;
import org.andstatus.app.MyContextHolder;
import org.andstatus.app.data.MyDatabase.Avatar;
import org.andstatus.app.data.MyDatabase.User;
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

public class AvatarLoader {
    private long userId;
    private long rowId = 0;
    private String fileNameStored = "";
    private AvatarStatus status = AvatarStatus.UNKNOWN; 

    private boolean hardError = false;
    private boolean softError = false;
    private URL url = null;
    private long loadTimeNew = 0;
    private String fileNameNew = "";

    protected boolean mockNetworkError = false;
    
    public AvatarLoader(long userIdIn) {
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
        Cursor c = null;
        try {
            c = db.rawQuery(sql, null);
            status = AvatarStatus.ABSENT;
            while (c.moveToNext()) {
                status = AvatarStatus.load(c.getInt(0));
                rowId = c.getLong(1);
                fileNameStored = c.getString(2);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        if (AvatarStatus.LOADED.equals(status) 
                && !new AvatarDrawable(userId, fileNameStored).exists()) {
           status = AvatarStatus.ABSENT;
        }
    }
    
    public void load(CommandData commandData) {
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
            commandData.commandResult.numParseExceptions++;
        }
        if (softError) {
            commandData.commandResult.numIoExceptions++;
        }
    }

    private void loadUrl() {
        loadTimeNew =  System.currentTimeMillis();
        fileNameNew =  Long.toString(userId) + "_" + Long.toString(loadTimeNew);
        File fileTemp = new AvatarDrawable(userId, "temp_" + fileNameNew).getFile();
        try {
            InputStream is = url.openStream();
            try {
                byte[] buffer = new byte[1024];
                int length;
                OutputStream out = null;
                out = new BufferedOutputStream(new FileOutputStream(fileTemp));
                try {
                    if (mockNetworkError) {
                        throw new IOException("Mocked IO exception");
                    }
                    while ((length = is.read(buffer))>0) {
                        out.write(buffer, 0, length);
                      }
                } finally {
                    out.close();
                }
            } finally {
                is.close();
            }
            
        } catch (FileNotFoundException e) {
            logError("File not found loading url", e);
            deleteFileSilently(fileTemp);
            hardError = true;
        } catch (IOException e) {
            logError("Loading url", e);
            deleteFileSilently(fileTemp);
            softError = true;
        }
        saveToDatabase(fileTemp);
        if (!isError()) {
            removeOld();
            MyLog.v(this, "Loaded avatar userId=" + userId);
        }
    }

    private void saveToDatabase(File fileTemp) {
        File fileNew = new AvatarDrawable(userId, fileNameNew).getFile();
        deleteFileSilently(fileNew);
        if (!isError() && !fileTemp.renameTo(fileNew)) {
            MyLog.v(this, "Couldn't rename file " + fileTemp + " to " + fileNew);
            softError = true;
        }
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
        if (!isError() && !TextUtils.isEmpty(fileNameStored)) {
            deleteFileSilently( new AvatarDrawable(userId, fileNameStored).getFile());
        }
    }

    private void deleteFileSilently(File file) {
        if(file.exists()) {
            if (file.delete()) {
                MyLog.v(this, "Deleted file " + file.toString());
            } else {
                MyLog.e(this, "Couldn't delete file " + file.toString());
            }
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
        
        for (int pass=0; pass<3; pass++) {
            SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();
            Cursor c = null;
            try {
                c = db.rawQuery(sql, null);
                while (c.moveToNext()) {
                    long rowIdOld = c.getLong(0);
                    String fileNameOld = c.getString(1);
                    if (!TextUtils.isEmpty(fileNameOld)) {
                        AvatarDrawable avatarDrawable = new AvatarDrawable(userId, fileNameOld);
                        deleteFileSilently(avatarDrawable.getFile());
                    }
                    rowsDeleted += db.delete(Avatar.TABLE_NAME, Avatar._ID + "=" + Long.toString(rowIdOld), null);
                }
                break;
            } catch (SQLiteException e) {
                MyLog.i(this, method + ", Database is locked, pass=" + pass, e);
            } finally {
                if (c != null) {
                    c.close();
                }
            }
            try {
                Thread.sleep(Math.round((Math.random() + 1) * 500));
            } catch (InterruptedException e) {
                MyLog.e(this, e);
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
