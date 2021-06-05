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
package org.apache.geode.test.junit.rules

import org.apache.geode.test.junit.ConditionalIgnore
import org.apache.geode.test.junit.IgnoreCondition
import org.apache.geode.test.junit.support.IgnoreConditionEvaluationException
import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.Serializable
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.reflect.KClass

/**
 * The ConditionalIgnoreRule class...
 *
 * @see org.junit.rules.TestRule
 *
 * @see org.junit.runner.Description
 *
 * @see org.junit.runners.model.Statement
 *
 * @see org.apache.geode.test.junit.ConditionalIgnore
 *
 * @see org.apache.geode.test.junit.IgnoreCondition
 */
class ConditionalIgnoreRule : TestRule, Serializable {

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                passOrThrowAssumptionViolation(base, description).evaluate()
            }
        }
    }

    private fun passOrThrowAssumptionViolation(statement: Statement, description: Description): Statement {
        var skipTest = false
        var message = ""
        val testCaseAnnotation = description.getAnnotation(ConditionalIgnore::class.java)
        if (testCaseAnnotation != null) {
            skipTest = evaluateAnnotation(testCaseAnnotation, description)
            message = testCaseAnnotation.value
        } else if (description.getTestClass().isAnnotationPresent(ConditionalIgnore::class.java)) {
            description.getTestClass().getAnnotation(ConditionalIgnore::class.java)?.let { annotation ->
                skipTest = evaluateAnnotation(annotation, description)
                message = annotation.value
            }
        }
        if (skipTest) {
            throw AssumptionViolatedException(format(message, description))
        }
        return statement
    }

    private fun isTest(description: Description): Boolean {
        return description.isSuite || description.isTest
    }

    private fun format(message: String, description: Description): String {
        val msgOut = if (!message.isEmpty()) message else DEFAULT_MESSAGE
        return String.format(msgOut, description.methodName, description.className)
    }

    private fun evaluateAnnotation(conditionalIgnoreAnnotation: ConditionalIgnore,
                                   description: Description): Boolean {
        return (evaluateCondition(conditionalIgnoreAnnotation.condition, description)
                || evaluateUntil(conditionalIgnoreAnnotation.until))
    }

    private fun evaluateCondition(ignoreConditionType: KClass<out IgnoreCondition>,
                                    description: Description?): Boolean {
        return try {
            ignoreConditionType.javaObjectType.newInstance().evaluate(description)
        } catch (e: Exception) {
            throw IgnoreConditionEvaluationException(
                    "failed to evaluate IgnoreCondition: ${ignoreConditionType.qualifiedName}", e)
        }
    }

    private fun evaluateUntil(timestamp: String): Boolean {
        return try {
            DATE_FORMAT.parse(timestamp)?.after(Calendar.getInstance().time) == true
        } catch (e: ParseException) {
            false
        }
    }

    companion object {
        private const val DATE_FORMAT_PATTERN: String = "yyyy-MM-dd"
        private const val DEFAULT_MESSAGE: String = "Ignoring test case (%1\$s) of test class (%2\$s)!"
        private val DATE_FORMAT: DateFormat = SimpleDateFormat(DATE_FORMAT_PATTERN)
    }
}
