package com.stepango.act.internal

import com.stepango.act.Agent
import com.stepango.act.KillMe
import com.stepango.act.SaveMe
import com.stepango.act.Strategy
import com.stepango.act.StrategyHolder
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

sealed class Act(
        val id: String,
        override val strategy: Strategy
) : StrategyHolder

private class CompletableAct(
        id: String,
        val completable: Completable,
        strategy: Strategy = SaveMe
) : Act(id, strategy)

private class SingleAct<T : Any>(
        id: String,
        val single: Single<T>,
        strategy: Strategy = SaveMe
) : Act(id, strategy)

fun Completable.toAct(id: String): Act = CompletableAct(id, this)
fun <T : Any> Single<T>.toAct(id: String): Act = SingleAct(id, this)

class AgentImpl : Agent {
    val map = ConcurrentHashMap<String, Disposable>()

    override fun execute(act: Act, e: (Throwable) -> Unit) = when {
        map.containsKey(act.id) -> log("${act.id} - Act duplicate")
        else -> startExecution(act, e).apply { log("${act.id} - Act Started") }
    }

    @Synchronized
    private fun startExecution(act: Act, e: (Throwable) -> Unit) {
        val removeFromMap = {
            map.remove(act.id)
            log("${act.id} - Act Finished")
        }
        when (act) {
            is CompletableAct -> act.completable
                    .doFinally(removeFromMap)
                    .subscribe({}, e)
            is SingleAct<*> -> act.single
                    .doFinally(removeFromMap)
                    .subscribe({}, e)
        }.let { map.put(act.id, it) }
    }

    override fun cancel(id: String) {
        map[id]?.dispose()
    }

    override fun cancelAll() = map.values.forEach(Disposable::dispose)
}

fun log(obj: Any) {
    System.out.println(obj)
}

fun <T : StrategyHolder> winner(me: T, notMe: T): T = when (me.strategy) {
    KillMe -> notMe
    SaveMe -> me
}

fun main(args: Array<String>) {
    val a = AgentImpl()
    a.execute(Completable.timer(2, TimeUnit.SECONDS).toAct("Hello"))
    a.execute(Completable.timer(2, TimeUnit.SECONDS).toAct("Hello"))
    a.execute(Completable.timer(2, TimeUnit.SECONDS).toAct("Hello"))
    CountDownLatch(1).await()
}
