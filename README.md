# kiger

Kotlin implementation of Tiger, the language in Andrew Appel's book Modern Compiler
Implementation in ML.

## Modules

  - `compiler` - the actual compiler with X86-64 and MIPS backends
  - `vm` - a simple interpreter for a subset of MIPS assembly produced by MIPS backend

## TODO

  - add mechanism for executing integration tests
  - clean up and freeze the simple version
  - SSA
  - stack-maps
  - GC
  - boxing of escaping variables
  - first class functions
  - objects
  - generic types
  - type inference
