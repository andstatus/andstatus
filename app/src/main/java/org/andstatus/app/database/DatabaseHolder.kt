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
package org.andstatus.app.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.andstatus.app.context.MyContextState
import org.andstatus.app.context.MyStorage
import org.andstatus.app.data.converter.DatabaseConverterController
import org.andstatus.app.util.MyLog
import java.util.concurrent.atomic.AtomicBoolean

class DatabaseHolder(context: Context, private val creationEnabled: Boolean) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DatabaseCreator.DATABASE_VERSION) {
    @Volatile
    private var databaseWasNotCreated = false
    private val onUpgradeTriggered: AtomicBoolean = AtomicBoolean(false)
    fun checkState(): MyContextState {
        if (databaseWasNotCreated) {
            return MyContextState.DATABASE_UNAVAILABLE
        }
        if (DatabaseConverterController.isUpgradeError()) {
            return MyContextState.ERROR
        }
        var state = MyContextState.ERROR
        try {
            onUpgradeTriggered.set(false)
            if (MyStorage.isDataAvailable()) {
                val db = writableDatabase
                if (onUpgradeTriggered.get() || DatabaseConverterController.isUpgrading()) {
                    state = MyContextState.UPGRADING
                } else {
                    if (db != null && db.isOpen) {
                        state = MyContextState.DATABASE_READY
                    }
                }
            }
        } catch (e: IllegalStateException) {
            MyLog.v(this, e)
            if (onUpgradeTriggered.get()) {
                state = MyContextState.UPGRADING
            }
        }
        return state
    }

    override fun onCreate(db: SQLiteDatabase) {
        if (!creationEnabled) {
            databaseWasNotCreated = true
            MyLog.e(this, "Database creation disabled")
            return
        }
        DatabaseCreator(db).create().insertData()
    }

    /**
     * We need here neither try-catch nor transactions because they are
     * being used in calling method
     *
     * @see android.database.sqlite.SQLiteOpenHelper.getWritableDatabase
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (onUpgradeTriggered.compareAndSet(false, true)) {
            DatabaseConverterController().onUpgrade(db, oldVersion, newVersion)
        }
    }

    companion object {
        val DATABASE_NAME: String = "andstatus.sqlite"
    }

    init {
        val databasePath = context.getDatabasePath(DATABASE_NAME)
        if (databasePath == null || !creationEnabled && !databasePath.exists()) {
            databaseWasNotCreated = true
        }
    }
}