package kiger.regalloc

import kiger.assem.Instr
import kiger.regalloc.InterferenceGraph.INode
import kiger.regalloc.InterferenceGraph.Move
import kiger.target.FrameType
import kiger.target.Register
import kiger.temp.Temp
import kiger.utils.removeAny
import java.util.*

fun color(instrs: List<Instr>, frameType: FrameType): Pair<Coloring, List<Temp>> {

    return GraphColorer(instrs.createFlowGraph(), frameType.tempMap, frameType.assignableRegisters).color()
}

/**
 * Graph coloring as described in pages 241-249 of Modern Compiler Implementation in ML.
 */
class GraphColorer(
        val flowGraph: FlowGraph,

        /** Mapping from temps to preallocated registers */
        val preallocatedColors: Map<Temp, Register>,

        /**
         * Registers that may be assigned. Might not contain all registers in [preallocatedColors].
         * (e.g. on MIPS `$zero` is preallocated, but may not be used for coloring anything.
         */
        val assignableRegisters: List<Register>) {

    /** The number of colors available */
    private val K = assignableRegisters.size

    /**
     * Interference graph.
     */
    val interferenceGraph = flowGraph.interferenceGraph()

    //
    // Node work-lists, sets and stacks.
    //
    // The following lists and sets are always
    // mutually disjoint and every node is always in exactly one of the sets or lists.
    //

    /** Machine registers, preassigned a color */
    private val precolored = NodeSet() { it.precolored }

    /** Temporary registers, not [precolored] and not yet processed */
    private val initial = NodeSet() { !it.precolored }

    /** List of low-degree non-move-related nodes */
    private val simplifyWorklist = Worklist() { !it.precolored }

    /** Low-degree move-related nodes */
    private val freezeWorklist = Worklist() { !it.precolored }

    /** High-degree nodes */
    private val spillWorklist = Worklist() { !it.precolored }

    /** Nodes marked for spilling during this round; initially empty */
    private val spilledNodes = NodeSet() { !it.precolored }

    /**
     * Registers that have been coalesced; when `u <- v` is coalesced,
     * `v` is added to this set and `u` is put back on some work-list (or vice versa).
     */
    private val coalescedNodes = NodeSet() { !it.precolored }

    /** Nodes successfully colored */
    private val coloredNodes = NodeSet() { !it.precolored }

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


    fun color(): Pair<Coloring, List<Temp>> {
        build()
        checkInvariants()
        makeWorklist()
        checkInvariants()
        mainLoop()
        checkInvariants()
        assignColors()

        return result()
    }

    /**
     * Construct the interference graph and categorize each node as either move-related
     * or non-move-related. A move-related node is one that is either the source or
     * destination of a move-instruction.
     */
    fun build() {
        // initialize colored and precolored
        for (node in interferenceGraph.nodes) {
            val color = preallocatedColors[node.temp]
            if (color != null) {
                node.color = color
                node.degree = Int.MAX_VALUE
                node.precolored = true
                precolored += node

            } else {
                initial += node
            }
        }

        worklistMoves += interferenceGraph.moves

        for (v in precolored)
            for (u in precolored)
                interferenceGraph.addEdge(v, u)
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
            val okColors = assignableRegisters.toMutableSet()

            for (w in n.adjList) {
                val wa = getAlias(w)
                if (wa in (coloredNodes + precolored)) // TODO: optimize
                    okColors -= wa.color
            }

            if (okColors.isEmpty()) {
                spilledNodes += n
            } else {
                coloredNodes += n
                n.color = okColors.removeAny()
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
        // Precolored nodes have infinite degree so we just ignore those.
        if (m.precolored) return

        val oldDegree = m.degree
        m.degree -= 1

        if (oldDegree == K) {
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
            interferenceGraph.addEdge(t, u)
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
            val src = m.src
            val dst = m.dst
            val v = if (getAlias(dst) == getAlias(u)) getAlias(src) else getAlias(dst)

            activeMoves -= m

            frozenMoves += m

            if (v.nodeMoves.isEmpty() && v.degree < K) {
                freezeWorklist -= v
                simplifyWorklist += v
            }
        }
    }

    /**
     * After initialization the following invariants always hold.
     */
    fun checkInvariants() {
//        interferenceGraph.check(precolored)
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

    private fun spillCost(temp: Temp): Double {
        val defs = flowGraph.nodes.count { temp in it.def }
        val uses = flowGraph.nodes.count { temp in it.use }
        val interferes = interferenceGraph.nodeForTemp(temp).adjList.size

        return (defs + uses).toDouble() / interferes.toDouble()
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

private class Worklist(val predicate: (INode) -> Boolean) : Iterable<INode> {

    private val nodes = mutableSetOf<INode>()

    operator fun plusAssign(node: INode) {
        require(predicate(node))
        nodes += node
    }

    operator fun minusAssign(node: INode) {
        nodes -= node
    }

    override fun iterator() = nodes.iterator()

    fun removeAny(): INode = nodes.removeAny()

    val size: Int
        get() = nodes.size

    override fun toString() = nodes.toString()
}

private class NodeStack : Iterable<INode> {

    private val stack = ArrayList<INode>()

    fun push(node: INode) {
        check(!node.precolored)
        stack.add(node)
    }

    fun pop(): INode =
        stack.removeAt(stack.lastIndex)

    override fun iterator(): Iterator<INode> = stack.iterator()

    fun isNotEmpty(): Boolean = stack.isNotEmpty()

    val size: Int
        get() = stack.size

    override fun toString() = stack.toString()
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

    operator fun plusAssign(ms: Iterable<Move>) {
        moves += ms
    }

    operator fun minusAssign(move: Move) {
        moves -= move
    }

    fun removeAny(): Move = moves.removeAny()

    override fun iterator() = moves.iterator()

    override fun toString() = moves.toString()

    val size: Int
        get() = moves.size
}

private class NodeSet(val predicate: (INode) -> Boolean) : Iterable<INode> {

    private val nodes = mutableSetOf<INode>()

    operator fun plusAssign(node: INode) {
        require(predicate(node))
        nodes += node
    }

    operator fun minusAssign(node: INode) {
        nodes -= node
    }

    override fun iterator() = nodes.iterator()

    val size: Int
        get() = nodes.size

    override fun toString() = nodes.toString()

    fun clear() {
        nodes.clear()
    }
}
