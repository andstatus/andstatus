/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.context;

import org.andstatus.app.util.MyLog;

import android.app.Application;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;

import java.io.File;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyApplication extends Application {
    private static final String TAG = MyApplication.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        MyLog.v(this, "onCreate started");
        MyContextHolder.storeContextIfNotPresent(this, this);
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
        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
            MyLog.v(this, "openOrCreateDatabase, name=" + name + ( db!=null ? " opened '" + db.getPath() + "'" : " NOT opened" ));
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
