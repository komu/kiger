package kiger.env

import kiger.temp.Label
import kiger.translate.Access
import kiger.translate.Level
import kiger.types.Type

sealed class EnvEntry {
    class Var(val access: Access, val type: Type) : EnvEntry()
    class Function(val level: Level, val label: Label, val formals: List<Type>, val result: Type) : EnvEntry()
}
