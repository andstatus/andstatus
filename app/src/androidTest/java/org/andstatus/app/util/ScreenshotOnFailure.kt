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
package org.andstatus.app.util

import android.app.Activity
import android.graphics.Bitmap
import eu.bolt.screenshotty.Screenshot
import eu.bolt.screenshotty.ScreenshotManagerBuilder
import eu.bolt.screenshotty.util.ScreenshotFileSaver.Companion.create
import io.vavr.control.CheckedRunnable
import org.andstatus.app.data.DbUtils
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Test case (any [Runnable]) wrapper allowing to make screenshot on test failure
 * @author yvolk@yurivolkov.com
 *
 * On Screenshotty library see https://github.com/bolteu/screenshotty
 */
object ScreenshotOnFailure {
    fun screenshotWrapper(activity: Activity, runnable: CheckedRunnable) {
        try {
            runnable.run()
        } catch (tr: Throwable) {
            activity.runOnUiThread(Runnable { ScreenshotOnFailure.makeScreenshot(activity) })
            DbUtils.waitMs(activity, 2000)
            throw RuntimeException(tr)
        }
    }

    fun makeScreenshot(activity: Activity) {
        try {
            val targetFile = MyLog.getFileInLogDir(MyLog.uniqueDateTimeFormatted() + "-scr.png", true)
            val screenshotManager = ScreenshotManagerBuilder(activity).build()
            val screenshotResult = screenshotManager.makeScreenshot()
            val observed = AtomicBoolean(false)
            screenshotResult.observe({ screenshot: Screenshot ->
                observed.set(true)
                val fileSaver = create(Bitmap.CompressFormat.PNG, 100)
                if (targetFile == null) {
                    MyLog.e(activity, "Failed to make screenshot, no file")
                } else {
                    fileSaver.saveToFile(targetFile, screenshot)
                    MyLog.i(activity, "Failure screenshot saved to file: $targetFile")
                }
                Unit
            }) { throwable: Throwable? ->
                observed.set(true)
                MyLog.e(activity, "Failed to make screenshot, file:$targetFile", throwable)
                Unit
            }
            if (!observed.get()) {
                MyLog.e(activity, "Failed to make screenshot: no result observed")
            }
        } catch (e: Throwable) {
            MyLog.e(activity, "Failed to make screenshot", e)
        }
    }
}