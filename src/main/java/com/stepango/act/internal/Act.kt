package com.stepango.act.internal

import com.stepango.act.ActExecutor
import com.stepango.act.Default
import com.stepango.act.GroupKey
import com.stepango.act.GroupStrategy
import com.stepango.act.GroupStrategyHolder
import com.stepango.act.Id
import com.stepango.act.KillGroup
import com.stepango.act.KillMe
import com.stepango.act.SaveMe
import com.stepango.act.GroupDisposable
import com.stepango.act.GroupDisposableImpl
import com.stepango.act.Strategy
import com.stepango.act.StrategyHolder
import com.stepango.act.defaultGroup
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

interface Act : StrategyHolder, GroupStrategyHolder {
    val id: Id
}

class CompletableAct(
        override val id: Id,
        val completable: Completable,
        override val strategy: Strategy = SaveMe,
        override val groupStrategy: GroupStrategy = Default,
        override val groupKey: GroupKey = defaultGroup
) : Act

class SingleAct<T : Any>(
        override val id: Id,
        val single: Single<T>,
        override val strategy: Strategy = SaveMe,
        override val groupStrategy: GroupStrategy = Default,
        override val groupKey: GroupKey = defaultGroup
) : Act

fun Completable.toAct(
        id: Id,
        strategy: Strategy = SaveMe,
        groupStrategy: GroupStrategy = Default,
        groupKey: GroupKey = defaultGroup
): Act = CompletableAct(id, this, strategy, groupStrategy, groupKey)

fun <T : Any> Single<T>.toAct(
        id: Id,
        strategy: Strategy = SaveMe,
        groupStrategy: GroupStrategy = Default,
        groupKey: GroupKey = defaultGroup
): Act = SingleAct(id, this, strategy, groupStrategy, groupKey)

typealias ActKey = String
typealias GroupMap = ConcurrentHashMap<ActKey, Disposable>

class ActExecutorImpl(
        val groupDisposable: GroupDisposable,
        lifecycle: Lifecycle
) : ActExecutor {

    init {
        lifecycle.doOnDestroy { cancelAll() }
    }

    @Synchronized
    override fun execute(act: Act, e: (Throwable) -> Unit) {
        if (act.groupStrategy == KillGroup) groupDisposable.removeGroup(act.groupKey)
        return when {
            groupDisposable.contains(act.groupKey, act.id) -> when (act.strategy) {
                KillMe -> {
                    stop(act.groupKey, act.id)
                    startExecution(act, e)
                }
                SaveMe -> log("${act.id} - Act duplicate")
            }
            else -> startExecution(act, e)
        }
    }

    private fun startExecution(act: Act, e: (Throwable) -> Unit) {
        log("${act.id} - Act Started " + System.currentTimeMillis())
        val removeFromMap = {
            groupDisposable.remove(act.groupKey, act.id)
            log("${act.id} - Act Finished " + System.currentTimeMillis())
        }
        groupDisposable.add(act.groupKey, act.id) {
            when (act) {
                is CompletableAct -> act.completable
                        .doFinally(removeFromMap)
                        .doOnDispose { log("${act.id} - Act Canceled " + System.currentTimeMillis()) }
                        .subscribe({}, e)
                is SingleAct<*> -> act.single
                        .doFinally(removeFromMap)
                        .doOnDispose { log("${act.id} - Act Canceled " + System.currentTimeMillis()) }
                        .subscribe({}, e)
                else -> throw IllegalArgumentException()
            }
        }
    }

    override fun stop(groupKey: GroupKey, id: Id) {
        groupDisposable.remove(groupKey, id)
    }

    override fun cancelAll() = groupDisposable.removeAll()
}

interface Lifecycle {
    fun doOnDestroy(f: () -> Unit)
}

fun log(obj: Any) {
    System.out.println(obj)
}

fun main(args: Array<String>) {
    val store = GroupDisposableImpl()
    val launcher = ActExecutorImpl(store, object : Lifecycle {
        override fun doOnDestroy(f: () -> Unit) = Unit
    })
    launcher.execute(Completable.timer(2, TimeUnit.SECONDS).toAct(
            id = "Like",
            groupStrategy = KillGroup,
            groupKey = "Like-Dislike-PostId-1234"))
    launcher.execute(Completable.timer(2, TimeUnit.SECONDS).toAct(
            id = "Dislike",
            groupStrategy = KillGroup,
            groupKey = "Like-Dislike-PostId-1234"))
    launcher.execute(Completable.timer(2, TimeUnit.SECONDS).toAct(
            id = "Like",
            groupStrategy = KillGroup,
            groupKey = "Like-Dislike-PostId-1234"))
    CountDownLatch(1).await()
}
