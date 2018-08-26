package com.stepango.act

import com.stepango.act.internal.Act
import com.stepango.act.internal.ActKey
import com.stepango.act.internal.GroupMap
import io.reactivex.disposables.Disposable
import java.util.concurrent.ConcurrentHashMap

interface Launcher {
    fun execute(act: Act,
                e: (Throwable) -> Unit = ::logError)

    fun stop(groupKey: GroupKey = defaultGroup, id: String)
    fun cancelAll()
}

fun logError(e: Throwable) {
    System.out.println(e.message)
}

interface Store {
    fun isRunning(groupKey: GroupKey = defaultGroup, id: Id): Boolean
    fun start(groupKey: GroupKey = defaultGroup, id: Id, disposable: () -> Disposable)
    fun stopAll()
    fun stopGroup(groupKey: GroupKey)
    fun stop(groupKey: GroupKey, id: Id)
}

class StoreImpl : Store {

    val map = ConcurrentHashMap<GroupKey, GroupMap>()

    override fun isRunning(groupKey: GroupKey, id: Id): Boolean {
        return map[groupKey]?.containsKey(id) ?: false
    }

    @Synchronized
    override fun start(groupKey: GroupKey, id: Id, starter: () -> Disposable) {
        val actsMap = map[groupKey]
                ?: ConcurrentHashMap<ActKey, Disposable>().apply { map[groupKey] = this }
        actsMap[id] = starter()
        println(actsMap.toString())
    }

    @Synchronized
    override fun stopGroup(groupKey: GroupKey) {
        map[groupKey]?.values?.forEach { it.dispose() }
    }

    override fun stop(groupKey: GroupKey, id: Id) {
        map[groupKey]?.apply {
            remove(id)?.dispose()
        }
    }

    override fun stopAll() {
        map.keys.forEach { stopGroup(it) }
    }
}