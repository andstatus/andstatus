/*
 * Copyright (C) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.util;

import android.app.Activity;
import android.graphics.Bitmap;

import org.andstatus.app.data.DbUtils;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.bolt.screenshotty.ScreenshotManager;
import eu.bolt.screenshotty.ScreenshotManagerBuilder;
import eu.bolt.screenshotty.ScreenshotResult;
import eu.bolt.screenshotty.util.ScreenshotFileSaver;
import io.vavr.control.CheckedRunnable;
import kotlin.Unit;

/**
 * Test case (any {@link Runnable}) wrapper allowing to make screenshot on test failure
 * @author yvolk@yurivolkov.com
 *
 * On Screenshotty library see https://github.com/bolteu/screenshotty
 * */
public class ScreenshotOnFailure {
    private ScreenshotOnFailure() {}

    public static void screenshotWrapper(Activity activity, CheckedRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable tr) {
            activity.runOnUiThread(() -> makeScreenshot(activity));
            DbUtils.waitMs(activity, 2000);
            throw new RuntimeException(tr);
        }
    }

    public static void makeScreenshot(Activity activity) {
        try {
            File targetFile = MyLog.getFileInLogDir(MyLog.uniqueDateTimeFormatted() + "-scr.png", true);
            ScreenshotManager screenshotManager = new ScreenshotManagerBuilder(activity).build();
            ScreenshotResult screenshotResult = screenshotManager.makeScreenshot();
            AtomicBoolean observed = new AtomicBoolean(false);
            screenshotResult.observe(screenshot -> {
                observed.set(true);
                ScreenshotFileSaver fileSaver = ScreenshotFileSaver.Companion.create(Bitmap.CompressFormat.PNG, 100);
                if (targetFile == null) {
                    MyLog.e(activity, "Failed to make screenshot, no file");
                } else {
                    fileSaver.saveToFile(targetFile, screenshot);
                    MyLog.i(activity, "Failure screenshot saved to file: " + targetFile);
                }
                return Unit.INSTANCE;
            }, throwable -> {
                observed.set(true);
                MyLog.e(activity, "Failed to make screenshot, file:" + targetFile, throwable);
                return Unit.INSTANCE;
            });
            if (!observed.get()) {
                MyLog.e(activity, "Failed to make screenshot: no result observed");
            }
        } catch (Throwable e) {
            MyLog.e(activity, "Failed to make screenshot", e);
        }
    }
}
