/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.test.junit.support

import org.apache.geode.test.junit.IgnoreCondition
import org.junit.runner.Description

/**
 * The DefaultIgnoreCondition class...
 *
 * @see org.junit.runner.Description
 *
 * @see org.apache.geode.test.junit.ConditionalIgnore
 *
 * @see org.apache.geode.test.junit.IgnoreCondition
 */
class DefaultIgnoreCondition constructor(private val ignore: Boolean = DEFAULT_IGNORE) : IgnoreCondition {

    override fun evaluate(testCaseDescription: Description?): Boolean {
        return ignore
    }

    companion object {
        const val DEFAULT_IGNORE = false
        val DO_NOT_IGNORE: DefaultIgnoreCondition = DefaultIgnoreCondition(false)
        val IGNORE: DefaultIgnoreCondition = DefaultIgnoreCondition(true)
    }
}
