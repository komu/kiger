	.section	__TEXT,__text,regular,pure_instructions

	.align	4, 0x90
rt_print:                                 ## @print
	pushq	%rbp
	movq	%rsp, %rbp
	subq	$16, %rsp
	leaq	L_.str(%rip), %rax
	movq	%rdi, -8(%rbp)
	movq	-8(%rbp), %rsi
	movq	%rax, %rdi
	movb	$0, %al
	callq	_printf
	movl	%eax, -12(%rbp)         ## 4-byte Spill
	addq	$16, %rsp
	popq	%rbp
	retq

	.align	4, 0x90
rt_printi:                                ## @printi
	pushq	%rbp
	movq	%rsp, %rbp
	subq	$16, %rsp
	leaq	L_.str.1(%rip), %rax
	movq	%rdi, -8(%rbp)
	movq	-8(%rbp), %rsi
	movq	%rax, %rdi
	movb	$0, %al
	callq	_printf
	movl	%eax, -12(%rbp)         ## 4-byte Spill
	addq	$16, %rsp
	popq	%rbp
	retq

#	.globl	_main
#	.align	4, 0x90
#_main:                                  ## @main
#    jmp main
#	pushq	%rbp
#	movq	%rsp, %rbp
#	subq	$16, %rsp
#	leaq	L_.str.2(%rip), %rdi
#	movl	$0, -4(%rbp)
#	movq	$4, -16(%rbp)
#	callq	_print
#	movq	-16(%rbp), %rdi
#	callq	_square
#	movq	%rax, %rdi
#	callq	_printi
#	leaq	L_.str.3(%rip), %rdi
#	callq	_print
#	xorl	%eax, %eax
#	addq	$16, %rsp
#	popq	%rbp
#	retq

	.section	__TEXT,__cstring,cstring_literals
L_.str:                                 ## @.str
	.asciz	"%s"

L_.str.1:                               ## @.str.1
	.asciz	"%lld"

.subsections_via_symbols
