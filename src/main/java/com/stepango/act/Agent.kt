package com.stepango.act

import com.stepango.act.internal.Act

interface Agent {
    fun execute(act: Act,
                e: (Throwable) -> Unit = ::logError)

    fun cancel(id: String)
    fun cancelAll()
}

fun logError(e: Throwable) {
    System.out.println(e.message)
}