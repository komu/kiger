package kiger.regalloc

import kiger.frame.Register
import kiger.temp.Temp
import java.util.*

fun newColor(interferenceGraph: InterferenceGraph,
             preallocatedColors: Map<Temp, Register>,
             spillCost: (Temp) -> Double,
             registers: List<Register>): Pair<Coloring, List<Temp>> {
    val colorer = GraphColorer(interferenceGraph, preallocatedColors, spillCost, registers)
    colorer.makeWorklist()
    colorer.mainLoop()
    colorer.assignColors()

    return colorer.result()
}

/**
 * Graph coloring as described in pages 241-249 of Modern Compiler Implementation in ML.
 */
private class GraphColorer(interferenceGraph: InterferenceGraph,
                           preallocatedColors: Map<Temp, Register>,
                           val spillCost: (Temp) -> Double,
                           val registers: List<Register>) {

    /** The number of colors available */
    val K = registers.size

    //
    // Node work-lists, sets and stacks.
    //
    // The following lists and sets are always
    // mutually disjoint and every node is always in exactly one of the sets or lists.
    //

    /** Machine registers, preassigned a color */
    val precolored = mutableSetOf<INode>()

    /** Temporary registers, not [precolored] and not yet processed */
    val initial = mutableSetOf<INode>()

    /** List of low-degree non-move-related nodes */
    val simplifyWorklist = Worklist()

    /** Low-degree move-related nodes */
    val freezeWorklist = Worklist()

    /** High-degree nodes */
    val spillWorklist = Worklist()

    /** Nodes marked for spilling during this round; initially empty */
    val spilledNodes = mutableSetOf<INode>()

    /**
     * Registers that have been coalesced; when `u <- v` is coalesced,
     * `v` is added to this set and `u` is put back on some work-list (or vice versa).
     */
    val coalescedNodes = mutableSetOf<INode>()

    /** Nodes successfully colored */
    val coloredNodes = mutableSetOf<INode>()

    /** Stack containing temporaries removed from the graph */
    val selectStack = NodeStack()

    //
    // Move sets.
    //
    // There are five sets of move instructions, and every move is in exactly one
    // of these sets (after Build through the end of Main).
    //

    /** Moves that have been coalesced */
    val coalescedMoves = MoveSet()

    /** Moves whose source and target interface */
    val constrainedMoves = MoveSet()

    /** Move that will no longer be considered for coalescing */
    val frozenMoves = MoveSet()

    /** Moves enabled for possible coalescing */
    val worklistMoves = MoveSet()

    /** Moves not yet ready for coalescing */
    val activeMoves = MoveSet()

    /** Mapping from nodes to their selected colors */
    val coloring = Coloring()

    val adjSet = AdjSet()

    init {
        // initialize colored and precolored
        for (node in interferenceGraph.nodes) {
            val color = preallocatedColors[node.temp]
            if (color != null) {
                node.color = color
                precolored += node
            } else {
                initial += node
            }
        }

        for (m in interferenceGraph.moves) {
            if (m.src !in precolored)
                m.src.moveList.add(m)

            if (m.dst !in precolored)
                m.dst.moveList.add(m)

            worklistMoves += m
        }

        for (p1 in precolored)
            for (p2 in precolored)
                addEdge(p1, p2)

        checkInvariants()
    }

    private fun addEdge(u: INode, v: INode) {
        if (!adjSet.contains(u, v) && u != v) {
            adjSet.addEdge(v, u)
            adjSet.addEdge(u, v)

            if (u !in precolored) {
                u.adjList += v
                u.degree += 1
            }

            if (v !in precolored) {
                v.adjList += u
                v.degree += 1
            }
        }
    }

    fun makeWorklist() {
        checkInvariants()

        for (n in initial) {
            when {
                n.degree >= K    -> spillWorklist += n
                n.isMoveRelated  -> freezeWorklist += n
                else             -> simplifyWorklist += n
            }
        }

        initial.clear() // we don't need to keep the data around

        checkInvariants()
    }

    fun mainLoop() {
        checkInvariants()
        while (true) {
            when {
                simplifyWorklist.any()  -> simplify()
                worklistMoves.any()     -> coalesce()
                freezeWorklist.any()    -> freeze()
                spillWorklist.any()     -> selectSpill()
                else                    -> return
            }

            checkInvariants()
        }
    }

    fun assignColors() {
        while (selectStack.isNotEmpty()) {
            val n = selectStack.pop()
            val okColors = registers.toMutableSet()
            for (w in n.adjList) {
                val wa = getAlias(w)
                if (wa in (coloredNodes + precolored)) // TODO: optimize
                    okColors -= wa.color

                if (okColors.isEmpty()) {
                    spilledNodes += n
                } else {
                    coloredNodes += n
                    n.color = okColors.removeAny()
                }
            }
        }

        for (n in coalescedNodes)
            n.color = getAlias(n).color
    }

    fun result(): Pair<Coloring, List<Temp>> =
        Pair(coloring, spilledNodes.map { it.temp })

    private fun simplify() {
        val n = simplifyWorklist.removeFirst()

        selectStack.push(n)
        for (m in n.adjacent)
            decrementDegree(m)
    }

    private fun decrementDegree(m: INode) {
        val d = m.degree
        m.degree = d - 1

        if (d == K) {
            enableMoves(m.adjacent + m)
            spillWorklist -= m
            if (m.isMoveRelated)
                freezeWorklist += m
            else
                simplifyWorklist += m
        }
    }

    private fun enableMoves(nodes: Iterable<INode>) {
        for (n in nodes)
            for (m in n.nodeMoves)
                if (m in activeMoves) {
                    activeMoves -= m
                    worklistMoves += m
                }
    }

    private fun coalesce() {
        val m = worklistMoves.removeAny()
        val x = getAlias(m.src)
        val y = getAlias(m.dst)

        val u: INode
        val v: INode
        if (y in precolored) {
            u = y
            v = x
        } else {
            u = x
            v = y
        }

        when {
            u == v -> {
                coalescedMoves += m
                addWorklist(u)
            }
            v in precolored || adjSet.contains(u, v) -> {
                constrainedMoves += m
                addWorklist(u)
                addWorklist(v)
            }
            u in precolored && v.adjacent.all { ok(it, u) } || u !in precolored && conservative(u.adjacent + v.adjacent) -> {
                coalescedMoves += m
                combine(u, v)
                addWorklist(u)
            }
            else ->
                activeMoves += m
        }
    }

    private fun addWorklist(u: INode) {
        if (u !in precolored && !u.isMoveRelated && u.degree < K) {
            freezeWorklist -= u
            simplifyWorklist += u
        }
    }

    private fun ok(t: INode, r: INode): Boolean =
        t.degree < K || t in precolored || adjSet.contains(t, r)

    private fun conservative(nodes: Iterable<INode>): Boolean =
        nodes.count { it.degree >= K } < K

    private fun combine(u: INode, v: INode) {
        if (v in freezeWorklist)
            freezeWorklist -= v
        else
            spillWorklist -= v

        coalescedNodes += v
        v.alias = u
        u.moveList += v.moveList
        enableMoves(setOf(v))

        for (t in v.adjacent) {
            addEdge(t, u)
            decrementDegree(t)
        }

        if (u.degree >= K && u in freezeWorklist) {
            freezeWorklist -= u
            spillWorklist += u
        }
    }

    private tailrec fun getAlias(n: INode): INode =
        if (n in coalescedNodes)
            getAlias(n.alias!!)
        else
            n

    private fun selectSpill() {
        val m = spillWorklist.minBy { spillCost(it.temp) }!!
        spillWorklist -= m
        simplifyWorklist += m
        freezeMoves(m)
    }

    private fun freeze() {
        val u = freezeWorklist.removeFirst()
        simplifyWorklist += u
        freezeMoves(u)
    }

    private fun freezeMoves(u: INode) {
        for (m in u.nodeMoves) {
            val (x, y) = m
            val v = if (getAlias(y) == getAlias(u)) getAlias(x) else getAlias(y)

            activeMoves -= m
            frozenMoves += m

            if (v.nodeMoves.isEmpty() && v.degree < K) { // && v !in precolored) { // TODO the precolored test is added
                freezeWorklist -= v
                simplifyWorklist += v
            }
        }
    }

    /**
     * After initialization the following invariants always hold.
     */
    private fun checkInvariants() {
        checkDegreeInvariant()
        checkSimplifyWorklistInvariant()
        checkFreezeWorklistInvariant()
        checkSpillWorklistInvariant()
    }

    private fun checkDegreeInvariant() {
        // TODO
        val lists = precolored + simplifyWorklist + freezeWorklist + spillWorklist

        for (n in simplifyWorklist + freezeWorklist + spillWorklist) {
            val expected = n.adjList.intersect(lists).size
            check(n.degree == expected) { "degree ${n.degree} != $expected" }
        }
    }

    private fun checkSimplifyWorklistInvariant() {
        for (n in simplifyWorklist) {
            // TODO: this is violated for some reason
            check(n.degree < K) { "simplifyWorklist has node ${n.temp} with invalid degree: ${n.degree} >= $K" }
            check(n.moveList.intersect(activeMoves + worklistMoves).isEmpty())
        }
    }

    private fun checkFreezeWorklistInvariant() {
        check(freezeWorklist.all { it.degree < K && it.moveList.intersect(activeMoves + worklistMoves).isNotEmpty() })
    }

    private fun checkSpillWorklistInvariant() {
        for (n in spillWorklist)
            check(n.degree >= K) { "spillWorklist has node with invalid degree: ${n.degree} < $K" }
    }

    private val INode.nodeMoves: Set<Move>
        get() = moveList.intersect(activeMoves + worklistMoves) // TODO: optimize

    private val INode.isMoveRelated: Boolean
        get() = nodeMoves.isNotEmpty()

    private var INode.color: Register
        get() = coloring[temp]!!
        set(v) {
            coloring[temp] = v
        }

    val INode.adjacent: List<INode>
        get() = adjList - (selectStack + coalescedNodes) // TODO: optimize
}

data class Move(val src: INode, val dst: INode)

private class Worklist(val predicate: (INode) -> Boolean = { true }) : Iterable<INode> {

    private val nodes = ArrayDeque<INode>()

    operator fun plusAssign(node: INode) {
        check(predicate(node)) { "node did not satisfy predicate" }
        nodes += node
    }

    operator fun minusAssign(node: INode) {
        nodes -= node
    }

    override fun iterator() = nodes.iterator()

    fun removeFirst(): INode = nodes.removeFirst()
}

private class NodeStack : Iterable<INode> {

    private val stack = ArrayList<INode>()

    fun push(node: INode) {
        stack.add(node)
    }

    fun pop(): INode =
        stack.removeAt(stack.lastIndex)

    override fun iterator(): Iterator<INode> = stack.iterator()

    fun isNotEmpty(): Boolean = stack.isNotEmpty()
}

// Note that the book says:
//
// "like the node work-lists, the move sets should be implemented as doubly-linked lists,
// with each move containing an enumeration value identifying which set it belongs to."
//
// At the moment we don't do that, but we probably should.
private class MoveSet : Iterable<Move> {

    private val moves = mutableSetOf<Move>()

    operator fun plusAssign(move: Move) {
        moves += move
    }

    operator fun minusAssign(move: Move) {
        moves -= move
    }

    fun removeAny(): Move = moves.removeAny()

    override fun iterator() = moves.iterator()
}

private fun <T> MutableIterable<T>.removeAny(): T {
    val it = iterator()
    val move = it.next()
    it.remove()
    return move
}

private class AdjSet {

    private val set = mutableSetOf<Pair<INode,INode>>() // TODO: use bitset

    fun contains(u: INode, v: INode) =
        Pair(u, v) in set

    fun addEdge(u: INode, v: INode) {
        set += Pair(u, v)
    }
}
