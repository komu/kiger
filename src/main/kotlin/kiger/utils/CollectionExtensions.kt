package kiger.utils

/**
 * Returns sub-list containing everything but the first element of the list.
 */
fun <T> List<T>.tail() =
    subList(1, size)

/**
 * Returns sub-list from [index] to the end of the list.
 */
fun <T> List<T>.tailFrom(index: Int) =
    subList(index, size)

/**
 * Returns sub-list containing everything but the last element of the list.
 */
fun <T> List<T>.withoutLast() =
    subList(0, size - 1)

/**
 * Returns pair containing everything but the last element and the last element.
 */
fun <T> List<T>.splitLast(): Pair<List<T>, T> =
    Pair(withoutLast(), last())
