package org.andstatus.app.util;

import java.util.NoSuchElementException;
import java.util.Optional;

import io.vavr.control.Try;

/**
 * @author yvolk@yurivolkov.com
 */
public class TryUtils {

    private TryUtils() {
        // Empty
    }

    /**
     * Creates a Try from an Optional.
     *
     * @param optional Optional holding a (success) value
     * @param <T>      Component type
     * @return {@code Success(optional.get)} if optional is not empty,
     *   otherwise returns {@code Failure} holding {@link NoSuchElementException}
     * @throws NullPointerException if {@code optional} is null
     */
    public static <T> Try<T> fromOptional(Optional<T> optional) {
        return optional.map(Try::success)
                .orElseGet(() -> Try.failure(new NoSuchElementException("Optional is empty")));
    }
}
