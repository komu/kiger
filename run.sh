#!/bin/sh

set -e

if [ -z $1 ]; then
    echo "usage: ./run.sh program"
    exit 1
fi

./gradlew :compiler:shadowJar
time java -jar compiler/build/libs/compiler-all.jar samples/$1.tig out/$1.s
cc out/$1.s runtime/build/binaries/runtimeStaticLibrary/libruntime.a -o out/$1
out/$1

