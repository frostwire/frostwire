// This is a sample C program that uses libc's uname() function to print the current
// CPU architecture.
//
// The build script must do something like this:
//
// cc machine_name.c -o machine_name.x86_64 -lc -arch x86_64
// cc machine_name.c -o machine_name.arm64  -lc -arch arm64
// lipo -create -output machine_name.universal machine_name.x86_64 machine_name.arm64
//
///Library/Developer/CommandLineTools/SDKs/MacOSX11.3.sdk/usr/include/sys/utsname.h
#include <sys/utsname.h>
#include <stdlib.h> //malloc
#include <stdio.h>
#include <errno.h>

int main(int args, char** argv) {
  struct utsname *name = malloc(sizeof(struct utsname));
  if (-1 == uname(name)) {
    printf("uname() failed. error:%d\n", errno);
    return errno;
  }
  printf("machine: %s\n", name->machine);
  return 0;
}
