package kiger.utils

import java.util.*

val profileTimes = TreeMap<String,Long>()

inline fun <T : Any?> profile(name: String, block: () -> T): T {
    val start = System.currentTimeMillis()
    try {
        return block()
    } finally {
        val time = System.currentTimeMillis() - start
        profileTimes[name] = time + (profileTimes[name] ?: 0)
    }
}

fun dumpProfileTimes() {
    val maxLength = profileTimes.keys.map { it.length }.max() ?: 0
    for ((name, time) in profileTimes.entries.sortedByDescending { it.value })
        println("${name.padStart(maxLength+1)}: ${time}ms")
}
