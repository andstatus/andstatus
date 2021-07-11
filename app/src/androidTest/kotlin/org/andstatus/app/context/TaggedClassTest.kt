package org.andstatus.app.context

import org.andstatus.app.util.TaggedClass
import org.andstatus.app.util.TaggedInstance
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert.assertEquals
import org.junit.Test

class TaggedClassTest: TaggedClass {

    @Test
    fun testTaggedClass() {
        val method = ::testTaggedClass.name
        assertEquals("testTaggedClass", method)

        val expectedClassName = "TaggedClassTest"
        assertEquals(expectedClassName, this::class.simpleName)
        MatcherAssert.assertThat(classTag, CoreMatchers.containsString(expectedClassName))
    }

    @Test
    fun testTaggedInstance() {
        val cl = MyCoolClass()
        val expectedClassName = "MyCoolClass"
        assertEquals(expectedClassName, cl::class.simpleName)
        assertEquals(expectedClassName, cl.classTag)
    }

    @Test
    fun testTaggedInstanceWithEmptyDelegateConstructor() {
        val cl = MyCoolClassTwo()
        val expectedClassName = "tagTwo"
        assertEquals(expectedClassName, cl.classTag)
    }

}

private class MyCoolClass(
    private val taggedInstance: TaggedInstance = TaggedInstance(MyCoolClass::class)
) : TaggedClass by taggedInstance {
    // Empty
}

private class MyCoolClassTwo(
    private val taggedInstance: TaggedInstance = TaggedInstance("tagTwo")
) : TaggedClass by taggedInstance {
    // Empty
}
