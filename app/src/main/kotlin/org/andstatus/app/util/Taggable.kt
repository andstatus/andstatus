package org.andstatus.app.util

import org.andstatus.app.util.Taggable.Companion.noNameTag
import kotlin.reflect.KClass

/**
 * We may override classTag method, providing e.g. "static final String TAG"
 * instead of directly calling getClass().getSimpleName() each time its needed,
 * because of its performance issue, see https://bugs.openjdk.java.net/browse/JDK-8187123
 * @author yvolk@yurivolkov.com
 */
interface Taggable {
    /** We override this method in order to solve Java's problem of getSimpleName() performance  */
    val classTag: String get() = this::class.simpleName ?: noNameTag

    companion object {
        const val MAX_TAG_LENGTH = 23
        const val noNameTag: String = "NoName"

        /** Truncated to [.MAX_TAG_LENGTH]  */
        fun anyToTruncatedTag(anyTag: Any?): String {
            val tag: String = Taggable.anyToTag(anyTag)
            return if (tag.length > MAX_TAG_LENGTH) tag.substring(0, MAX_TAG_LENGTH) else tag
        }

        fun anyToTag(anyTag: Any?): String {
            val tag: String = when (anyTag) {
                null -> "(null)"
                is Identifiable -> anyTag.instanceTag
                is Taggable -> anyTag.classTag
                is String -> anyTag
                is Enum<*> -> anyTag.toString()
                is KClass<*> -> anyTag.simpleName ?: noNameTag
                is Class<*> -> anyTag.simpleName
                else -> anyTag::class.simpleName ?: noNameTag
            }
            return if (tag.trim { it <= ' ' }.isEmpty()) {
                "(empty)"
            } else tag
        }
    }
}

/** To be used as a delegate implementing [Taggable]
 * See [Delegation](https://kotlinlang.org/docs/delegation.html) */
class TaggedInstance(val tag: String) : Taggable {

    constructor(clazz: KClass<*>): this(clazz.simpleName ?: noNameTag)

    override val classTag: String = tag
}
