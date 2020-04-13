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

package org.andstatus.app.data.converter;

import android.database.sqlite.SQLiteDatabase;

import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.util.MyLog;

class DatabaseConverter {
    long startTime = java.lang.System.currentTimeMillis();
    ProgressLogger progressLogger;
    String converterError = "";

    protected boolean execute(DatabaseConverterController.UpgradeParams params) {
        boolean success = false;
        progressLogger = params.progressLogger;
        String msgLog = "";
        long endTime = 0;
        try {
            convertAll(params.db, params.oldVersion, params.newVersion);
            success = true;
            endTime = java.lang.System.currentTimeMillis();
        } catch (ApplicationUpgradeException e) {
            endTime = java.lang.System.currentTimeMillis();
            msgLog = e.getMessage();
            progressLogger.logProgress(msgLog);
            converterError = msgLog;
            MyLog.ignored(this, e);
            DbUtils.waitMs("ApplicationUpgradeException", 30000);
        } finally {
            DbUtils.waitMs("execute finally", 2000);
            if (success) {
                msgLog = "Upgrade successfully completed in "
                        + java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(endTime - startTime)
                        + " seconds";
                MyLog.i(this, msgLog);
            } else {
                msgLog = "Upgrade failed in "
                        + java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(endTime - startTime)
                        + " seconds";
                MyLog.e(this, msgLog);
            }
            DbUtils.waitMs("execute finally 2", 500);
        }
        return success;
    }

    private void convertAll(SQLiteDatabase db, int oldVersion, int newVersion) {
        int currentVersion = oldVersion;
        MyLog.i(this, "Upgrading database from version " + oldVersion + " to version " + newVersion);
        boolean converterNotFound = false;
        String lastError = "?";
        ConvertOneStep oneStep;
        do {
            oneStep = null;
            try {
                int prevVersion = currentVersion;
                Class clazz = Class.forName(this.getClass().getPackage().getName() + ".Convert" + currentVersion);
                oneStep = (ConvertOneStep) clazz.newInstance();
                currentVersion = oneStep.execute(db, currentVersion, progressLogger);
                if (currentVersion == prevVersion) {
                    lastError = oneStep.getLastError();
                    MyLog.e(this, "Stuck at version " + prevVersion + "\n"
                            + "Error: " + lastError);
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
            } catch (InstantiationException | IllegalAccessException e) {
                MyLog.e(this, "Error at version " + currentVersion, e);
            }
        } while (oneStep != null && currentVersion < newVersion);

        if (currentVersion == newVersion) {
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
                    + newVersion + ". Current database version=" + currentVersion
                    + " \n" + "Error: " + lastError;

            MyLog.e(this, msgLog);
            throw new ApplicationUpgradeException(msgLog);
        }
    }

}
