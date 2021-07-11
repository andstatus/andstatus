package org.andstatus.app.util

import kotlin.reflect.KClass

/**
 * We may override classTag method, providing e.g. "static final String TAG"
 * instead of directly calling getClass().getSimpleName() each time its needed,
 * because of its performance issue, see https://bugs.openjdk.java.net/browse/JDK-8187123
 * @author yvolk@yurivolkov.com
 */
interface TaggedClass {
    /** We override this method in order to solve Java's problem of getSimpleName() performance  */
    val classTag: String get() = javaClass.simpleName
}

/** To be used as a delegate implementing [TaggedClass]
 * See [Delegation](https://kotlinlang.org/docs/delegation.html) */
class TaggedInstance(val tag: String) : TaggedClass {

    constructor(clazz: KClass<*>): this(clazz.simpleName ?: "NoName")

    override val classTag: String = tag
}
