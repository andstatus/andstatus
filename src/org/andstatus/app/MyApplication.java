/**
 * 
 */
package org.andstatus.app;

import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.util.MyLog;

import android.app.Application;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.util.Log;

import java.io.File;

/**
 *
 * @author yvolk (Yuri Volkov), http://yurivolkov.com
 */
public class MyApplication extends Application {
    private static final String TAG = MyApplication.class.getSimpleName();

    @Override
    public void onCreate() {
        MyPreferences.initialize(this, this);
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            MyLog.v(TAG, "onCreate");
        }
        super.onCreate();
    }

    @Override
    public File getDatabasePath(String name) {
        return MyPreferences.getDatabasePath(name, null);
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, CursorFactory factory) {
        SQLiteDatabase db = null;
        File dbAbsolutePath = getDatabasePath(name);
        if (dbAbsolutePath != null) {
            db = SQLiteDatabase.openDatabase(dbAbsolutePath.getPath(), factory, SQLiteDatabase.CREATE_IF_NECESSARY + SQLiteDatabase.OPEN_READWRITE );
        }
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            MyLog.v(TAG, "openOrCreateDatabase, name=" + name + ( db!=null ? " opened '" + db.getPath() + "'" : " NOT opened" ));
        }
        return db;
    }

    
    /**
     * Since: API Level 11
     * Simplified implementation
     */
    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, CursorFactory factory,
            DatabaseErrorHandler errorHandler) {
        
        return openOrCreateDatabase(name, mode, factory);
    }

    @Override
    public String toString() {
        return "AndStatus. " + super.toString();
    }

}
