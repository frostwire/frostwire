#!/bin/bash
###############################################################################################
#
# Author: Angel Leon (@gubatron), August 22 2019
#
###############################################################################################

INCLUDE_PATH="-I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin"
FLAGS_X86_64="-fPIC -mmacosx-version-min=11.5 -fno-strict-aliasing -fvisibility=default -D_DARWIN_C_SOURCE -Os -arch x86_64 -dynamiclib"
FLAGS_ARM64="-fPIC -mmacosx-version-min=11.5 -fno-strict-aliasing -fvisibility=default -D_DARWIN_C_SOURCE -Os -arch arm64 -dynamiclib"
# Assign DEBUG_FLAGS=${FLAGS} to add debug symbols to the builds
#DEBUG_FLAGS_X86_64="-fPIC -mmacosx-version-min=11.5 -fno-strict-aliasing -fvisibility=default -D_DARWIN_C_SOURCE -g -O0 -arch x86_64 -dynamiclib"
#FLAGS_X86_64=${DEBUG_FLAGS_X86_64}
OUTPUT_PATH="../../native"

###############################################################################################
# libMacOSXUtil.dylib
###############################################################################################
echo "Building libMacOSXUtil.dylib..."
cc ${INCLUDE_PATH} ${FLAGS_X86_64} -framework Foundation -framework CoreServices -framework CoreFoundation -o libMacOSXUtils.x86_64 MacOSXUtils.m
cc ${INCLUDE_PATH} ${FLAGS_ARM64}  -framework Foundation -framework CoreServices -framework CoreFoundation -o libMacOSXUtils.arm64  MacOSXUtils.m
lipo -create -output ${OUTPUT_PATH}/libMacOSXUtils.dylib libMacOSXUtils.x86_64 libMacOSXUtils.arm64

###############################################################################################
# libGURL.dylib
###############################################################################################
echo "Building libGURL.dylib..."
cc ${INCLUDE_PATH} ${FLAGS_X86_64} -framework Carbon -o libGURL.x86_64 GURLjnilib.c
cc ${INCLUDE_PATH} ${FLAGS_ARM64}  -framework Carbon -o libGURL.arm64  GURLjnilib.c
lipo -create -output ${OUTPUT_PATH}/libGURL.dylib libGURL.x86_64 libGURL.arm64

rm lib*.arm64
rm lib*.x86_64

git status ${OUTPUT_PATH}
