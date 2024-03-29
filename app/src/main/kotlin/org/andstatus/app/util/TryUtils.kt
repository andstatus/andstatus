package org.andstatus.app.util

import android.database.sqlite.SQLiteDiskIOException
import io.vavr.control.Try
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.os.ExceptionsCounter
import java.util.*
import java.util.concurrent.Callable

/**
 * @author yvolk@yurivolkov.com
 */
object TryUtils {
    private val NOT_FOUND_EXCEPTION: NoSuchElementException = NoSuchElementException("Not found")
    private val NOT_FOUND: Try<*> = Try.failure<Any>(NOT_FOUND_EXCEPTION)
    private val CANCELLED: Try<*> = Try.failure<Any>(kotlinx.coroutines.CancellationException())
    private val OPTIONAL_IS_EMPTY: NoSuchElementException = NoSuchElementException("Optional is empty")
    private val CALLABLE_IS_NULL: NoSuchElementException = NoSuchElementException("Callable is null")
    private val VALUE_IS_NULL: NoSuchElementException = NoSuchElementException("Value is null")
    val TRUE: Try<Boolean> = Try.success(true)
    val SUCCESS: Try<Unit> = Try.success(Unit)

    fun <T> failure(exception: Throwable?): Try<T> {
        return failure(null, exception)
    }

    fun <T> failure(message: String?, exception: Throwable?): Try<T> {
        checkException<Throwable?>(exception)
        return Try.failure(ConnectionException.of(exception, message))
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
    fun <T> ofNullable(value: T?): Try<T> {
        return if (value == null) Try.failure(VALUE_IS_NULL) else Try.success(value)
    }

    fun <T> ofNullableCallable(callable: Callable<out T?>?): Try<T> {
        return if (callable == null) Try.failure(CALLABLE_IS_NULL)
        else Try.of(callable).flatMap { value: T? ->
            if (value == null) Try.failure(VALUE_IS_NULL) else Try.success(value)
        }
    }

    /**
     * Creates a Try from an Optional.
     *
     * @param optional Optional holding a (success) value
     * @param <T>      Component type
     * @return `Success(optional.get)` if optional is not empty,
     * otherwise returns `Failure` holding [NoSuchElementException]
     * @throws NullPointerException if `optional` is null */
    fun <T> fromOptional(optional: Optional<T>): Try<T> {
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
     * </T> */
    fun <T> fromOptional(optional: Optional<T>, ifEmpty: () -> Throwable): Try<T> {
        return optional.map { value -> Try.success(value) }.orElseGet { Try.failure(ifEmpty()) }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> notFound(): Try<T> {
        return NOT_FOUND as Try<T>
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> cancelled(): Try<T> {
        return CANCELLED as Try<T>
    }

    val Try<*>.isCancelled: Boolean get() = isFailure && cause is kotlinx.coroutines.CancellationException

    fun <T> failure(message: CharSequence?): Try<T> =
        if (message.isNullOrEmpty()) notFound()
        else Try.failure(Exception(message.toString()))

    fun <T> emptyList(): Try<List<T>> {
        return Try.success(kotlin.collections.emptyList<T>())
    }

    /**
     * Creates a Try of a suspend Callable.
     *
     * @param callable A supplier that may throw a checked exception
     * @param <T>      Component type
     * @return `Success(callable.call())` if no exception occurs, otherwise `Failure(cause)` if a
     * non-fatal error occurs calling `callable.call()`.
     * @throws Error if the cause of the [Try.Failure] is fatal, i.e. non-recoverable
    </T> */
    suspend fun <T> ofS(callable: suspend () -> T): Try<T> {
        Objects.requireNonNull(callable, "callable is null")
        return try {
            Try.success(callable())
        } catch (t: Throwable) {
            Try.failure(t)
        }
    }

    suspend fun <T> Try<T>.onSuccessS(action: suspend (T) -> Unit): Try<T> {
        if (isSuccess) {
            action(get())
        }
        return this
    }

    suspend fun <T> Try<T>.onFailureS(action: suspend (Throwable) -> Unit): Try<T> {
        if (isFailure) {
            action(cause)
        }
        return this
    }

    inline fun <T> Try<T>.getOrElseRecover(recoveryFunction: (Throwable) -> T): T {
        return if (isSuccess) get() else recoveryFunction(cause)
    }

    fun <T> Try<T>.onFailureAsConnectionException(action: (ConnectionException) -> Unit): Try<T> {
        if (isFailure) {
            action(ConnectionException.of(cause))
        }
        return this
    }

    inline fun <T, U> Try<List<T>>.flatMapL(mapper: (T) -> Try<U>): Try<List<U>> = iFlatMap {
        it.map(mapper).toTry()
    }

    inline fun <T, U> Try<T>.iFlatMap(mapper: (T) -> Try<U>): Try<U> = if (isSuccess) {
        try {
            mapper(get())
        } catch (t: Throwable) {
            Try.failure(t)
        }
    } else {
        this as Try<U>
    }

    fun <T> List<Try<T>>.toTry(): Try<List<T>> = map {
        if (it.isFailure) return it as Try<List<T>>
        it.get()
    }
        .let { Try.success(it) }

    fun <T> T.toSuccess(): Try<T> = Try.success(this)

    fun <T, U : Throwable> U.toFailure(): Try<T> = Try.failure(this)

    inline fun <T> Try<T>.ionSuccess(action: (T) -> Unit): Try<T> {
        if (isSuccess) {
            action(get())
        }
        return this
    }

}
