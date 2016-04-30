package kiger.assem

import kiger.temp.Label
import kiger.temp.Temp

private val registerRegex = Regex("`([sdj])(\\d+)")

sealed class Instr {

    open val isJump: Boolean
        get() = false

    class Lbl(val assem: String, val label: Label) : Instr() {
        override fun toString() = assem
    }

    class Oper(val assem: String, val dst: List<Temp> = emptyList(), val src: List<Temp> = emptyList(), val jump: List<Label>? = null) : Instr() {

        override val isJump: Boolean
            get() = jump != null

        override fun toString(): String =
            "    " + registerRegex.replace(assem) { m ->
                val type = m.groupValues[1]
                val index = m.groupValues[2].toInt()
                when (type) {
                    "s" -> src[index].name
                    "d" -> dst[index].name
                    "j" -> jump!![index].name
                    else -> error("invalid type '$type'")
                }
            }
    }

    class Move(val assem: String, val dst: Temp, val src: Temp) : Instr() {
        override fun toString() = assem
    }
}

/*
structure Assem = struct

  type reg = string
  type temp = Temp.temp
  type label = Temp.label

  datatype instr = OPER of {assem: string,
			                      dst: temp list,
			                      src: temp list,
			                      jump: label list option}
                 | LABEL of {assem: string, lab: Temp.label}
                 | MOVE of {assem: string,
			                      dst: temp,
			                      src: temp}

  fun format saytemp =
    let fun speak(assem,dst,src,jump) =
      let val saylab = Symbol.name
        fun f(#"`":: #"s":: i::rest) =
		        (explode(saytemp(List.nth(src,ord i - ord #"0"))) @ f rest)
		      | f( #"`":: #"d":: i:: rest) =
		        (explode(saytemp(List.nth(dst,ord i - ord #"0"))) @ f rest)
		      | f( #"`":: #"j":: i:: rest) =
		        (explode(saylab(List.nth(jump,ord i - ord #"0"))) @ f rest)
		      | f( #"`":: #"`":: rest) = #"`" :: f rest
		      | f( #"`":: _ :: rest) = ErrorMsg.impossible "bad Assem format"
		      | f(c :: rest) = (c :: f rest)
		      | f nil = nil
	    in implode(f(explode assem))
	    end
    in fn OPER{assem,dst,src,jump=NONE} => speak(assem,dst,src,nil)
        | OPER{assem,dst,src,jump=SOME j} => speak(assem,dst,src,j)
	      | LABEL{assem,...} => assem
	      | MOVE{assem,dst,src} => speak(assem,[dst],[src],nil)
    end
end
*/
