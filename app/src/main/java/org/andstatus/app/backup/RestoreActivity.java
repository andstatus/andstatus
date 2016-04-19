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

public class RestoreActivity extends MyActivity {
    File backupFile = null;
    RestoreTask asyncTask = null;
    private int progressCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.restore;
        super.onCreate(savedInstanceState);

        Permissions.checkAndRequest(this, Permissions.PermissionType.READ_EXTERNAL_STORAGE);

        findViewById(R.id.button_restore).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (asyncTask == null || !asyncTask.needsBackgroundWork()) {
                    resetProgress();
                    asyncTask = new RestoreTask();
                    new AsyncTaskLauncher<File>().execute(this, asyncTask, true, backupFile);
                }
            }
        });

        findViewById(R.id.button_select_backup_file).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new SimpleFileDialog(RestoreActivity.this,
                        SimpleFileDialog.TypeOfSelection.FILE_OPEN,
                        new SimpleFileDialog.SimpleFileDialogListener() {
                            @Override
                            public void onChosenDir(String selectedFile) {
                                setBackupFile(new File(selectedFile));
                            }
                        })
                        .chooseFileOrDir(getBackupFolder().getAbsolutePath());
            }
        });
    }

    private File getBackupFolder() {
        File backupFolder;
        if (backupFile != null && backupFile.exists()) {
            backupFolder = new File(backupFile.getParent());
        } else {
            backupFolder = MyBackupManager.getDefaultBackupDirectory(this);
        }
        if (!backupFolder.exists() || !backupFolder.isDirectory()) {
            backupFolder = new File(SimpleFileDialog.getRootFolder());
        }
        return backupFolder;
    }
    
    void setBackupFile(File backupFileIn) {
        if ( backupFileIn == null ) {
            MyLog.i(this, "No backup file selected");
            return;
        } else if ( backupFileIn.exists() ) {
            if (backupFileIn.isDirectory()) {
                MyLog.i(this, "Is not a file '" + backupFileIn.getAbsolutePath() + "'");
                return;
            }
        } else {
            MyLog.i(this, "The file doesn't exist: '" + backupFileIn.getAbsolutePath() + "'");
            return;
        }
        TextView view = (TextView) findViewById(R.id.backup_file);
        view.setText(backupFileIn.getAbsolutePath());
        this.backupFile = backupFileIn;
    }
    
    private class RestoreTask extends MyAsyncTask<File, String, Boolean> {
        Boolean success = false;

        public RestoreTask() {
            super(PoolEnum.LONG_UI);
        }

        @Override
        protected Boolean doInBackground2(File... params) {
            MyBackupManager.restoreInteractively(params[0], new ProgressLogger.ProgressCallback() {
                
                @Override
                public void onProgressMessage(String message) {
                   publishProgress(message);
                }
                
                @Override
                public void onComplete(boolean successIn) {
                    RestoreTask.this.success = successIn;
                }
            }
            );
            return success;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            addProgressMessage(values[0]);
        }
    }

    private void resetProgress() {
        progressCounter = 0;
        TextView progressLog = (TextView) findViewById(R.id.progress_log);
        progressLog.setText("");
    }
    
    private void addProgressMessage(String message) {
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
