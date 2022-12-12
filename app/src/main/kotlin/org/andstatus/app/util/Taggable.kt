package org.andstatus.app.util

import org.andstatus.app.util.Taggable.Companion.noNameTag
import kotlin.reflect.KClass

private fun klassToStringTag(clazz: KClass<*>) = clazz.simpleName
    ?.let { simpleName ->
        if (simpleName == "Companion") {
            clazz.qualifiedName?.split(".")
                ?.takeIf { it.size > 1 }
                ?.let {
                    it[it.size - 2] + ".$simpleName"
                }
                ?: simpleName

        } else simpleName
    }
    ?: noNameTag

/**
 * We may override classTag method, providing e.g. "static final String TAG"
 * instead of directly calling getClass().getSimpleName() each time its needed,
 * because of its performance issue, see https://bugs.openjdk.java.net/browse/JDK-8187123
 * @author yvolk@yurivolkov.com
 */
interface Taggable {
    /** We override this method in order to solve Java's problem of getSimpleName() performance  */
    val classTag: String get() = klassToStringTag(this::class)

    companion object {
        const val MAX_TAG_LENGTH = 23
        const val noNameTag: String = "NoName"

        /** Truncated to [.MAX_TAG_LENGTH]  */
        fun anyToTruncatedTag(anyTag: Any?): String {
            val tag: String = anyToTag(anyTag)
            return if (tag.length > MAX_TAG_LENGTH) tag.substring(0, MAX_TAG_LENGTH) else tag
        }

        fun anyToTag(anyTag: Any?): String {
            val tag: String = when (anyTag) {
                null -> "(null)"
                is Identifiable -> anyTag.instanceTag
                is Taggable -> anyTag.classTag
                is String -> anyTag
                is Enum<*> -> anyTag.toString()
                is KClass<*> -> klassToStringTag(anyTag)
                is Class<*> -> anyTag.simpleName
                else -> klassToStringTag(anyTag::class)
            }
            return if (tag.trim { it <= ' ' }.isEmpty()) {
                "(empty)"
            } else tag
        }
    }
}

/** To be used as a delegate implementing [Taggable]
 * See [Delegation](https://kotlinlang.org/docs/delegation.html) */
class TaggedInstance(tag: String) : Taggable {

    constructor(clazz: KClass<*>) : this(klassToStringTag(clazz))

    override val classTag: String = tag
}
