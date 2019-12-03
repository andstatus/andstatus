/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.Permissions;

public class BackupActivity extends MyActivity implements ProgressLogger.ProgressCallback {
    DocumentFile backupFolder = null;
    BackupTask asyncTask = null;
    private int progressCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.backup;
        super.onCreate(savedInstanceState);

        Permissions.checkPermissionAndRequestIt(this, Permissions.PermissionType.WRITE_EXTERNAL_STORAGE);

        findViewById(R.id.button_backup).setOnClickListener(this::doBackup);
        findViewById(R.id.backup_folder).setOnClickListener(this::selectBackupFolder);
        findViewById(R.id.button_select_backup_folder).setOnClickListener(this::selectBackupFolder);
        showBackupFolder();
    }

    private void doBackup(View v) {
        if (asyncTask == null || asyncTask.completedBackgroundWork()) {
            resetProgress();
            asyncTask = new BackupTask(BackupActivity.this);
            new AsyncTaskLauncher<DocumentFile>().execute(this, true, asyncTask, getBackupFolder());
        }
    }

    private void selectBackupFolder(View v) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getBackupFolder().getUri());
        }
        startActivityForResult(intent, ActivityRequestCode.SELECT_BACKUP_FOLDER.id);
    }

    @NonNull
    private DocumentFile getBackupFolder() {
        if (backupFolder != null && backupFolder.exists()) {
            return backupFolder;
        }
        return MyBackupManager.getDefaultBackupFolder(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (ActivityRequestCode.fromId(requestCode)) {
            case SELECT_BACKUP_FOLDER:
                if (resultCode == RESULT_OK) {
                    setBackupFolder(DocumentFile.fromTreeUri(this, data.getData()));
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    void setBackupFolder(DocumentFile backupFolder) {
        if ( backupFolder == null ) {
            MyLog.d(this, "No backup folder selected");
            return;
        } else if ( backupFolder.exists() ) {
            if (!backupFolder.isDirectory()) {
                MyLog.d(this, "Is not a folder '" + backupFolder.getUri() + "'");
                return;
            }
        } else {
            MyLog.i(this, "The folder doesn't exist: '" + backupFolder.getUri() + "'");
            return;
        }
        this.backupFolder = backupFolder;
        showBackupFolder();
        resetProgress();
    }

    private void showBackupFolder() {
        TextView view = findViewById(R.id.backup_folder);
        if (view != null) {
            view.setText(getBackupFolder().getUri().getPath());
        }
    }

    private static class BackupTask extends MyAsyncTask<DocumentFile, CharSequence, Void> {
        private final BackupActivity activity;

        BackupTask(BackupActivity activity) {
            super(PoolEnum.LONG_UI);
            this.activity = activity;
        }

        @Override
        protected Void doInBackground2(DocumentFile file) {
            MyBackupManager.backupInteractively(file, activity, activity);
            return null;
        }
    }

    private void resetProgress() {
        progressCounter = 0;
        TextView progressLog = findViewById(R.id.progress_log);
        progressLog.setText("");
    }

    private void addProgressMessage(CharSequence message) {
        progressCounter++;
        TextView progressLog = findViewById(R.id.progress_log);
        String log = progressCounter + ". " + message + "\n" + progressLog.getText();
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

    @Override
    public void onProgressMessage(CharSequence message) {
        runOnUiThread( () -> addProgressMessage(message));
    }
}
