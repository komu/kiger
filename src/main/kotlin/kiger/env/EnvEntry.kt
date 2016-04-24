package kiger.env

import kiger.translate.Access
import kiger.types.Type

sealed class EnvEntry {

    class Var(val access: Access, val type: Type) : EnvEntry()
    class Function : EnvEntry()
}
