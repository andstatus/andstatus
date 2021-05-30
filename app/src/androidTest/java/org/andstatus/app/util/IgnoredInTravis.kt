package org.andstatus.app.util

import org.apache.geode.test.junit.ConditionalIgnore
import org.apache.geode.test.junit.rules.ConditionalIgnoreRule
import org.junit.Rule

/** This class and all its subclasses should be ignored in Travis */
@ConditionalIgnore(IsTravisTest::class)
open class IgnoredInTravis {
    @Rule
    @JvmField
    var conditionalIgnoreRule: ConditionalIgnoreRule = ConditionalIgnoreRule()

}
