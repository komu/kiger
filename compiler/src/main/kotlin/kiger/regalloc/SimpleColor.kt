package kiger.regalloc

import kiger.frame.Register
import kiger.temp.Temp
import kiger.utils.cons
import kiger.utils.splitFirst

/**
 * Simple register coloring with spilling, but without coalescing. *)
 */
fun simpleColor(interference: InterferenceGraph,
                initAlloc: Map<Temp, Register>,
                spillCost: (Temp) -> Double,
                registers: List<Register>): Pair<Allocation, List<Temp>> {

    // # of colors available
    val K = registers.size

    val precolored = mutableMapOf<Temp,Register>()
    val initial = mutableListOf<INode>()

    for (n in interference.nodes) {
        val reg = initAlloc[n.temp]
        if (reg != null)
            precolored[n.temp] = reg
        else
            initial += n
    }

    // A map from graph nodes to their *initial* degree
    val degreeMap = mutableMapOf<Temp, Int>()
    for (n in interference.nodes)
        degreeMap[n.temp] = n.adj.size

    // Create initial worklist
    fun makeWorkLists(initial: List<INode>): WorkLists {
        val (simplify, spill) = initial.partition { it.adj.size < K }
        return WorkLists(simplify, spill)
    }

    // decrement degree for graph node n, return
    // modified degreeMap and a (possibly augmented) simplify worklist *)
    fun decrementDegree (n: INode, degreeMap: Map<Temp, Int>, wls: WorkLists): Pair<Map<Temp,Int>, WorkLists> {
        // only decrement those non-precolored nodes - for
        // precolored nodes, we treat as if they have infinite
        // degree, since we shouldn't reassign them to different registers
        val reg = initAlloc[n.temp]
        return if (reg != null)
            Pair(degreeMap, wls)
        else {
            val d = degreeMap[n.temp]!!
            val dm2 = degreeMap + (n.temp to d-1) //  update n's degree
            if (d == K)
                Pair(dm2, WorkLists(wls.simplify + n, wls.spill - n))
            else
                Pair(dm2,wls)
        }
    }

    // adjacent nodes
    fun adjacent(node: INode, st: List<INode>) = node.adj.filter { it !in st }

    // simplify the graph by keep removing the first node from simplify
    // worklist and add to select stack. At same time, decrement degree
    // for adjacent nodes of the removed node. *)
    tailrec fun simplify(stack: List<INode>, wls: WorkLists, degreeMap: Map<Temp,Int>): Triple<List<INode>, List<INode>, Map<Temp,Int>> {
        if (wls.simplify.isEmpty())
            return Triple(stack, wls.spill, degreeMap)

        val (n, si2) = wls.simplify.splitFirst()
        val (dm2, wls2) = adjacent(n, stack).fold(Pair(degreeMap, WorkLists(si2, wls.spill))) { state, m ->
            decrementDegree(m, state.first, state.second)
        }

        return simplify(cons(n, stack), wls2, dm2)
    }

    // select a node for spill, according to spill cost
    fun selectSpill(wls: WorkLists): WorkLists {
        val (si, sp) = wls

        tailrec fun f (min: INode, tlist: List<INode>): INode =
            if (tlist.isEmpty())
                min
            else {
                val (r, rs) = tlist.splitFirst()
                val c1 = spillCost(min.temp)
                val c2 = spillCost(r.temp)
                if (c1 >= c2)
                    f(r, rs)
                else
                    f(min, rs)
            }

        val (r, rs) = sp.splitFirst()
        val min = f(r, rs)
        return WorkLists(cons(min, si), sp - min)
    }

    val coloring = Allocation()
    for ((t, r) in precolored)
        coloring[t] = r

    // assign color to all nodes on select stack. The parameter
    // colored is all nodes that are already assigned a color.
    tailrec fun assignColors(stack: List<INode>, spills: List<Temp>): List<Temp> {
        if (stack.isEmpty())
            return spills

        val (head, ns) = stack.splitFirst()
        val n = head.temp

        val availableColors = registers.toMutableSet()
        for (w in head.adj) {
            val reg = coloring[w.temp]
            if (reg != null)
                availableColors -= reg
        }

        // if no available colors, add the node to spills
        return if (availableColors.isEmpty()) {
            assignColors(ns, cons(n, spills))
        } else {
            // choose a color from available colors, and assign
            // it to node n. Also, mark this node as colored
            coloring[n] = availableColors.first()
            assignColors(ns, spills)
        }
    }


    // main loop

    tailrec fun iter(sti: List<INode>, wls: WorkLists, dmi: Map<Temp, Int>): List<INode> {
        val (sto, spills, dmo) = simplify (sti, wls, dmi)

        return if (spills.isEmpty())
            sto
        else
            iter(sto, selectSpill(WorkLists(emptyList(), spills)), dmo)
    }

    val wls = makeWorkLists(initial)
    val stack = iter(emptyList(), wls, degreeMap)

    val spills = assignColors(stack, emptyList())
    return Pair(coloring, spills)
}

private data class WorkLists(val simplify: List<INode>, val spill: List<INode>)
