package kiger.tree

enum class RelOp {
    EQ, NE, LT, GT, LE, GE, ULT, ULE, UGT, UGE;

    fun not() = when (this) {
        EQ -> NE
        NE -> EQ
        LT -> GE
        GT -> LE
        LE -> GT
        GE -> LT
        ULT -> UGE
        ULE -> UGT
        UGT -> ULE
        UGE -> ULT
    }

    fun commute() = when (this) {
        EQ -> EQ
        NE -> NE
        LT -> GT
        GE -> LE
        GT -> LT
        LE -> GE
        ULT -> UGT
        ULE -> UGE
        UGT -> ULT
        UGE -> ULE
    }
}
