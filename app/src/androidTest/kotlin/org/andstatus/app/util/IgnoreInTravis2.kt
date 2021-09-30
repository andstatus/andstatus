/*
 * Copyright (C) 2021 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.context.TestSuite
import org.apache.geode.test.junit.IgnoreCondition
import org.junit.runner.Description

/**
 * We have two ignore conditions for Travis.ci, because all tests run longer than it allows.
 * @author yvolk@yurivolkov.com
 */
class IgnoreInTravis2 : IgnoreCondition {
    override fun evaluate(testCaseDescription: Description?): Boolean {
        TestSuite.initialize(this)
        return false // effectively turn off this condition for now
    }
}
