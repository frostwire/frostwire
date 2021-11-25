#!/bin/bash
###############################################################################################
#
# Author: Angel Leon (@gubatron), August 22 2019
#
###############################################################################################

INCLUDE_PATH="-I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin"
# Assign DEBUG_FLAGS=${FLAGS} to add debug symbols to the builds
#DEBUG_FLAGS="-fPIC -mmacosx-version-min=10.11 -fno-strict-aliasing -fvisibility=default -D_DARWIN_C_SOURCE -g -O0 -arch x86_64 -dynamiclib"
FLAGS="-fPIC -mmacosx-version-min=10.11 -fno-strict-aliasing -fvisibility=default -D_DARWIN_C_SOURCE -Os -arch x86_64 -dynamiclib"
OUTPUT_PATH="../../native"

###############################################################################################
# libSystemUtilities.dylib
###############################################################################################
echo "Building libSystemUtilities.dylib..."
cc ${INCLUDE_PATH} ${FLAGS} -o ${OUTPUT_PATH}/libSystemUtilities.dylib SystemUtilities.m

###############################################################################################
# libMacOSXUtil.dylib
###############################################################################################
echo "Building libMacOSXUtil.dylib..."
cc ${INCLUDE_PATH} ${FLAGS} -framework Foundation -framework CoreServices -framework CoreFoundation -o ${OUTPUT_PATH}/libMacOSXUtils.dylib MacOSXUtils.m

###############################################################################################
# libGURL.dylib
###############################################################################################
echo "Building libGURL.dylib..."
cc ${INCLUDE_PATH} ${FLAGS} -framework Carbon -o ${OUTPUT_PATH}/libGURL.dylib GURLjnilib.c

git status ${OUTPUT_PATH}
