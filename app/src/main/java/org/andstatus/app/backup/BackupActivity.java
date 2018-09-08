/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.backup;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.Permissions;
import org.andstatus.app.util.SimpleFileDialog;

import java.io.File;

public class BackupActivity extends MyActivity {
    File backupFolder = new File(SimpleFileDialog.getRootFolder());
    BackupTask asyncTask = null;
    private int progressCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.backup;
        super.onCreate(savedInstanceState);

        Permissions.checkPermissionAndRequestIt(this, Permissions.PermissionType.WRITE_EXTERNAL_STORAGE);

        setBackupFolder(MyBackupManager.getDefaultBackupDirectory(this));

        findViewById(R.id.button_backup).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (asyncTask == null || asyncTask.completedBackgroundWork()) {
                    resetProgress();
                    asyncTask = new BackupTask();
                    new AsyncTaskLauncher<File>().execute(this, true, asyncTask, backupFolder);
                }
            }
        });

        findViewById(R.id.button_change_folder).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new SimpleFileDialog(BackupActivity.this,
                        SimpleFileDialog.TypeOfSelection.FOLDER_CHOOSE,
                        new SimpleFileDialog.SimpleFileDialogListener() {
                            @Override
                            public void onChosenDir(String chosenDir) {
                                setBackupFolder(new File(chosenDir));
                            }
                        }).chooseFileOrDir(backupFolder.getAbsolutePath());
            }
        });
    }

    void setBackupFolder(File backupFolder) {
        if (backupFolder.exists() ) {
            if (!backupFolder.isDirectory()) {
                MyLog.i(this, "Is not a directory '" + backupFolder.getAbsolutePath() + "'");
                return;
            }
        } else {
            if (!backupFolder.mkdirs()) {
                MyLog.i(this, "Couldn't create '" + backupFolder.getAbsolutePath() + "'");
                return;
            }
        }
        TextView view = (TextView) findViewById(R.id.backup_folder);
        view.setText(backupFolder.getAbsolutePath());
        this.backupFolder = backupFolder;
    }
    
    private class BackupTask extends MyAsyncTask<File, CharSequence, Boolean> {
        Boolean success = false;

        public BackupTask() {
            super(PoolEnum.LONG_UI);
        }

        @Override
        protected Boolean doInBackground2(File... params) {
            MyBackupManager.backupInteractively(params[0], BackupActivity.this, new ProgressLogger.ProgressCallback() {
                
                @Override
                public void onProgressMessage(CharSequence message) {
                   publishProgress(message);
                }
                
                @Override
                public void onComplete(boolean successIn) {
                    BackupTask.this.success = successIn;
                }
            }
            );
            return success;
        }

        @Override
        protected void onProgressUpdate(CharSequence... values) {
            addProgressMessage(values[0]);
        }
    }

    private void resetProgress() {
        progressCounter = 0;
        TextView progressLog = (TextView) findViewById(R.id.progress_log);
        progressLog.setText("");
    }
    
    private void addProgressMessage(CharSequence message) {
        progressCounter++;
        TextView progressLog = (TextView) findViewById(R.id.progress_log);
        String log = Integer.toString(progressCounter) + ". " + message + "\n" + progressLog.getText();
        progressLog.setText(log);
    }

    @Override
    protected void onResume() {
        MyContextHolder.get().setInForeground(true);
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MyContextHolder.get().setInForeground(false);
    }
}
