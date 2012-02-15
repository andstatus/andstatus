/**
 * 
 */
package org.andstatus.app;

import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.util.MyLog;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.util.Log;

import java.io.File;

/**
 *
 */
public class MyApplication extends Application {
    private static final String TAG = "MyApplication";

    /*
     * @see android.app.Application#onCreate()
     */
    @Override
    public void onCreate() {
        MyPreferences.initialize(this, this);
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            MyLog.v(TAG, "onCreate");
        }
        super.onCreate();
    }

    /*
     * @see android.content.ContextWrapper#getDatabasePath(java.lang.String)
     */
    @Override
    public File getDatabasePath(String name) {
        File dbDir = MyPreferences.getDataFilesDir(MyPreferences.DIRECTORY_DATABASES);
        File dbAbsolutePath = null;
        if (dbDir != null) {
            dbAbsolutePath = new File(dbDir.getPath() + "/" + name);
        }
        return dbAbsolutePath;
    }

    /*
     * @see android.content.ContextWrapper#openOrCreateDatabase(java.lang.String, int, android.database.sqlite.SQLiteDatabase.CursorFactory)
     */
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

}
