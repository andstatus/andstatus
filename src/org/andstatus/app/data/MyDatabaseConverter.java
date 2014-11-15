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

package org.andstatus.app.data;

import android.app.Activity;
import android.app.ProgressDialog;
import android.database.sqlite.SQLiteDatabase;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccountConverter;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabaseConverterController.UpgradeParams;
import org.andstatus.app.util.DialogFactory;
import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.MyLog;

import java.io.File;

class MyDatabaseConverter {
    long startTime = java.lang.System.currentTimeMillis();
    private Activity activity;
    private ProgressDialog progress = null;

    protected boolean execute(UpgradeParams params) {
        boolean success = false;
        activity = params.upgradeRequestor; 
        try {
            upgradeStarted();
            convertAll(params.db, params.oldVersion, params.newVersion);
            success = true;
        } catch (ApplicationUpgradeException e) {
            closeProgressDialog();
            showProgressDialog(e.getMessage());
            try {
                Thread.sleep(5000);
            } catch (Exception e2) {
                MyLog.d(this, "while sleeping", e2);
            }
        } finally {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                MyLog.d(this, "while sleeping", e);
            }
            upgradeEnded();
            if (MyContextHolder.get().isTestRun()) {
                activity.finish();
                try {
                    Thread.sleep(500);
                } catch (Exception e) {
                    MyLog.d(this, "while sleeping", e);
                }
            }
        }
        long endTime = java.lang.System.currentTimeMillis();
        if (success) {
            MyLog.w(this, "Upgrade successfully completed in " + ((endTime - startTime)/1000) + " seconds");
        } else {
            MyLog.e(this, "Upgrade failed in " + ((endTime - startTime)/1000) + " seconds");
        }
        return success;
    }

    private void upgradeStarted() {
        showProgressDialog(activity.getText(R.string.label_upgrading));
    }

    private void showProgressDialog(final CharSequence message) {
        try {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progress = new ProgressDialog(activity, ProgressDialog.STYLE_SPINNER);
                    progress.setTitle(R.string.app_name);
                    progress.setMessage(message);
                    progress.show();
                }
            });
        } catch (Exception e) {
            MyLog.d(this, "upgradeStarted", e);
        }
    }

    private void upgradeEnded() {
        closeProgressDialog();
    }

    private void closeProgressDialog() {
        try {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DialogFactory.dismissSafely(progress);
                }
              });
        } catch (Exception e) {
            MyLog.d(this, "upgradeEnded", e);
        }
    }
    
    private void convertAll(SQLiteDatabase db, int oldVersion, int newVersion) throws ApplicationUpgradeException {
        int currentVersion = oldVersion;
        MyLog.i(this, "Upgrading database from version " + oldVersion + " to version " + newVersion);
        if (oldVersion < 14) {
            throw new ApplicationUpgradeException("Upgrade from this database version (" 
                    + oldVersion + ") is not supported. Please reinstall the application");
        } 
        if (currentVersion == 14) {
            currentVersion = convert14to15(db, currentVersion);
        }
        if (currentVersion == 15) {
            currentVersion = convert15to16(db, currentVersion);
        }
        if (currentVersion == 16) {
            currentVersion = convert16to17(db, currentVersion);
        }
        if (currentVersion == 17) {
            currentVersion = convert17to18(db, currentVersion);
        }
        if (currentVersion == 18) {
            currentVersion = convert18to19(db, currentVersion);
        }
        if ( currentVersion == newVersion) {
            MyLog.i(this, "Successfully upgraded database from version " + oldVersion + " to version "
                    + newVersion + ".");
        } else {
            MyLog.e(this, "Error upgrading database from version " + oldVersion + " to version "
                    + newVersion + ". Current database version=" + currentVersion);
            throw new ApplicationUpgradeException("Database upgrade failed. Current database version=" + currentVersion);
        }
    }

    private int convert14to15(SQLiteDatabase db, int oldVersion) {
        final int versionTo = 15;
        boolean ok = false;
        String sql = "";
        try {
            MyLog.i(this, "Database upgrading step from version " + oldVersion + " to version " + versionTo );
            
            sql = "ALTER TABLE msg ADD COLUMN public BOOLEAN DEFAULT 0 NOT NULL";
            MyDatabase.execSQL(db, sql);
            sql = "UPDATE msg SET public=0";
            MyDatabase.execSQL(db, sql);
            
            ok = true;
        } catch (Exception e) {
            MyLog.e(this, e);
        }
        if (ok) {
            MyLog.i(this, "Database upgrading step successfully upgraded database from " + oldVersion + " to version " + versionTo);
        } else {
            MyLog.e(this, "Database upgrading step failed to upgrade database from " + oldVersion 
                    + " to version " + versionTo
                    + " SQL='" + sql +"'");
        }
        return ok ? versionTo : oldVersion;
    }

    private int convert15to16(SQLiteDatabase db, int oldVersion) {
        final int versionTo = 16;
        boolean ok = false;
        String sql = "";
        try {
            MyLog.i(this, "Database upgrading step from version " + oldVersion + " to version " + versionTo );

            ok = MyAccountConverter.convert14to16(db, oldVersion) == versionTo;
            if (ok) {
                sql = "DELETE FROM Origin WHERE _ID IN(6, 7)";
                MyDatabase.execSQL(db, sql);
            }
        } catch (Exception e) {
            MyLog.e(this, e);
        }
        if (ok) {
            MyLog.i(this, "Database upgrading step successfully upgraded database from " + oldVersion + " to version " + versionTo);
        } else {
            MyLog.e(this, "Database upgrading step failed to upgrade database from " + oldVersion 
                    + " to version " + versionTo
                    + " SQL='" + sql +"'");
        }
        return ok ? versionTo : oldVersion;
    }

    private int convert16to17(SQLiteDatabase db, int oldVersion) {
        final int versionTo = 17;
        boolean ok = false;
        String sql = "";
        try {
            MyLog.i(this, "Database upgrading step from version " + oldVersion + " to version " + versionTo );

            File avatarsDir = MyPreferences.getDataFilesDir("avatars", null);
            if (avatarsDir.exists()) {
                FileUtils.deleteFilesRecursively(avatarsDir);
                avatarsDir.delete();
            }
            
            sql = "DROP TABLE avatar";
            MyDatabase.execSQL(db, sql);
            sql = "CREATE TABLE download (_id INTEGER PRIMARY KEY AUTOINCREMENT,download_type INTEGER NOT NULL,user_id INTEGER,msg_id INTEGER,content_type INTEGER NOT NULL,valid_from INTEGER NOT NULL,url TEXT NOT NULL,loaded_date INTEGER,download_status INTEGER NOT NULL DEFAULT 0,file_name TEXT)";
            MyDatabase.execSQL(db, sql);
            sql = "CREATE INDEX idx_download_user ON download (user_id, download_status)";
            MyDatabase.execSQL(db, sql);
            sql = "CREATE INDEX idx_download_msg ON download (msg_id, content_type, download_status)";
            MyDatabase.execSQL(db, sql);
            
            sql = "ALTER TABLE origin RENAME TO oldorigin";
            MyDatabase.execSQL(db, sql);
            sql = "DROP INDEX idx_origin_name";
            MyDatabase.execSQL(db, sql);

            sql = "CREATE TABLE origin (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_type_id INTEGER NOT NULL,origin_name TEXT NOT NULL,origin_url TEXT NOT NULL,ssl BOOLEAN DEFAULT 0 NOT NULL,allow_html BOOLEAN DEFAULT 0 NOT NULL,text_limit INTEGER NOT NULL,short_url_length INTEGER NOT NULL DEFAULT 0)";
            MyDatabase.execSQL(db, sql);
            sql = "CREATE UNIQUE INDEX idx_origin_name ON origin (origin_name)";
            MyDatabase.execSQL(db, sql);
            sql = "INSERT INTO origin (_id, origin_type_id, origin_name, origin_url, ssl, allow_html, text_limit, short_url_length)" + 
            " SELECT _id, origin_type_id, origin_name, host, ssl, allow_html, text_limit, short_url_length" +
                    " FROM oldorigin";
            MyDatabase.execSQL(db, sql);
            sql = "DROP TABLE oldorigin";
            MyDatabase.execSQL(db, sql);
            
            ok = true;
        } catch (Exception e) {
            MyLog.e(this, e);
        }
        if (ok) {
            MyLog.i(this, "Database upgrading step successfully upgraded database from " + oldVersion + " to version " + versionTo);
        } else {
            MyLog.e(this, "Database upgrading step failed to upgrade database from " + oldVersion 
                    + " to version " + versionTo
                    + " SQL='" + sql +"'");
        }
        return ok ? versionTo : oldVersion;
    }
    
    private int convert17to18(SQLiteDatabase db, int oldVersion) {
        final int versionTo = 18;
        boolean ok = false;
        String sql = "";
        try {
            MyLog.i(this, "Database upgrading step from version " + oldVersion + " to version " + versionTo );

            sql = "DROP INDEX IF EXISTS idx_username";
            MyDatabase.execSQL(db, sql);

            sql = "CREATE INDEX idx_user_origin ON user (origin_id, user_oid)";
            MyDatabase.execSQL(db, sql);

            sql = "ALTER TABLE user ADD COLUMN webfinger_id TEXT";
            MyDatabase.execSQL(db, sql);
            sql = "UPDATE user SET webfinger_id=username";
            MyDatabase.execSQL(db, sql);
            
            ok = true;
        } catch (Exception e) {
            MyLog.e(this, e);
        }
        if (ok) {
            MyLog.i(this, "Database upgrading step successfully upgraded database from " + oldVersion + " to version " + versionTo);
        } else {
            MyLog.e(this, "Database upgrading step failed to upgrade database from " + oldVersion 
                    + " to version " + versionTo
                    + " SQL='" + sql +"'");
        }
        return ok ? versionTo : oldVersion;
    }

    private int convert18to19(SQLiteDatabase db, int oldVersion) {
        final int versionTo = 19;
        boolean ok = false;
        String sql = "";
        try {
            MyLog.i(this, "Database upgrading step from version " + oldVersion + " to version " + versionTo );

            sql = "DROP INDEX IF EXISTS idx_msg_origin";
            MyDatabase.execSQL(db, sql);

            sql = "CREATE INDEX idx_msg_origin ON msg (origin_id, msg_sent_date)";
            MyDatabase.execSQL(db, sql);
            
            sql = "CREATE INDEX idx_msg_sent_date ON msg (msg_sent_date)";
            MyDatabase.execSQL(db, sql);
            
            ok = true;
        } catch (Exception e) {
            MyLog.e(this, e);
        }
        if (ok) {
            MyLog.i(this, "Database upgrading step successfully upgraded database from " + oldVersion + " to version " + versionTo);
        } else {
            MyLog.e(this, "Database upgrading step failed to upgrade database from " + oldVersion 
                    + " to version " + versionTo
                    + " SQL='" + sql +"'");
        }
        return ok ? versionTo : oldVersion;
    }
}
