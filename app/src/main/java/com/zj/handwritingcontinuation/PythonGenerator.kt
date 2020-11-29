package com.zj.handwritingcontinuation

import kotlin.coroutines.*

/**
 * Created by jiangjunxiang on 11/29/20
 */
class PythonGenerator {
    interface Generator<T> {
        operator fun iterator(): Iterator<T>
    }

    class GeneratorImpl<T>(
        private val block: suspend GeneratorScope<T>.(T) -> Unit,
        private val parameter: T
    ) : Generator<T> {
        override fun iterator(): Iterator<T> {
            return GeneratorIterator(block, parameter)
        }
    }

    sealed class State {
        class NotReady(val continuation: Continuation<Unit>) : State()
        class Ready<T>(val continuation: Continuation<Unit>, val nextValue: T) : State()
        object Done : State()
    }

    class GeneratorIterator<T>(
        private val block: suspend GeneratorScope<T>.(T) -> Unit,
        override val parameter: T
    ) : Iterator<T>, Continuation<Any?>, GeneratorScope<T>() {
        override val context: CoroutineContext = EmptyCoroutineContext
        private var state: State

        init {
            val coroutineBlock: suspend GeneratorScope<T>.() -> Unit = {
                block(parameter)
            }
            val start = coroutineBlock.createCoroutine(this, this)
            state = State.NotReady(start)
        }

        override fun hasNext(): Boolean {
            resume()
            return state != State.Done
        }

        override fun next(): T {
            return when (val currentState = state) {
                is State.NotReady -> {
                    resume()
                    return next()
                }
                is State.Ready<*> -> {
                    state = State.NotReady(currentState.continuation)
                    (currentState as State.Ready<T>).nextValue
                }
                State.Done -> throw IndexOutOfBoundsException("No Value Left")
            }
        }

        override fun resumeWith(result: Result<Any?>) {
            state = State.Done
            result.getOrThrow()
        }

        override suspend fun yield(value: T) {
            return suspendCoroutine {
                state = when (state) {
                    is State.NotReady -> State.Ready(it, value)
                    is State.Ready<*> -> throw IllegalStateException("Cannot yield a value while ready")
                    State.Done -> throw IllegalStateException("Cannot yield a value while Done")
                }
            }
        }

        private fun resume() {
            when (val currentState = state) {
                is State.NotReady -> currentState.continuation.resume(Unit)
            }
        }

    }

    abstract class GeneratorScope<T> internal constructor() {
        protected abstract val parameter: T
        abstract suspend fun yield(value: T)
    }

    fun <T> generator(block: suspend GeneratorScope<T>.(T) -> Unit): (T) -> Generator<T> {
        return {
            GeneratorImpl(block, it)
        }
    }

    fun main() {
        val nums = generator { start: Int ->
            //限制yield的调用范围，只能在lambda中使用
            for (i in 0..5) {
                yield(start + i)
            }
        }
        val seqs = nums(10)
        for (j in seqs){
            println(j)
        }
    }
}