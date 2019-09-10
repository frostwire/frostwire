#!/usr/bin/env bash

#
# This script will download the latest jlibtorrent .jar builds
# It will place the android jars in the android/libs/ project
# It will place the java .jar on desktop/lib/jars and it will copy the
# necessary shared libraries (.so, .dll, .dylib) into desktop/lib/native

if [[ ! -n ${JLIBTORRENT_ROOT} ]]; then
  echo
  echo "Error JLIBTORRENT_ROOT not set. Aborting jlibtorrent update"
  echo
  exit 1
fi

if [[ ! -f ${JLIBTORRENT_ROOT}/swig/package-remote-build.sh ]]; then
  echo
  echo "Error JLIBTORRENT's swig/package-remote-build.sh script not found."
  echo "Try: git clone git@github.com:frostwire/frostwire-jlibtorrent ${JLIBTORRENT_ROOT}"
  echo "and then run this script again"
  echo
  exit 2
fi

pushd ${JLIBTORRENT_ROOT}/swig
source package-remote-build.sh
source build-utils.shinc
popd

abort_if_var_unset "JLIBTORRENT_VERSION" ${JLIBTORRENT_VERSION}

if [[ ! -d ${JLIBTORRENT_ROOT}/build/libs ]]; then
  prompt_msg "Error: jlibtorrent builds not ready, check jlibtorrent's swig/package-remote-build.sh for logic errors"
  exit 3
fi

# Android
rm android/libs/jlibtorrent-*.jar
cp ${JLIBTORRENT_ROOT}/build/libs/jlibtorrent-${JLIBTORRENT_VERSION}.jar android/libs/
cp ${JLIBTORRENT_ROOT}/build/libs/jlibtorrent-android-arm-${JLIBTORRENT_VERSION}.jar android/libs/
cp ${JLIBTORRENT_ROOT}/build/libs/jlibtorrent-android-x86-${JLIBTORRENT_VERSION}.jar android/libs/

# Desktop (ALL) - The java classes jar
cp ${JLIBTORRENT_ROOT}/build/libs/jlibtorrent-${JLIBTORRENT_VERSION}.jar desktop/lib/jars/

# Windows dlls for both x86 and x86_64
cp ${JLIBTORRENT_ROOT}/swig/bin/release/windows/x86_64/jlibtorrent.dll desktop/lib/native/jlibtorrent.dll

# MacOS only x86_64
cp ${JLIBTORRENT_ROOT}/swig/bin/release/macosx/x86_64/libjlibtorrent.dylib desktop/lib/native/

# Linux
cp ${JLIBTORRENT_ROOT}/swig/bin/release/linux/x86_64/libjlibtorrent.so desktop/lib/native/libjlibtorrent.so
