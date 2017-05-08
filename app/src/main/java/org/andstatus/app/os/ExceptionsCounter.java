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
import android.support.annotation.MainThread;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.DialogFactory;

/**
 * @author yvolk@yurivolkov.com
 */
public class ExceptionsCounter {

    private static volatile long diskIoExceptionsCount = 0;
    private static volatile long diskIoExceptionsCountShown = 0;
    private static volatile Dialog diskIoDialog = null;

    private ExceptionsCounter() {
        // Empty
    }

    public static long getDiskIoExceptionsCount() {
        return diskIoExceptionsCount;
    }

    public static void onDiskIoException() {
        diskIoExceptionsCount++;
    }

    public static void forget() {
        DialogFactory.dismissSafely(diskIoDialog);
        diskIoExceptionsCount = 0;
        diskIoExceptionsCountShown = 0;
    }

    @MainThread
    public static void showErrorDialogIfErrorsPresent() {
        if (diskIoExceptionsCountShown == diskIoExceptionsCount ) return;

        diskIoExceptionsCountShown = diskIoExceptionsCount;
        DialogFactory.dismissSafely(diskIoDialog);
        final String text = String.format(MyContextHolder.get().context().getText(R.string.database_disk_io_error).toString(),
                diskIoExceptionsCount);
        diskIoDialog = DialogFactory.showOkAlertDialog(ExceptionsCounter.class, MyContextHolder.get().context(),
                R.string.app_name, text);
    }
}
