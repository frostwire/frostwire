#!/bin/bash
INCLUDE_PATH="-I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin"
FLAGS="-fPIC -mmacosx-version-min=10.11 -fno-strict-aliasing -fvisibility=default -D_DARWIN_C_SOURCE -Os -arch x86_64 -dynamiclib"

echo "Building libSysteUtilities.dylib..."
cc ${INCLUDE_PATH} ${FLAGS} -o ../../native/libSystemUtilities.dylib SystemUtilities.m

echo "Building libMacOSXUtil.dylib..."
cc ${INCLUDE_PATH} ${FLAGS} -framework Foundation -framework CoreServices -framework CoreFoundation -o ../../native/libMacOSXUtils.dylib MacOSXUtils.m

echo "Building libGURL.dylib..."
cc ${INCLUDE_PATH} ${FLAGS} -framework Carbon -o ../../native/libGURL.dylib GURLjnilib.c

if [ -f a.out ]; then
    rm -f a.out
fi
