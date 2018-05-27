package com.stepango.act.internal

import com.stepango.act.Agent
import com.stepango.act.Default
import com.stepango.act.GroupKey
import com.stepango.act.GroupStrategy
import com.stepango.act.GroupStrategyHolder
import com.stepango.act.KillGroup
import com.stepango.act.KillMe
import com.stepango.act.SaveMe
import com.stepango.act.Strategy
import com.stepango.act.StrategyHolder
import com.stepango.act.defaultGroup
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

sealed class Act(
        val id: String,
        override val strategy: Strategy,
        override val groupStrategy: GroupStrategy,
        override val groupKey: GroupKey
) : StrategyHolder, GroupStrategyHolder

private class CompletableAct(
        id: String,
        val completable: Completable,
        strategy: Strategy = SaveMe,
        groupStrategy: GroupStrategy = Default,
        groupKey: GroupKey = defaultGroup
) : Act(id, strategy, groupStrategy, groupKey)

private class SingleAct<T : Any>(
        id: String,
        val single: Single<T>,
        strategy: Strategy = SaveMe,
        groupStrategy: GroupStrategy = Default,
        groupKey: GroupKey = defaultGroup
) : Act(id, strategy, groupStrategy, groupKey)

fun Completable.toAct(
        id: String,
        strategy: Strategy = SaveMe,
        groupStrategy: GroupStrategy = Default,
        groupKey: GroupKey = defaultGroup
): Act = CompletableAct(id, this, strategy, groupStrategy)

fun <T : Any> Single<T>.toAct(
        id: String,
        strategy: Strategy = SaveMe,
        groupStrategy: GroupStrategy = Default,
        groupKey: GroupKey = defaultGroup
): Act = SingleAct(id, this, strategy, groupStrategy)

typealias ActKey = String
typealias GroupMap = ConcurrentHashMap<ActKey, Disposable>

class AgentImpl : Agent {
    private val groupsMap = ConcurrentHashMap<GroupKey, GroupMap>()

    override fun execute(act: Act, e: (Throwable) -> Unit) {
        val actsMap = groupsMap[act.groupKey]
                ?: ConcurrentHashMap<ActKey, Disposable>().apply { groupsMap[act.groupKey] = this }
        if (act.groupStrategy == KillGroup) actsMap.values.forEach { it.dispose() }
        return when {
            actsMap.containsKey(act.id) -> when (act.strategy) {
                KillMe -> {
                    cancel(act.id)
                    startExecution(act, actsMap, e)
                }
                SaveMe -> log("${act.id} - Act duplicate")
            }
            else -> startExecution(act, actsMap, e)
        }
    }

    @Synchronized
    private fun startExecution(act: Act, map: GroupMap, e: (Throwable) -> Unit) {
        log("${act.id} - Act Started")
        val removeFromMap = {
            map.remove(act.id)
            log("${act.id} - Act Finished")
        }
        when (act) {
            is CompletableAct -> act.completable
                    .doFinally(removeFromMap)
                    .doOnDispose { log("${act.id} - Act Canceled") }
                    .subscribe({}, e)
            is SingleAct<*> -> act.single
                    .doFinally(removeFromMap)
                    .doOnDispose { log("${act.id} - Act Canceled") }
                    .subscribe({}, e)
        }.let { map.put(act.id, it) }
    }

    override fun cancel(id: String) {
        groupsMap.values.forEach { it[id]?.dispose() }
    }

    override fun cancelAll() = groupsMap.values.forEach { it.values.forEach(Disposable::dispose) }
}

fun log(obj: Any) {
    System.out.println(obj)
}

fun main(args: Array<String>) {
    val a = AgentImpl()
    a.execute(Completable.timer(2, TimeUnit.SECONDS).toAct(
            id = "Like",
            groupStrategy = KillGroup,
            groupKey = "Like-Dislike-PostId-1234"))
    a.execute(Completable.timer(2, TimeUnit.SECONDS).toAct(
            id = "Dislike",
            groupStrategy = KillGroup,
            groupKey = "Like-Dislike-PostId-1234"))
    a.execute(Completable.timer(2, TimeUnit.SECONDS).toAct(
            id = "Like",
            groupStrategy = KillGroup,
            groupKey = "Like-Dislike-PostId-1234"))
    CountDownLatch(1).await()
}
