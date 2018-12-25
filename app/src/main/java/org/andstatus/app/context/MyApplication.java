/*
 * Copyright (C) 2013-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import androidx.annotation.NonNull;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.andstatus.app.R;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TamperingDetector;

import java.io.File;
import java.util.List;

@ReportsCrashes(mode = ReportingInteractionMode.DIALOG,
        mailTo = "andstatus@gmail.com",
        resDialogIcon = R.drawable.icon,
        resDialogText = R.string.crash_dialog_text,
        resDialogCommentPrompt = R.string.crash_dialog_comment_prompt,
        alsoReportToAndroidFramework = true)
public class MyApplication extends Application {
    public volatile boolean isAcraProcess = false;

    @Override
    public void onCreate() {
        super.onCreate();
        String processName = getCurrentProcessName(this);
        isAcraProcess = processName.endsWith(":acra");
        MyLog.v(this, "onCreate "
                + (isAcraProcess ? "ACRA" : "'" + processName + "'") + " process");
        if (!isAcraProcess) {
            MyContextHolder.storeContextIfNotPresent(this, this);
            MyLocale.setLocale(this);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(isAcraProcess ? newConfig :
                MyLocale.onConfigurationChanged(this, newConfig));
    }
    
    @Override
    public File getDatabasePath(String name) {
        return isAcraProcess ? super.getDatabasePath(name) : MyStorage.getDatabasePath(name);
    }

    @Override
    protected void attachBaseContext(Context base) {
        MyLog.v(this, () -> "attachBaseContext started" + (isAcraProcess ? ". ACRA process" : ""));
        super.attachBaseContext(base);
        ACRA.init(this);
        TamperingDetector.initialize(this);
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, CursorFactory factory) {
        if (isAcraProcess) {
            return super.openOrCreateDatabase(name, mode, factory);
        }
        SQLiteDatabase db = null;
        File dbAbsolutePath = getDatabasePath(name);
        if (dbAbsolutePath != null) {
            db = SQLiteDatabase.openDatabase(dbAbsolutePath.getPath(), factory,
                    SQLiteDatabase.CREATE_IF_NECESSARY + SQLiteDatabase.OPEN_READWRITE );
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "openOrCreateDatabase, name=" + name + ( db!=null ? " opened '"
                    + db.getPath() + "'" : " NOT opened" ));
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
        if (isAcraProcess) {
            return super.openOrCreateDatabase(name, mode, factory, errorHandler);
        }
        return openOrCreateDatabase(name, mode, factory);
    }

    @Override
    public String toString() {
        return "AndStatus. " + (isAcraProcess ? "acra." : "") + super.toString();
    }

    @NonNull
    private static String getCurrentProcessName(@NonNull Application app) {
        final int processId = android.os.Process.myPid();
        final ActivityManager manager = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processInfos = manager.getRunningAppProcesses();
        String processName = null;
        if (processInfos != null) {
            for (final ActivityManager.RunningAppProcessInfo processInfo : processInfos) {
                if (processInfo.pid == processId) {
                    processName = processInfo.processName;
                    break;
                }
            }
        }
        return StringUtils.isEmpty(processName) ? "?" : processName;
    }

}
