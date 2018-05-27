package com.stepango.act

interface StrategyHolder {
    val strategy: Strategy
}

sealed class Strategy
object KillMe : Strategy()
object SaveMe : Strategy()
