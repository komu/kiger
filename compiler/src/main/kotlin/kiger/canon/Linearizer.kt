package kiger.canon

import kiger.temp.Temp
import kiger.tree.TreeExp
import kiger.tree.TreeExp.*
import kiger.tree.TreeStm
import kiger.tree.TreeStm.*
import kiger.tree.TreeStm.Branch.CJump
import kiger.tree.TreeStm.Branch.Jump
import kiger.utils.cons
import kiger.utils.splitFirst
import kiger.utils.tail

/**
 * Linearizes arbitrary [TreeStm] into a list of "simple" statements.
 *
 * The "simple statements" satisfy the following properties:
 *
 * 1. No SEQ's or ESEQ's
 * 2. The parent of every CALL is an EXP(..) or a MOVE(TEMP t,..)
 *
 * @see toQuads
 */
fun TreeStm.linearize(): List<TreeStm> =
    doStm(this).linearizeTopLevel().toList()

/**
 * Performs selective delinearization of statements: inline all temporaries
 * that are assigned and used only once.
 */
fun List<TreeStm>.delinearize(): List<TreeStm> {

    // First pass: gather all temporaries along with their use first definition and definition counts
    val definitionCounts = hashMapOf<Temp, Pair<TreeExp, Int>>()

    for (stm in this) {
        if (stm is TreeStm.Move) {
            val temp = (stm.target as? Temporary)?.temp
            if (temp != null) {
                val def = definitionCounts[temp]
                definitionCounts[temp] = if (def == null) Pair(stm.source, 1) else Pair(stm.source, def.second + 1)
            }
        }
    }

    // Second pass: gather use counts for temporaries
    val useCounts = hashMapOf<Temp, Int>()
    for (stm in this)
        stm.countUses(useCounts)

    // Third pass: create a map of temporaries to be inlined
    val inlineableTemps = hashMapOf<Temp, TreeExp>()
    for ((temp, p) in definitionCounts) {
        val (def, defCount) = p

        if (defCount == 1 && useCounts[temp] == 1)
            inlineableTemps[temp] = def
    }

    // Now remove all the inlineable temps from the result
    val exps = this.filterNot { it is Move && it.target is Temporary && it.target.temp in inlineableTemps }

    return exps.map { it.inline(inlineableTemps) }
}

/**
 * Returns the original tree rewritten so that all temps in [inlinedTemps] are replaced
 * by their corresponding expressions.
 *
 * (When the temp is grafted to a new tree, this inlining will also be recursively performed
 * on the newly inlined tree.)
 */
fun TreeStm.inline(inlinedTemps: Map<Temp, TreeExp>): TreeStm =
    this.mapExps { it.inline(inlinedTemps) }

fun TreeExp.inline(inlinedTemps: Map<Temp, TreeExp>): TreeExp = when (this) {
    is TreeExp.Temporary    -> inlinedTemps[temp]?.let { it.inline(inlinedTemps) } ?: this
    is TreeExp.BinOp        -> BinOp(binop, lhs.inline(inlinedTemps), rhs.inline(inlinedTemps))
    is TreeExp.Mem          -> Mem(exp.inline(inlinedTemps))
    is TreeExp.ESeq         -> ESeq(stm.inline(inlinedTemps), exp.inline(inlinedTemps))
    is TreeExp.Call         -> Call(func.inline(inlinedTemps), args.map { it.inline(inlinedTemps) })
    is TreeExp.Name         -> this
    is TreeExp.Const        -> this
}

private fun TreeStm.countUses(uses: MutableMap<Temp, Int>): Unit = when (this) {
    is TreeStm.Seq      -> { lhs.countUses(uses); rhs.countUses(uses) }
    is TreeStm.Labeled  -> { }
    is TreeStm.Branch   -> countUses(uses)
    is TreeStm.Move     -> { target.countUsesInChildren(uses); source.countUses(uses) }
    is TreeStm.Exp      -> exp.countUses(uses)
}

private fun TreeStm.Branch.countUses(uses: MutableMap<Temp, Int>): Unit = when (this) {
    is TreeStm.Branch.Jump  -> exp.countUses(uses)
    is TreeStm.Branch.CJump -> { lhs.countUses(uses); rhs.countUses(uses) }
}

fun TreeExp.countUses(uses: MutableMap<Temp, Int>) {
    if (this is Temporary)
        uses[temp] = 1 + (uses[temp] ?: 0)
    else
        countUsesInChildren(uses)
}

fun TreeExp.countUsesInChildren(uses: MutableMap<Temp, Int>): Unit = when (this) {
    is TreeExp.BinOp        -> { lhs.countUses(uses); rhs.countUses(uses) }
    is TreeExp.Mem          -> exp.countUses(uses)
    is TreeExp.ESeq         -> { stm.countUses(uses); exp.countUses(uses) }
    is TreeExp.Call         -> { func.countUses(uses); args.forEach { it.countUses(uses) }}
    is TreeExp.Temporary    -> { }
    is TreeExp.Name         -> { }
    is TreeExp.Const        -> { }
}

private val nop = Exp(Const(0))

private fun reorder(exps: List<TreeExp>): Pair<TreeStm, List<TreeExp>> = when {
    exps.isEmpty() ->
        Pair(nop, emptyList())

    exps[0] is Call -> {
        val t = Temp.gen()
        val (head, tail) = exps.splitFirst()
        reorder(cons(ESeq(Move(Temporary(t), head), Temporary(t)), tail))
    }

    else -> {
        val (head, tail) = exps.splitFirst()
        val (stms, e) = doExp(head)
        val (stms2, el) = reorder(tail)

        if (stms2.commute(e)) {
            Pair(stms + stms2, cons(e, el))
        } else {
            val t = Temp.gen()
            Pair(stms + Move(Temporary(t), e) + stms2, cons(Temporary(t), el))
        }
    }
}

private fun doStm(stm: TreeStm): TreeStm = when (stm) {
    is Seq     -> doStm(stm.lhs) + doStm(stm.rhs)
    is Jump    -> reorderStm(stm.exp) { Jump(it, stm.labels) }
    is CJump   -> reorderStm(stm.lhs, stm.rhs) { l, r -> CJump(stm.relop, l, r, stm.trueLabel, stm.falseLabel) }
    is Labeled -> stm
    is Move    -> doMove(stm)
    is Exp ->
        if (stm.exp is Call)
            reorderStm(stm.exp.func, stm.exp.args) { h, t -> Exp(Call(h, t)) }
        else
            reorderStm(stm.exp) { Exp(it) }
    else ->
        error("invalid stm $stm")
}

private fun doMove(stm: Move): TreeStm = when (stm.target) {
    is Temporary ->
        if (stm.source is Call)
            reorderStm(stm.source.func, stm.source.args) { h, t -> Move(TreeExp.Temporary(stm.target.temp), Call(h, t)) }
        else
            reorderStm(stm.source) { Move(Temporary(stm.target.temp), it) }
    is Mem ->
        reorderStm(stm.target.exp, stm.source) { t, s -> Move(Mem(t), s) }
    is ESeq ->
        doStm(Seq(stm.target.stm, Move(stm.target.exp, stm.source)))
    else ->
        error("invalid target for move: ${stm.target}")
}

private fun doExp(exp: TreeExp): Pair<TreeStm, TreeExp> = when (exp) {
    is BinOp    -> reorderExp(exp.lhs, exp.rhs) { l, r -> BinOp(exp.binop, l, r) }
    is Mem      -> reorderExp(exp.exp) { Mem(it) }
    is ESeq     -> {
        val stms = doStm(exp.stm)
        val (stms2, e) = doExp(exp.exp)
        Pair(stms + stms2, e)
    }
    is Call     -> reorderExp(exp.func, exp.args) { h, t -> Call(h, t) }
    else        -> Pair(nop, exp)
}

private inline fun reorderExp(el: List<TreeExp>, build: (List<TreeExp>) -> TreeExp): Pair<TreeStm, TreeExp> {
    val (stms, el2) = reorder(el)
    return Pair(stms, build(el2))
}

private inline fun reorderExp(e: TreeExp, build: (TreeExp) -> TreeExp): Pair<TreeStm, TreeExp> =
    reorderExp(listOf(e)) { build(it.single()) }

private inline fun reorderExp(e1: TreeExp, e2: TreeExp, build: (TreeExp, TreeExp) -> TreeExp): Pair<TreeStm, TreeExp> =
    reorderExp(listOf(e1, e2)) { build(it[0], it[1]) }

private inline fun reorderExp(e: TreeExp, el: List<TreeExp>, build: (TreeExp, List<TreeExp>) -> TreeExp): Pair<TreeStm, TreeExp> =
    reorderExp(cons(e, el)) { build(it.first(), it.tail()) }

private inline fun reorderStm(el: List<TreeExp>, build: (List<TreeExp>) -> TreeStm): TreeStm {
    val (stms, el2) = reorder(el)
    return stms + build(el2)
}

private inline fun reorderStm(e: TreeExp, build: (TreeExp) -> TreeStm): TreeStm =
    reorderStm(listOf(e)) { build(it.single()) }

private inline fun reorderStm(e1: TreeExp, e2: TreeExp, build: (TreeExp, TreeExp) -> TreeStm): TreeStm =
    reorderStm(listOf(e1, e2)) { build(it[0], it[1]) }

private inline fun reorderStm(e: TreeExp, el: List<TreeExp>, build: (TreeExp, List<TreeExp>) -> TreeStm): TreeStm =
    reorderStm(cons(e, el)) { build(it.first(), it.tail()) }

/**
 * Merge two statements into sequence, removing constant expressions.
 */
private operator fun TreeStm.plus(rhs: TreeStm): TreeStm = when {
    this.isConstExpr() -> rhs
    rhs.isConstExpr()  -> this
    else               -> Seq(this, rhs)
}

/**
 * Calculates conservative approximation to whether expressions commute.
 */
private fun TreeStm.commute(rhs: TreeExp): Boolean = when {
    this.isConstExpr()    -> true
    rhs is TreeExp.Name   -> true
    rhs is TreeExp.Const  -> true
    else                  -> false
}

/**
 * Get rid of the top-level SEQ's
 */
private fun TreeStm.linearizeTopLevel(): Sequence<TreeStm> =
    if (this is TreeStm.Seq) {
        lhs.linearizeTopLevel() + rhs.linearizeTopLevel()
    } else {
        sequenceOf(this)
    }
