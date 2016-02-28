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
            MyLog.ignored(this, e);
            mySleepWithLogging(5000);
        } finally {
            mySleepWithLogging(1000);
            upgradeEnded();
            if (MyContextHolder.get().isTestRun()) {
                activity.finish();
                mySleepWithLogging(500);
            }
        }
        long endTime = java.lang.System.currentTimeMillis();
        if (success) {
            MyLog.w(this, "Upgrade successfully completed in "
                    + java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(endTime - startTime)
                    + " seconds");
        } else {
            MyLog.e(this, "Upgrade failed in "
                    + java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(endTime - startTime) 
                    + " seconds");
        }
        return success;
    }

    private void mySleepWithLogging(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception e) {
            MyLog.d(this, "while sleeping", e);
        }
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
        boolean converterNotFound = false;
        OneStep oneStep;
        do {
            oneStep = null;
            try {
                int prevVersion = currentVersion;
                Class clazz = Class.forName(this.getClass().getName() + "$Convert" + Integer.toString(currentVersion));
                oneStep = (OneStep) clazz.newInstance();
                currentVersion = oneStep.execute(db, currentVersion);
                if (currentVersion == prevVersion) {
                    MyLog.e(this, "Stuck at version " + prevVersion);
                    oneStep = null;
                }
            } catch (ClassNotFoundException e) {
                converterNotFound = true;
                String msgLog = "No converter for version " + currentVersion;
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(this, msgLog, e);
                } else {
                    MyLog.i(this, msgLog);
                }
            } catch (InstantiationException e) {
                MyLog.e(this, "Error at version " + currentVersion, e);
            } catch (IllegalAccessException e) {
                MyLog.e(this, "Error at version " + currentVersion, e);
            }
        } while (oneStep != null && currentVersion < newVersion);

        if ( currentVersion == newVersion) {
            MyLog.i(this, "Successfully upgraded database from version " + oldVersion + " to version "
                    + newVersion + ".");
        } else {
            String msgLog;
            if (converterNotFound) {
                msgLog = "This version of application doesn't support database upgrade";
            } else {
                msgLog = "Error upgrading database";
            }
            msgLog += " from version " + oldVersion + " to version "
                    + newVersion + ". Current database version=" + currentVersion;
            MyLog.e(this, msgLog);
            throw new ApplicationUpgradeException(msgLog);
        }
    }

    private static abstract class OneStep {
        SQLiteDatabase db;
        int oldVersion;
        int versionTo;
        String sql = "";

        int execute (SQLiteDatabase db, int oldVersion) {
            boolean ok = false;
            this.db = db;
            this.oldVersion = oldVersion;
            try {
                MyLog.i(this, "Database upgrading step from version " + oldVersion + " to version " + versionTo);
                execute2();
                ok = true;
            } catch (Exception e) {
                MyLog.e(this, e);
            }
            if (ok) {
                MyLog.i(this, "Database upgrading step successfully upgraded database from "
                        + oldVersion + " to version " + versionTo);
            } else {
                MyLog.e(this, "Database upgrading step failed to upgrade database from " + oldVersion
                        + " to version " + versionTo
                        + " SQL='" + sql + "'");
            }
            return ok ? versionTo : oldVersion;
        }

        protected abstract void execute2();
    }

    static class Convert14 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 15;
            sql = "ALTER TABLE msg ADD COLUMN public BOOLEAN DEFAULT 0 NOT NULL";
            MyDatabase.execSQL(db, sql);
            sql = "UPDATE msg SET public=0";
            MyDatabase.execSQL(db, sql);
        }
    }

    static class Convert15 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 16;
            boolean ok = MyAccountConverter.convert14to16(db, oldVersion) == versionTo;
            if (ok) {
                sql = "DELETE FROM Origin WHERE _ID IN(6, 7)";
                MyDatabase.execSQL(db, sql);
            }
        }
    }

    static class Convert16 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 17;
            File avatarsDir = MyPreferences.getDataFilesDir("avatars", null);
            if (avatarsDir.exists()) {
                FileUtils.deleteFilesRecursively(avatarsDir);
                if (!avatarsDir.delete()) {
                    MyLog.e(this, "Couldn't delete " + avatarsDir.getAbsolutePath());
                }
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
        }
    }

    static class Convert17 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 18 ;

            sql = "DROP INDEX IF EXISTS idx_username";
            MyDatabase.execSQL(db, sql);

            sql = "CREATE INDEX idx_user_origin ON user (origin_id, user_oid)";
            MyDatabase.execSQL(db, sql);

            sql = "ALTER TABLE user ADD COLUMN webfinger_id TEXT";
            MyDatabase.execSQL(db, sql);
            sql = "UPDATE user SET webfinger_id=username";
            MyDatabase.execSQL(db, sql);
        }
    }

    static class Convert18 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 19;

            sql = "CREATE INDEX idx_msg_sent_date ON msg (msg_sent_date)";
            MyDatabase.execSQL(db, sql);
        }
    }

    static class Convert19 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 20;

            sql = "ALTER TABLE origin ADD COLUMN ssl_mode INTEGER DEFAULT 1";
            MyDatabase.execSQL(db, sql);
            sql = "ALTER TABLE origin ADD COLUMN in_combined_global_search BOOLEAN DEFAULT 1";
            MyDatabase.execSQL(db, sql);
            sql = "ALTER TABLE origin ADD COLUMN in_combined_public_reload BOOLEAN DEFAULT 1";
            MyDatabase.execSQL(db, sql);
            sql = "UPDATE origin SET ssl_mode=1, in_combined_global_search=1, in_combined_public_reload=1";
            MyDatabase.execSQL(db, sql);
            sql = "UPDATE origin SET ssl_mode=2 WHERE origin_url LIKE '%quitter.zone%'";
            MyDatabase.execSQL(db, sql);
        }
    }

    static class Convert20 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 21;

            sql = "ALTER TABLE origin ADD COLUMN mention_as_webfinger_id INTEGER DEFAULT 3";
            MyDatabase.execSQL(db, sql);
            sql = "UPDATE origin SET mention_as_webfinger_id=3";
            MyDatabase.execSQL(db, sql);
            sql = "CREATE INDEX idx_msg_in_reply_to_msg_id ON msg (in_reply_to_msg_id)";
            MyDatabase.execSQL(db, sql);
        }
    }

    static class Convert21 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 22;

            sql = "ALTER TABLE origin ADD COLUMN use_legacy_http INTEGER DEFAULT 3";
            MyDatabase.execSQL(db, sql);
            sql = "UPDATE origin SET use_legacy_http=3";
            MyDatabase.execSQL(db, sql);
        }
    }

    static class Convert22 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 23;

            sql = "ALTER TABLE msg ADD COLUMN msg_status INTEGER NOT NULL DEFAULT 0";
            MyDatabase.execSQL(db, sql);
            sql = "UPDATE msg SET msg_status=0";
            MyDatabase.execSQL(db, sql);
            sql = "UPDATE msg SET msg_status=2 WHERE msg_created_date IS NOT NULL";
            MyDatabase.execSQL(db, sql);


        }
    }

    static class Convert23 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 24;

            sql = "UPDATE user SET user_created_date = 0 WHERE user_created_date IS NULL";
            MyDatabase.execSQL(db, sql);
            sql = "DROP INDEX idx_user_origin";
            MyDatabase.execSQL(db, sql);
            sql = "ALTER TABLE user RENAME TO olduser";
            MyDatabase.execSQL(db, sql);

            sql = "CREATE TABLE user (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,user_oid TEXT,username TEXT NOT NULL,webfinger_id TEXT NOT NULL,real_name TEXT,user_description TEXT,location TEXT,profile_url TEXT,homepage TEXT,avatar_url TEXT,banner_url TEXT,msg_count INTEGER DEFAULT 0 NOT NULL,favorited_count INTEGER DEFAULT 0 NOT NULL,following_count INTEGER DEFAULT 0 NOT NULL,followers_count INTEGER DEFAULT 0 NOT NULL,user_created_date INTEGER,user_updated_date INTEGER,user_ins_date INTEGER NOT NULL,home_timeline_position TEXT DEFAULT '' NOT NULL,home_timeline_item_date INTEGER DEFAULT 0 NOT NULL,home_timeline_date INTEGER DEFAULT 0 NOT NULL,favorites_timeline_position TEXT DEFAULT '' NOT NULL,favorites_timeline_item_date INTEGER DEFAULT 0 NOT NULL,favorites_timeline_date INTEGER DEFAULT 0 NOT NULL,direct_timeline_position TEXT DEFAULT '' NOT NULL,direct_timeline_item_date INTEGER DEFAULT 0 NOT NULL,direct_timeline_date INTEGER DEFAULT 0 NOT NULL,mentions_timeline_position TEXT DEFAULT '' NOT NULL,mentions_timeline_item_date INTEGER DEFAULT 0 NOT NULL,mentions_timeline_date INTEGER DEFAULT 0 NOT NULL,user_timeline_position TEXT DEFAULT '' NOT NULL,user_timeline_item_date INTEGER DEFAULT 0 NOT NULL,user_timeline_date INTEGER DEFAULT 0 NOT NULL,following_user_date INTEGER DEFAULT 0 NOT NULL,followers_user_date INTEGER DEFAULT 0 NOT NULL,user_msg_id INTEGER DEFAULT 0 NOT NULL,user_msg_date INTEGER DEFAULT 0 NOT NULL)";
            MyDatabase.execSQL(db, sql);
            sql = "CREATE UNIQUE INDEX idx_user_origin ON user (origin_id, user_oid)";
            MyDatabase.execSQL(db, sql);
            sql = "INSERT INTO user (" +
                    " _id, origin_id, user_oid, username, webfinger_id, real_name, user_description, location," +
                    " profile_url, homepage, avatar_url, banner_url," +
                    " msg_count, favorited_count, following_count, followers_count," +
                    " user_created_date, user_updated_date, user_ins_date," +
                    " home_timeline_position, home_timeline_item_date, home_timeline_date, favorites_timeline_position, favorites_timeline_item_date, favorites_timeline_date, direct_timeline_position, direct_timeline_item_date, direct_timeline_date, mentions_timeline_position, mentions_timeline_item_date, mentions_timeline_date, user_timeline_position, user_timeline_item_date, user_timeline_date," +
                    " following_user_date, followers_user_date, user_msg_id, user_msg_date" +
                    ") SELECT " +
                    " FROM olduser" +
                    " _id, origin_id, user_oid, username, webfinger_id, real_name, user_description, NULL," +
                    " url,         homepage, avatar_url, NULL," +
                    "         0,               0,               0,               0," +
                    " user_created_date,                 0, user_ins_date," +
                    " home_timeline_position, home_timeline_item_date, home_timeline_date, favorites_timeline_position, favorites_timeline_item_date, favorites_timeline_date, direct_timeline_position, direct_timeline_item_date, direct_timeline_date, mentions_timeline_position, mentions_timeline_item_date, mentions_timeline_date, user_timeline_position, user_timeline_item_date, user_timeline_date," +
                    " following_user_date,                   0, user_msg_id, user_msg_date";
            MyDatabase.execSQL(db, sql);
            sql = "DROP TABLE olduser";
            MyDatabase.execSQL(db, sql);

        }
    }
}
