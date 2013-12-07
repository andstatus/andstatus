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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class AvatarLoader {
    private long userId;
    private long rowId = 0;

    private boolean hardError = false;
    private boolean softError = false;
    private URL url = null;
    private long loadTime = 0;
    private String fileName = "";
    private File fileNew = null;
    private AvatarStatus status = AvatarStatus.ABSENT; 

    protected boolean mockNetworkError = false;
    
    public AvatarLoader(long userIdIn) {
        userId = userIdIn;
    }

    public void load(CommandData commandData) {
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
        if (!hardError) {
            status = getStatusAndRowId(url);
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

    protected AvatarStatus getStatusAndRowId(URL url) {
        AvatarStatus status = AvatarStatus.UNKNOWN;
        String where = Avatar.USER_ID + "=" + userId
                + " AND " + Avatar.URL + "=" + MyProvider.quoteIfNotQuoted(url.toExternalForm()) ;
        String sql = "SELECT " + Avatar.STATUS + ", " 
                + Avatar._ID
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
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return status;
    }

    private void loadUrl() {
        loadTime =  System.currentTimeMillis();
        fileName =  Long.toString(userId) + "_" + Long.toString(loadTime);
        File fileTemp = new AvatarDrawable(userId, "temp_" + fileName).getFile();
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
        fileNew = new AvatarDrawable(userId, fileName).getFile();
        deleteFileSilently(fileNew);
        if (!fileTemp.renameTo(fileNew)) {
            MyLog.v(this, "Couldn't rename file " + fileTemp + " to " + fileNew);
            softError = true;
        }
        if (softError) {
            status = AvatarStatus.SOFT_ERROR;
        } else if (hardError) {
            status = AvatarStatus.HARD_ERROR;
        } else {
            status = AvatarStatus.LOADED;
        }
        try {
            AvatarStatus statusOld = getStatusAndRowId(url);
            switch (statusOld) {
                case ABSENT:
                    addNew();
                    break;
                default:
                    update();
                    break;
            }
        } catch (Exception e) {
            logError("Couldn't save to database", e);
            softError = true;
        }
    }

    private void addNew() {
       ContentValues values = new ContentValues();
       values.put(Avatar.USER_ID, userId);
       values.put(Avatar.VALID_FROM, loadTime);
       values.put(Avatar.URL, url.toExternalForm());
       values.put(Avatar.STATUS, status.save());
       values.put(Avatar.FILE_NAME, fileName);
       values.put(Avatar.LOADED_DATE, loadTime);

       for (int pass=0; pass<5; pass++) {
           try {
               rowId = MyContextHolder.get().getDatabase().getReadableDatabase()
                       .insert(Avatar.TABLE_NAME, null, values);
               break;
           } catch (SQLiteException e) {
               rowId = -1;
               MyLog.i(this, "update, Database is locked, pass=" + pass, e);
               try {
                   Thread.sleep(Math.round((Math.random() + 1) * 200));
               } catch (InterruptedException e2) {
                   MyLog.e(this, e2);
               }
           }
       }
       if (rowId == -1) {
           logError("Failed to insert row", null);
           hardError = true;
       }
    }

    private boolean isError() {
        return softError || hardError;
    }
    
    private void update() {
        ContentValues values = new ContentValues();
        values.put(Avatar.STATUS, status.save());
        values.put(Avatar.FILE_NAME, fileName);
        values.put(Avatar.LOADED_DATE, loadTime);

        int rowsUpdated = 0;
        for (int pass=0; pass<5; pass++) {
            try {
                rowsUpdated = MyContextHolder.get().getDatabase().getReadableDatabase()
                        .update(Avatar.TABLE_NAME, values, Avatar._ID + "=" + Long.toString(rowId), null);
                break;
            } catch (SQLiteException e) {
                MyLog.i(this, "update, Database is locked, pass=" + pass, e);
                try {
                    Thread.sleep(Math.round((Math.random() + 1) * 200));
                } catch (InterruptedException e2) {
                    MyLog.e(this, e2);
                }
            }
        }
        if (rowsUpdated != 1) {
            logError("Failed to update rowId=" + rowId + " updated " + rowsUpdated + " rows", null);
            hardError = true;
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
    
    protected void removeOld() {
        String where = Avatar.USER_ID + "=" + userId
                + " AND " + Avatar._ID + "<>" + Long.toString(rowId) ;
        String sql = "SELECT " + Avatar._ID + ", "
                + Avatar.FILE_NAME
                + " FROM " + Avatar.TABLE_NAME 
                + " WHERE " + where;
        
        SQLiteDatabase db = MyContextHolder.get().getDatabase().getWritableDatabase();
        Cursor c = null;
        try {
            c = db.rawQuery(sql, null);
            status = AvatarStatus.ABSENT;
            while (c.moveToNext()) {
                long rowIdOld = c.getLong(0);
                String fileNameOld = c.getString(1);
                if (!TextUtils.isEmpty(fileNameOld)) {
                    AvatarDrawable avatarDrawable = new AvatarDrawable(userId, fileNameOld);
                    deleteFileSilently(avatarDrawable.getFile());
                }
                sql = "DELETE FROM " + Avatar.TABLE_NAME 
                        + " WHERE " + Avatar._ID + "=" + Long.toString(rowIdOld);
                db.execSQL(sql);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    protected String getFileName() {
        return fileName;
    }
    
    protected long getRowId() {
        return rowId;
    }
}
