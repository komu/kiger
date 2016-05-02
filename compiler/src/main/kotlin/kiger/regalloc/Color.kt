package kiger.regalloc

import kiger.frame.Register
import kiger.temp.Temp
import kiger.utils.cons
import kiger.utils.splitFirst

/*
(* The book recommends associating each node with
a membership flag. WE defined it here, but leave it for future improvement *)

(* move set *)
structure NS = BinarySetFn(
  type ord_key = LI.node
  fun compare(LI.NODE{temp=t1,...},LI.NODE{temp=t2,...})
      = String.compare(Temp.makestring t1,Temp.makestring t2))

structure MS = BinarySetFn(
  type ord_key = LI.node*LI.node
  fun compare((LI.NODE{temp=t1,...},
                LI.NODE{temp=t2,...}),
               (LI.NODE{temp=t1',...},
                LI.NODE{temp=t2',...})) =
    case String.compare(Temp.makestring t1,Temp.makestring t1') of
      EQUAL => String.compare(Temp.makestring t2,Temp.makestring t2')
     | od => od)

(* register set *)
structure RS = ListSetFn(
    type ord_key = Frame.register
    fun compare (r1,r2) = String.compare(r1,r2))

structure WL = NS
structure TT = Temp.Table
structure T = Temp

type allocation = Frame.register TT.table
*/

fun color(interference: InterferenceGraph,
          initAlloc: Map<Temp, Register>,
          spillCost: (Temp) -> Double,
          registers: List<Register>): Pair<Allocation, List<Temp>> {

    val (graph, moves) = interference

    var simplifyWL = emptyList<INode>()
    var freezeWL = emptyList<INode>()
    var spillWL = emptyList<INode>()

    var coalescedMS = emptySet<Pair<INode, INode>>()
    var constrainedMS = emptySet<Pair<INode, INode>>()
    var frozenMS = emptySet<Pair<INode, INode>>()
    var worklistMS = emptySet<Pair<INode, INode>>()
    var activeMS = emptySet<Pair<INode, INode>>()

    var spillNS = emptySet<INode>()
    var coalescedNS = emptySet<INode>()
    var coloredNS = emptySet<INode>()

    var selectStack = emptyList<INode>()
    var colored = Allocation.empty()
    var moveList = emptyMap<Temp, Set<Pair<INode, INode>>>()

    var precolored = emptySet<INode>()
    var initial = emptyList<INode>()
    var alias = emptyMap<Temp, INode>()

    // # of available colors
    val K = registers.size

    // precolorTable is a mapping from temp to register,
    // while initial is a list of uncolored nodes
    fun build() {
        fun addMove(node: INode, mv: Pair<INode, INode>) {
            val s = moveList[node.temp] ?: emptySet()

            moveList += node.temp to (s + mv)
        }

        // initialize colored and precolored
        for (n in graph) {
            val r = initAlloc[n.temp]
            if (r != null) {
                colored = colored.enter(n.temp, r)
                precolored += n
            } else {
                initial += n
            }
        }

        // associate each node with a empty move set
        for (n in graph)
            moveList += n.temp to emptySet()

        // initialize worklistMS and moveList
        for (m in moves) {
            val (src, dst) = m

            if (src !in precolored)
                addMove(src, m)

            if (dst !in precolored)
                addMove(dst, m)

            worklistMS += m
        }
    }

    fun INode.nodeMoves() =
            moveList[temp]!!.intersect(activeMS + worklistMS)

    fun INode.isMoveRelated() =
            nodeMoves().any()

    // Create initial worklist
    fun makeWorklist() {
        for (n in initial) {
            check(n.isInGraph)

            if (n.degree >= K)
                spillWL += n
            else if (n.isMoveRelated())
                freezeWL += n
            else
                simplifyWL += n
        }
    }

    fun enableMoves(nodes: Set<INode>) {
        for (n in nodes) {
            for (m in n.nodeMoves()) {
                if (m in activeMS) {
                    activeMS -= m
                    worklistMS += m
                }
            }
        }
    }

    fun addWorklist(n: INode) {
        check(n.isInGraph)

        if (n !in precolored && !n.isMoveRelated() && n.degree < K) {
            freezeWL -= n
            simplifyWL += n
        }
    }

    tailrec fun getAlias(n: INode): INode =
        if (n in coalescedNS)
            getAlias(alias[n.temp]!!)
        else
            n

    // adjacent nodes
    fun adjacent(n: INode) =
        n.adj.toSet() - (selectStack.toSet() + coalescedNS)

    // decrement degree for graph node n, return
    // modified degreeMap and a (possibly augmented) simplify worklist *)
    fun decrementDegree(n: INode) {
        // only decrement those non-precolored nodes - for
        // precolored nodes, we treat as if they have infinite
        // degree, since we shouldn't reassign them to different registers
        check(n.isInGraph)

        val d = n.degree
        if (n.temp !in initAlloc) {
            n.status = IStatus.InGraph(d - 1)
            if (d == K) {
                enableMoves(adjacent(n) + n)
                spillWL -= n
                if (n.isMoveRelated())
                    freezeWL += n
                else
                    simplifyWL += n
            }
        }
    }

    // whether v is in adj of u.
    // TODO: replace with more efficient adjSet
    fun INode.inAdj(v: INode) = v in adj

    fun ok(t: INode, r: INode) =
        t.degree < K || t in precolored || t.inAdj(r)

    fun conservative(nodes: Collection<INode>): Boolean {
        var k = 0
        for (n in nodes)
            if (n.degree >= K)
                k++
        return k < K
    }

    // add new edge to graph
    fun addEdge(u: INode, v: INode) {
        check(u.isInGraph && v.isInGraph)

        if (!u.inAdj(v) && u != v) {
            if (u !in precolored) {
                u.adj += v
                u.status = IStatus.InGraph(u.degree + 1)
            }

            if (v !in precolored) {
                v.adj += u
                v.status = IStatus.InGraph(v.degree + 1)
            }
        }
    }

    fun combine(u: INode, v: INode) {
        if (v in freezeWL)
            freezeWL -= v
        else
            spillWL -= v

        coalescedNS += v
        alias += (v.temp to u)

        val mvU = moveList[u.temp]!!
        val mvV = moveList[v.temp]!!

        moveList += (u.temp to mvU + mvV)
        enableMoves(setOf(v))

        for (t in v.adj) {
            addEdge(t, u)
            decrementDegree(t)
        }

        if (u.degree >= K && u in freezeWL) {
            freezeWL -= u
            spillWL += u
        }
    }

    fun coalesce() {
        val m = worklistMS.first()
        val x = getAlias(m.first)
        val y = getAlias(m.second)
        val (u, v) = if (y in precolored) Pair(y, x) else Pair(x, y)
        worklistMS -= m

        fun allOk(u: INode, nodes: Collection<INode>) = nodes.all { ok(it, u) }

        if (u == v) {
            coalescedMS += m
            addWorklist(u)
        } else if (v in precolored || u.inAdj(v)) {
            constrainedMS += m
            addWorklist(u)
            addWorklist(v)
        } else if ((u in precolored && allOk(u, v.adj)) || (u !in precolored && conservative(u.adj + v.adj))) {
            coalescedMS += m
            combine(u, v)
            addWorklist(u)
        } else {
            activeMS += m
        }
    }

    fun freezeMoves(u: INode) {
        for (m in u.nodeMoves()) {
            val (x, y) = m

            val v = if (getAlias(y) == getAlias(u)) getAlias(x) else getAlias(y)
            activeMS -= m
            frozenMS += m

            if (v.nodeMoves().isEmpty() && v.degree < K && v !in precolored) {
                freezeWL -= v
                simplifyWL += v
            }
        }
    }

    fun freeze() {
        val (u, us) = freezeWL.splitFirst() // TODO: use proper mutable queue
        freezeWL = us

        simplifyWL += u
        freezeMoves(u)
    }

    // simplify the graph by keep removing the first node from simplify
    // worklist and add to select stack. At same time, decrement degree
    // for adjacent nodes of the removed node.
    // precondition: simplifyWL not nil.
    fun simplify() {
        val (n, ns) = simplifyWL.splitFirst() // TODO: use proper mutable queue
        simplifyWL = ns

        selectStack = cons(n, selectStack)

        for (r in n.adj)
            decrementDegree(r)
    }

    fun selectSpill() {
        tailrec fun f(min: INode, tlist: List<INode>): INode =
            if (tlist.isEmpty()) {
                min
            } else {
                val (r, rs) = tlist.splitFirst()
                val c1 = spillCost(min.temp)
                val c2 = spillCost(r.temp)
                if (c1 >= c2)
                    f(r, rs)
                else
                    f(min, rs)
            }

        val (r, rs) = spillWL.splitFirst() // TODO: use priority queue
        val min = f(r, rs)
        spillWL -= min
        simplifyWL += min
        freezeMoves(min)
    }

    fun pickColor(regs: Set<Register>): Register =
        regs.first()

    // assign color to all nodes on select stack. The parameter
    // colored is all nodes that are already assigned a color.
    tailrec fun assignColors(): Allocation =
        if (selectStack.isEmpty()) {
            for (n in coalescedNS) {
                val t = getAlias(n).temp
                val c = colored[t]!!

                colored = colored.enter(n.temp, c)
            }

            colored

        } else {
            val (n, ns) = selectStack.splitFirst() // TODO: use proper stack
            selectStack = ns

            val availableColors = n.adj.fold(registers.toSet()) { cset, w ->
                val w2 = getAlias(w)
                if (w2 in coloredNS || w2 in precolored) {
                    val c = colored[w2.temp]!!
                    if (c in cset)
                        cset - c
                    else
                        cset
                } else {
                    cset
                }
            }

            if (availableColors.isEmpty()) {
                spillNS += n
            } else {
                val r = pickColor(availableColors)
                coloredNS += n
                colored = colored.enter(n.temp, r)
            }

            assignColors()
        }

    build()
    makeWorklist()

    mainLoop@while (true) {
        when {
            simplifyWL.any() -> simplify()
            worklistMS.any() -> coalesce()
            freezeWL.any()   -> freeze()
            spillWL.any()    -> selectSpill()
            else -> break@mainLoop
        }
    }

    return Pair(assignColors(), spillNS.map { it.temp })
}
