package kiger.regalloc

import kiger.frame.Register
import kiger.regalloc.InterferenceGraph.INode
import kiger.regalloc.InterferenceGraph.Move
import kiger.temp.Temp
import kiger.utils.removeAny
import java.util.*

fun color(flowGraph: FlowGraph,
          interferenceGraph: InterferenceGraph,
          preallocatedColors: Map<Temp, Register>,
          spillCost: (Temp) -> Double,
          registers: Collection<Register>): Pair<Coloring, List<Temp>> {

    val colorer = GraphColorer(flowGraph, interferenceGraph, spillCost, registers)

    colorer.build(preallocatedColors)
    colorer.checkInvariants()
    colorer.makeWorklist()
    colorer.checkInvariants()
    colorer.mainLoop()
    colorer.checkInvariants()
    colorer.assignColors()

    return colorer.result()
}

/**
 * Graph coloring as described in pages 241-249 of Modern Compiler Implementation in ML.
 */
class GraphColorer(val flowGraph: FlowGraph, val interferenceGraph: InterferenceGraph, val spillCost: (Temp) -> Double, val registers: Collection<Register>) {

    /** The number of colors available */
    private val K = registers.size

    //
    // Node work-lists, sets and stacks.
    //
    // The following lists and sets are always
    // mutually disjoint and every node is always in exactly one of the sets or lists.
    //

    /** Machine registers, preassigned a color */
    private val precolored = mutableSetOf<INode>()

    /** Temporary registers, not [precolored] and not yet processed */
    private val initial = mutableSetOf<INode>()

    /** List of low-degree non-move-related nodes */
    private val simplifyWorklist = Worklist()

    /** Low-degree move-related nodes */
    private val freezeWorklist = Worklist()

    /** High-degree nodes */
    private val spillWorklist = Worklist()

    /** Nodes marked for spilling during this round; initially empty */
    private val spilledNodes = mutableSetOf<INode>()

    /**
     * Registers that have been coalesced; when `u <- v` is coalesced,
     * `v` is added to this set and `u` is put back on some work-list (or vice versa).
     */
    private val coalescedNodes = mutableSetOf<INode>()

    /** Nodes successfully colored */
    private val coloredNodes = mutableSetOf<INode>()

    /** Stack containing temporaries removed from the graph */
    private val selectStack = NodeStack()

    //
    // Move sets.
    //
    // There are five sets of move instructions, and every move is in exactly one
    // of these sets (after Build through the end of Main).
    //

    /** Moves that have been coalesced */
    private val coalescedMoves = MoveSet()

    /** Moves whose source and target interface */
    private val constrainedMoves = MoveSet()

    /** Move that will no longer be considered for coalescing */
    private val frozenMoves = MoveSet()

    /** Moves enabled for possible coalescing */
    private val worklistMoves = MoveSet()

    /** Moves not yet ready for coalescing */
    private val activeMoves = MoveSet()

    /** Mapping from nodes to their selected colors */
    private val coloring = Coloring()

    /**
     * Construct the interference graph and categorize each node as either move-related
     * or non-move-related. A move-related nove is one that is either the source or
     * destination of a move-instruction.
     */
    fun build(preallocatedColors: Map<Temp, Register>) {
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
            // TODO: why are the move-lists conditions in original code?
            if (m.src !in precolored)
                m.src.moveList.add(m)

            if (m.dst !in precolored)
                m.dst.moveList.add(m)

            worklistMoves += m
        }

        // TODO: where is this coming from if not here?
        for (v in precolored) {
            v.degree = K
            for (u in precolored)
                addEdge(v, u)
        }

        for (i in flowGraph.nodes) {
            for (d in i.def) {
                for (l in i.liveOut) {
                    addEdge(interferenceGraph.nodes.find { it.temp == l }!!, interferenceGraph.nodes.find { it.temp == d }!!)
                }
            }
        }
    }

    private fun addEdge(u: INode, v: INode) {
        if (!interferenceGraph.contains(u, v) && u != v) {
            interferenceGraph.addEdge(v, u)
            interferenceGraph.addEdge(u, v)

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
        for (n in initial) {
            when {
                n.degree >= K    -> spillWorklist += n
                n.isMoveRelated  -> freezeWorklist += n
                else             -> simplifyWorklist += n
            }
        }

        initial.clear() // we don't need to keep the data around
    }

    fun mainLoop() {
        while (true) {
            when {
                simplifyWorklist.any()  -> simplify()
                worklistMoves.any()     -> coalesce()
                freezeWorklist.any()    -> freeze()
                spillWorklist.any()     -> selectSpill()
                else                    -> return
            }
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

    /**
     * One at a time, remove non-move-related nodes of low (< K) degree from the graph.
     */
    private fun simplify() {
        val n = simplifyWorklist.removeAny()

        selectStack.push(n)
        for (m in n.adjacent)
            decrementDegree(m)
    }

    private fun decrementDegree(m: INode) {
        // only decrement those non-precolored nodes - for
        // precolored nodes, we treat as if they have infinite
        // degree, since we shouldn't reassign them to different registers
        if (m in precolored) return // TODO: not in book

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

    /**
     * Perform conservative coalescing on the reduced graph obtained in the simplification
     * phase. Since the degrees of many nodes have been reduced by [simplify], the conservative
     * strategy is likely to find many more moves to coalesce than it would have in the initial
     * inference graph. After two nodes have been coalesced (and the move instruction deleted),
     * if the resulting node is no longer move-related it will be available for the next round
     * of simplification. [simplify] and [coalesce] are repeated until only significant-degree
     * or move-related nodes remain.
     */
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
            v in precolored || interferenceGraph.contains(u, v) -> {
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
        t.degree < K || t in precolored || interferenceGraph.contains(t, r)

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

    /**
     * If neither [simplify] or [coalesce] applies, we look for a move-related node of low degree.
     * We freeze the moves ([freezeMoves]) in which this node is involved: that is, we give up
     * hope of coalescing those moves. This causes the node (and perhaps other nodes related to
     * the frozen moves) to be considered non-move-related, which should enable more simplification.
     * Now [simplify] and [coalesce] are resumed.
     */
    private fun freeze() {
        val u = freezeWorklist.removeAny()
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
    fun checkInvariants() {
        interferenceGraph.check(precolored)
        checkWorkListsAreDistinct()
        checkDegreeInvariant()
        checkSimplifyWorklistInvariant()
        checkFreezeWorklistInvariant()
        checkSpillWorklistInvariant()
    }

    private fun checkWorkListsAreDistinct() {
        val lists = listOf("precolored" to precolored, "initial" to initial, "simplify" to simplifyWorklist, "freeze" to freezeWorklist, "spill" to spillWorklist, "spilled" to spilledNodes, "coalesced" to coalescedNodes, "colored" to coloredNodes, "selectStack" to selectStack)

        // check that each node is in single list
        for (n in interferenceGraph.nodes) {
            val ls = lists.filter { n in it.second }.map { it.first }
            check(ls.size == 1) { "node $n was in $${ls.size} lists: $ls"}
        }

        // .. and all nodes are in some list
        val totalSize = lists.sumBy { it.second.count() }
        check(totalSize == interferenceGraph.nodes.size) { "expected ${interferenceGraph.nodes.size} nodes, but got $totalSize"}
    }

    private fun checkDegreeInvariant() {
        val lists = precolored + simplifyWorklist + freezeWorklist + spillWorklist

        fun checkInvariant(n: INode, listName: String) {
            val expected = n.adjList.intersect(lists).size
            check(n.degree == expected) { "degree for node ${n.temp} in $listName unexpected: ${n.degree} != $expected" }
        }

        for (n in simplifyWorklist)
            checkInvariant(n, "simplifyWorklist")

        for (n in freezeWorklist)
            checkInvariant(n, "freezeWorklist")

        for (n in spillWorklist)
            checkInvariant(n, "spillWorklist")
    }

    private fun checkSimplifyWorklistInvariant() {
        for (n in simplifyWorklist) {
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

    val INode.adjacent: Iterable<INode>
        get() = adjList - (selectStack + coalescedNodes) // TODO: optimize
}

private class Worklist : Iterable<INode> {

    private val nodes = mutableSetOf<INode>()

    operator fun plusAssign(node: INode) {
        nodes += node
    }

    operator fun minusAssign(node: INode) {
        nodes -= node
    }

    override fun iterator() = nodes.iterator()

    fun removeAny(): INode = nodes.removeAny()

    val size: Int
        get() = nodes.size
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

    val size: Int
        get() = stack.size
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
