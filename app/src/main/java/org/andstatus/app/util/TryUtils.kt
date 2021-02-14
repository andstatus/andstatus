package org.andstatus.app.util

import android.database.sqlite.SQLiteDiskIOException
import io.vavr.control.Try
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.os.ExceptionsCounter
import java.util.*
import java.util.concurrent.Callable
import java.util.function.Function
import java.util.function.Supplier

/**
 * @author yvolk@yurivolkov.com
 */
object TryUtils {
    private val NOT_FOUND_EXCEPTION: NoSuchElementException? = NoSuchElementException("Not found")
    private val NOT_FOUND: Try<*>? = Try.failure<Any?>(NOT_FOUND_EXCEPTION)
    private val OPTIONAL_IS_EMPTY: NoSuchElementException? = NoSuchElementException("Optional is empty")
    private val CALLABLE_IS_NULL: NoSuchElementException? = NoSuchElementException("Callable is null")
    private val VALUE_IS_NULL: NoSuchElementException? = NoSuchElementException("Value is null")
    val TRUE = Try.success(true)
    val SUCCESS = Try.success<Void?>(null)
    fun <T> failure(exception: Throwable?): Try<T?>? {
        return failure("", exception)
    }

    fun <T> failure(message: String?, exception: Throwable?): Try<T?>? {
        checkException<Throwable?>(exception)
        return Try.failure(if (StringUtil.nonEmpty(message)) ConnectionException.Companion.of(exception).append(message) else exception)
    }

    fun <T : Throwable?> checkException(exception: T?): T? {
        if (exception is SQLiteDiskIOException) {
            ExceptionsCounter.onDiskIoException(exception)
        }
        return exception
    }

    /**
     * Creates a Try from nullable value.
     *
     * @param value success value if not null
     * @param <T>      Component type
     * @return `Success(value` if the value is not null,
     * otherwise returns `Failure` holding [NoSuchElementException]
    </T> */
    fun <T> ofNullable(value: T?): Try<T?>? {
        return if (value == null) Try.failure(VALUE_IS_NULL) else Try.success(value)
    }

    fun <T> ofNullableCallable(callable: Callable<out T?>?): Try<T?>? {
        return if (callable == null) Try.failure(CALLABLE_IS_NULL) else Try.of(callable).flatMap { value: T? -> if (value == null) Try.failure(VALUE_IS_NULL) else Try.success(value) }
    }

    /**
     * Creates a Try from an Optional.
     *
     * @param optional Optional holding a (success) value
     * @param <T>      Component type
     * @return `Success(optional.get)` if optional is not empty,
     * otherwise returns `Failure` holding [NoSuchElementException]
     * @throws NullPointerException if `optional` is null
    </T> */
    fun <T> fromOptional(optional: Optional<T?>?): Try<T?>? {
        return fromOptional(optional) { OPTIONAL_IS_EMPTY }
    }

    /**
     * Creates a Try from an Optional.
     *
     * @param optional Optional holding a (success) value
     * @param ifEmpty  Supplier of an exception
     * @param <T>      Component type
     * @return `Success(optional.get)` if optional is not empty,
     * otherwise returns `Failure` holding exception, supplied by `ifEmpty` argument
     * @throws NullPointerException if `optional` is null
    </T> */
    fun <T> fromOptional(optional: Optional<T?>?, ifEmpty: Supplier<Throwable?>?): Try<T?>? {
        return optional.map(Function { value: T? -> Try.success(value) }).orElseGet { Try.failure(ifEmpty.get()) }
    }

    fun <T> notFound(): Try<T?>? {
        return NOT_FOUND
    }

    fun <T> failure(message: String?): Try<T?>? {
        return Try.failure(Exception(message))
    }

    fun <T> emptyList(): Try<MutableList<T?>?>? {
        return Try.success(emptyList())
    }
}