package org.andstatus.app.os;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

/** See https://stackoverflow.com/questions/12850143/android-basics-running-code-in-the-ui-thread */
public class UiThreadExecutor implements Executor {
    public static final UiThreadExecutor INSTANCE = new UiThreadExecutor();

    @Override
    public void execute(@NonNull Runnable command) {
        new Handler(Looper.getMainLooper()).post(command);
    }
}
