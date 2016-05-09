	.section	__TEXT,__text,regular,pure_instructions

	.align	4, 0x90
rt_print:                                 ## @print
	pushq	%rbp
	movq	%rsp, %rbp
	subq	$16, %rsp
#	leaq	L_.str(%rip), %rax
#	movq	%rdi, -8(%rbp)
#	movq	-8(%rbp), %rsi
#	movq	%rax, %rdi
        movq %rdi, %rsi
        leaq L_.str(%rip), %rdi
	movb	$0, %al
	callq	_printf
	#movl	%eax, -12(%rbp)         ## 4-byte Spill
	addq	$16, %rsp
	popq	%rbp
	retq

	.align	4, 0x90
rt_printi:                                ## @printi
	pushq	%rbp
	movq	%rsp, %rbp
	subq	$16, %rsp
#	leaq	L_.str.1(%rip), %rax
#	movq	%rdi, -8(%rbp)
#	movq	-8(%rbp), %rsi
#	movq	%rax, %rdi
        movq    %rdi, %rsi
        leaq    L_.str.1(%rip), %rdi
	movb	$0, %al
	callq	_printf
#	movl	%eax, -12(%rbp)         ## 4-byte Spill
	addq	$16, %rsp
	popq	%rbp
	retq

	.align	4, 0x90
rt_println:                                 ## @print
	pushq	%rbp
	movq	%rsp, %rbp
	subq	$16, %rsp
    movq    %rdi, %rsi
    leaq    L_.str.2(%rip), %rdi
	movb	$0, %al
	callq	_printf
	#movl	%eax, -12(%rbp)         ## 4-byte Spill
	addq	$16, %rsp
	popq	%rbp
	retq

    .align  4, 0x90
rt_exit:                               ## @rt_exit
    jmp    _exit

    .align  4, 0x90
rt_streq:                              ## @rt_streq
    pushq   %rbp
    movq    %rsp, %rbp
    subq    $16, %rsp
#    movq    %rdi, -8(%rbp)
#    movq    %rsi, -16(%rbp)
#    movq    -8(%rbp), %rdi
#    movq    -16(%rbp), %rsi
    callq   _strcmp
    cmpl    $0, %eax
    sete    %cl
    andb    $1, %cl
    movzbl  %cl, %eax
    addq    $16, %rsp
    popq    %rbp
    retq

    .align  4, 0x90
rt_strne:                              ## @rt_strne
    pushq   %rbp
    movq    %rsp, %rbp
    subq    $16, %rsp
#    movq    %rdi, -8(%rbp)
#    movq    %rsi, -16(%rbp)
#    movq    -8(%rbp), %rdi
#    movq    -16(%rbp), %rsi
    callq   _strcmp
    cmpl    $0, %eax
    setne   %cl
    andb    $1, %cl
    movzbl  %cl, %eax
    addq    $16, %rsp
    popq    %rbp
    retq

        .align  4, 0x90
rt_initArray:                          ## @rt_initArray
        .cfi_startproc
## BB#0:
        pushq   %rbp
Ltmp3:
        .cfi_def_cfa_offset 16
Ltmp4:
        .cfi_offset %rbp, -16
        movq    %rsp, %rbp
Ltmp5:
        .cfi_def_cfa_register %rbp
        subq    $48, %rsp
        movq    %rdi, -8(%rbp)
        movq    %rsi, -16(%rbp)
        movq    -8(%rbp), %rsi
        shlq    $3, %rsi
        movq    %rsi, -24(%rbp)
        movq    -24(%rbp), %rdi
        callq   _malloc
        movq    %rax, -32(%rbp)
        movq    $0, -40(%rbp)
LBB1_1:                                 ## =>This Inner Loop Header: Depth=1
        movq    -40(%rbp), %rax
        cmpq    -8(%rbp), %rax
        jge     LBB1_4
## BB#2:                                ##   in Loop: Header=BB1_1 Depth=1
        movq    -16(%rbp), %rax
        movq    -40(%rbp), %rcx
        movq    -32(%rbp), %rdx
        movq    %rax, (%rdx,%rcx,8)
## BB#3:                                ##   in Loop: Header=BB1_1 Depth=1
        movq    -40(%rbp), %rax
        addq    $1, %rax
        movq    %rax, -40(%rbp)
        jmp     LBB1_1
LBB1_4:
        movq    -32(%rbp), %rax
        addq    $48, %rsp
        popq    %rbp
        retq
        .cfi_endproc



	.section	__TEXT,__cstring,cstring_literals
L_.str:                                 ## @.str
	.asciz	"%s"

L_.str.1:                               ## @.str.1
	.asciz	"%lld"

L_.str.2:                               ## @.str.1
	.asciz	"\n"

.subsections_via_symbols
