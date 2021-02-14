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

package org.andstatus.app.context;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.apache.geode.test.junit.ConditionalIgnore;
import org.apache.geode.test.junit.rules.ConditionalIgnoreRule;
import org.junit.Rule;

import io.vavr.control.CheckedRunnable;

import static org.andstatus.app.util.ScreenshotOnFailure.screenshotWrapper;

/** Helper for Activity tests, based on https://google.github.io/android-testing-support-library/
 * See https://developer.android.com/training/testing/ui-testing/espresso-testing.html
 */
@ConditionalIgnore( condition = NoScreenSupport.class )
public abstract class ActivityTest<T extends Activity> {

    @Rule
    public ConditionalIgnoreRule conditionalIgnoreRule = new ConditionalIgnoreRule();

    @Rule
    public ActivityTestRule<T> mActivityRule =
            new ActivityTestRule<T>(getActivityClass()) {
                @Override
                protected Intent getActivityIntent() {
                    return ActivityTest.this.getActivityIntent();
                }

            };

    protected abstract Class<T> getActivityClass();

    protected Intent getActivityIntent() {
        return null;
    }

    public T getActivity() {
        return mActivityRule.getActivity();
    }

    public Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    /** Make screenshot on failure */
    public void wrap(CheckedRunnable runnable) {
        screenshotWrapper(getActivity(), runnable);
    }

}
