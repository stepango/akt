package com.stepango.act

import com.stepango.act.internal.Act
import com.stepango.act.internal.ActKey
import com.stepango.act.internal.GroupMap
import io.reactivex.disposables.Disposable
import java.util.concurrent.ConcurrentHashMap

interface ActExecutor {
    fun execute(act: Act,
                e: (Throwable) -> Unit = ::logError)

    fun stop(groupKey: GroupKey = defaultGroup, id: String)
    fun cancelAll()
}

fun logError(e: Throwable) {
    System.out.println(e.message)
}

interface GroupDisposable {
    fun contains(groupKey: GroupKey = defaultGroup, id: Id): Boolean
    fun add(groupKey: GroupKey = defaultGroup, id: Id, disposable: () -> Disposable)
    fun removeAll()
    fun removeGroup(groupKey: GroupKey)
    fun remove(groupKey: GroupKey, id: Id)
}

class GroupDisposableImpl : GroupDisposable {

    val map = ConcurrentHashMap<GroupKey, GroupMap>()

    override fun contains(groupKey: GroupKey, id: Id): Boolean {
        return map[groupKey]?.containsKey(id) ?: false
    }

    @Synchronized
    override fun add(groupKey: GroupKey, id: Id, starter: () -> Disposable) {
        val actsMap = map[groupKey]
                ?: ConcurrentHashMap<ActKey, Disposable>().apply { map[groupKey] = this }
        actsMap[id] = starter()
        println(actsMap.toString())
    }

    @Synchronized
    override fun removeGroup(groupKey: GroupKey) {
        map[groupKey]?.values?.forEach { it.dispose() }
    }

    override fun remove(groupKey: GroupKey, id: Id) {
        map[groupKey]?.apply {
            remove(id)?.dispose()
        }
    }

    override fun removeAll() {
        map.keys.forEach { removeGroup(it) }
    }
}