/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.os;

import android.app.Dialog;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import org.acra.ACRA;
import org.andstatus.app.R;
import org.andstatus.app.util.DialogFactory;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * @author yvolk@yurivolkov.com
 */
public class ExceptionsCounter {
    private static final String TAG = ExceptionsCounter.class.getSimpleName();

    private static final AtomicLong diskIoExceptionsCount = new AtomicLong();
    private static final AtomicLong diskIoExceptionsCountShown = new AtomicLong();
    private static volatile Dialog diskIoDialog = null;
    public static final AtomicReference<String> firstError = new AtomicReference<>();

    private ExceptionsCounter() {
        // Empty
    }

    public static long getDiskIoExceptionsCount() {
        return diskIoExceptionsCount.get();
    }

    public static void onDiskIoException(Throwable e) {
        diskIoExceptionsCount.incrementAndGet();
        logSystemInfo(e);
    }

    @NonNull
    public static void logSystemInfo(Throwable throwable) {
        final String systemInfo = myContextHolder.getSystemInfo(myContextHolder.getNow().context(), true);
        ACRA.getErrorReporter().putCustomData("systemInfo", systemInfo);
        logError(systemInfo, throwable);
    }

    private static void logError(String msgLog, Throwable tr) {
        MyLog.e(TAG, msgLog, tr);
        if (!StringUtil.isEmpty(firstError.get()) || tr == null) {
            return;
        }
        firstError.set(MyLog.getStackTrace(tr));
    }

    public static void forget() {
        DialogFactory.dismissSafely(diskIoDialog);
        diskIoExceptionsCount.set(0);
        diskIoExceptionsCountShown.set(0);
    }

    @MainThread
    public static void showErrorDialogIfErrorsPresent() {
        if (diskIoExceptionsCountShown.get() == diskIoExceptionsCount.get() ) return;

        diskIoExceptionsCountShown.set(diskIoExceptionsCount.get());
        DialogFactory.dismissSafely(diskIoDialog);
        final String text = StringUtil.format(myContextHolder.getNow().context(), R.string.database_disk_io_error,
                diskIoExceptionsCount.get());
        diskIoDialog = DialogFactory.showOkAlertDialog(ExceptionsCounter.class, myContextHolder.getNow().context(),
                R.string.app_name, text);
    }
}
