
# runtime starts here

#
# Allocate an array.
#
# in:
#   a0 - size of array
#   a1 - initial value
# out:
#   v0 - pointer to start of array
#
initArray:
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

#
# Allocate a record.
#
# in:
#   a0 - amount of fields
# out:
#   v0 - pointer to the record
#

allocRecord:
    li $a2, 4
    mul $a0, $a0, $a2
    li $v0, 9
    syscall
    jr $ra

#
# Print a string.
#
# in:
#   a0 - pointer to zero-terminated string to print
#
print:
    li $v0, 4
    syscall
    jr $ra

#
# Print an integer
#
# in:
#   a0 - integer to print
#
printi:
    li $v0, 1
    syscall
    jr $ra

flush:
    jr $ra

streq:
    li $a2, 0
  streq_start:
    lb $t1, 0($a0)
    lb $t2, 0($a1)
    beq $t1, $zero, streq_end_t1
    beq $t2, $zero, streq_fail
    bne $t1, $t2, streq_fail
    addi $a0, $a0, 1
    addi $a1, $a1, 1
    j streq_start
  streq_fail:
    li $v0, 0
    xor $v0, $v0, $a2
    jr $ra
  streq_end_t1:
    bne $t2, $zero, streq_fail
    li $v0, 1
    xor $v0, $v0, $a2
    jr $ra

strne:
    li $a2, 1           # pass argument to invert the result
    j streq_start       # ... and jump into streq skipping setting default value of $a2

strcmp:
    strcmptest:
    lb $a2, 0($a0)
    lb $a3, 0($a1)
    beq $a2, $zero, strcmpend
    beq $a3, $zero, strcmpend
    bgt $a2, $a3, strcmpgreat
    blt $a2, $a3, strcmpless
    add $a0, $a0, 1
    add $a1, $a1, 1
    j strcmptest
    strcmpgreat:
    li $v0, 1
    jr $ra
    strcmpless:
    li $v0, -1
    jr $ra
    strcmpend:
    bne $a2, $zero, strcmpgreat
    bne $a3, $zero, strcmpless
    li $v0, 0
    jr $ra

# size:
#     move $v0, $zero
#     sizeloop:
#     lb $a1, 0($a0)
#     beq $a1, $zero, sizeexit
#     add $v0, $v0, 1
#     add $a0, $a0, 1
#     j sizeloop
#     sizeexit:
#     jr $ra

ord:
    lb $a1, 0($a0)
    li $v0,-1
    beqz $a1,Lrunt5
    lb $v0, 0($a0)
    Lrunt5:
    jr $ra

getchar_s:
    li $v0, 9
    li $a0, 2
    syscall
    move $a0, $v0
    li $a1, 2
    li $v0, 8
    syscall
    move $v0, $a0
    jr $ra

chr:
    move $a1, $a0
    li $v0, 9
    li $a0, 2
    syscall
    sb $a1, 0($v0)
    sb $zero, 1($v0)
    jr $ra

exit:
     li $v0, 10
     syscall

# substring:
#     add $a1, $a0, $a1
#     move $a3, $a1
#     li $v0, 9
#     add $a2, $a2, 1
#     move $a0, $a2
#     add $a0, $a0, 1
#     syscall
#     # got a new string in $v0
#     add $a2,$a2,$a3
#     add $a2,$a2,-1
#     move $a0, $v0
#     substringcopy:
#     beq $a1, $a2, substringexit
#     lb $a3, 0($a1)
#     sb $a3, 0($a0)
#     add $a1, $a1, 1
#     add $a0, $a0, 1
#     j substringcopy
#     substringexit:
#     sb $zero, 0($a0)
#     jr $ra

# copy:
#     copyloop:
#     lb $a2, 0($a1)
#     beq $zero, $a2, copyexit
#     sb $a2, 0($a0)
#     add $a0,$a0,1
#     add $a1,$a1,1
#     j copyloop
#     copyexit:
#     sb $zero, 0($a0)
#     move $v0, $a0
#     jr $ra

# concat:
#     sw $a0, -4($sp)
#     sw $a1, -8($sp)
#     sw $ra, -12($sp)
#     jal size
#     li $a3, 1
#     add $a3,$a3,$v0
#     lw $a0, -8($sp)
#     jal size
#     add $a3, $a3, $v0
#     move $a0, $a3
#     li $v0, 9
#     syscall
#     move $a3, $v0
#     move $a0, $v0
#     lw   $a1, -4($sp)
#     jal copy
#     move $a0, $v0
#     lw $a1, -8($sp)
#     jal copy
#     move $v0, $a3
#     lw $ra, -12($sp)
#     jr $ra
