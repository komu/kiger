package kiger.regalloc

import kiger.assem.Instr
import kiger.frame.FrameType
import kiger.frame.Register
import kiger.temp.Temp

data class Allocation(val registerAssignments: Map<Temp, Register>) {
    operator fun get(t: Temp): Register? = registerAssignments[t]
    fun name(t: Temp): String =
        registerAssignments[t]?.name ?: t.name

    fun enter(t: Temp, r: Register): Allocation =
        Allocation(registerAssignments + (t to r))
}

tailrec fun List<Instr>.allocateRegisters(frameType: FrameType): Pair<List<Instr>, Allocation> {
    val graph = this.createFlowGraph()
    val igraph = graph.interferenceGraph()

    fun spillCost(temp: Temp): Double {
        val numDu = graph.nodes.sumBy { n -> n.def.containsToInt(temp) + n.use.containsToInt(temp) }
        val node = igraph.graph.find { it.temp == temp } ?: error("could not find node for $temp")
        val interferes = node.adj.size

        return numDu.toDouble() / interferes.toDouble()
    }


    // TODO

    val (allocTable, spills) = color(igraph, frameType.tempMap, ::spillCost, frameType.registers)

    fun Instr.isRedundant() = when (this) {
        is Instr.Move -> allocTable[dst] == allocTable[src]
        else          -> false
    }

    return if (spills.isEmpty())
        Pair(filterNot { it.isRedundant() }, allocTable)
    else
        rewrite(this, frameType, spills).allocateRegisters(frameType)
}

private fun rewrite(instrs: List<Instr>, frameType: FrameType, spills: Any): List<Instr> {
    TODO()
}
/*

fun rewrite (instrs:A.instr list, frame, spills) : A.instr list =
    let
      (* rewrite one temp *)
      fun rewrite1 (instrs:A.instr list, t:T.temp) =
          let
            val ae = Frame.exp (Frame.allocLocal(frame) true) (Tr.TEMP Frame.FP)

            (* generate fetch or store instruction *)
            fun gen_instrs (is_def:bool, t:T.temp) =
                if is_def then MipsGen.codegen(frame)(Tr.MOVE(ae,Tr.TEMP t))
                else MipsGen.codegen(frame)(Tr.MOVE(Tr.TEMP t,ae))

            (* allocate new temp for each occurence of t in dus, replace
             * the occurence with the new temp. *)
            fun alloc_du (is_def:bool, dus:T.temp list, t) =
                if List.exists (fn (t') => t = t') dus then
                  let val nt = T.newtemp () in
                    (gen_instrs(is_def,nt),
                     map (fn (t') => if t = t' then nt else t') dus)
                  end
                else ([],dus)

            (* transform one instruction for one spilled temp *)
            fun trans_instr instr =
                case instr of
                    A.OPER{assem,dst,src,jump} =>
                    let val (store,dst') = alloc_du(true,dst,t)
                        val (fetch,src') = alloc_du(false,src,t)
                    in (fetch@[A.OPER{assem=assem,dst=dst',
                                      src=src',jump=jump}]@store)
                    end
                  | A.MOVE{assem,dst,src} =>
                    let val (store,[dst']) = alloc_du(true,[dst],t)
                        val (fetch,[src']) = alloc_du(false,[src],t)
                    in (fetch@[A.MOVE{assem=assem,dst=dst',src=src'}]@store)
                    end
                  | instr => [instr]
          in
            List.foldl (fn (i,acc) => acc @ trans_instr i) nil instrs
          end

      val format0 = A.format(Temp.makestring)
    in
      List.foldl (fn (t,ins) => rewrite1(ins,t)) instrs spills
    end

 */

private fun <T> Collection<T>.containsToInt(t: T) = if (t in this) 1 else 0
