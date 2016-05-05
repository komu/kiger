
# runtime starts here

rt_initArray:
        li $a2, 4
        mul $a0, $a0, $a2
        li $v0, 9
        syscall
        move $v1, $v0
        add $a0, $a0, $v0
    _initArray_0:
        sw $a1, 0($v1)
        addi $v1, $v1, 4
        bne $v1, $a0, _initArray_0
        jr $ra

rt_print:
    li $v0, 4
    syscall
    jr $ra
