/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.andstatus.app.context.MyContextState;
import org.andstatus.app.context.MyStorage;
import org.andstatus.app.util.MyLog;

public final class DatabaseHolder extends SQLiteOpenHelper  {
    private final boolean creationEnabled;
    private boolean wasNotCreated = false;

    public static final String DATABASE_NAME = "andstatus.sqlite";

    public DatabaseHolder(Context context, boolean creationEnabled) {
        super(context, DATABASE_NAME, null, DatabaseCreator.DATABASE_VERSION);
        this.creationEnabled = creationEnabled;
        if (!creationEnabled && !context.getDatabasePath(DATABASE_NAME).exists()) {
            wasNotCreated = true;
        }
    }

    private final ThreadLocal<Boolean> onUpgradeTriggered = new ThreadLocal<>();
    public MyContextState checkState() {
        if (wasNotCreated) {
            return MyContextState.DATABASE_UNAVAILABLE;
        }
        if (DatabaseConverterController.isUpgradeError()) {
            return MyContextState.ERROR;
        }
        MyContextState state = MyContextState.ERROR;
        try {
            onUpgradeTriggered.set(false);
            if (MyStorage.isDataAvailable()) {
                SQLiteDatabase db = getWritableDatabase();
                if (onUpgradeTriggered.get() || DatabaseConverterController.isUpgrading()) {
                    state = MyContextState.UPGRADING;
                } else {
                    if (db != null && db.isOpen()) {
                        state = MyContextState.DATABASE_READY;
                    }
                }
            }
        } catch (IllegalStateException e) {
            MyLog.v(this, e);
            if (onUpgradeTriggered.get()) {
                state = MyContextState.UPGRADING;
            }
        } catch (Exception e) {
            MyLog.d(this, "Error during checkState", e);
        }
        return state;
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        if (!creationEnabled) {
            wasNotCreated = true;
            MyLog.e(this, "Database creation disabled");
            return;
        }
        new DatabaseCreator(db).create().insertData();
    }

    /**
     * We don't need here neither try-catch nor transactions because they are
     * being used in calling method
     * 
     * @see android.database.sqlite.SQLiteOpenHelper#getWritableDatabase
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)  {
        onUpgradeTriggered.set(true);
        new DatabaseConverterController().onUpgrade(db, oldVersion, newVersion);
    }
}
