package org.andstatus.app.util;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;

import io.vavr.control.Try;

/**
 * @author yvolk@yurivolkov.com
 */
public class TryUtils {
    private static final NoSuchElementException NOT_FOUND = new NoSuchElementException("Not found");
    private static final NoSuchElementException OPTIONAL_IS_EMPTY = new NoSuchElementException("Optional is empty");
    private static final NoSuchElementException VALUE_IS_NULL = new NoSuchElementException("Value is null");

    private TryUtils() {
        // Empty
    }


    /**
     * Creates a Try from nullable value.
     *
     * @param value success value if not null
     * @param <T>      Component type
     * @return {@code Success(value} if the value is not null,
     *   otherwise returns {@code Failure} holding {@link NoSuchElementException}
     */
    public static <T> Try<T> ofNullable(T value) {
        return value == null
            ? Try.failure(VALUE_IS_NULL)
            : Try.success(value);
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
        return fromOptional(optional, () -> OPTIONAL_IS_EMPTY);
    }

    /**
     * Creates a Try from an Optional.
     *
     * @param optional Optional holding a (success) value
     * @param ifEmpty  Supplier of an exception
     * @param <T>      Component type
     * @return {@code Success(optional.get)} if optional is not empty,
     *   otherwise returns {@code Failure} holding exception, supplied by {@code ifEmpty} argument
     * @throws NullPointerException if {@code optional} is null
     */
    public static <T> Try<T> fromOptional(Optional<T> optional, Supplier<Throwable> ifEmpty) {
        return optional.map(Try::success).orElseGet(() -> Try.failure(ifEmpty.get()));
    }

    public static <T> Try<T> notFound() {
        return Try.failure(NOT_FOUND);
    }
}
