/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import org.hamcrest.CoreMatchers
import org.hamcrest.Description
import org.hamcrest.Matcher

/**
 * From https://stackoverflow.com/a/39650813/297710
 */
object EspressoUtils {
    fun setChecked(checked: Boolean): ViewAction? {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View?>? {
                return object : Matcher<View?> {
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

            override fun perform(uiController: UiController?, view: View?) {
                val checkableView = view as Checkable?
                if (checkableView.isChecked() != checked) {
                    checkableView.setChecked(checked)
                }
            }
        }
    }
}