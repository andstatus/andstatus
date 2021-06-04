package org.andstatus.app.util

import org.apache.geode.test.junit.ConditionalIgnore
import org.apache.geode.test.junit.rules.ConditionalIgnoreRule
import org.junit.Rule

/** This class and all its subclasses should be ignored in Travis */
@ConditionalIgnore(IgnoreInTravis2::class)
open class IgnoredInTravis2 {
    @Rule
    @JvmField
    var conditionalIgnoreRule: ConditionalIgnoreRule = ConditionalIgnoreRule()

}
