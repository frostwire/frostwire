#!/bin/bash
###############################################################################################
#
# Author: Angel Leon (@gubatron), August 22 2019
#
###############################################################################################

INCLUDE_PATH="-I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin"
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


###############################################################################################
# libDispatch.dylib
###############################################################################################
# This one needs MacOS SDK's JavaNativeFoundation headers
# For MacOS 10.14 I installed them this way on a fresh machine:
#
# https://developer.apple.com/download/more/
# Downloaded Command Line Tools (macOS 10.14) for XCode 10.3, released Jul 22, 2019
# and installed that package, as xcode-select --install no longer works. Not sure if this did it
# or if this made the macOS_SDK_headers_for_macOS_10.14.pkg available, but then I found it
# inside /Library/Developer/CommandLineTools/Packages
# so I did:
# 
# $ cd /Library/Developer/CommandLineTools/Packages
# $ open macOS_SDK_headers_for_macOS_10.14.pkg
#
# After this last step I finally had all the JNF (JavaNativeFoundation) apple headers
# /System/Library/Frameworks/JavaVM.framework/Frameworks/JavaNativeFoundation.framework/Versions/Current/Headers
# or here at the MacOS SDK folders
# /Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/System/Library/Frameworks/JavaVM.framework/Versions/A/Frameworks/JavaNativeFoundation.framework/Headers
#

# JavaNativeFoundation.h, which includes all of the JNF*.h headers
MACOS_JNF_HEADERS_PATH="-I/Library/Developer/CommandLineTools/SDKs/MacOSX.sdk/System/Library/Frameworks/JavaVM.framework/Frameworks/JavaNativeFoundation.framework/Headers"

# Hardcoded path of the actual JavaNativeFoundation library, link directly against it, as "-framework JavaNativeFoundation" did not work
JNF_LIB="/System/Library/Frameworks/JavaVM.framework/Versions/A/Frameworks/JavaNativeFoundation.framework/Versions/A/JavaNativeFoundation"
cc ${MACOS_JNF_HEADERS_PATH} ${FLAGS} ${JNF_LIB} -framework Cocoa -o ${OUTPUT_PATH}/libDispatch.dylib Dispatch.m

git status ${OUTPUT_PATH}
