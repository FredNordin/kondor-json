package com.ubertob.kondor.outcome


sealed class Outcome<out E : OutcomeError, out T> {

    fun <U> transform(f: (T) -> U): Outcome<E, U> =
        when (this) {
            is Success -> f(this.value).asSuccess()
            is Failure -> this
        }

    fun <F : OutcomeError> transformFailure(f: (E) -> F): Outcome<F, T> =
        when (this) {
            is Success -> this
            is Failure -> f(this.error).asFailure()
        }

    fun orThrow(): T =
        when (this) {
            is Success -> value
            is Failure -> throw OutcomeException(error)
        }

    companion object {
        fun <T, U, E : OutcomeError> lift(f: (T) -> U): (Outcome<E, T>) -> Outcome<E, U> =
            { o -> o.transform { f(it) } }

        inline fun <T> tryOrFail(block: () -> T): Outcome<ThrowableError, T> =
            try {
                block().asSuccess()
            } catch (t: Throwable) {
                ThrowableError(t).asFailure()
            }

    }

}

data class Success<T> internal constructor(val value: T) : Outcome<Nothing, T>()
data class Failure<E : OutcomeError> internal constructor(val error: E) : Outcome<E, Nothing>()


inline fun <T, E : OutcomeError> Outcome<E, T>.recover(recoverError: (E) -> T): T =
    when (this) {
        is Success -> value
        is Failure -> recoverError(error)
    }

inline fun <T, U, E : OutcomeError> Outcome<E, T>.bind(f: (T) -> Outcome<E, U>): Outcome<E, U> =
    when (this) {
        is Success -> f(value)
        is Failure -> this
    }

inline fun <T, E : OutcomeError, F : OutcomeError> Outcome<E, T>.bindFailure(f: (E) -> Outcome<F, T>): Outcome<F, T> =
    when (this) {
        is Success -> this
        is Failure -> f(error)
    }


fun <T, U, E : OutcomeError> Outcome<E, T>.combine(other: Outcome<E, U>): Outcome<E, Pair<T, U>> =
    bind { first -> other.transform { second -> first to second } }

//Keisli composition
infix fun <A, B, C, E : OutcomeError> ((A) -> Outcome<E, B>).compose(other: (B) -> Outcome<E, C>): (A) -> Outcome<E, C> =
    { a -> this(a).bind(other) }


fun <T, E : OutcomeError> Outcome<E, Outcome<E, T>>.join(): Outcome<E, T> =
    bind { it }

fun <T, E : OutcomeError> Outcome<E, T>.failIf(predicate: (T) -> Boolean, error: (T) -> E): Outcome<E, T> =
    when (this) {
        is Success -> if (predicate(value).not()) this else error(value).asFailure()
        is Failure -> this
    }

fun <T : Any, E : OutcomeError> Outcome<E, T?>.failIfNull(error: E): Outcome<E, T> =
    when (this) {
        is Success -> if (value != null) value.asSuccess() else error.asFailure()
        is Failure -> this
    }

inline fun <T, E : OutcomeError> Outcome<E, T>.onFailure(exitBlock: (E) -> Nothing): T =
    when (this) {
        is Success<T> -> value
        is Failure<E> -> exitBlock(error)
    }


interface OutcomeError {
    val msg: String
}

data class OutcomeException(val error: OutcomeError) : RuntimeException() {
    override val message: String = error.msg
}

fun <E : OutcomeError> E.asFailure(): Outcome<E, Nothing> = Failure(this)
fun <T> T.asSuccess(): Outcome<Nothing, T> = Success(this)


fun <T : Any, E : OutcomeError> T?.failIfNull(error: E): Outcome<E, T> = this?.asSuccess() ?: error.asFailure()

fun <T, E : OutcomeError> T.failIf(predicate: (T) -> Boolean, error: (T) -> E): Outcome<E, T> =
    if (predicate(this)) asSuccess() else error(this).asFailure()


data class ThrowableError(val throwable: Throwable) : OutcomeError {
    override val msg: String = throwable.message.orEmpty()
}

data class MessageError(override val msg: String) : OutcomeError

fun String.asFailure() = MessageError(this).asFailure()

fun <T, ERR : OutcomeError, U> Iterable<T>.foldOutcome(
    initial: U,
    operation: (acc: U, T) -> Outcome<ERR, U>
): Outcome<ERR, U> =
    fold(initial.asSuccess() as Outcome<ERR, U>) { acc, el -> acc.bind { operation(it, el) } }


fun <E : OutcomeError, T> Iterable<Outcome<E, T>>.extractList(): Outcome<E, List<T>> =
    foldOutcome(mutableListOf()) { acc, e -> e.transform { acc.add(it); acc } } //todo


fun <E : OutcomeError, T> Sequence<Outcome<E, T>>.extractList(): Outcome<E, List<T>> =
    foldOutcome(mutableListOf()) { acc, e -> e.transform { acc.add(it); acc }  }


fun <T, ERR : OutcomeError, U> Sequence<T>.foldOutcome(
    initial: U,
    operation: (acc: U, T) -> Outcome<ERR, U>
): Outcome<ERR, U> {

    val iter = iterator()

    tailrec fun loop(acc: U): Outcome<ERR, U> =
        if (!iter.hasNext()) acc.asSuccess()
        else when (val el = operation(acc, iter.next())) {
            is Failure -> el
            is Success -> loop(el.value)
        }

    return loop(initial)
}


infix fun <A, B, D, ER : OutcomeError> ((A, B) -> D).`!`(other: Outcome<ER, A>): Outcome<ER, (B) -> D> =
    other.transform { a -> { this(a, it) } }

infix fun <A, B, C, D, ER : OutcomeError> ((A, B, C) -> D).`!`(other: Outcome<ER, A>): Outcome<ER, (B) -> (C) -> D> =
    other.transform { a -> { b -> { this(a, b, it) } } }

@Suppress("DANGEROUS_CHARACTERS")
infix fun <A, B, ER : OutcomeError> Outcome<ER, (A) -> B>.`*`(a: Outcome<ER, A>): Outcome<ER, B> =
    bind { a.transform(it) }