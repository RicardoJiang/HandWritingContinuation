package com.zj.handwritingcontinuation.lua

import com.zj.handwritingcontinuation.utils.log
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


class SymCoroutine<T>(
    override val context: CoroutineContext = EmptyCoroutineContext,
    private val block: suspend SymCoroutine<T>.SymCoroutineBody.(T) -> Unit
) : Continuation<T> {

    companion object {
        lateinit var main: SymCoroutine<Any?>

        suspend fun main(block: suspend SymCoroutine<*>.SymCoroutineBody.() -> Unit) {
            SymCoroutine<Any?> { block() }.also { main = it }.start(Unit)
        }

        fun <T> create(
            context: CoroutineContext = EmptyCoroutineContext,
            block: suspend SymCoroutine<T>.SymCoroutineBody.(T) -> Unit
        ): SymCoroutine<T> {
            return SymCoroutine(context, block)
        }
    }

    inner class SymCoroutineBody {
        private tailrec suspend fun <P> transferInner(symCoroutine: SymCoroutine<P>, value: Any?): T{
            if(this@SymCoroutine.isMain){
                return if(symCoroutine.isMain){
                    value as T
                } else {
                    val parameter = symCoroutine.coroutine.resume(value as P)
                    transferInner(parameter.coroutine, parameter.value)
                }
            } else {
                this@SymCoroutine.coroutine.run {
                   return yield(Parameter(symCoroutine, value as P))
                }
            }
        }

        suspend fun <P> transfer(symCoroutine: SymCoroutine<P>, value: P): T {
           return transferInner(symCoroutine, value)
        }
    }

    class Parameter<T>(val coroutine: SymCoroutine<T>, val value: T)

    val isMain: Boolean
        get() = this == main

    private val body = SymCoroutineBody()

    private val coroutine = Coroutine<T, Parameter<*>>(context) {
        Parameter(this@SymCoroutine, suspend {
            block(body, it)
            if(this@SymCoroutine.isMain) Unit else throw IllegalStateException("SymCoroutine cannot be dead.")
        }() as T)
    }

    override fun resumeWith(result: Result<T>) {
        throw IllegalStateException("SymCoroutine cannot be dead!")
    }

    suspend fun start(value: T){
        coroutine.resume(value)
    }
}

object SymCoroutines {
    val coroutine0: SymCoroutine<Int> = SymCoroutine.create<Int> { param: Int ->
        log("coroutine-0", param)
        var result = transfer(com.zj.handwritingcontinuation.lua.SymCoroutines.coroutine2, 0)
        log("coroutine-0 1", result)
        result = transfer(com.zj.handwritingcontinuation.lua.SymCoroutine.main, Unit)
        log("coroutine-0 1", result)
    }

    val coroutine1: SymCoroutine<Int> = SymCoroutine.create { param: Int ->
        log("coroutine-1", param)
        val result = transfer(com.zj.handwritingcontinuation.lua.SymCoroutines.coroutine0, 1)
        log("coroutine-1 1", result)
    }

    val coroutine2: SymCoroutine<Int> = SymCoroutine.create { param: Int ->
        log("coroutine-2", param)
        var result = transfer(com.zj.handwritingcontinuation.lua.SymCoroutines.coroutine1, 2)
        log("coroutine-2 1", result)
        result = transfer(com.zj.handwritingcontinuation.lua.SymCoroutines.coroutine1, 2)
        log("coroutine-2 2", result)
    }
}

suspend fun main() {

    SymCoroutine.main {
        log("main", 0)
        val result = transfer(com.zj.handwritingcontinuation.lua.SymCoroutines.coroutine2, 3)
        log("main end", result)
    }

}
