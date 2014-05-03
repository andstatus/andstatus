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
import org.andstatus.app.data.MyDatabaseConverterController.UpgradeParams;
import org.andstatus.app.util.DialogFactory;
import org.andstatus.app.util.MyLog;

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
            db.execSQL(sql);
            sql = "UPDATE msg SET public=0";
            db.execSQL(sql);
            
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
                db.execSQL(sql);
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
}
