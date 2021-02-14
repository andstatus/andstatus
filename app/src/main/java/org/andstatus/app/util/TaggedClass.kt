package org.andstatus.app.util;

/**
 * We may override classTag method, providing e.g. "static final String TAG"
 *   instead of directly calling getClass().getSimpleName() each time its needed,
 *   because of its performance issue, see https://bugs.openjdk.java.net/browse/JDK-8187123
 * @author yvolk@yurivolkov.com
 */
public interface TaggedClass {

    /** We override this method in order to solve Java's problem of getSimpleName() performance */
    default String classTag() {
        return getClass().getSimpleName();
    }
}
