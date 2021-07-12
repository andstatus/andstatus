package org.andstatus.app.context

import org.andstatus.app.util.Taggable
import org.andstatus.app.util.TaggedInstance
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert.assertEquals
import org.junit.Test

class TaggableTest: Taggable {

    @Test
    fun testAnyToTag() {
        var tag: Any? = this
        val className = "TaggableTest"
        assertEquals(className, Taggable.anyToTag(tag))
        tag = this::class
        assertEquals(className, Taggable.anyToTag(tag))
        tag = javaClass
        assertEquals(className, Taggable.anyToTag(tag))
        tag = "Other tag"
        assertEquals(tag.toString(), Taggable.anyToTag(tag))
        tag = null
        assertEquals("(null)", Taggable.anyToTag(tag))
    }

    @Test
    fun testTaggable() {
        val method = ::testTaggable.name
        assertEquals("testTaggable", method)

        val expectedClassName = "TaggableTest"
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
) : Taggable by taggedInstance {
    // Empty
}

private class MyCoolClassTwo(
    private val taggedInstance: TaggedInstance = TaggedInstance("tagTwo")
) : Taggable by taggedInstance {
    // Empty
}
