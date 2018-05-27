package com.stepango.act

typealias GroupKey = String

val defaultGroup = "default_group"

interface GroupStrategyHolder {
    val groupStrategy: GroupStrategy
    val groupKey: GroupKey
}

sealed class GroupStrategy
object Default : GroupStrategy()
object KillGroup : GroupStrategy()