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
import org.andstatus.app.util.Permissions;

import io.vavr.control.Try;

public class RestoreActivity extends MyActivity implements ProgressLogger.ProgressListener {
    private static final int MAX_RESTORE_SECONDS = 600;
    DocumentFile dataFolder = null;
    RestoreTask asyncTask = null;
    private int progressCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.restore;
        super.onCreate(savedInstanceState);

        Permissions.checkPermissionAndRequestIt(this, Permissions.PermissionType.READ_EXTERNAL_STORAGE);

        findViewById(R.id.button_restore).setOnClickListener(this::doRestore);
        findViewById(R.id.backup_folder).setOnClickListener(this::selectBackupFolder);
        findViewById(R.id.button_select_backup_folder).setOnClickListener(this::selectBackupFolder);
        showDataFolder();
    }

    private void doRestore(View v) {
        if (asyncTask == null || asyncTask.completedBackgroundWork()) {
            resetProgress();
            asyncTask = (RestoreTask) new RestoreTask(RestoreActivity.this)
                    .setMaxCommandExecutionSeconds(MAX_RESTORE_SECONDS)
                    .setCancelable(false);
            new AsyncTaskLauncher<DocumentFile>().execute(this, true, asyncTask, getDataFolder());
        }
    }

    private void selectBackupFolder(View v) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getDataFolder().getUri());
        }
        startActivityForResult(intent, ActivityRequestCode.SELECT_BACKUP_FOLDER.id);
    }

    @NonNull
    private DocumentFile getDataFolder() {
        if (dataFolder != null && dataFolder.exists()) {
            return dataFolder;
        }
        return MyBackupManager.getDefaultBackupFolder(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (ActivityRequestCode.fromId(requestCode)) {
            case SELECT_BACKUP_FOLDER:
                if (resultCode == RESULT_OK) {
                  setDataFolder(DocumentFile.fromTreeUri(this, data.getData()));
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    void setDataFolder(DocumentFile selectedFolder) {
        resetProgress();
        if ( selectedFolder == null ) {
            addProgressMessage("No backup data folder selected");
            return;
        } else if (selectedFolder.exists() ) {
            if (!selectedFolder.isDirectory()) {
                addProgressMessage("Is not a folder: '" + selectedFolder.getUri().getPath() + "'");
                return;
            }
        } else {
            addProgressMessage("The folder doesn't exist: '" + selectedFolder.getUri().getPath() + "'");
            return;
        }
        Try<DocumentFile> descriptorFile = MyBackupManager.getExistingDescriptorFile(selectedFolder);
        if (descriptorFile.isFailure()) {
            addProgressMessage("This is not an AndStatus backup folder." +
                    " Descriptor file " + MyBackupManager.DESCRIPTOR_FILE_NAME +
                    " doesn't exist in: '" + selectedFolder.getUri().getPath() + "'");
            return;
        }

        this.dataFolder = selectedFolder;
        showDataFolder();
        resetProgress();
    }

    private void showDataFolder() {
        TextView view = findViewById(R.id.backup_folder);
        if (view != null) {
            DocumentFile folder = getDataFolder();
            view.setText(MyBackupManager.isDataFolder(folder) ? folder.getUri().getPath() : getText(R.string.not_set));
        }
    }

    private static class RestoreTask extends MyAsyncTask<DocumentFile, CharSequence, Void> {
        private final RestoreActivity activity;

        RestoreTask(RestoreActivity activity) {
            super(PoolEnum.thatCannotBeShutDown());
            this.activity = activity;
        }

        @Override
        protected Void doInBackground2(DocumentFile dataFolder) {
            MyBackupManager.restoreInteractively(dataFolder, activity, activity);
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

    @Override
    public void onProgressMessage(CharSequence message) {
        runOnUiThread( () -> addProgressMessage(message));
    }
}
