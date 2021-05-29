/**
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.context

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import io.vavr.control.CheckedRunnable
import org.andstatus.app.util.ScreenshotOnFailure
import org.apache.geode.test.junit.ConditionalIgnore
import org.apache.geode.test.junit.rules.ConditionalIgnoreRule
import org.junit.Rule

/** Helper for Activity tests, based on https://google.github.io/android-testing-support-library/
 * See https://developer.android.com/training/testing/ui-testing/espresso-testing.html
 */
@ConditionalIgnore(condition = NoScreenSupport::class)
abstract class ActivityTest<T : Activity> {
    @Rule
    @JvmField
    var conditionalIgnoreRule: ConditionalIgnoreRule = ConditionalIgnoreRule()

    @Rule
    @JvmField
    var mActivityRule: ActivityTestRule<T> = object : ActivityTestRule<T>(getActivityClass()) {
        override fun getActivityIntent(): Intent? {
            return this@ActivityTest.getActivityIntent()
        }
    }

    protected abstract fun getActivityClass(): Class<T>
    protected open fun getActivityIntent(): Intent? {
        return null
    }

    val activity: T get() = mActivityRule.getActivity()

    fun getInstrumentation(): Instrumentation {
        return InstrumentationRegistry.getInstrumentation()
    }

    /** Make screenshot on failure  */
    fun wrap(runnable: CheckedRunnable) {
        ScreenshotOnFailure.screenshotWrapper(activity, runnable)
    }
}
