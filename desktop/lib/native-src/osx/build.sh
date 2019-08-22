#!/bin/bash
INCLUDE_PATH="-I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin"
FLAGS="-fPIC -mmacosx-version-min=10.11 -fno-strict-aliasing -fvisibility=default -D_DARWIN_C_SOURCE -Os -arch x86_64 -dynamiclib"
OUTPUT_PATH="../../native"

echo "Building libSysteUtilities.dylib..."
cc ${INCLUDE_PATH} ${FLAGS} -o ${OUTPUT_PATH}/libSystemUtilities.dylib SystemUtilities.m

echo "Building libMacOSXUtil.dylib..."
cc ${INCLUDE_PATH} ${FLAGS} -framework Foundation -framework CoreServices -framework CoreFoundation -o ${OUTPUT_PATH}/libMacOSXUtils.dylib MacOSXUtils.m

echo "Building libGURL.dylib..."
cc ${INCLUDE_PATH} ${FLAGS} -framework Carbon -o ${OUTPUT_PATH}/libGURL.dylib GURLjnilib.c

git status ${OUTPUT_PATH}
