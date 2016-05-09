#include <stdio.h>
#include <stdlib.h>
#include <string.h>

long long ord(const char* s) {
  return s[0];
}

const char* chr(char c) {
    char*s = malloc(2);
    s[0] = c;
    s[1] = 0;
    return s;
}

const char* getchar_s() {
    char*s = malloc(2);
    s[0] = getchar();
    s[1] = 0;
    return s;
}

void* initArray(long long len, long long initial) {
  long long s = len * sizeof(void*);
  long long* ptr = malloc(s);

  for (long long i = 0; i < len; i++)
    ptr[i] = initial;

  return ptr;
}

void* allocRecord(long long size) {
    return malloc(size);
}

void printi(long long i) {
  printf("%lld", i);
}

void print(const char* s) {
  printf("%s", s);
}

int streq(const char* s1, const char* s2) {
  return strcmp(s1, s2) == 0;
}

int strne(const char* s1, const char* s2) {
  return strcmp(s1, s2) != 0;
}
