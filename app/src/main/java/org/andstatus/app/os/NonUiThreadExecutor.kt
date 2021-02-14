package org.andstatus.app.os;

import android.os.AsyncTask;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

import static org.andstatus.app.os.MyAsyncTask.isUiThread;

public class NonUiThreadExecutor implements Executor {
    public static final NonUiThreadExecutor INSTANCE = new NonUiThreadExecutor();

    @Override
    public void execute(@NonNull Runnable command) {
        if (isUiThread()) {
            AsyncTask.THREAD_POOL_EXECUTOR.execute(command);
        } else {
            command.run();
        }
    }
}
