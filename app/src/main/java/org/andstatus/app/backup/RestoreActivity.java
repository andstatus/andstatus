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
import androidx.annotation.NonNull;
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
    private static final int MAX_RESTORE_SECONDS = 300;
    File selectedFolder = null;
    MyAsyncTask<File, CharSequence, Boolean> asyncTask = null;
    private int progressCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.restore;
        super.onCreate(savedInstanceState);

        Permissions.checkPermissionAndRequestIt(this, Permissions.PermissionType.READ_EXTERNAL_STORAGE);

        findViewById(R.id.button_restore).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (asyncTask == null || asyncTask.completedBackgroundWork()) {
                    resetProgress();
                    asyncTask = new MyAsyncTask<File, CharSequence, Boolean>( this,
                            MyAsyncTask.PoolEnum.QUICK_UI) {

                        Boolean success = false;

                        @Override
                        protected Boolean doInBackground2(File... params) {
                            MyBackupManager.restoreInteractively(params[0], RestoreActivity.this, new ProgressLogger.ProgressCallback() {

                                        @Override
                                        public void onProgressMessage(CharSequence message) {
                                            publishProgress(message);
                                        }

                                        @Override
                                        public void onComplete(boolean successIn) {
                                            success = successIn;
                                        }
                                    }
                            );
                            return success;
                        }

                        @Override
                        protected void onProgressUpdate(CharSequence... values) {
                            addProgressMessage(values[0]);
                        }
                    }.setMaxCommandExecutionSeconds(MAX_RESTORE_SECONDS).setCancelable(false);
                    new AsyncTaskLauncher<File>().execute(this, true, asyncTask, getSelectedFolder());
                }
            }
        });

        findViewById(R.id.button_select_backup_folder).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new SimpleFileDialog(RestoreActivity.this,
                        SimpleFileDialog.TypeOfSelection.FOLDER_CHOOSE,
                        new SimpleFileDialog.SimpleFileDialogListener() {
                            @Override
                            public void onChosenDir(String chosenFolder) {
                                setSelectedFolder(new File(chosenFolder));
                            }
                        })
                        .chooseFileOrDir(getSelectedFolder().getAbsolutePath());
            }
        });

        showBackupFolder();
    }

    @NonNull
    private File getSelectedFolder() {
        File folder;
        if (selectedFolder != null && selectedFolder.exists()) {
            folder = selectedFolder;
        } else {
            folder = MyBackupManager.getDefaultBackupDirectory(this);
        }
        if (!folder.exists() || !folder.isDirectory()) {
            folder = new File(SimpleFileDialog.getRootFolder());
        }
        return folder;
    }
    
    void setSelectedFolder(File backupFolderIn) {
        if ( backupFolderIn == null ) {
            MyLog.d(this, "No backup folder selected");
            return;
        } else if ( backupFolderIn.exists() ) {
            if (!backupFolderIn.isDirectory()) {
                MyLog.d(this, "Is not a folder '" + backupFolderIn.getAbsolutePath() + "'");
                return;
            }
        } else {
            MyLog.i(this, "The folder doesn't exist: '" + backupFolderIn.getAbsolutePath() + "'");
            return;
        }
        this.selectedFolder = backupFolderIn;
        showBackupFolder();
        resetProgress();
    }

    private void showBackupFolder() {
        TextView view = (TextView) findViewById(R.id.backup_folder);
        if (view != null) {
            File folder = getSelectedFolder();
            view.setText(MyBackupManager.isBackupFolder(folder) ? folder.getAbsolutePath() : "");
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
