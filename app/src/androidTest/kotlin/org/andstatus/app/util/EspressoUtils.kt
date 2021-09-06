/*
 * Copyright (c) 2017-2021: yvolk (Yuri Volkov), http://yurivolkov.com, and others
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
package org.andstatus.app.util

import android.view.View
import android.widget.Checkable
import androidx.test.espresso.Espresso
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers
import org.hamcrest.Description
import org.hamcrest.Matcher


/**
 * From https://stackoverflow.com/a/39650813/297710
 */
object EspressoUtils {
    fun setChecked(checked: Boolean): ViewAction {

        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return object : Matcher<View> {
                    override fun matches(item: Any?): Boolean {
                        return CoreMatchers.isA(Checkable::class.java).matches(item)
                    }

                    override fun describeMismatch(item: Any?, mismatchDescription: Description?) {}
                    override fun _dont_implement_Matcher___instead_extend_BaseMatcher_() {}
                    override fun describeTo(description: Description?) {}
                }
            }

            override fun getDescription(): String? {
                return null
            }

            override fun perform(uiController: UiController, view: View) {
                val checkableView = view as Checkable
                if (checkableView.isChecked != checked) {
                    checkableView.isChecked = checked
                }
            }
        }
    }

    fun waitForIdleSync() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        try {
            Espresso.onView(isRoot()).perform(waitMs(200))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Espresso.onView(isRoot()).perform(waitMs(1000))
        } catch (e: Throwable) {
            // Exception can happen when no activities are running
            runBlocking {
                delay(200)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                delay(1000)
            }
        }
    }

    /**
     * Perform action of waiting for a specific time.
     * Solution from https://stackoverflow.com/a/35924943/297710
     */
    fun waitMs(millis: Long): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isRoot()
            }

            override fun getDescription(): String {
                return "Wait for $millis milliseconds."
            }

            override fun perform(uiController: UiController, view: View) {
                uiController.loopMainThreadForAtLeast(millis)
            }
        }
    }
}
