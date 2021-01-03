package com.zj.handwritingcontinuation.lua

import com.zj.handwritingcontinuation.utils.log
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.*

sealed class Status {
    class Created(val continuation: Continuation<Unit>): Status()
    class Yielded<P>(val continuation: Continuation<P>): Status()
    class Resumed<R>(val continuation: Continuation<R>): Status()
    object Dead: Status()
}

class Coroutine<P, R> (
    override val context: CoroutineContext = EmptyCoroutineContext,
    private val block: suspend Coroutine<P,R>.CoroutineBody.(P) -> R
): Continuation<R> {

    companion object {
        fun <P, R> create(
            context: CoroutineContext = EmptyCoroutineContext,
            block: suspend Coroutine<P,R>.CoroutineBody.(P) -> R
        ): Coroutine<P, R> {
            return Coroutine(context, block)
        }
    }

    inner class CoroutineBody {
        var parameter: P? = null

        suspend fun yield(value: R): P = suspendCoroutine { continuation ->
            val previousStatus = status.getAndUpdate {
                when(it) {
                    is Status.Created -> throw IllegalStateException("Never started!")
                    is Status.Yielded<*> -> throw IllegalStateException("Already yielded!")
                    is Status.Resumed<*> -> Status.Yielded(continuation)
                    Status.Dead -> throw IllegalStateException("Already dead!")
                }
            }

            (previousStatus as? Status.Resumed<R>)?.continuation?.resume(value)
        }
    }

    private val body = CoroutineBody()

    private val status: AtomicReference<Status>

    val isActive: Boolean
        get() = status.get() != Status.Dead

    init {
        val coroutineBlock: suspend CoroutineBody.() -> R = { block(parameter!!) }
        val start = coroutineBlock.createCoroutine(body, this)
        status = AtomicReference(Status.Created(start))
    }

    override fun resumeWith(result: Result<R>) {
        val previousStatus = status.getAndUpdate {
            when(it) {
                is Status.Created -> throw IllegalStateException("Never started!")
                is Status.Yielded<*> -> throw IllegalStateException("Already yielded!")
                is Status.Resumed<*> -> {
                    Status.Dead
                }
                Status.Dead -> throw IllegalStateException("Already dead!")
            }
        }
        (previousStatus as? Status.Resumed<R>)?.continuation?.resumeWith(result)
    }

    suspend fun resume(value: P): R = suspendCoroutine { continuation ->
        val previousStatus = status.getAndUpdate {
            when(it) {
                is Status.Created -> {
                    body.parameter = value
                    Status.Resumed(continuation)
                }
                is Status.Yielded<*> -> {
                    Status.Resumed(continuation)
                }
                is Status.Resumed<*> -> throw IllegalStateException("Already resumed!")
                Status.Dead -> throw IllegalStateException("Already dead!")
            }
        }

        when(previousStatus){
            is Status.Created -> previousStatus.continuation.resume(Unit)
            is Status.Yielded<*> -> (previousStatus as Status.Yielded<P>).continuation.resume(value)
        }
    }

    suspend fun <SymT> SymCoroutine<SymT>.yield(value: R): P {
        return body.yield(value)
    }
}

class Dispatcher: ContinuationInterceptor {
    override val key = ContinuationInterceptor

    private val executor = Executors.newSingleThreadExecutor()

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        return DispatcherContinuation(continuation, executor)
    }
}

class DispatcherContinuation<T>(val continuation: Continuation<T>, val executor: Executor): Continuation<T> by continuation {

    override fun resumeWith(result: Result<T>) {
        executor.execute {
            continuation.resumeWith(result)
        }
    }
}

suspend fun main() {
    val producer = Coroutine.create<Unit, Int>(Dispatcher()) {
        for (i in 0..3) {
            log("send", i)
            yield(i)
        }
        200
    }

    val consumer = Coroutine.create<Int, Unit>(Dispatcher()) { param: Int ->
        log("start", param)
        for (i in 0..3) {
            val value = yield(Unit)
            log("receive", value)
        }
    }

    while (producer.isActive && consumer.isActive){
        val result = producer.resume(Unit)
        consumer.resume(result)
    }
}