/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.os.Build
import org.andstatus.app.context.ExecutionMode
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.TestSuite
import org.apache.geode.test.junit.IgnoreCondition
import org.junit.runner.Description

/**
 * All Activity (screen) - related tests are marked with this annotation
 * @author yvolk@yurivolkov.com
 */
class IgnoreInTravis1 : IgnoreCondition {
    override fun evaluate(testCaseDescription: Description?): Boolean {
        TestSuite.initialize(this)
        return myContextHolder.executionMode == ExecutionMode.TRAVIS_TEST && Build.VERSION.SDK_INT < 29
    }
}
